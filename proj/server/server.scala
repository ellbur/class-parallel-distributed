
package misty

import com.sun.net.httpserver._
import java.net._
import java.io._
import scala.collection.mutable.{Map => MMap}
import scalaz.Scalaz._
import scala.collection.JavaConversions._
import net.liftweb.json._
import scala.io.Source

import akka.stm._
import akka.actor._
import akka.actor.Actor._
import akka.dispatch.Dispatchers

import scala.util.Random

import streams._

class Server(val peers: Vector[Peer], val which: Peer)
    extends RequestDispatcher
    with ContainerManager
    with ControlRequests
    with Peers
    with Storage
    with Debug
{
    val server = {
        val server = HttpServer.create(new InetSocketAddress(which.port), 10)
        server.createContext("/", handler)
        
        message("Starting " + which)
        server.start // Runs in a background thread
        
        server
    }
}

trait ControlRequests {
    self: ContainerManager with Peers with Debug =>
    
    type ExHand = HttpExchange => Unit
        
    val controlRequest: PartialFunction[List[String],ExHand] = {
        case _::"upload"::_    => readModuleFromUser _
        case _::"propagate"::_ => readModuleFromPeer _
        case _::"getmodule"::modName::_ => (giveModuleToPeer(modName, _))
    }
    
    def readModuleFromUser(ex: HttpExchange) {
        readModule(ex) foreach propagate _
    }

    def readModuleFromPeer(ex: HttpExchange) {
        readModule(ex)
    }
    
    def giveModuleToPeer(modName: String, ex: HttpExchange) {
        getModule(modName) match {
            case None =>
                ex.sendResponseHeaders(404, -1)
                ex.close
                
            case Some(container) =>
                println("Giving code for "+modName)
                val fin = new FileInputStream(container.jarFile)
                ex.sendResponseHeaders(200, fin.available)
                val out = ex.getResponseBody
                new FileInputStream(container.jarFile) pipeTo out
                out.close
                ex.close
        }
    }

    def propagate(container: Container) {
        peers filter (_ != which) foreach (propagate(_, container))
    }
    
    def propagate(peer: Peer, container: Container) {
        val url = peer at "/propagate"
        message("Sending to " + url)
        val con = url.openConnection.asInstanceOf[HttpURLConnection]
        con.setInstanceFollowRedirects(false)
        con.setRequestMethod("POST")
        con.setDoOutput(true)
        new FileInputStream(container.jarFile) pipeTo con.getOutputStream
        message("Got " + con.getResponseCode)
    }
    
    def getModuleFromPeer(modName: String): Boolean = {
        def iter(peers: List[Peer]): Boolean = peers match {
            case Nil => false
            case p :: ps =>
                println("Trying " + p)
                val con = p get ("/getmodule/"+modName)
                try {
                    con.getResponseCode match {
                        case 200 =>
                            println("200, reading it in")
                            readModule(con)
                            true
                            
                        case bad =>
                            println("Failed to get "+modName+" from "+p+"; "+bad)
                            iter(ps)
                    }
                }
                catch { case bad: IOException =>
                    println("Failed to get "+modName+" from "+p+"; "+bad)
                    iter(ps)
                }
        }
        
        iter(notUs toList)
    }
}

trait RequestDispatcher {
    self: ControlRequests with ContainerManager with Peers
        with Storage with Debug =>
    
    val handler = new HttpHandler {
        def handle(ex: HttpExchange) { dispatch(ex) }
    }
    
    def dispatch(ex: HttpExchange) {
        val uri = ex.getRequestURI
        message("Dispatching " + uri)
        val parts = uri.getPath.split("\\/").toList
        
        val doControlRequest = ((controlRequest orElse databaseSet.respond)
            andThen (_(ex)))
        
        parts |> (doControlRequest orElse {
            case _::modName::_ => dispatchModule(modName, ex)
            case _ =>
                message("No module name given, aborting.")
                ex.close
        })
    }
    
    // -------------------------------------------------
    // Threading and such
    
    def dispatchModule(modName: String, ex: HttpExchange) {
        getModule(modName) match {
            case None =>
                message("No module named "+modName)
                message("Trying to get it")
                getModuleFromPeer(modName)
                getModule(modName) match {
                    case None =>
                        println("Nope")
                        send404(ex)
                    case Some(container) =>
                        delegateModule(ex, container)
                }
            case Some(container) =>
                delegateModule(ex, container)
        }
    }
    
    // Policy point
    def delegateModule(ex: HttpExchange, container: Container) {
        Delegate(ex, container) |> sendToActor _
    }
    case class Delegate(exchange: HttpExchange, container: Container)
    
    val threadPoolSize = 50
    
    val allActors = (1 to threadPoolSize) map (n => new RequestActor(n))
    val availableActors = Ref[List[RequestActor]](allActors.toList)
    
    def sendToActor(d: Delegate) {
        message("sendToActor")
        val pick = atomic { availableActors.get match {
            case Nil         => None
            case first::rest =>
                if (shouldRedirect(threadPoolSize - rest.length - 1))
                    None
                else {
                    availableActors.set(rest)
                    Some(first)
                }
        }}
        
        pick match {
            case None =>
                sendRedirect(d.exchange)
            case Some(pick) =>
                message("Chose " + pick.n)
                pick send d
        }
    }
    
    def replaceActor(ra: RequestActor) {
        atomic(availableActors alter { rest =>
            ra :: rest
        })
    }
    
