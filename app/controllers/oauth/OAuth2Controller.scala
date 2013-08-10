package controllers.oauth

import play.api._
import services._
import scala.util.control.NonFatal
import play.api.mvc._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Generic controller containing common functionality between all the OAuth2 login services.
 */
trait OAuth2Controller extends Controller {

  /**
   * The settings for this OAuth service
   */
  def settings: OAuth2Settings

  /**
   * The name of this service.  Used for error reporting/logging.
   */
  def name: String

  /**
   * Authenticate action.  The same URL is used for authentication and for redirecting to with the access token.
   */
  def authenticate = Action { implicit req =>

    // Check if this is an authentication request, or a access token request.
    req.queryString.get("code").flatMap(_.headOption) match {

      case Some(code) => {
        // It's an access token request.  Get the state from the session and from the query string.
        (for {
          sessionState <- req.session.get("state")
          queryStateValues <- req.queryString.get("state")
          queryState <- queryStateValues.headOption
        } yield {
          // Verify that the state matches.
          if (queryState == sessionState) {
            Async {
              (for {
                // Get the access token from the OAuth service
                accessToken <- OAuth2.requestAccessToken(settings, redirectUri, code)
                // And the user info from the OAuth service
                userInfo <- getUserInfo(accessToken)
                // And find or save that user
                signatory <- UserService.findOrSaveUser(userInfo)
              } yield {
                // Return the page for the popup that will communicate back to the main page what the user is,
                // and then close itself.
                Ok(views.html.popup()).withSession("user" -> signatory.id.stringify)
              }).recover {
                case NonFatal(t) => {
                  Logger.warn("Error logging in user to " + name, t)
                  Forbidden
                }
              }
            }
          } else {

            // The state doesn't match, reject the request.
            Forbidden("State doesn't match")
          }
        }).getOrElse(NotFound("State not found"))
      }

      case None => {
        // It's an authentication request, generate state, and redirect the user to the service
        val state = OAuth2.generateState
        Redirect(OAuth2.signInUrl(settings, redirectUri, state, extraParams:_*)).withSession("state" -> state)
      }
    }
  }

  /**
   * Extra service specific params necessary for the redirect to the OAuth service
   */
  def extraParams: Seq[(String, String)]

  /**
   * Get the redirect URI for this service
   */
  def redirectUri(implicit req: RequestHeader): String

  /**
   * Get the user info fro this service for the given access token
   */
  def getUserInfo(accessToken: String): Future[UserService.OAuthUser]

}
