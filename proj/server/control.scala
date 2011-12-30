
package misty

import java.net._
import java.io._

import streams._

object control {
    def main(args: Array[String]) {
        val url  = "http://localhost:8000/upload"
        val path = "../foochan/.target/scala-2.9.1/foochan_2.9.1-0.1.jar"
        
        println("Sending to " + url)
        val con = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
        con.setRequestMethod("POST")
        con.setDoOutput(true)
        (new FileInputStream(path)) pipeTo con.getOutputStream
        println("Got " + con.getResponseCode)
    }
}

object control2 {
    def main(args: Array[String]) {
        val url  = "http://localhost:8000/upload"
        val path = "../barchan/.target/scala-2.9.1/barchan_2.9.1-0.1.jar"
        
        println("Sending to " + url)
        val con = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
        con.setRequestMethod("POST")
        con.setDoOutput(true)
        (new FileInputStream(path)) pipeTo con.getOutputStream
        println("Got " + con.getResponseCode)
    }
}

object control3 {
    def send(name: String) {
        val url  = "http://localhost:8000/upload"
        val path = "../foos/"+name+"/.target/scala-2.9.1/module_2.9.1-0.1.jar"
        
        println("Sending "+name+" to " + url)
        val con = (new URL(url)).openConnection.asInstanceOf[HttpURLConnection]
        con.setRequestMethod("POST")
        con.setDoOutput(true)
        (new FileInputStream(path)) pipeTo con.getOutputStream
        println("Got " + con.getResponseCode)
    }
    
    def main(args: Array[String]) {
        List("foo1", "foo2", "foo3", "foo4", "foo5") foreach send _
    }
}

