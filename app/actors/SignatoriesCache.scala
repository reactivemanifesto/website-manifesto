package actors

import akka.actor.{ActorRef, Actor}
import models.Signatory
import reactivemongo.bson.BSONObjectID
import play.api.Logger
import scala.util._
import services.UserService
import scala.util.control.NonFatal

/**
 * Messages sent/received by the signatories cache
 */
object SignatoriesCache {

  /**
   * Get all the signatories.  Will send back a Signatories message.
   */
  case object GetSignatories

  /**
   * A list of signatories, in reverse chronological order.
   *
   * @param signatories The signatories.
   * @param hash A hash of the signatories.
   */
  case class Signatories(signatories: List[Signatory], hash: Int)

  /**
   * Sign the manifesto.  Will return an Updated or UpdateFailed message.
   *
   * @param id The id of the user that is signing the manifesto.
   */
  case class Sign(id: BSONObjectID)

  /**
   * Remove a signature from the manifesto.  Will return an Updated or UpdateFailed message.
   *
   * @param id The id of the user who is removing their signature.
   */
  case class Unsign(id: BSONObjectID)

  /**
   * Sent in response to operations that update a signatory to indicate the update was successful.
   *
   * @param signatory The updated signatory.
   */
  case class Updated(signatory: Signatory)

  /**
   * Sent in response to operations that update a signatory, to indicate the update failed.
   *
   * @param msg The error message.
   */
  case class UpdateFailed(msg: String)

  /**
   * Reload all the signatories from the database.
   *
   * Used to sync the cache with the database.
   */
  case object Reload
}

/**
 * An actor that represents a signatories cache.
 *
 * This actor has three states.
 *
 * The default state is an error state.  GetSignatories will always return an empty list, and update operations will
 * always fail.  The actor will only move out of this state if a Reload message is sent to it.
 *
 * The second state is a loading state.  The actor will enter this state when it receives a Reload message.
 * GetSignatories messages will be deferred to be handled later, unless the cache already holds some signatories to
 * return.  All update messages will be deferred to be handled later.
 *
 * The third state is a hot state.  The cache is hot and consistent.  All messages will be handled normally.
 */
class SignatoriesCache extends Actor {

  // Internal messages, sent from asynchronous callbacks.
  case class SignatoryAdded(signatory: Signatory)
  case class SignatoryRemoved(signatory: Signatory)
  case class SignatoriesLoaded(signatories: List[Signatory])
  case object LoadFailed

  import SignatoriesCache._

  import context._

  // The cached signatories.
  var signatories: List[Signatory] = Nil
  // Hash code of the cached signatories.
  var signatoriesHash = signatories.hashCode()

  def update(signatories: List[Signatory]) = {
    this.signatories = signatories
    this.signatoriesHash = signatories.hashCode()
  }

  // Pending requests. Sent when we are
  var pendingRequests: List[(ActorRef, AnyRef)] = Nil

  // This will trigger an immediate connect to MongoDB, if not already connected, so hopefully by the time we
  // receive a reload request, it's connected and authenticated.
  override def preStart() = UserService.collection

  // Default state, in error.
  def receive = {
    case GetSignatories => sender ! Signatories(Nil, Nil.hashCode())
    case s: Sign => sender ! UpdateFailed("Unknown error")
    case u: Unsign => sender ! UpdateFailed("Unknown error")
    case Reload => loadSignatories
  }

  // We are cold, we haven't loaded the database yet, so hold off returning anything or saving anything
  def loading: Receive = {
    case g @ GetSignatories => {
      if (signatories.isEmpty) {
        pendingRequests = (sender, g) :: pendingRequests
      } else {
        sender ! Signatories(signatories, signatoriesHash)
      }
    }
    case s: Sign => {
      pendingRequests = (sender, s) :: pendingRequests
    }
    case u: Unsign => {
      pendingRequests = (sender, u) :: pendingRequests
    }
    case SignatoriesLoaded(sigs) => {
      signatories = sigs
      signatoriesHash = signatories.hashCode()
      become(hot)
      sendPending
    }
    case LoadFailed => {
      // If loading failed, but we still have signatories to serve, then go hot and serve those
      if (signatories.isEmpty)
        unbecome()
      else
        become(hot)
      sendPending
    }
  }

  // We are hot, and guaranteed to be internally consistent on this node.
  def hot: Receive = {

    case GetSignatories => sender ! Signatories(signatories, signatoriesHash)

    /*
      Sign and unsign could have either failed for a known reason, ie returned Left, which we want to report back to
      the user, or failed for an unknown unrecoverable reason (eg database error), which we recover from by logging
      the error and converting it to an "Unknown error" message for the user.
     */

    case Sign(oid) => {
      val from = sender
      UserService.sign(oid).recover {
        case NonFatal(e) => {
          Logger.error("Error signing " + oid, e)
          Left("Unknown error")
        }
      } onSuccess {
        case Left(err) => from ! UpdateFailed(err)
        case Right(s) => {
          from ! Updated(s)
          self ! SignatoryAdded(s)
        }
      }
    }

    case Unsign(oid) => {
      val from = sender
      UserService.unsign(oid).recover {
        case NonFatal(e) => {
          Logger.error("Error unsigning " + oid, e)
          Left("Unknown error")
        }
      } onSuccess {
        case Left(err) => from ! UpdateFailed(err)
        case Right(s) => {
          from ! Updated(s)
          self ! SignatoryRemoved(s)
        }
      }
    }

    case SignatoryAdded(signatory) => {
      signatories = signatory :: signatories
      signatoriesHash = signatories.hashCode()
    }

    case SignatoryRemoved(signatory) => {
      signatories = signatories.filterNot(_.id == signatory.id)
      signatoriesHash = signatories.hashCode()
    }

    case Reload => loadSignatories
  }

  def sendPending = {
    pendingRequests.foreach { p =>
      self.!(p._2)(p._1)
    }
    pendingRequests = Nil
  }

  def loadSignatories = {
    Logger.info("Loading signatories...")
    become(loading)

    UserService.loadSignatories().onComplete {
      case Success(results) => {
        Logger.info("Loaded " + results.size + " signatories")
        self ! SignatoriesLoaded(results)
      }
      case Failure(t) => {
        Logger.error("Error loading signatories", t)
        self ! LoadFailed
      }
    }
  }
}
