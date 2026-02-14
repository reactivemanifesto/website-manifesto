package controllers.oauth

import play.api.mvc._
import services._
import play.api.libs.ws.WSClient
import scala.concurrent.{ExecutionContext, Future}
import models.OAuthUser

/**
 * GitHub OAuth2 login provider
 */
class GitHubController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, oauth2: OAuth2,
  userService: UserService, userInfoProvider: UserInfoProvider)(implicit ec: ExecutionContext)
  extends OAuth2Controller(components, ws, oauth2, userService, "GitHub", config.github) {

  val settings = config.github

  val name = "GitHub"

  def redirectUri(implicit req: RequestHeader) = routes.GitHubController.authenticate().absoluteURL(true)

  def getUserInfo(accessToken: String): Future[OAuthUser] = userInfoProvider.lookupGitHubCurrentUser(accessToken)
}
