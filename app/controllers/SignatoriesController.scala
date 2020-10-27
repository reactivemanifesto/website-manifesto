package controllers

import akka.actor.ActorRef
import play.api.mvc._
import akka.pattern.ask
import akka.util.Timeout._
import akka.util.Timeout
import actors.SignatoriesCache._
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import reactivemongo.api.bson.BSONObjectID

/**
 * Provides access to signatories.  All access to the signatories is through an actor, which ensures consistent caching.
 */
class SignatoriesController(components: ControllerComponents, signatoriesActor: ActorRef)(implicit ec: ExecutionContext) extends AbstractController(components) {

  implicit val askTimeout: Timeout = 5.seconds

  /**
   * Gets a count of all the people that have signed the manifesto
   */
  def count = Action.async {
    (signatoriesActor ? GetSignatories).map {
      case Signatories(signatories, hash) =>
        Ok(Json.toJson(Json.obj("total" -> signatories.length)))
    }
  }

  /**
   * Lists all the users that have signed the manifesto
   *
   * @param page The page number, 1 based
   * @param perPage The number of users per page, maximum 200
   * @return The users that have signd the manifesto
   */
  def list(page: Int, perPage: Int) = pagedAction(page, perPage, routes.SignatoriesController.list) { () =>
    (signatoriesActor ? GetSignatories).mapTo[Signatories]
  }

  def search(page: Int, perPage: Int, query: String) = pagedAction(page, perPage,
    (p, pp) => routes.SignatoriesController.search(p, pp, query)) { () =>
    if (query.length < 2) {
      Future.successful(Signatories(Nil, 0))
    } else {
      (signatoriesActor ? Search(query)).mapTo[Signatories]
    }
  }

  def pagedAction(p: Int, pp: Int, reverseRoute: (Int, Int) => Call)(loadSignatories: () => Future[Signatories]) = Action.async { implicit req =>
    loadSignatories().map {
      case Signatories(signatories, hash) =>
        val total = signatories.length
        // Make page 0 based
        val page = Math.max(p - 1, 0)
        val perPage = Math.max(Math.min(pp, 200), 1)

        val etag = s""""$hash""""
        // Check E-Tag
        if (req.headers.get(IF_NONE_MATCH).contains(etag)) {
          NotModified
        } else {
          val sigPage = signatories.slice(page * perPage, page * perPage + perPage).map(_.toWeb)
          // It's minus 1 so that if it's an exact divisor, we don't get one extra page
          val lastPage = (total - 1) / perPage
          val linkHeader = if (lastPage > page) {
            val nextUrl= reverseRoute(page + 2, perPage).absoluteURL()
            val lastUrl = reverseRoute(lastPage + 1, perPage).absoluteURL()
            Some("Link" -> s"""<$nextUrl>; rel=next, <$lastUrl>; rel=last""")
          } else None

          Ok(Json.toJson(sigPage)).withHeaders(linkHeader.toSeq:_*).withHeaders(
            ETAG -> etag
          )
        }

    }
  }

  /**
   * Sign the manifesto
   */
  def sign = Action.async { req =>
    req.session.get("user") match {
      case Some(id) => (signatoriesActor ? Sign(BSONObjectID.parse(id).get)).map {
        case Updated(signatory) => Ok(Json.toJson(signatory.toWeb))
        case UpdateFailed(msg) => NotFound(Json.toJson(Json.obj("error" -> msg)))
      }
      case None => Future.successful(Forbidden)
    }
  }

  /**
   * Remove signature from the manifesto
   */
  def unsign = Action.async { req =>
    req.session.get("user") match {
      case Some(id) => (signatoriesActor ? Unsign(BSONObjectID.parse(id).get)).map {
        case Updated(signatory) => Ok(Json.toJson(signatory.toWeb))
        case UpdateFailed(msg) => NotFound(Json.toJson(Json.obj("error" -> msg)))
      }
      case None => Future.successful(Forbidden)
    }
  }
}