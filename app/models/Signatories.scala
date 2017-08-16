package models

import java.time.Instant

import reactivemongo.bson.{BSONLongHandler => _, _}
import reactivemongo.bson.Macros._
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
  signed: Option[Instant]
) {
  def id = _id
}

object Signatory extends Implicits {
  implicit val format = Json.format[Signatory]
  implicit val bsonHandler = handler[Signatory]
}

/**
 * A login provider
 */
sealed trait Provider

object Provider extends Implicits {
  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit val bsonHandler: BSONHandler[BSONDocument, Provider] = new BSONHandler[BSONDocument, Provider] {

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

  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit val format: Format[Provider] = new Format[Provider] {

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
      case gh: GitHub => writeProvider("github", gh)(GitHub.format)
      case t: Twitter => writeProvider("twitter", t)(Twitter.format)
      case g: Google => writeProvider("google", g)(Google.format)
      case ln: LinkedIn => writeProvider("linkedin", ln)(LinkedIn.format)
    }
  }
}

case class GitHub(id: Long, login: String) extends Provider

object GitHub extends Implicits {
  implicit val bsonHandler: BSONDocumentWriter[GitHub] with BSONDocumentReader[GitHub] = handler[GitHub]
  implicit val format: Format[GitHub] = Json.format[GitHub]
}

case class Twitter(id: Long, screenName: String) extends Provider

object Twitter extends Implicits {
  implicit val bsonHandler: BSONDocumentWriter[Twitter] with BSONDocumentReader[Twitter] = handler[Twitter]
  implicit val format: Format[Twitter] = Json.format[Twitter]
}

case class Google(id: String) extends Provider

object Google extends Implicits {
  implicit val bsonHandler: BSONDocumentWriter[Google] with BSONDocumentReader[Google] = handler[Google]
  implicit val format: Format[Google] = Json.format[Google]
}

case class LinkedIn(id: String) extends Provider

object LinkedIn extends Implicits {
  implicit val bsonHandler: BSONDocumentWriter[LinkedIn] with BSONDocumentReader[LinkedIn] = handler[LinkedIn]
  implicit val format: Format[LinkedIn] = Json.format[LinkedIn]
}

/**
 * Implicits for various types
 */
trait Implicits {

  implicit val idHandler: BSONHandler[BSONValue, Long] = new BSONHandler[BSONValue, Long] {
    def write(v: Long) = BSONLong(v)
    def read(bson: BSONValue) = bson match {
      case BSONLong(l) => l
      case BSONDouble(d) => d.toLong
      case _ => throw new IllegalArgumentException("Expected a long or double, but got " + bson)
    }
  }

  implicit val dateTimeHandler: BSONHandler[BSONDateTime, Instant] = new BSONHandler[BSONDateTime, Instant] {
    def read(time: BSONDateTime) = Instant.ofEpochMilli(time.value)
    def write(instant: Instant) = BSONDateTime(instant.toEpochMilli)
  }

  implicit val bsonObjectIdFormat: Format[BSONObjectID] = new Format[BSONObjectID] {
    def reads(json: JsValue) = json match {
      case JsString(v) => try {
        JsSuccess(BSONObjectID.parse(v).get)
      } catch {
        case NonFatal(e) => JsError("Cannot parse object id from " + v)
      }
      case _ => JsError("Cannot parse object id from " + json)
    }

    def writes(oid: BSONObjectID) = JsString(oid.stringify)
  }
}

