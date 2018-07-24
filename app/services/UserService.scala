package services

import java.time.Instant

import play.modules.reactivemongo.ReactiveMongoApi
import models._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.api.commands.GetLastError
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.api.Cursor

import scala.concurrent.duration.FiniteDuration

class UserService(reactiveMongo: ReactiveMongoApi)(implicit ec: ExecutionContext) {

  private val collectionFuture: Future[BSONCollection] = reactiveMongo.database.map(_.apply[BSONCollection]("signatories"))

  /**
   * Find the given OAuth user, and if the user can't be found, create a new one.
   *
   * @param user The user to save
   * @return A future of the found or saved signatory
   */
  def findOrSaveUser(user: OAuthUser): Future[DbSignatory] = {
    def providerUserId: BSONValue = user.provider match {
      case Twitter(id, _) => BSONLong(id)
      case Google(id) => BSONString(id)
      case GitHub(id, _) => BSONLong(id)
      case LinkedIn(id) => BSONString(id)
    }
    def find(collection: BSONCollection) = collection.find(BSONDocument(
      "provider.id" -> BSONString(user.provider.providerId),
      "provider.details.id" -> providerUserId
    )).one[DbSignatory]

    def returnOrSave(collection: BSONCollection, s: Option[DbSignatory]) = s match {
      case Some(signatory) =>
        Future.successful(signatory)

      case None =>
        val signatory = DbSignatory(BSONObjectID.generate, user.provider, user.name, user.avatar, user.signed, Some(Instant.now()))
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
  def findUser(id: String): Future[Option[DbSignatory]] = findUser(BSONObjectID.parse(id).get)

  /**
   * Find the user with the given id.
   *
   * @param id The id of the user to find.
   * @return A future of the user, if found.
   */
  def findUser(id: BSONObjectID): Future[Option[DbSignatory]] = {
    collectionFuture.flatMap(_.find(BSONDocument("_id" -> id)).one[DbSignatory])
  }

  /**
   * Load all the people that have signed the manifesto, in reverse chronological order.
   */
  def loadSignatories(): Future[List[DbSignatory]] = {
    collectionFuture.flatMap(_.find(BSONDocument("signed" -> BSONDocument("$exists" -> true))).sort(
      BSONDocument("signed" -> BSONInteger(-1))
    ).cursor[DbSignatory]().collect[List](-1, Cursor.FailOnError[List[DbSignatory]]()))
  }

  /**
   * Sign the manifesto
   *
   * @param id The id of the user that is signing
   * @return The updated user if successful, or an error message if the user is not allowed to sign.
   */
  def sign(id: BSONObjectID): Future[Either[String, DbSignatory]] = {

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
  def unsign(id: BSONObjectID): Future[Either[String, DbSignatory]] = {

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

  /**
    * Update a signatory profile.
    */
  def updateProfile(signatory: DbSignatory): Future[Unit] = {
    val setFields = BSONDocument(
      "provider" -> signatory.provider,
      "name" -> signatory.name,
      "profileLastRefreshed" -> BSONDateTime(System.currentTimeMillis())
    ) ++ signatory.avatarUrl.fold(BSONDocument.empty) { url =>
      BSONDocument(
        "avatarUrl" -> url
      )
    }

    val unset = if (signatory.avatarUrl.isEmpty) {
      BSONDocument(
        "$unset" -> BSONDocument(
          "avatarUrl" -> 1
        )
      )
    } else BSONDocument.empty

    val update = BSONDocument(
      "$set" -> setFields
    ) ++ unset

    for {
      collection <- collectionFuture
      lastError <- collection.update(BSONDocument("_id" -> signatory.id), update, GetLastError.Default)
    } yield {
      if (lastError.ok) {
        ()
      } else {
        throw new RuntimeException(s"Error updating signatory with id ${signatory.id}: " + lastError.errmsg)
      }
    }
  }

  /**
    * Touch the profile last refreshed date.
    */
  def touchProfile(id: BSONObjectID): Future[Unit] = {
    for {
      collection <- collectionFuture
      lastError <- collection.update(BSONDocument("_id" -> id), BSONDocument("$set" ->
        BSONDocument("profileLastRefreshed" -> BSONDateTime(System.currentTimeMillis()))
      ), GetLastError.Default)
    } yield {
      if (lastError.ok) {
        ()
      } else {
        throw new RuntimeException(s"Error touching signatory with id $id: " + lastError.errmsg)
      }
    }
  }

  def removeOldUnsignedProfiles(maxAgeUnsigned: FiniteDuration, maxDelete: Int): Future[Int] = {
    val deleteOlder = System.currentTimeMillis() - maxAgeUnsigned.toMillis
    (for {
      collection <- collectionFuture
      sigs <- collection.find(BSONDocument("signed" -> BSONDocument("$exists" -> false)))
        .sort(BSONDocument("_id" -> BSONInteger(1)))
        .cursor[DbSignatory]()
        .collect[List](maxDelete, Cursor.FailOnError[List[DbSignatory]]())
    } yield {
      val toDelete = sigs.filter(_._id.time < deleteOlder).map(_._id)
      if (toDelete.nonEmpty) {
        collection.remove(BSONDocument(
          "_id" -> BSONDocument("$in" -> toDelete),
          // Not necessary, but safe
          "signed" -> BSONDocument("$exists" -> false)
        )).map(_.n)
      } else {
        Future.successful(0)
      }
    }).flatten
  }
}
