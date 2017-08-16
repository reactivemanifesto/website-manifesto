package services

import java.time.Instant

import play.modules.reactivemongo.ReactiveMongoApi
import models._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.GetLastError
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}
import org.joda.time.DateTime
import reactivemongo.api.Cursor

class UserService(reactiveMongo: ReactiveMongoApi)(implicit ec: ExecutionContext) {

  private val collectionFuture: Future[BSONCollection] = reactiveMongo.database.map(_.apply[BSONCollection]("signatories"))

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
    def find(collection: BSONCollection) = collection.find(BSONDocument(
      "provider.id" -> BSONString(providerAndId._1),
      "provider.details.id" -> providerAndId._2
    )).one[Signatory]

    def returnOrSave(collection: BSONCollection, s: Option[Signatory]) = s match {
      case Some(signatory) =>
        Future.successful(signatory)

      case None =>
        val signatory = Signatory(BSONObjectID.generate, user.provider, user.name, user.avatar, user.signed)
        for {
          lastError <- collection.insert(signatory, writeConcern = GetLastError.Default)
        } yield {
          if (lastError.ok) {
            signatory
          } else {
            throw new RuntimeException("Unable to save signatory: " + lastError.writeErrors)
          }
        }
    }

    for {
      collection <- collectionFuture
      signatory <- find(collection)
      toReturn <- returnOrSave(collection, signatory)
    } yield toReturn
  }

  /**
   * Find the user with the given id.
   *
   * @param id The id of the user to find.
   * @return A future of the user, if found.
   */
  def findUser(id: String): Future[Option[Signatory]] = findUser(BSONObjectID.parse(id).get)

  /**
   * Find the user with the given id.
   *
   * @param id The id of the user to find.
   * @return A future of the user, if found.
   */
  def findUser(id: BSONObjectID): Future[Option[Signatory]] = {
    collectionFuture.flatMap(_.find(BSONDocument("_id" -> id)).one[Signatory])
  }

  /**
   * Load all the people that have signed the manifesto, in reverse chronological order.
   */
  def loadSignatories(): Future[List[Signatory]] = {
    collectionFuture.flatMap(_.find(BSONDocument("signed" -> BSONDocument("$exists" -> true))).sort(
      BSONDocument("signed" -> BSONInteger(-1))
    ).cursor[Signatory]().collect[List](-1, Cursor.FailOnError[List[Signatory]]()))
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
        case None =>
          val signed = Instant.now()
          collectionFuture.flatMap(_.update(BSONDocument("_id" -> id), BSONDocument("$set" ->
            BSONDocument("signed" -> BSONDateTime(signed.toEpochMilli))
          ), GetLastError.Default)).map {
            lastError =>
              if (lastError.ok) {
                Right(signatory.copy(signed = Some(signed)))
              } else {
                throw new RuntimeException("Error signing: " + lastError.errmsg)
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
      case Some(signatory) =>
        collectionFuture.flatMap(_.update(BSONDocument("_id" -> id), BSONDocument("$unset" ->
          BSONDocument("signed" -> BSONInteger(1))
        ), GetLastError.Default)).map {
          lastError =>
            if (lastError.ok) {
              Right(signatory.copy(signed = None))
            } else {
              throw new RuntimeException("Error unsigning: " + lastError.errmsg)
            }
        }

      case None => Future.successful(Left("Signatory not found"))
    }
  }

}
