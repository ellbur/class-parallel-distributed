
package misty

import com.sun.net.httpserver._
import scala.collection.generic.FilterMonadic

trait Module {
    def handle(ex: HttpExchange): Unit
}

trait MetaModule {
    val name: String
    val version: String
}

trait ModuleEnv {
    type Trans[A] <: {
        def flatMap[B](f: A=>Trans[B]): Trans[B]
        def map[B](f: A=>B): Trans[B]
    }
    
    def runTransaction[A](trans: Trans[A]): A
    def select(query: Query): Trans[List[PTuple]]
    def delete(query: Query): Trans[Unit]
    def insert(tuple: PTuple): Trans[Unit]
}

// ---------------------------------------------------
// Primitives

sealed trait PPrimitive
case class PInt(value: Long) extends PPrimitive
case class PString(value: String) extends PPrimitive
case class PFloat(value: Double) extends PPrimitive

// ---------------------------------------------------
// Tuples

case class PTuple(items: List[PPrimitive])

// ---------------------------------------------------
// Queries

case class Query(terms: List[QueryTerm])

sealed trait QueryTerm
case class QDefined(as: PPrimitive) extends QueryTerm
case object QWild extends QueryTerm

// ---------------------------------------------------
// Sugar

object Poodle {
    
    val ___ : QWild.type = QWild
    
    implicit def intToQT(x: Int): QueryTerm = QDefined(PInt(x))
    implicit def longToQT(x: Long): QueryTerm = QDefined(PInt(x))
    implicit def stringToQT(x: String): QueryTerm = QDefined(PString(x))
    implicit def doubleToQT(x: Double): QueryTerm = QDefined(PFloat(x))
    implicit def wildToQT(w: QWild.type): QueryTerm = w
    
    implicit def joinQT[T<%QueryTerm](x: T) = new {
        def ~:[S<%QueryTerm](y: S) = List[QueryTerm](y, x)
    }
    implicit def joinQTs(x: List[QueryTerm]) = new {
        def ~:[S<%QueryTerm](y: S) = (y: QueryTerm) :: x
    }
    
    implicit def termsToQuery(x: List[QueryTerm]): Query = Query(x)
    
    implicit def intToPrim(x: Int) = PInt(x)
    implicit def longToPrim(x: Long) = PInt(x)
    implicit def stringToPrim(x: String) = PString(x)
    implicit def doubleToPrim(x: Double) = PFloat(x)
    
    implicit def joinPT[T<%PPrimitive](x: T) = new {
        def =:[S<%PPrimitive](y: S) = List[PPrimitive](y, x)
    }
    implicit def joinPTs(x: List[PPrimitive]) = new {
        def =:[S<%PPrimitive](y: S) = (y: PPrimitive) :: x
    }
    
    implicit def elemsToTuple(x: List[PPrimitive]) = PTuple(x)
    
    object ~: {
        def unapply(t: PTuple): Option[(PPrimitive,PTuple)] =
            t.items match {
                case Nil   => None
                case x::xs => Some((x, PTuple(xs)))
            }
    }
}

