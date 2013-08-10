package controllers.oauth

import play.api.mvc._
import services._
import play.api.Play
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.util.control.NonFatal
import models.LinkedIn

/**
 * LinkedIn OAuth 2 login provider
 */
object LinkedInController extends OAuth2Controller {

  lazy val settings = (for {
    app <- Play.maybeApplication
    clientId <- app.configuration.getString("linkedin.clientId")
    clientSecret <- app.configuration.getString("linkedin.clientSecret")
  } yield {
    OAuth2Settings(
      clientId = clientId,
      clientSecret = clientSecret,
      signInUrl = "https://www.linkedin.com/uas/oauth2/authorization",
      accessTokenUrl = "https://www.linkedin.com/uas/oauth2/accessToken",
      scopes = Seq("r_basicprofile")
    )
  }).getOrElse(throw new IllegalStateException("Could not load LinkedIn creds"))

  val name = "LinkedIn"

  def redirectUri(implicit req: RequestHeader) = routes.LinkedInController.authenticate().absoluteURL()

  def extraParams = Seq("response_type" -> "code")

  def getUserInfo(accessToken: String): Future[UserService.OAuthUser] = {
    WS.url("https://api.linkedin.com/v1/people/~:(id,picture-url,formatted-name)")
      .withQueryString("oauth2_access_token" -> accessToken)
      .withHeaders("X-Li-Format" -> "json", "Accept" -> "application/json").get().map { response =>
      if (response.status == 200) {
        try {
          val id = (response.json \ "id").as[String]
          val name = (response.json \ "formattedName").as[String]
          val avatar = (response.json \ "pictureUrl").asOpt[String]
          UserService.OAuthUser(LinkedIn(id), name, avatar)
        } catch {
          case NonFatal(e) => throw new IllegalArgumentException("Error parsing profile information: " + response.body)
        }
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }
}
