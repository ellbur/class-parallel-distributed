
package misty

import net.liftweb.json._
import scala.collection.generic.FilterMonadic
import java.net._
import java.io._
import scala.util.Random
import com.sun.net.httpserver._
import akka.actor._
import akka.actor.Actor._
import java.util.UUID
import scala.io.Source

// ---------------------------------------------------
// Query Matching

object matching {
    implicit def toMatch(q: Query) = { (t: PTuple) =>
        if (t.items.length != q.terms.length) false
        else
            t.items zip q.terms forall { case (p, t) =>
                t match {
                    case QDefined(p2) if (p2 != p) => false
                    case _ => true
                }
            }
    }
}

// ---------------------------------------------------
// Operations

sealed trait Operation
case class Select(query: Query) extends Operation
case class Delete(query: Query) extends Operation
case class Insert(tuple: PTuple) extends Operation

// -----------------------------------------------
// Transaction monad (client side)

case object Interrupted extends RuntimeException

class CSTransState(peer: Peer, modName: String, id: String) {
    def send(op: Operation): List[PTuple] = {
        val ans = peer postJSON ("/op/"+modName+"/"+id, encoding encodeOperation op)
        ans match {
            case JSONDown =>
                println("Posting operation failed")
                throw Interrupted
            case JSONOK(j) =>
                encoding decodeResponse j
        }
    }
    
    def commit() {
        val con = peer get ("/commit/"+modName+"/"+id)
        val code = con.getResponseCode
        println("Committed with " + code)
        if (code != 200) throw Interrupted
        con.disconnect
    }
}

sealed trait CSTrans[A] {
    def produce(st: CSTransState): A
    def flatMap[B](f: A => CSTrans[B]) = CSTransChain(this, f)
    def map[B](f: A => B) = flatMap(f andThen CSTransPure.apply _)
}
case class CSTransPure[A](a: A) extends CSTrans[A] {
    def produce(st: CSTransState) = a
}
case class CSTransSelect(query: Query) extends CSTrans[List[PTuple]] {
    def produce(st: CSTransState) = {
        println("Sending a select")
        st.send(Select(query))
    }
}
case class CSTransDelete(query: Query) extends CSTrans[Unit] {
    def produce(st: CSTransState) {
        st.send(Delete(query))
    }
}
case class CSTransInsert(tuple: PTuple) extends CSTrans[Unit] {
    def produce(st: CSTransState) {
        st.send(Insert(tuple))
    }
}
case class CSTransChain[A,B](source: CSTrans[A], sink: A=>CSTrans[B]) extends CSTrans[B] {
    def produce(st: CSTransState) = {
        sink(source.produce(st)).produce(st)
    }
}

trait ModuleEnvWithTrans extends ModuleEnv {
    def databaseSet: DatabaseSet
    val modName: String
    
    type Trans[A] = CSTrans[A]
    
    def runTransaction[A](trans: Trans[A]) = {
        def runIter(backoff: Int): A =
            try {
                databaseSet.runTransaction(modName, trans)
            }
            catch { case Interrupted =>
                println("Transaction interrupted!! Retrying.")
                // Backoff
                Thread sleep (Random nextInt backoff)
                runIter(backoff * 2)
            }
            
        val res = runIter(1)
        println("All done running transaction")
        res
    }
    
    def select(query: Query) = CSTransSelect(query)
    def delete(query: Query) = CSTransDelete(query)
    def insert(tuple: PTuple) = CSTransInsert(tuple)
}

// ---------------------------------------------------
// Databases

class Database(val modName: String) {
    var version = System.currentTimeMillis
    var active: Boolean = true
    
    def handle(msg: Any) = actor ! msg
    
    def currentRows: List[PTuple] =
        (actor ? GetRows).as[List[PTuple]] match {
            case Some(rows) => rows
            case None       => Nil
        }
    
    private val actor = actorOf(new Actor {
        var rows: List[PTuple] = Nil
        var currentTransaction: Option[String] = None
        var lastHeardFrom: Long = 0
        var transactionRows: List[PTuple] = Nil
        
        val timeout = 2000
        
        def receive = {
            case NewTransaction(id, ex) =>
                println("new-transaction")
                currentTransaction match {
                    case Some(_) =>
                        val now = System.currentTimeMillis
                        if (now - lastHeardFrom > timeout)
                            newTransaction(id, ex)
                        else
                            busy(ex)
                    case None =>
                        newTransaction(id, ex)
                }
                
            case DoOperation(id, op, ex) =>
                println("do-operation " + op)
                currentTransaction match {
                    case Some(`id`) =>
                        doOperation(op, ex)
                    case _ =>
                        busy(ex)
                }
                
            case Commit(id, ex) =>
                println("commit")
                currentTransaction match {
                    case Some(`id`) =>
                        commit(ex)
                    case _ =>
                        busy(ex)
                }
                
            case Feed(newRows) =>
                println("Feeding!")
                rows = newRows
                // This will interrupt any current transaction
                currentTransaction = None
                
            case GetRows =>
                self.channel ! rows
        }
        
        def busy(ex: HttpExchange) {
            ex.sendResponseHeaders(503, -1)
            ex.close
        }
        
        def newTransaction(id: String, ex: HttpExchange) {
            currentTransaction = Some(id)
            transactionRows = rows
            lastHeardFrom = System.currentTimeMillis
            
            ex.sendResponseHeaders(200, -1)
            ex.close
        }
        
        def doOperation(op: Operation, ex: HttpExchange) {
            import matching._
            
            lastHeardFrom = System.currentTimeMillis
            
            val respRows: List[PTuple] = op match {
                case Select(q) => transactionRows filter q
                case Delete(q) =>
                    transactionRows = transactionRows filterNot q
                    Nil
                case Insert(t) =>
                    transactionRows = t :: transactionRows
                    Nil
            }
            val resp = compact(render(encoding encodeResponse respRows))
            
            ex.sendResponseHeaders(200, resp.length)
            ex.getResponseBody.write(resp.getBytes)
            ex.close
        }
        
        def commit(ex: HttpExchange) {
            rows = transactionRows
            currentTransaction = None
            ex.sendResponseHeaders(200, -1)
            ex.close
        }
    })
    actor.start
}

