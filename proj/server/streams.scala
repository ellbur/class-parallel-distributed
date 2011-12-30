
package misty

import java.io._
import scala.io._

object streams {
//

implicit def pipeTo(in: InputStream) = new {
    def pipeTo(out: OutputStream) {
        iterate(in) foreach out.write _
        in.close
        out.close
    }
    
    def iterate(in: InputStream) =
        Stream.continually(in.read) takeWhile (_ != -1)
}

//
}

