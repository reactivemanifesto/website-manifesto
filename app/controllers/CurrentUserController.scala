package controllers

import play.api.mvc.{AbstractController, ControllerComponents}
import services.UserService

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.Json


/**
 * Controller for managing the currently logged in user.
 */
class CurrentUserController(components: ControllerComponents, userService: UserService)(implicit ec: ExecutionContext)
  extends AbstractController(components) {

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
      case Some(id) => userService.findUser(id).map {
        case Some(signatory) => Ok(Json.toJson(signatory))
        case None => NotFound
      }
      case None => Future.successful(NotFound)
    }
  }
}
