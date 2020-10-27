package actors

import java.time.Instant

import akka.actor.{Actor, ActorLogging, ActorRef, Timers}
import models._
import play.api.Configuration

import scala.util._
import services.{UserInfoProvider, UserService}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import reactivemongo.api.bson.BSONObjectID

/**
 * Messages sent/received by the signatories cache
 */
object SignatoriesCache {

  /**
   * Get all the signatories.  Will send back a Signatories message.
   */
  case object GetSignatories

  /**
   * Search for signatories by first or last name using the given prefix.
   *
   * Will send back a Signatories message.
   */
  case class Search(query: String)

  /**
   * A list of signatories, in reverse chronological order.
   *
   * @param signatories The signatories.
   * @param hash A hash of the signatories.
   */
  case class Signatories(signatories: List[DbSignatory], hash: Int)

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
  case class Updated(signatory: DbSignatory)

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
class SignatoriesCache(userService: UserService, userInfoProvider: UserInfoProvider) extends Actor with Timers with ActorLogging {

  // Internal messages, sent from asynchronous callbacks.
  case class SignatoryAdded(signatory: DbSignatory)
  case class SignatoryRemoved(signatory: DbSignatory)
  case class SignatoriesLoaded(signatories: List[DbSignatory])
  case object LoadFailed

  import SignatoriesCache._

  import context._

  // The cached signatories.
  private var signatories: List[DbSignatory] = Nil
  // Hash code of the cached signatories.
  private var signatoriesHash = signatories.hashCode()
  // Prefix lookup index of the signatories
  private var signatoriesIndex = Trie.empty[DbSignatory]

  private def update(signatories: List[DbSignatory]): Unit = {
    this.signatories = signatories
    this.signatoriesHash = signatories.hashCode()
  }

  // Pending requests. Sent when we are
  private var pendingRequests: List[(ActorRef, AnyRef)] = Nil

  private val config = Configuration(system.settings.config.getConfig("signatories.cache"))
  private val profileRefreshInterval = config.get[FiniteDuration]("profile.refreshInterval")
  private val profileRefreshMax = config.get[Int]("profile.refreshMax")
  private val maxAgeUnsigned = config.get[FiniteDuration]("unsigned.maxAge")
  private val maxUnsignedDelete = config.get[Int]("unsigned.maxDelete")

  override def preStart(): Unit = {
    timers.startPeriodicTimer(Reload, Reload, config.get[FiniteDuration]("reloadInterval"))
    self ! Reload
  }

  // Default state, in error.
  def receive: Receive = {
    case GetSignatories => sender ! Signatories(Nil, Nil.hashCode())
    case Search(_) => sender ! Signatories(Nil, Nil.hashCode())
    case _: Sign => sender ! UpdateFailed("Not started")
    case _: Unsign => sender ! UpdateFailed("Not started")
    case Reload => loadSignatories()
  }

  // We are cold, we haven't loaded the database yet, so hold off returning anything or saving anything
  private def loading: Receive = {
    case g @ GetSignatories =>
      if (signatories.isEmpty) {
        pendingRequests = (sender(), g) :: pendingRequests
      } else {
        sender ! Signatories(signatories, signatoriesHash)
      }

    case s @ Search(query) =>
      if (signatories.isEmpty) {
        pendingRequests = (sender(), s) :: pendingRequests
      } else {
        sender ! search(query)
      }

    case s: Sign =>
      pendingRequests = (sender(), s) :: pendingRequests

    case u: Unsign =>
      pendingRequests = (sender(), u) :: pendingRequests

    case SignatoriesLoaded(sigs) =>
      signatories = sigs
      signatoriesHash = signatories.hashCode()
      val start = System.currentTimeMillis()
      signatoriesIndex = Trie(sigs.flatMap(s => extractSearchTerms(s).map(_ -> s)):_*)
      val time = System.currentTimeMillis() - start
      log.info(s"Indexing ${signatories.length} signatories took ${time}ms")
      become(hot)
      sendPending()
      refreshProfiles()
      deleteOldUnsignedProfiles()

    case LoadFailed =>
      // If loading failed, but we still have signatories to serve, then go hot and serve those
      if (signatories.isEmpty)
        unbecome()
      else
        become(hot)
      sendPending()
  }

