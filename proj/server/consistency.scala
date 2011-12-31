
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

class ConsistencyMultiClient {
    def run(duration: Double): Seq[(String,List[Result])] = {
        val apps = Vector("foo1", "foo2", "foo3", "foo4", "foo5")
        val numClients = 12
        val clients = (1 to numClients) map { n =>
            val app = apps(Random nextInt apps.length)
            new Client(n, app)
        }
        clients foreach (_.start)
        
        val done = Ref[Boolean](false)
        
        Thread sleep (duration*1000).intValue
        
        clients foreach (_.stop)
        clients foreach (_.block)
        clients map { client =>
            (client.app, client.getResults)
        }
    }
    
    case class Result(num: Int)
    
    class Client(id: Int, val app: String) {
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
                        result foreach addResult _
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
            val max = 3000
            val pause = Random nextInt max
            
            Thread sleep pause
        }
        
        private def doRequest(url: String) = {
            def requestIter
                (url: String, numRedirects: Int): (String, Option[Result]) =
            {
                if (numRedirects > maxRedirects) (url, None)
                else doOneStep(url) match {
                    case OK(num) => (url, Some(Result(num)))
                    case Redirect(to) => requestIter(to, numRedirects+1)
                    case StepError    => (url, None)
                }
            }
            
            requestIter(url, 0)
        }
        
        sealed trait Step
        case class OK(num: Int) extends Step
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
                        val num = (Source fromInputStream conn.getInputStream).mkString.toInt
                        println("%s(%d): %d" format (app, id, num))
                        OK(num)
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

object consistency {
    // ------------------------------------------
    // Main
    
    def main(args: Array[String]) {
        val mc = new ConsistencyMultiClient
        val time = 60
        val allResults = mc run time
        
        val counts = allResults groupBy (_._1) map { case (app, results) =>
            val numSets = results map (_._2 map (_.num))
            numSets foreach println _
            val nums = numSets.flatten
            println(nums)
            
            val max = nums.max + 1
            val unique = Set(nums:_*).size
            val total = nums.length
            
            val error = max + total - 2*unique
            
            println()
            (total, error)
        }
        
        val total = counts map (_._1) sum
        val error = counts map (_._2) sum
        val rate = error.doubleValue / total
        
        println("%d,%d,%.3f" format (
            total,
            error,
            rate
        ))
    }
}

// 
// 1 2 3 4 5 6 7 8
// max    = 8
// total  = 8
// unique = 8
// error  = 0
// 
// 1 2 3 1 2 3 4 5
// max    = 5
// total  = 8
// unique = 5
// error  = 3
// 
// 1 2 3 1 2 3 5 6
// max    = 6
// total  = 8
// unique = 5
// error  = 4
// 
// 1 2 3 4 6 7 8 8
// max    = 8
// total  = 8
// unique = 7
// error  = 2
// 
// error = (max - unique) + (total - unique)
//       = max + total - 2*unique
// 

