
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

class MultiClient(level: Int) {
    def run(duration: Double): List[Result] = {
        val apps = Vector("foo1", "foo2", "foo3", "foo4", "foo5")
        val clients = (1 to 50) map { _ =>
            val app = apps(Random nextInt apps.length)
            new Client(app)
        }
        println("Starting")
        clients foreach (_.start)
        
        val done = Ref[Boolean](false)
        
        Signal handle (new Signal("INT"), new SignalHandler {
            def handle(sig: Signal) {
                println("SIGINT, Stopping...")
                if (atomic(done.get)) System exit 1
                atomic(done set true)
            }
        })
        
        {
            implicit val waiting = TransactionFactory(
                blockingAllowed=true, trackReads=true, timeout=duration seconds)
            try
                atomic(if (!done.get) retry)
            catch {
                // Timeout is OK
                case _: Exception => println("Reached natural end")
            }
        }
        
        println("Stopping")
        clients foreach (_.stop)
        println("Blocking")
        clients foreach (_.block)
        println("Done")
        getResults
    }
    
    val results = Ref[List[Result]](Nil)
    def addResult(r: Result) {
        try atomic {
            results alter (r :: _)
        }
        catch { case _: Exception =>
            // Whatever; not a big deal
        }
    }
    def getResults = atomic {
        results.get
    }.reverse
    
    sealed trait Result
    case object TooManyRedirects extends Result
    case class Success(time: Double) extends Result
    case object ResultError extends Result
    
    class Client(app: String) {
        val startURL = "http://"+Config.primaryHost+":"+Config.primaryPort+"/"+app
        val maxRedirects = 5
        private val fStop = Ref[Boolean](false)
        private val fDone = Ref[Boolean](false)
        
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
                blockingAllowed=true, trackReads=true, timeout=1000 seconds)
            atomic {
                if (!fDone.get) retry
            }
        }
        
        private def run(self: ActorRef) {
            def runIter(url: String) {
                doRequest(url) match {
                    case (nextURL, result) =>
                        addResult(result)
                        pause()
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
            val max = (100 - level)*4
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
                    case OK => (url, Success {
                        val now = System.currentTimeMillis
                        (now - startTime).doubleValue / 1e3
                    })
                    case Redirect(to) => requestIter(to, numRedirects+1)
                    case StepError    => (url, ResultError)
                }
            }
            
            requestIter(url, 0)
        }
        
        sealed trait Step
        case object OK extends Step
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
                        OK
                    case 301 =>
                        conn getHeaderField "Location" match {
                            case null =>
                                println("Redirect without location")
                                StepError
                            case to => Redirect(to)
                        }
                    case 404 =>
                        println("That's not good it couldn't find it!")
                        StepError
                    case 503 =>
                        StepError
                    case other =>
                        println("I don't know " + other)
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
        List(0, 20, 40, 60, 80, 100) map { level =>
            println("-- %d --" format level)
            val mc = new MultiClient(level)
            val results = mc run 5.0
            
            val succeeded = (results collect {case _: mc.Success=>()}).length
            val total = results.length
            
            println("Succeeded: %.0f%% (%d/%d)" format (
                succeeded*100. / (results.length), succeeded, total
            ))
        }
    }
}

