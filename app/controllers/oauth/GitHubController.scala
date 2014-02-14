package controllers.oauth

import play.api.mvc._
import services._
import play.api.Play
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models.GitHub

/**
 * GitHub OAuth2 login provider
 */
object GitHubController extends OAuth2Controller {

  lazy val settings = (for {
    app <- Play.maybeApplication
    clientId <- app.configuration.getString("github.clientId")
    clientSecret <- app.configuration.getString("github.clientSecret")
  } yield {
    OAuth2Settings(
      clientId = clientId,
      clientSecret = clientSecret,
      signInUrl = "https://github.com/login/oauth/authorize",
      accessTokenUrl = "https://github.com/login/oauth/access_token",
      scopes = Seq()
    )
  }).getOrElse(throw new IllegalStateException("Could not load GitHub creds"))

  val name = "GitHub"

  def redirectUri(implicit req: RequestHeader) = routes.GitHubController.authenticate().absoluteURL()

  def extraParams = Nil

  def getUserInfo(accessToken: String): Future[UserService.OAuthUser] = {
    WS.url("https://api.github.com/user")
      .withQueryString("access_token" -> accessToken).get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[Long]
        val login = (response.json \ "login").as[String]
        val name = (response.json \ "name").as[String]
        val avatar = (response.json \ "avatar_url").asOpt[String]
        UserService.OAuthUser(GitHub(id, login), name, avatar)
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }
}