case class NewTransaction(id: String, ex: HttpExchange)
case class DoOperation(id: String, op: Operation, ex: HttpExchange)
case class Commit(id: String, ex: HttpExchange)
case class Feed(rows: List[PTuple])
case object GetRows

// ---------------------------------------------------
// Keeping track of who has which database.

class DatabaseSet(us: Peer, all: List[Peer]) {
    var local = Map[String,Database]()
    var remote = Map[String,Peer]()
    var searching: Boolean = false
    
    // Policy point
    val takeProbability = 0.05
    
    def runTransaction[A](modName: String, tr: CSTrans[A]) = {
        println("Running transaction")
        
        val peer = lookupPeer(modName)
        // Make a unique ID for this transaction
        val id = UUID.randomUUID.toString
        val con = peer get ("/query/"+modName+"/"+id)
        val code = con.getResponseCode
        println("Opened transaction with " + code)
        if (code != 200) throw Interrupted
        con.disconnect
        
        val state = new CSTransState(peer, modName, id)
        println("Producing " + tr)
        val ans = tr.produce(state)
        state.commit
        ans
    }
    
    val respond: PartialFunction[List[String],HttpExchange=>Unit] = {
        case _::"query"::modName::id::_ => ex =>
            println("Opening a transaction for " + modName)
            withLocal(modName, ex) { db =>
                db handle NewTransaction(id, ex)
            }
            
        case _::"op"::modName::id::_ => ex =>
            println("Running an operation for " + modName)
            val op = encoding decodeOperation
                parse(Source fromInputStream ex.getRequestBody mkString)
            withLocal(modName, ex) { db =>
                db handle DoOperation(id, op, ex)
            }
            
        case _::"commit"::modName::id::_ => ex =>
            println("Committing for " + modName)
            withLocal(modName, ex) { db =>
                db handle Commit(id, ex)
            }
        
        case _::"check"::modName::Nil => ex =>
            println("Got request to check for " + modName)
            if (local get modName map (_.active) getOrElse false) {
                send(ex, JArray(JString(us.host) :: JInt(us.port) :: Nil))
            }
            else {
                if (searching) {
                    println("Recursive loop detected; discarding")
                    ex.sendResponseHeaders(404, -1)
                    ex.close
                }
                else {
                    println("Propagating")
                    spawn {
                        val peer = lookupPeer(modName)
                        send(ex, JArray(JString(peer.host) :: JInt(peer.port) :: Nil))
                    }
                }
            }
            
        case _::"has"::modName::Nil => ex =>
            local get modName match {
                case Some(db) => send(ex, JArray(JInt(db.version)::Nil))
                case None     => send(ex, JArray(JNull::Nil))
            }
            
        case _::"recover"::modName::Nil => ex =>
            makeOurselves(modName)
            send(ex, JArray(JNull::Nil))
            
        case _::"take"::modName::Nil => ex =>
            println("Giving up " + modName)
            local get modName match {
                case Some(db) =>
                    db.active = false
                    send(ex, encoding encodeResponse db.currentRows)
                case None => send(ex, encoding encodeResponse Nil)
            }
    }
    
    def withLocal(modName: String, ex: HttpExchange)(rest: Database=>Unit) {
        local get modName match {
            case None =>
                ex.sendResponseHeaders(404, -1)
                ex.close
            case Some(db) => rest(db)
        }
    }
    
    def send(ex: HttpExchange, j: JValue) {
        ex.sendResponseHeaders(200, 0)
        val out = new PrintWriter(ex.getResponseBody)
        out.println(compact(render(j)))
        out.flush
        out.close
    }
    
    def lookupPeer(modName: String) = {
        println("Looking up data for " + modName)
        
        searching = true
        try {
            val lookup =
                remote get modName match {
                    case Some(peer) => checkPeer(modName, peer)
                    case None       => findPeer(modName)
                }
            
            val peer =
                if (lookup != us && Random.nextDouble < takeProbability) {
                    take(lookup, modName)
                }
                else lookup
            
            remote += modName -> peer
            peer
        }
        finally {
            searching = false
        }
    }
    
