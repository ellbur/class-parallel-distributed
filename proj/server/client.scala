
package misty

import java.net._

import akka.stm._
import akka.actor._
import akka.actor.Actor._
import akka.util.duration._
import akka.dispatch.Dispatchers

import sun.misc.{Signal, SignalHandler}
import org.apache.commons.math.stat.StatUtils._
import scala.util.Random
import scala.io.Source

import System.{err => stderr}

class MultiClient(level: Int) {
    def run(duration: Double): List[Result] = {
        val apps = Vector("foo1", "foo2", "foo3", "foo4", "foo5")
        val numClients = 5 + level*2
        val clients = (1 to numClients) map { _ =>
            val app = apps(Random nextInt apps.length)
            new Client(app)
        }
        clients foreach (_.start)
        
        val done = Ref[Boolean](false)
        
        Thread sleep (duration*1000).intValue
        
        clients foreach (_.stop)
        clients foreach (_.block)
        clients flatMap (_.getResults) toList
    }
    
    sealed trait Result
    case object TooManyRedirects extends Result
    case class Success(time: Double, numRedirects: Int, times: (Int,Int,Int,Int)) extends Result
    case object ResultError extends Result
    
    class Client(app: String) {
        val startURL = "http://"+Config.primaryHost+":"+Config.primaryPort+"/"+app
        val maxRedirects = 5
        private val fStop = Ref[Boolean](false)
        private val fDone = Ref[Boolean](false)
        
        private var results: List[Result] = Nil
        def addResult(r: Result) {
            results = r :: results
        }
        def getResults = results
        
        def start() {
            // Do this with an actor and an explicit dispatcher just to be
            // *really sure* that this is creating a new thread.
            val actor = actorOf(new Actor {
                self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)
                def receive = { case _ => run(self) }
            })
            actor.start
            actor ! ()
        }
        
        def stop() {
            atomic(fStop set true)
        }
        
        def block() {
            // http://akka.io/docs/akka/1.2/scala/stm.html#blocking-transactions
            implicit val blocking = TransactionFactory(
                blockingAllowed=true, trackReads=true, timeout=10 seconds)
            try {
                atomic {
                    if (!fDone.get) retry
                }
            }
            catch { case _: Exception =>
                // Timeout. Whatevs
            }
        }
        
        private def run(self: ActorRef) {
            def runIter(url: String) {
                pause
                doRequest(url) match {
                    case (nextURL, result) =>
                        addResult(result)
                        if (atomic(fStop.get)) ()
                        else
                            runIter(nextURL)
                }
            }
            
            try runIter(startURL)
            finally {
                atomic(fDone set true)
                self.stop
            }
        }
        
        private def pause() {
            val max = (100 - level)*20
            val pause =
                if (max > 0) Random nextInt max
                else 0
                
            Thread sleep pause
        }
        
        private def doRequest(url: String) = {
            val startTime = System.currentTimeMillis
            
            def requestIter
                (url: String, numRedirects: Int): (String, Result) =
            {
                if (numRedirects > maxRedirects) (url, TooManyRedirects)
                else doOneStep(url) match {
                    case OK(times) =>
                        val now = System.currentTimeMillis
                        val time = (now - startTime).doubleValue / 1e3
                        (url, Success(time, numRedirects, times))
                        
                    case Redirect(to) => requestIter(to, numRedirects+1)
                    case StepError    => (url, ResultError)
                }
            }
            
            requestIter(url, 0)
        }
        
        sealed trait Step
        case class OK(times: (Int,Int,Int,Int)) extends Step
        case class Redirect(to: String) extends Step
        case object StepError extends Step
        
        private def doOneStep(url: String): Step = {
            val conn = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
            conn.setRequestMethod("GET")
            conn.setInstanceFollowRedirects(false)
            
            try {
                val responseCode = conn.getResponseCode
                responseCode match {
                    case 200 =>
                        val text = (Source fromInputStream conn.getInputStream).mkString
                        val texts = text split "\\," toList
                        val times = texts map (_.toInt)
                        
                        times match {
                            case List(t1,t2,t3,t4) => OK((t1,t2,t3,t4))
                            case _ =>
                                println("Bad times " + text)
                                StepError
                        }
                    case 301 =>
                        conn getHeaderField "Location" match {
                            case null =>
                                stderr.println("Redirect without location")
                                StepError
                            case to => Redirect(to)
                        }
                    case 404 =>
                        stderr.println("That's not good it couldn't find it!")
                        StepError
                    case 503 =>
                        // Server overloaded. Treat as redirect.
                        Redirect(url)
                    case other =>
                        stderr.println("I don't know " + other)
                        StepError
                }
            }
            catch { case e: SocketException =>
                StepError
            }
        }
        
    }
}

object client {
    // ------------------------------------------
    // Main
    
    def main(args: Array[String]) {
        0 to (100, 4) map { level =>
            val mc = new MultiClient(level)
            val time = 10.0
            val results = mc run time
            
            val succeeded = (results collect {case _: mc.Success=>()}).length
            val attempted = results.length
            
            val times = results collect {
                case mc.Success(t, _, _) => t
            } toArray
            val redirects = results collect {
                case mc.Success(_, r, _) => r
            } toArray
            
            val parts = results collect {
                case mc.Success(_, _, p) => p
            }
            
            println("%d,%s,%.2f,%d,%.1f,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f" format (
                Config.peers.length,
                Config.redirectStyle,
                Config.takeProbability,
                level,
                time,
                succeeded,
                attempted,
                percentile(times, 25),
                percentile(times, 50),
                percentile(times, 75),
                redirects.sum.doubleValue / redirects.length,
                (parts map (_._1)).sum.doubleValue / succeeded,
                (parts map (_._2)).sum.doubleValue / succeeded,
                (parts map (_._3)).sum.doubleValue / succeeded,
                (parts map (_._4)).sum.doubleValue / succeeded
            ))
        }
    }
}

