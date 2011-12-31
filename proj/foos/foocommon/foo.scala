
import com.sun.net.httpserver._
import java.net._
import scala.util.Random
import java.io._

import misty._
// Query DSL
import misty.Poodle._

object foocommon {
    val version = "5"
    
    def handle(env: ModuleEnv, ex: HttpExchange) {
        import env._
        
        // Database request
        val (t1, num) = time(env.runTransaction {
            select("foo" ~: ___) flatMap {
                case Nil =>
                    for {
                        _ <- insert("foo" =: 1)
                    }
                    yield 0l
                case (_ ~: PInt(x) ~: _)::_ =>
                    for {
                        _ <- env.delete("foo" ~: ___)
                        _ <- env.insert("foo" =: (x+1))
                    }
                    yield x
            }
        })
        
        // Some random delay
        val (t2, _) = time {
            Thread sleep (Random nextInt 50)
        }
        
        // Some disk IO
        val (t3, _) = time {
            (1 to 20) foreach { n =>
                val file = File createTempFile ("foo-"+n, ".txt")
                val out = new PrintWriter(new FileOutputStream(file))
                out.println(n)
                out.flush
                out.close
                file.delete()
            }
        }
        
        // Some computation
        val (t4, zeta) = time {
            val terms = (1 to 50000) map { i =>
                scala.math.pow(i, -(2+num))
            }
            terms.sum
        }
        
        // Make sure it doesn't optimize that out:
        if (zeta != 2.117) {
            val resp = ("%d,%d,%d,%d" format (t1,t2,t3,t4)).getBytes
            ex.sendResponseHeaders(200, resp.length)
            try {
                val out = ex.getResponseBody
                out.write(resp)
                out.close
            }
            catch { case _: IOException => }
        }
    }
    
    def time[A](a: => A): (Long, A) = {
        val start = System.currentTimeMillis
        val x = a
        val end = System.currentTimeMillis
        (start-end, x)
    }
}

