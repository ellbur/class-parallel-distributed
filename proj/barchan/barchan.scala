
import com.sun.net.httpserver._
import java.net._
import scala.util.Random
import java.io._
import scala.io.Source

import misty._

// Query DSL
import misty.Poodle._

class Module(env: ModuleEnv) extends misty.Module {
    def handle(ex: HttpExchange) {
        val path = ex.getRequestURI.getPath.split("\\/").toList
        path match {
            case _::_::"add"::_ =>
                addWord(ex)
                
            case _ =>
                // Nothing
        }
        
        servePage(ex)
    }
    
    def addWord(ex: HttpExchange) {
        import env._
        
        def content = Source fromInputStream ex.getRequestBody mkString
        val query = Map((content split "\\&" toList) map (_ split "\\=" toList) collect {
            case key :: value :: Nil => key -> value
        }: _*)
        
        println("Query is " + query)
        
        query get "word" foreach { word =>
            runTransaction {
                insert("bar" =: word)
            }
        }
    }
    
    def servePage(ex: HttpExchange) {
        import env._
        
        lazy val html =
            <html>
                <head><title>Barchan</title></head>
                <body>{body}</body>
            </html>
        
        lazy val body = {
            val items = runTransaction { select("bar" ~: ___) } collect {
                case (_ ~: PString(x) ~: _) => x
            }
            <div>
                <p>Items: {items.reverse mkString ", "}</p>
                <form method="POST" action="/barchan/add">
                    <p>Add another:
                        <input type="text" name="word"/>
                        <input type="submit" value="Add"/>
                    </p>
                </form>
            </div>
        }
        
        val resp = html.toString
        val bytes = resp.getBytes
        ex.sendResponseHeaders(200, bytes.length)
        try {
            val out = ex.getResponseBody
            out.write(bytes)
            out.close
        }
        catch { case _: IOException => }
    }
}
object Module extends MetaModule {
    val name = "barchan"
    val version = "5"
}


