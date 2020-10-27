package models

import java.time.Instant

import play.api.libs.json._
import reactivemongo.api.bson.{BSONLongHandler => _, _}
import reactivemongo.api.bson.Macros._

import scala.util.{Failure, Success, Try}

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
  def id: BSONObjectID = _id

  def toWeb: WebSignatory = WebSignatory(provider.providerId, name, avatarUrl, signed)
}

object DbSignatory extends Implicits {
  implicit val bsonHandler: BSONDocumentHandler[DbSignatory] = handler[DbSignatory]
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
  implicit val bsonHandler: BSONHandler[Provider] = new BSONHandler[Provider] {

    private def readProvider[T](bson: BSONDocument)(implicit reader: BSONDocumentReader[T]): Try[T] =
      bson.getAsTry("details")(reader)

    override def readTry(bson: BSONValue): Try[Provider] = bson match {
      case document: BSONDocument =>
        document.getAsOpt[String]("id") match {
          case Some(GitHub.Id) => readProvider[GitHub](document)
          case Some(Twitter.Id) => readProvider[Twitter](document)
          case Some(Google.Id) => readProvider[Google](document)
          case Some(LinkedIn.Id) => readProvider[LinkedIn](document)
          case unknown => Failure(new IllegalArgumentException(s"Unknown provider: $unknown"))
        }
      case _ => Failure(new IllegalArgumentException(s"Expected document for provider, but got $bson"))
    }

    private def writeProvider[T <: Provider](provider: T)(implicit writer: BSONDocumentWriter[T]) = {
      writer.writeTry(provider).map { providerValue =>
        BSONDocument(
          "id" -> provider.providerId,
          "details" -> providerValue
        )
      }
    }

    override def writeTry(provider: Provider): Try[BSONValue] = provider match {
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

    private def readProvider[T](json: JsValue)(implicit reads: Reads[T]): JsSuccess[T] = JsSuccess((json \ "details").as[T])

    override def reads(json: JsValue): JsResult[Provider] = (json \ "id").asOpt[String] match {
      case Some(GitHub.Id) => readProvider[GitHub](json)
      case Some(Twitter.Id) => readProvider[Twitter](json)
      case Some(Google.Id) => readProvider[Google](json)
      case Some(LinkedIn.Id) => readProvider[LinkedIn](json)
      case unknown => JsError("Unknown provider: " + unknown)
    }

    private def writeProvider[T <: Provider](provider: T)(implicit writes: Writes[T]) = Json.obj(
      "id" -> provider.providerId,
      "details" -> provider
    )

    override def writes(provider: Provider): JsValue = provider match {
      case gh: GitHub => writeProvider(gh)(GitHub.format)
      case t: Twitter => writeProvider(t)(Twitter.format)
      case g: Google => writeProvider(g)(Google.format)
      case ln: LinkedIn => writeProvider(ln)(LinkedIn.format)
    }
  }
}

case class GitHub(id: Long, login: String) extends Provider {
  override val providerId: String = GitHub.Id
}

object GitHub extends Implicits {
  val Id = "github"
  implicit val bsonHandler: BSONDocumentWriter[GitHub] with BSONDocumentReader[GitHub] = handler[GitHub]
  implicit val format: Format[GitHub] = Json.format[GitHub]
}

case class Twitter(id: Long, screenName: String) extends Provider {
  override val providerId: String = Twitter.Id
}

object Twitter extends Implicits {
  val Id = "twitter"
  implicit val bsonHandler: BSONDocumentWriter[Twitter] with BSONDocumentReader[Twitter] = handler[Twitter]
  implicit val format: Format[Twitter] = Json.format[Twitter]
}

case class Google(id: String) extends Provider {
  override val providerId: String = Google.Id
}

object Google extends Implicits {
  val Id = "google"
  implicit val bsonHandler: BSONDocumentWriter[Google] with BSONDocumentReader[Google] = handler[Google]
  implicit val format: Format[Google] = Json.format[Google]
}

case class LinkedIn(id: String) extends Provider {
  override val providerId: String = LinkedIn.Id
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

  implicit val idHandler: BSONHandler[Long] = new BSONHandler[Long] {
    override def writeTry(v: Long): Try[BSONValue] = Success(BSONLong(v))
    override def readTry(bson: BSONValue): Try[Long] = bson match {
      case BSONLong(l) => Success(l)
      case BSONDouble(d) => Success(d.toLong)
      case _ => Failure(new IllegalArgumentException("Expected a long or double, but got " + bson))
    }
  }

  implicit val instantHandler: BSONHandler[Instant] = new BSONHandler[Instant] {
    override def readTry(bson: BSONValue): Try[Instant] = bson match {
      case BSONDateTime(millis) => Success(Instant.ofEpochMilli(millis))
      case _ => Failure(new IllegalArgumentException("Expected a BSON datetime, but got " + bson))
    }
    override def writeTry(instant: Instant): Try[BSONValue] = Success(BSONDateTime(instant.toEpochMilli))
  }

}

