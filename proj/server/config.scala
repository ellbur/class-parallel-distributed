
package misty

import scala.xml._
import java.net._

object Config {
    val doc = XML loadFile "properties.xml"
    
    val primaryHost = (doc\\"properties"\"@primaryHost").text
    val primaryPort = (doc\\"properties"\"@primaryPort").text.toInt
    val redirectStyle = (doc\\"properties"\"@redirectStyle").text match {
        case "flat"    => 'flat
        case "convex"  => 'convex
        case "concave" => 'concave
    }
    val takeProbability = (doc\\"properties"\"@takeProbability").text.toDouble
    
    val peers = Vector((doc\\"peers"\"peer") map { node =>
        Peer((node\"@host").text, (node\"@port").text.toInt)
    }: _*)
    
    val host = InetAddress.getLocalHost.getHostName
}

