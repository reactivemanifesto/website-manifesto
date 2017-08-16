package controllers.oauth

import play.api.mvc.{ControllerComponents, RequestHeader}
import services._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}
import models.{Google, OAuthUser}

/**
 * Google OAuth 2 login provider
 */
class GoogleController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, oauth2: OAuth2, userService: UserService)(implicit ec: ExecutionContext)
  extends OAuth2Controller(components, ws, oauth2, userService, "Google", config.google, Seq("response_type" -> "code")) {

  def getUserInfo(accessToken: String): Future[OAuthUser] = {
    ws.url("https://www.googleapis.com/plus/v1/people/me")
      .addQueryStringParameters("access_token" -> accessToken).get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[String]
        val name = (response.json \ "displayName").as[String]
        val avatar = (response.json \ "image" \ "url").asOpt[String]
        OAuthUser(Google(id), name, avatar)
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

  def redirectUri(implicit req: RequestHeader) = routes.GoogleController.authenticate().absoluteURL()

}
