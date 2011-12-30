
package misty

import scala.xml._
import java.net._

object Config {
    val stream = getClass.getClassLoader.getResourceAsStream("properties.xml")
    val doc = XML load stream
    
    val primaryHost = (doc\\"properties"\"@primaryHost").text
    val primaryPort = (doc\\"properties"\"@primaryPort").text.toInt
    val redirectStyle = (doc\\"properties"\"@redirectStyle").text match {
        case "flat"    => 'flat
        case "convex"  => 'convex
        case "concave" => 'concave
    }
    val takeProbability = (doc\\"properties"\"@takeProbability").text.toDouble
    
    val peers = (doc\\"peers"\"peer") map { node =>
        Peer(node\"@host".text, node\"@port".text.toInt)
    } toVector
    
    val host = InetAddress.getLocalHost.getHostName
}

