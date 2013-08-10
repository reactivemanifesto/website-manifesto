package services

import play.modules.reactivemongo.ReactiveMongoPlugin
import play.api.Play._
import models._
import models.Handlers.signatoryHandler
import reactivemongo.bson._
import reactivemongo.core.commands.GetLastError
import reactivemongo.api.collections.default.BSONCollection
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import org.joda.time.DateTime

object UserService {

  /**
   * A user that has logged in using OAuth.
   *
   * @param provider The service specific identifier for the user.
   * @param name The name of the user.
   * @param avatar The users avatar, if they have one.
   * @param signed When the user signed the manifesto, if they signed it.
   */
  case class OAuthUser(provider: Provider, name: String, avatar: Option[String], signed: Option[DateTime] = None)

  lazy val collection = ReactiveMongoPlugin.db(current)[BSONCollection]("signatories")

  /**
   * Find the given OAuth user, and if the user can't be found, create a new one.
   *
   * @param user The user to save
   * @return A future of the found or saved signatory
   */
  def findOrSaveUser(user: OAuthUser): Future[Signatory] = {
    def providerAndId: (String, BSONValue) = user.provider match {
      case Twitter(id, _) => ("twitter", BSONLong(id))
      case Google(id) => ("google", BSONString(id))
      case GitHub(id, _) => ("github", BSONLong(id))
      case LinkedIn(id) => ("linkedin", BSONString(id))
    }
    def find = collection.find(BSONDocument(
      "provider.id" -> BSONString(providerAndId._1),
      "provider.details.id" -> providerAndId._2
    )).cursor[Signatory].headOption()

    def returnOrSave(s: Option[Signatory]) = s match {
      case Some(signatory) => {
        Future.successful(signatory)
      }
      case None => {
        val signatory = Signatory(BSONObjectID.generate, user.provider, user.name, user.avatar, user.signed)
        for {
          lastError <- collection.save(signatory, writeConcern = GetLastError(awaitJournalCommit = true))
        } yield {
          if (lastError.ok) {
            signatory
          } else {
            throw new RuntimeException("Unable to save signatory: " + lastError.message)
          }
        }
      }
    }

    for {
      signatory <- find
      toReturn <- returnOrSave(signatory)
    } yield toReturn
  }

  /**
   * Find the user with the given id.
   *
   * @param id The id of the user to find.
   * @return A future of the user, if found.
   */
  def findUser(id: String): Future[Option[Signatory]] = findUser(BSONObjectID(id))

  /**
   * Find the user with the given id.
   *
   * @param id The id of the user to find.
   * @return A future of the user, if found.
   */
  def findUser(id: BSONObjectID): Future[Option[Signatory]] = {
    collection.find(BSONDocument("_id" -> id)).cursor[Signatory].headOption()
  }

  /**
   * Load all the people that have signed the manifesto, in reverse chronological order.
   */
  def loadSignatories(): Future[List[Signatory]] = {
    collection.find(BSONDocument("signed" -> BSONDocument("$exists" -> true))).sort(
      BSONDocument("signed" -> BSONInteger(-1))
    ).cursor[Signatory].toList()
  }

  /**
   * Sign the manifesto
   *
   * @param id The id of the user that is signing
   * @return The updated user if successful, or an error message if the user is not allowed to sign.
   */
  def sign(id: BSONObjectID): Future[Either[String, Signatory]] = {

    findUser(id).flatMap {
      case Some(signatory) => signatory.signed match {
        case None => {
          val signed = DateTime.now
          collection.update(BSONDocument("_id" -> id), BSONDocument("$set" ->
            BSONDocument("signed" -> BSONDateTime(signed.getMillis))
          ), GetLastError(awaitJournalCommit = true)).map {
            lastError =>
              if (lastError.ok) {
                Right(signatory.copy(signed = Some(signed)))
              } else {
                throw new RuntimeException("Error signing: " + lastError.message)
              }
          }
        }
        case Some(signed) => Future.successful(Left("Already signed on " + signed))
      }

      case None => Future.successful(Left("Signatory not found"))
    }
  }

  /**
   * Remove a signature from the manifesto
   *
   * @param id The id of the user that is removing their signature
   * @return The updated user
   */
  def unsign(id: BSONObjectID): Future[Either[String, Signatory]] = {

    findUser(id).flatMap {
      case Some(signatory) => {
        collection.update(BSONDocument("_id" -> id), BSONDocument("$unset" ->
          BSONDocument("signed" -> BSONInteger(1))
        ), GetLastError(awaitJournalCommit = true)).map {
          lastError =>
            if (lastError.ok) {
              Right(signatory.copy(signed = None))
            } else {
              throw new RuntimeException("Error unsigning: " + lastError.message)
            }
        }
      }

      case None => Future.successful(Left("Signatory not found"))
    }
  }

}
