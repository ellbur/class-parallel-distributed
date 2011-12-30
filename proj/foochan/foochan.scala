
import com.sun.net.httpserver._
import java.net._
import scala.util.Random
import java.io._

import misty._
// Query DSL
import misty.Poodle._

class Module(env: ModuleEnv) extends misty.Module {
    def handle(ex: HttpExchange) {
        import env._
        
        Thread sleep (Random nextInt 200)
        
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
        
        val resp = "Hi %d\n" format num
        ex.sendResponseHeaders(200, resp.length)
        try {
            val out = ex.getResponseBody
            out.write(resp.getBytes)
            out.close
        }
        catch { case _: IOException =>
            // This is not really a problem, it just means
            // the client closed the connection before we could write the
            // data
        }
    }
}
object Module extends MetaModule {
    val name = "foochan"
    val version = "10"
}

