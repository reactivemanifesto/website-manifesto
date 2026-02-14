package controllers.oauth

import play.api.mvc._
import services._
import play.api.libs.ws.WSClient
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import models.{OAuthUser, LinkedIn}

/**
 * LinkedIn OAuth 2 login provider
 */
class LinkedInController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, oauth2: OAuth2,
  userService: UserService, userInfoProvider: UserInfoProvider)(implicit ec: ExecutionContext)
  extends OAuth2Controller(components, ws, oauth2, userService, "LinkedIn", config.linkedIn, Seq("response_type" -> "code")) {

  def redirectUri(implicit req: RequestHeader) = routes.LinkedInController.authenticate().absoluteURL(true)

  def getUserInfo(accessToken: String): Future[OAuthUser] = userInfoProvider.lookupLinkedInCurrentUser(accessToken)
}
