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
class LinkedInController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, oauth2: OAuth2, userService: UserService)(implicit ec: ExecutionContext)
  extends OAuth2Controller(components, ws, oauth2, userService, "LinkedIn", config.linkedIn, Seq("response_type" -> "code")) {

  def redirectUri(implicit req: RequestHeader) = routes.LinkedInController.authenticate().absoluteURL()

  def getUserInfo(accessToken: String): Future[OAuthUser] = {
    ws.url("https://api.linkedin.com/v1/people/~:(id,picture-url,formatted-name)")
      .addQueryStringParameters("oauth2_access_token" -> accessToken)
      .addHttpHeaders("X-Li-Format" -> "json", "Accept" -> "application/json").get().map { response =>
      if (response.status == 200) {
        try {
          val id = (response.json \ "id").as[String]
          val name = (response.json \ "formattedName").as[String]
          val avatar = (response.json \ "pictureUrl").asOpt[String]
          OAuthUser(LinkedIn(id), name, avatar)
        } catch {
          case NonFatal(e) => throw new IllegalArgumentException("Error parsing profile information: " + response.body)
        }
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }
}
