package controllers.oauth

import models.OAuthUser
import play.api.mvc._
import play.api.libs.ws.WSClient
import play.api.libs.oauth._
import services.{OAuthConfig, UserService}
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json
import play.api._

/**
 * Twitter login provider
 */
class TwitterController(config: OAuthConfig, ws: WSClient, userService: UserService)(implicit ec: ExecutionContext) extends Controller {

  def authenticate = Action.async { implicit request =>

    import scala.concurrent.Future.{successful => sync}

    // See if this is a redirect
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      config.twitter.retrieveAccessToken(tokenPair, verifier) match {
        case Right(token) =>

          // We received the authorized tokens in the OAuth object - use them to find the details of the user
          ws.url("https://api.twitter.com/1.1/account/verify_credentials.json")
            .sign(OAuthCalculator(config.twitter.info.key, token)).get().flatMap { response =>

            // Check if response is ok
            if (response.status == 200) {
              val id = (response.json \ "id").as[Long]
              val screenName = (response.json \ "screen_name").as[String]
              val name = (response.json \ "name").as[String]
              val avatar = (response.json \ "profile_image_url").asOpt[String]
              val userInfo = OAuthUser(models.Twitter(id, screenName), name, avatar)
              userService.findOrSaveUser(userInfo).map { signatory =>
                // Log the user in and return their details
                Ok(views.html.popup()).withSession("user" -> signatory.id.stringify)
              }
            } else {
              Logger.error("Unable to get user details from Twitter, got response: " +
                response.status + " " + response.body)
              sync(Forbidden(Json.toJson(Json.obj("error" -> "Twitter rejected credentials"))))
            }
          }

        case Left(e) =>
          Logger.error("Failed to retrieve access token", e)
          sync(NotFound(Json.toJson(Json.obj("error" -> "Failed to retrieve access token"))))
      }
    }.getOrElse(
      config.twitter.retrieveRequestToken(routes.TwitterController.authenticate().absoluteURL()) match {
        case Right(t) =>
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          sync(Redirect(config.twitter.redirectUrl(t.token))
            .withSession("token" -> t.token, "secret" -> t.secret))

        case Left(e) =>
          Logger.error("Failed to retrieve request token", e)
          sync(NotFound(Json.toJson(Json.obj("error" -> "Failed to retrieve request token"))))
      })
  }

  def sessionTokenPair(implicit request: RequestHeader): Option[RequestToken] = {
    for {
      token <- request.session.get("token")
      secret <- request.session.get("secret")
    } yield {
      RequestToken(token, secret)
    }
  }
}

