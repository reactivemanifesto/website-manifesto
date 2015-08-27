package controllers.oauth

import models.OAuthUser
import play.api._
import play.api.libs.ws.WSClient
import services._
import scala.util.control.NonFatal
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Generic controller containing common functionality between all the OAuth2 login services.
 */
abstract class OAuth2Controller(ws: WSClient, oauth2: OAuth2, userService: UserService, name: String,
                                settings: OAuth2Settings, extraParams: Seq[(String, String)] = Nil)(implicit ec: ExecutionContext) extends Controller {

  /**
   * Authenticate action.  The same URL is used for authentication and for redirecting to with the access token.
   */
  def authenticate = Action.async { implicit req =>

    import scala.concurrent.Future.{successful => sync}

    // Check if this is an authentication request, or a access token request.
    req.queryString.get("code").flatMap(_.headOption) match {

      case Some(code) =>
        // It's an access token request.  Get the state from the session and from the query string.
        (for {
          sessionState <- req.session.get("state")
          queryStateValues <- req.queryString.get("state")
          queryState <- queryStateValues.headOption
        } yield {
          // Verify that the state matches.
          if (queryState == sessionState) {
            (for {
              // Get the access token from the OAuth service
              accessToken <- oauth2.requestAccessToken(settings, redirectUri, code)
              // And the user info from the OAuth service
              userInfo <- getUserInfo(accessToken)
              // And find or save that user
              signatory <- userService.findOrSaveUser(userInfo)
            } yield {
              // Return the page for the popup that will communicate back to the main page what the user is,
              // and then close itself.
              Ok(views.html.popup()).withSession("user" -> signatory.id.stringify)
            }).recover {
              case NonFatal(t) =>
                Logger.warn("Error logging in user to " + name, t)
                Forbidden
            }
          } else {

            // The state doesn't match, reject the request.
            sync(Forbidden("State doesn't match"))
          }
        }).getOrElse(sync(NotFound("State not found")))

      case None =>
        // It's an authentication request, generate state, and redirect the user to the service
        val state = oauth2.generateState
        sync(Redirect(oauth2.signInUrl(settings, redirectUri, state, extraParams:_*)).withSession("state" -> state))
    }
  }

  /**
   * Get the redirect URI for this service
   */
  def redirectUri(implicit req: RequestHeader): String

  /**
   * Get the user info fro this service for the given access token
   */
  def getUserInfo(accessToken: String): Future[OAuthUser]

}
