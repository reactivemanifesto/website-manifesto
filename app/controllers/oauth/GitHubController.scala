package controllers.oauth

import play.api.mvc._
import services._
import play.api.libs.ws.WSClient
import scala.concurrent.{ExecutionContext, Future}
import models.{OAuthUser, GitHub}

/**
 * GitHub OAuth2 login provider
 */
class GitHubController(config: OAuthConfig, ws: WSClient, oauth2: OAuth2, userService: UserService)(implicit ec: ExecutionContext)
  extends OAuth2Controller(ws, oauth2, userService, "GitHub", config.github) {

  val settings = config.github

  val name = "GitHub"

  def redirectUri(implicit req: RequestHeader) = routes.GitHubController.authenticate().absoluteURL()

  def getUserInfo(accessToken: String): Future[OAuthUser] = {
    ws.url("https://api.github.com/user")
      .withQueryString("access_token" -> accessToken).get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[Long]
        val login = (response.json \ "login").as[String]
        val name = (response.json \ "name").as[String]
        val avatar = (response.json \ "avatar_url").asOpt[String]
        OAuthUser(GitHub(id, login), name, avatar)
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }
}
