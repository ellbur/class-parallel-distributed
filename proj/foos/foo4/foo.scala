
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
        val num = env.runTransaction {
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
        }
        
        // Some random delay
        Thread sleep (Random nextInt 50)
        
        // Some disk IO
        (1 to 20) foreach { n =>
            val file = File createTempFile ("foo-"+n, ".txt")
            val out = new PrintWriter(new FileOutputStream(file))
            out.println(n)
            out.flush
            out.close
            file.delete()
        }
        
        // Some computation
        val terms = (1 to 50000) map { i =>
            scala.math.pow(i, -(2+num))
        }
        val zeta = terms.sum
        
        // Make sure it doesn't optimize that out:
        if (zeta != 2.117) {
            val resp = num.toString.getBytes
            ex.sendResponseHeaders(200, resp.length)
            try {
                val out = ex.getResponseBody
                out.write(resp)
                out.close
            }
            catch { case _: IOException => }
        }
    }
}