  // We are hot, and guaranteed to be internally consistent on this node.
  private def hot: Receive = {

    case GetSignatories => sender ! Signatories(signatories, signatoriesHash)

    case Search(query) => sender ! search(query)

    /*
      Sign and unsign could have either failed for a known reason, ie returned Left, which we want to report back to
      the user, or failed for an unknown unrecoverable reason (eg database error), which we recover from by logging
      the error and converting it to an "Unknown error" message for the user.
     */

    case Sign(oid) =>
      val from = sender()
      userService.sign(oid).recover {
        case NonFatal(e) =>
          log.error("Error signing " + oid, e)
          Left("Unknown error")
      } foreach {
        case Left(err) => from ! UpdateFailed(err)
        case Right(s) =>
          from ! Updated(s)
          self ! SignatoryAdded(s)
      }

    case Unsign(oid) =>
      val from = sender()
      userService.unsign(oid).recover {
        case NonFatal(e) =>
          log.error("Error unsigning " + oid, e)
          Left("Unknown error")
      } foreach {
        case Left(err) => from ! UpdateFailed(err)
        case Right(s) =>
          from ! Updated(s)
          self ! SignatoryRemoved(s)
      }

    case SignatoryAdded(signatory) =>
      signatories ::= signatory
      signatoriesHash = signatories.hashCode()
      signatoriesIndex = signatoriesIndex.index(extractSearchTerms(signatory), signatory)

    case SignatoryRemoved(signatory) =>
      signatories = signatories.filterNot(_.id == signatory.id)
      signatoriesHash = signatories.hashCode()
      signatoriesIndex = signatoriesIndex.deindex(extractSearchTerms(signatory), _.id == signatory.id)

    case Reload => loadSignatories()
  }

  private def sendPending(): Unit = {
    pendingRequests.foreach { p =>
      self.!(p._2)(p._1)
    }
    pendingRequests = Nil
  }

  private def search(query: String): Signatories = {
    // Split into words, take a maximum of 2 terms
    val results = query.split("\\s+").take(2)
      // Search for all signatories for each term
      .map(signatoriesIndex.getAllWithPrefix)
      // Take the intersection of the results
      .reduce(_ intersect _)
      // Sorted
      .sortBy(_.name)

    Signatories(results, results.hashCode())
  }
  
  private def loadSignatories(): Unit = {
    log.info("Loading signatories...")
    become(loading)

    userService.loadSignatories().onComplete {
      case Success(results) =>
        log.info("Loaded " + results.size + " signatories")
        self ! SignatoriesLoaded(results)

      case Failure(t) =>
        log.error("Error loading signatories", t)
        self ! LoadFailed
    }
  }

  private def extractSearchTerms(signatory: DbSignatory) = signatory.name.split("\\s+")

  private def deleteOldUnsignedProfiles(): Unit = {
    userService.removeOldUnsignedProfiles(maxAgeUnsigned, maxUnsignedDelete)
      .foreach(deleted => log.info(s"Deleted $deleted old unsigned profiles"))
  }

  private def refreshProfiles(): Unit = {
    val refreshOlderThan = Instant.ofEpochMilli(System.currentTimeMillis() - profileRefreshInterval.toMillis)
    val toRefresh = signatories
      .filter(_.signed.nonEmpty)
      .sortBy(s => (s.profileLastRefreshed, s.signed))
      .take(profileRefreshMax)
      .takeWhile(_.profileLastRefreshed.forall(_.isBefore(refreshOlderThan)))
    refreshProfiles(toRefresh).foreach { _ =>
      log.info(s"Refreshed ${toRefresh.size} profiles")
    }
  }

  private def refreshProfiles(toRefresh: List[DbSignatory]): Future[Unit] = {
    toRefresh match {
      case Nil => Future.successful(())
      case signatory :: rest =>
        refreshProfile(signatory).flatMap { _ =>
          refreshProfiles(rest)
        }
    }
  }

  private def refreshProfile(signatory: DbSignatory): Future[Unit] = {
    val oauthDetails = signatory.provider match {
      case GitHub(id, _) =>
        userInfoProvider.lookupGitHubUser(id)
      case Google(id) =>
        userInfoProvider.lookupGoogleUser(id, signatory.name)
      case Twitter(id, _) =>
        userInfoProvider.lookupTwitterUser(id)
      case LinkedIn(id) =>
        Future.successful(None)
    }

    oauthDetails.recover {
      case e =>
        log.warning(s"Failed to fetch profile for signatory ${signatory.provider}", e)
        None
    }.flatMap {
      case Some(oauthUser) =>
        if (signatory.provider != oauthUser.provider || signatory.name != oauthUser.name ||
          signatory.avatarUrl != oauthUser.avatar) {

          log.info(s"Updating changed signatory ${signatory.provider} with details $oauthUser")

          userService.updateProfile(signatory.copy(
            provider = oauthUser.provider,
            name = signatory.name,
            avatarUrl = oauthUser.avatar
          ))
        } else {
          userService.touchProfile(signatory.id)
        }
      case None if signatory.provider.isInstanceOf[LinkedIn] =>
        userService.touchProfile(signatory.id)
      case None =>
        log.info(s"Signatory ${signatory.provider} no longer found with provider")
        userService.touchProfile(signatory.id)
    }
  }

}