    class RequestActor(val n: Int) {
        outer =>
        
        def send(d: Delegate) { actor ! d }
        
        val actor = actorOf(new Actor {
            self.dispatcher = Dispatchers.newThreadBasedDispatcher(self)
            def receive = {
                case d: Delegate =>
                    message("Actor "+n+" got "+d.exchange.getRequestURI)
                    d.container handle d.exchange
                    replaceActor(outer)
            }
        })
        actor.start
    }
    
    // Policy point
    // This is the rule for how load balancing works
    def shouldRedirect(running: Int) = Random.nextDouble < redirectProbability(running)
    def redirectProbability(running: Int) = {
        running.doubleValue / threadPoolSize
    }
    
    def sendRedirect(ex: HttpExchange) {
        if (notUs.isEmpty) {
            message("Dropping")
            ex.sendResponseHeaders(503, -1)
            ex.close
        }
        else {
            message("Redirecting")
            // pick a peer at random
            val target = notUs(Random nextInt notUs.length)
            val redirectURL = target at ex.getRequestURI
            
            ex.getResponseHeaders set ("Location", redirectURL.toString)
            ex.sendResponseHeaders(301, -1)
            ex.close
        }
    }
    
    def send404(ex: HttpExchange) {
        ex.sendResponseHeaders(404, -1)
        ex.close
    }
}

trait ContainerManager {
    self: Storage with Debug =>
        
    case class Container(jarFile: File, name: String, version: String, module: Module) {
        def handle(ex: HttpExchange) { module.handle(ex) }
        def dispose() { jarFile.delete }
    }
    
    val modules = MMap[String,Container]()
    
    def getModule(name: String) = modules get name
    
    def addModule(jarFile: File): Option[Container] = {
        val url = jarFile.toURI.toURL
        val loader = URLClassLoader.newInstance(Array(url), getClass.getClassLoader)
        val clazz = (Class forName ("Module", true, loader)).asInstanceOf[Class[Module]]
        
        // Meta data
        val mclazz = (Class forName ("Module$", true, loader))
        val meta = mclazz.getDeclaredField("MODULE$").get(null).asInstanceOf[MetaModule]
        val name    = meta.name
        val version = meta.version
        
        val have = modules get name map
            (_.version == version) getOrElse false
            
        if (have) {
            message("Already have %s version %s" format (name, version))
            None
        }
        else {
            val env = new Env(name)
            
            val module = clazz.getConstructor(classOf[ModuleEnv]).newInstance(env)
            val container = Container(jarFile, name, version, module)
            
            Some(addNewModule(jarFile, container))
        }
    }

    def addNewModule(_jarFile: File, container: Container): Container = {
        message("Loaded module is \"" + container.name + "\"")
        
        // Get rid of old jars
        modules get container.name foreach (_.dispose)
        modules += (container.name -> container)
        
        container
    }

    def readModule(ex: HttpExchange): Option[Container] = {
        val it = readModule(ex.getRequestBody)
        ex.sendResponseHeaders(200, -1)
        ex.close
        it
    }
    
    def readModule(con: HttpURLConnection): Option[Container] = {
        val it = readModule(con.getInputStream)
        con.disconnect
        it
    }
    
    def readModule(stream: InputStream): Option[Container] = {
        val file = File createTempFile ("module", ".jar")
        message("Receiving module...")
        stream pipeTo (new FileOutputStream(file))
        
        try addModule(file)
        catch {
            case e: Any =>
                message("Module add failed with " + e)
                None
        }
        finally message("Done")
    }
    
    class Env(val modName: String)
        extends ModuleEnv with ModuleEnvWithTrans
    {
        def databaseSet = self.databaseSet
    }
}

trait Storage {
    self: Peers with Debug =>
        
    val databaseSet = new DatabaseSet(which, peers toList)
}

trait Peers {
    val peers: Vector[Peer]
    val which: Peer
    
    val notUs = peers filter (_ != which)
}

case class Peer(host: String, port: Int) {
    def at(path: String) = new URL("http", host, port, path)
    def at(uri: URI) = new URL("http", host, port, uri.getPath)
    def get(path: String) = at(path).openConnection.asInstanceOf[HttpURLConnection]
    def postJSON(path: String, j: JValue) =
        try {
            val con = this get path
            con.setDoOutput(true)
            val out = new PrintWriter(con.getOutputStream)
            out.println(compact(render(j)))
            out.flush()
            out.close()
            val resp = JSONOK(parse(Source fromInputStream con.getInputStream mkString))
            con.disconnect
            resp
        }
        catch {
            case _: IOException => JSONDown
        }
    def json(path: String) =
        try {
            val con = this get path
            val resp = JSONOK(parse(Source fromInputStream con.getInputStream mkString))
            con.disconnect
            resp
        }
        catch {
            case _: IOException => JSONDown
        }
    
    override def toString: String = this at "/" toString
}

sealed trait JSONResponse
case class JSONOK(message: JValue) extends JSONResponse
case object JSONDown extends JSONResponse

trait Debug {
    val which: Peer
    
    def message(s: String) {
        println(which + " " + s)
    }
}

object server {
    def main(args: Array[String]) {
        val peers = Config.peers
        println("Peers are " + peers)
        println("We are " + Config.host)
        val us = peers filter (_.host == Config.host) head
        
        new Server(peers, us)
    }
}