    def take(peer: Peer, modName: String): Peer = {
        println("Taking " + modName + " from " + peer)
        
        peer json ("/take/"+modName) match {
            case JSONDown =>
                println("Darn. Just went down")
                makeOurselves(modName)
                
            case JSONOK(resp) =>
                val rows = encoding decodeResponse resp
                val db =
                    local get modName match {
                        case Some(db) => db
                        case None =>
                            val db = new Database(modName)
                            local = local + (modName -> db)
                            db
                    }
                
                db handle Feed(rows)
                db.active = true
                db.version = System.currentTimeMillis
        }
        
        us
    }
        
    def checkPeer(modName: String, peer: Peer) = {
        println("Following initial lead " + peer)
        
        peer json ("/check/"+modName) match {
            case JSONDown =>
                println("Down. Go Fishing.")
                huntPeer(modName)
            case JSONOK(JArray(JString(host)::JInt(port)::Nil)) =>
                println("Good")
                Peer(host, port.intValue)
        }
    }
    
    def findPeer(modName: String) = {
        def findIter(searchList: List[Peer]): Peer = searchList match {
            case Nil => makeOurselves(modName)
            case p :: ps => 
                p json ("/check/"+modName) match {
                    case JSONDown => findIter(ps)
                    case JSONOK(JArray(JString(host)::JInt(port)::Nil)) =>
                        Peer(host, port.intValue)
                }
        }
        findIter(all sortBy (_ => Random.nextDouble) toList)
    }
    
    def huntPeer(modName: String) = {
        def huntIter(huntList: List[Peer], winner: Option[(Peer,Long)]): Peer =
            huntList match {
                case Nil     => recover(modName, winner map (_._1))
                case p :: ps =>
                    println("Pinging " + p)
                    p json ("/has/"+modName) match {
                        case JSONDown =>
                            println("Also down")
                            huntIter(ps, winner)
                        case JSONOK(JArray(JNull::_)) =>
                            println("No luck")
                            huntIter(ps, winner)
                        case JSONOK(JArray(JInt(time)::_)) =>
                            println("It has an old version")
                            val newWinner = winner match {
                                case Some((_, ptime)) if (ptime > time) => winner
                                case _ => Some((p, time.longValue))
                            }
                            huntIter(ps, newWinner)
                    }
            }
        huntIter(all sortBy (_ => Random.nextDouble), None)
    }
    
    def recover(modName: String, from: Option[Peer]) = from match {
        case None => makeOurselves(modName)
        case Some(peer) =>
            peer json ("/recover/"+modName)
            peer
    }
    
    def makeOurselves(modName: String) = {
        println("No one has it; making ourselves")
        
        val current = local get modName getOrElse {
            val db = new Database(modName)
            local += modName -> db
            db
        }
        current.active = true
        
        us
    }
}

// ---------------------------------------------------
// Encoding

case object BadEncoding extends RuntimeException

object encoding {
    def encodePrim(p: PPrimitive) = p match {
        case PInt(x)    => JInt(x)
        case PString(x) => JString(x)
        case PFloat(x)  => JDouble(x)
    }
    
    def decodePrim(j: JValue) = j match {
        case JInt(x)    => PInt(x.longValue)
        case JString(x) => PString(x)
        case JDouble(x) => PFloat(x)
        case _          => throw BadEncoding
    }
    
    def encodeTerm(qt: QueryTerm) = qt match {
        case QDefined(p) => encodePrim(p)
        case QWild       => JNull
    }
    
    def decodeTerm(j: JValue) = j match {
        case JNull => QWild
        case x     => QDefined(decodePrim(x))
    }
    
    def encodeQuery(q: Query) = JArray(q.terms map encodeTerm _)
    def decodeQuery(j: JValue) = j match {
        case JArray(elems) => Query(elems map decodeTerm _)
        case _            => throw BadEncoding
    }
    
    def encodeTuple(t: PTuple) = JArray(t.items map encodePrim _)
    def decodeTuple(j: JValue) = j match {
        case JArray(elems) => PTuple(elems map decodePrim _)
        case _ => throw BadEncoding
    }
    
    def encodeOperation(op: Operation) = op match {
        case Select(query) => JArray(JString("select")::encodeQuery(query)::Nil)
        case Delete(query) => JArray(JString("delete")::encodeQuery(query)::Nil)
        case Insert(tuple) => JArray(JString("insert")::encodeTuple(tuple)::Nil)
    }
    def decodeOperation(j: JValue) = j match {
        case JArray(JString("select")::q::Nil) => Select(decodeQuery(q))
        case JArray(JString("delete")::q::Nil) => Delete(decodeQuery(q))
        case JArray(JString("insert")::t::Nil) => Insert(decodeTuple(t))
        case _ => throw BadEncoding
    }
    
    def encodeResponse(r: List[PTuple]) = JArray(r map encodeTuple _)
    def decodeResponse(j: JValue) = j match {
        case JArray(elems) => elems map decodeTuple _ toList
        case _ => throw BadEncoding
    }
}

