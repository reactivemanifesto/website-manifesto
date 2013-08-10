package controllers.oauth

import play.api.mvc._
import services._
import play.api.Play
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import models.Google

/**
 * Google OAuth 2 login provider
 */
object GoogleController extends OAuth2Controller {

  lazy val settings = (for {
    app <- Play.maybeApplication
    clientId <- app.configuration.getString("google.clientId")
    clientSecret <- app.configuration.getString("google.clientSecret")
  } yield {
    OAuth2Settings(
      clientId = clientId,
      clientSecret = clientSecret,
      signInUrl = "https://accounts.google.com/o/oauth2/auth",
      accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
      scopes = Seq("openid", "profile")
    )
  }).getOrElse(throw new IllegalStateException("Could not load Google creds"))

  val name = "Google"

  def redirectUri(implicit req: RequestHeader) = routes.GoogleController.authenticate().absoluteURL()

  def extraParams = Seq("response_type" -> "code")

  def getUserInfo(accessToken: String): Future[UserService.OAuthUser] = {
    WS.url("https://www.googleapis.com/plus/v1/people/me")
      .withQueryString("access_token" -> accessToken).get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[String]
        val name = (response.json \ "displayName").as[String]
        val avatar = (response.json \ "image" \ "url").asOpt[String]
        UserService.OAuthUser(Google(id), name, avatar)
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }
}
