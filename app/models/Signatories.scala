package models

import reactivemongo.bson.{BSONLongHandler => _, _}
import reactivemongo.bson.Macros._
import org.joda.time.DateTime
import play.api.libs.json._
import scala.util.control.NonFatal

/**
 * A signatory
 *
 * @param _id The internal id of the signatory
 * @param provider The provider specific ID of the signatory
 * @param name The name
 * @param avatarUrl The URL of the signatories avatar
 * @param signed The date the signatory signed, if they signed
 */
case class Signatory(
  _id: BSONObjectID,
  provider: Provider,
  name: String,
  avatarUrl: Option[String],
  signed: Option[DateTime]
) {
  def id = _id
}

/**
 * A login provider
 */
sealed trait Provider

case class GitHub(id: Long, login: String) extends Provider
case class Twitter(id: Long, screenName: String) extends Provider
case class Google(id: String) extends Provider
case class LinkedIn(id: String) extends Provider

/**
 * Reactive mongo handlers
 */
object Handlers {

  implicit object idHandler extends BSONHandler[BSONValue, Long] {
    def write(v: Long) = new BSONLong(v)
    def read(bson: BSONValue) = bson match {
      case BSONLong(l) => l
      case BSONDouble(d) => d.toLong
      case _ => throw new IllegalArgumentException("Expected a long or double, but got " + bson)
    }
  }

  implicit val githubHandler = handler[GitHub]
  implicit val twitterHandler = handler[Twitter]
  implicit val googleHandler = handler[Google]
  implicit val linkedInHandler = handler[LinkedIn]

  implicit object dateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(time: BSONDateTime) = new DateTime(time.value)
    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit lazy val providerHandler = new BSONHandler[BSONDocument, Provider] {

    def readProvider[T](bson: BSONDocument)(implicit reader: BSONDocumentReader[T]): T =
      bson.getAs[T]("details").getOrElse(throw new RuntimeException("Could not parse provider details"))

    def read(bson: BSONDocument) = bson.getAs[String]("id") match {
      case Some("github") => readProvider[GitHub](bson)
      case Some("twitter") => readProvider[Twitter](bson)
      case Some("google") => readProvider[Google](bson)
      case Some("linkedin") => readProvider[LinkedIn](bson)
      case unknown => throw new IllegalArgumentException("Unknown provider: " + unknown)
    }

    def writeProvider[T](id: String, provider: T)(implicit writer: BSONDocumentWriter[T]) = BSONDocument(
      "id" -> id,
      "details" -> writer.write(provider)
    )

    def write(provider: Provider) = provider match {
      case gh: GitHub => writeProvider("github", gh)
      case t: Twitter => writeProvider("twitter", t)
      case g: Google => writeProvider("google", g)
      case ln: LinkedIn => writeProvider("linkedin", ln)
    }
  }

  implicit lazy val signatoryHandler = handler[Signatory]
}

/**
 * JSON formats
 */
object Formats {

  implicit lazy val bsonObjectIdFormat = new Format[BSONObjectID] {
    def reads(json: JsValue) = json match {
      case JsString(v) => try {
        JsSuccess(new BSONObjectID(v))
      } catch {
        case NonFatal(e) => JsError("Cannot parse object id from " + v)
      }
      case _ => JsError("Cannot parse object id from " + json)
    }

    def writes(oid: BSONObjectID) = JsString(oid.stringify)
  }

  implicit val githubFormat = Json.format[GitHub]
  implicit val twitterFormat = Json.format[Twitter]
  implicit val googleFormat = Json.format[Google]
  implicit val linkedInFormat = Json.format[LinkedIn]

  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit lazy val providerFormat = new Format[Provider] {

    def readProvider[T](json: JsValue)(implicit reads: Reads[T]): JsSuccess[T] = JsSuccess((json \ "details").as[T])

    def reads(json: JsValue): JsResult[Provider] = (json \ "id").asOpt[String] match {
      case Some("github") => readProvider[GitHub](json)
      case Some("twitter") => readProvider[Twitter](json)
      case Some("google") => readProvider[Google](json)
      case Some("linkedin") => readProvider[LinkedIn](json)
      case unknown => JsError("Unknown provider: " + unknown)
    }

    def writeProvider[T](id: String, provider: T)(implicit writes: Writes[T]) = Json.obj(
      "id" -> id,
      "details" -> provider
    )

    def writes(provider: Provider) = provider match {
      case gh: GitHub => writeProvider("github", gh)
      case t: Twitter => writeProvider("twitter", t)
      case g: Google => writeProvider("google", g)
      case ln: LinkedIn => writeProvider("linkedin", ln)
    }
  }

  implicit lazy val signatoryFormat = Json.format[Signatory]
}

