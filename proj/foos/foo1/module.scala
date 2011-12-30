
import com.sun.net.httpserver._
import java.net._
import scala.util.Random
import java.io._

import misty._

class Module(env: ModuleEnv) extends misty.Module {
    def handle(ex: HttpExchange) {
        foocommon.handle(env, ex)
    }
}
object Module extends MetaModule {
    val name = "foo1"
    val version = foocommon.version
}

