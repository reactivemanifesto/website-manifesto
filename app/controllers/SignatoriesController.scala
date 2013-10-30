package controllers

import play.api.mvc._
import play.modules.reactivemongo.MongoController
import play.api.libs.concurrent.Akka
import akka.pattern.ask
import akka.util.Timeout._
import akka.util.Timeout
import actors.SignatoriesCache._
import play.api.libs.json.{JsArray, Json}
import reactivemongo.bson.BSONObjectID
import scala.concurrent.duration._

import play.api.Play.current
import models.Formats.signatoryFormat
import scala.concurrent.Future

/**
 * Provides access to signatories.  All access to the signatories is through an actor, which ensures consistent caching.
 */
object SignatoriesController extends Controller with MongoController {

  implicit val askTimeout: Timeout = 5 seconds

  lazy val signatoriesActor = {
    val system = Akka.system
    system.actorFor(system / "signatories")
  }

  /**
   * Gets a count of all the people that have signed the manifesto
   */
  def count = Action {
    Async {
      (signatoriesActor ? GetSignatories).map {
        case Signatories(signatories, hash) => {
          Ok(Json.toJson(Json.obj("total" -> signatories.length)))
        }
      }
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

  def pagedAction(p: Int, pp: Int, reverseRoute: (Int, Int) => Call)(loadSignatories: () => Future[Signatories]) = Action { implicit req =>
    Async {
      loadSignatories().map {
        case Signatories(signatories, hash) =>
          val total = signatories.length
          // Make page 0 based
          val page = Math.max(p - 1, 0)
          val perPage = Math.max(Math.min(pp, 200), 1)

          // Check E-Tag
          if (req.headers.get(IF_NONE_MATCH).exists(_ == hash.toString)) {
            NotModified
          } else {
            val sigPage = signatories.drop(page * perPage).take(perPage)
            // It's minus 1 so that if it's an exact divisor, we don't get one extra page
            val lastPage = (total - 1) / perPage
            val linkHeader = if (lastPage > page) {
              val nextUrl= reverseRoute(page + 2, perPage).absoluteURL()
              val lastUrl = reverseRoute(lastPage + 1, perPage).absoluteURL()
              Some("Link" -> s"""<$nextUrl>; rel="next", <$lastUrl>; rel="last"""")
            } else None

            Ok(Json.toJson(sigPage)).withHeaders(linkHeader.toSeq:_*).withHeaders(
              ETAG -> hash.toString
            )
          }

      }
    }
  }

  /**
   * Sign the manifesto
   */
  def sign = Action { req =>
    req.session.get("user") match {
      case Some(id) => AsyncResult((signatoriesActor ? Sign(new BSONObjectID(id))).map {
        case Updated(signatory) => Ok(Json.toJson(signatory))
        case UpdateFailed(msg) => NotFound(Json.toJson(Json.obj("error" -> msg)))
      })
      case None => Forbidden
    }
  }

  /**
   * Remove signature from the manifesto
   */
  def unsign = Action { req =>
    req.session.get("user") match {
      case Some(id) => AsyncResult((signatoriesActor ? Unsign(new BSONObjectID(id))).map {
        case Updated(signatory) => Ok(Json.toJson(signatory))
        case UpdateFailed(msg) => NotFound(Json.toJson(Json.obj("error" -> msg)))
      })
      case None => Forbidden
    }
  }
}