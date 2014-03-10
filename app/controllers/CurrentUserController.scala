package controllers

import play.api.mvc.{Controller, Action}
import scala.concurrent.Future
import services.UserService
import play.api.libs.json.Json

import models.Formats.signatoryFormat
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Controller for managing the currently logged in user.
 */
object CurrentUserController extends Controller {

  /**
   * Log the user out.
   *
   * @return 200 Ok
   */
  def logOut = Action {
    Ok.withNewSession
  }

  /**
   * Get the current user.
   *
   * @return The logged in user as JSON, or 404 if no user is logged in.
   */
  def getUser = Action.async { req =>
    req.session.get("user") match {
      case Some(id) => UserService.findUser(id).map {
        case Some(signatory) => Ok(Json.toJson(signatory))
        case None => NotFound
      }
      case None => Future.successful(NotFound)
    }
  }
}
