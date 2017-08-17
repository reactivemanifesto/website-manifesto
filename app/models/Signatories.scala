package models

import java.time.Instant

import reactivemongo.bson.{BSONLongHandler => _, _}
import reactivemongo.bson.Macros._
import play.api.libs.json._

/**
 * A signatory that gets stored in the database
 *
 * @param _id The internal id of the signatory
 * @param provider The provider specific ID of the signatory
 * @param name The name
 * @param avatarUrl The URL of the signatories avatar
 * @param signed The date the signatory signed, if they signed
 * @param profileLastRefreshed When the avatar was last loaded
 */
case class DbSignatory(
  _id: BSONObjectID,
  provider: Provider,
  name: String,
  avatarUrl: Option[String],
  signed: Option[Instant],
  profileLastRefreshed: Option[Instant]
) {
  def id = _id

  def toWeb = WebSignatory(provider.providerId, name, avatarUrl, signed)
}

object DbSignatory extends Implicits {
  implicit val bsonHandler = handler[DbSignatory]
}

/**
 * A signatory that gets published to the web via the JSON API.
 *
 * @param name The name
 * @param avatarUrl The URL of the signatories avatar
 * @param signed The date the signatory signed, if they signed
 */
case class WebSignatory(
  providerId: String,
  name: String,
  avatarUrl: Option[String],
  signed: Option[Instant]
)

object WebSignatory {
  implicit val format: Format[WebSignatory] = Json.format
}

/**
 * A login provider
 */
sealed trait Provider {
  val providerId: String
}

object Provider extends Implicits {
  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit val bsonHandler: BSONHandler[BSONDocument, Provider] = new BSONHandler[BSONDocument, Provider] {

    def readProvider[T](bson: BSONDocument)(implicit reader: BSONDocumentReader[T]): T =
      bson.getAs[T]("details").getOrElse(throw new RuntimeException("Could not parse provider details"))

    def read(bson: BSONDocument) = bson.getAs[String]("id") match {
      case Some(GitHub.Id) => readProvider[GitHub](bson)
      case Some(Twitter.Id) => readProvider[Twitter](bson)
      case Some(Google.Id) => readProvider[Google](bson)
      case Some(LinkedIn.Id) => readProvider[LinkedIn](bson)
      case unknown => throw new IllegalArgumentException("Unknown provider: " + unknown)
    }

    def writeProvider[T <: Provider](provider: T)(implicit writer: BSONDocumentWriter[T]) = BSONDocument(
      "id" -> provider.providerId,
      "details" -> writer.write(provider)
    )

    def write(provider: Provider) = provider match {
      case gh: GitHub => writeProvider(gh)
      case t: Twitter => writeProvider(t)
      case g: Google => writeProvider(g)
      case ln: LinkedIn => writeProvider(ln)
    }
  }

  /**
   * Provides polymorphic serialisation and deserialisation of providers
   */
  implicit val format: Format[Provider] = new Format[Provider] {

    def readProvider[T](json: JsValue)(implicit reads: Reads[T]): JsSuccess[T] = JsSuccess((json \ "details").as[T])

    def reads(json: JsValue): JsResult[Provider] = (json \ "id").asOpt[String] match {
      case Some(GitHub.Id) => readProvider[GitHub](json)
      case Some(Twitter.Id) => readProvider[Twitter](json)
      case Some(Google.Id) => readProvider[Google](json)
      case Some(LinkedIn.Id) => readProvider[LinkedIn](json)
      case unknown => JsError("Unknown provider: " + unknown)
    }

    def writeProvider[T <: Provider](provider: T)(implicit writes: Writes[T]) = Json.obj(
      "id" -> provider.providerId,
      "details" -> provider
    )

    def writes(provider: Provider) = provider match {
      case gh: GitHub => writeProvider(gh)(GitHub.format)
      case t: Twitter => writeProvider(t)(Twitter.format)
      case g: Google => writeProvider(g)(Google.format)
      case ln: LinkedIn => writeProvider(ln)(LinkedIn.format)
    }
  }
}

case class GitHub(id: Long, login: String) extends Provider {
  override val providerId = GitHub.Id
}

object GitHub extends Implicits {
  val Id = "github"
  implicit val bsonHandler: BSONDocumentWriter[GitHub] with BSONDocumentReader[GitHub] = handler[GitHub]
  implicit val format: Format[GitHub] = Json.format[GitHub]
}

case class Twitter(id: Long, screenName: String) extends Provider {
  override val providerId = Twitter.Id
}

object Twitter extends Implicits {
  val Id = "twitter"
  implicit val bsonHandler: BSONDocumentWriter[Twitter] with BSONDocumentReader[Twitter] = handler[Twitter]
  implicit val format: Format[Twitter] = Json.format[Twitter]
}

case class Google(id: String) extends Provider {
  override val providerId = Google.Id
}

object Google extends Implicits {
  val Id = "google"
  implicit val bsonHandler: BSONDocumentWriter[Google] with BSONDocumentReader[Google] = handler[Google]
  implicit val format: Format[Google] = Json.format[Google]
}

case class LinkedIn(id: String) extends Provider {
  override val providerId = LinkedIn.Id
}

object LinkedIn extends Implicits {
  val Id = "linkedin"
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

  implicit val instantHandler: BSONHandler[BSONDateTime, Instant] = new BSONHandler[BSONDateTime, Instant] {
    def read(time: BSONDateTime) = Instant.ofEpochMilli(time.value)
    def write(instant: Instant) = BSONDateTime(instant.toEpochMilli)
  }

}

