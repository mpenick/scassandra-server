package uk.co.scassandra.priming

import spray.json._
import spray.httpx.SprayJsonSupport
import uk.co.scassandra.cqlmessages.ColumnType
import uk.co.scassandra.cqlmessages.Consistency
import uk.co.scassandra.priming.query._
import uk.co.scassandra.priming.query.PrimeCriteria
import uk.co.scassandra.priming.prepared.ThenPreparedSingle
import uk.co.scassandra.priming.prepared.WhenPreparedSingle
import uk.co.scassandra.priming.query.PrimeQuerySingle
import uk.co.scassandra.priming.query.TypeMismatch
import uk.co.scassandra.priming.query.When
import uk.co.scassandra.priming.query.Then
import uk.co.scassandra.priming.prepared.PrimePreparedSingle

object PrimingJsonImplicits extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object ConsistencyJsonFormat extends RootJsonFormat[Consistency] {
    def write(c: Consistency) = JsString(c.string)

    def read(value: JsValue) = value match {
      case JsString(consistency) => Consistency.fromString(consistency)
      case _ => throw new IllegalArgumentException("Expected Consistency as JsString")
    }
  }

  implicit object ColumnTypeJsonFormat extends RootJsonFormat[ColumnType] {
    def write(c: ColumnType) = JsString(c.stringRep)

    def read(value: JsValue) = value match {
      case JsString(string) => ColumnType.fromString(string).get
      case _ => throw new IllegalArgumentException("Expected ColumnType as JsString")
    }
  }

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case n: Long => JsNumber(n)
      case s: String => JsString(s)
      case seq: Seq[_] => seqFormat[Any].write(seq)
      case m: Map[String, _] => mapFormat[String, Any].write(m)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case set: Set[Any] => setFormat[Any].write(set)
      case other => serializationError("Do not understand object of type " + other.getClass.getName)
    }
    def read(value: JsValue) = value match {
      case JsNumber(n) => n.longValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(value)
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case x => deserializationError("Do not understand how to deserialize " + x)
    }
  }

  implicit val impThen = jsonFormat3(Then)
  implicit val impWhen = jsonFormat4(When)
  implicit val impPrimeQueryResult = jsonFormat2(PrimeQuerySingle)
  implicit val impConnection = jsonFormat1(Connection)
  implicit val impQuery = jsonFormat2(Query)
  implicit val impPrimeCriteria = jsonFormat2(PrimeCriteria)
  implicit val impConflictingPrimes = jsonFormat1(ConflictingPrimes)
  implicit val impTypeMismatch = jsonFormat3(TypeMismatch)
  implicit val impTypeMismatches = jsonFormat1(TypeMismatches)
  implicit val impWhenPreparedSingle = jsonFormat1(WhenPreparedSingle)
  implicit val impThenPreparedSingle = jsonFormat3(ThenPreparedSingle)
  implicit val impPrimePreparedSingle = jsonFormat2(PrimePreparedSingle)
}
