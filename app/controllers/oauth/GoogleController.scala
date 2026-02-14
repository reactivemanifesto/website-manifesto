package controllers.oauth

import play.api.mvc.{ControllerComponents, RequestHeader}
import services._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import models.OAuthUser

/**
 * Google OAuth 2 login provider
 */
class GoogleController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, oauth2: OAuth2,
  userService: UserService, userInfoProvider: UserInfoProvider)(implicit ec: ExecutionContext)
  extends OAuth2Controller(components, ws, oauth2, userService, "Google", config.google, Seq("response_type" -> "code")) {

  def getUserInfo(accessToken: String): Future[OAuthUser] = userInfoProvider.lookupGoogleCurrentUser(accessToken)

  def redirectUri(implicit req: RequestHeader) = routes.GoogleController.authenticate().absoluteURL(true)

}
