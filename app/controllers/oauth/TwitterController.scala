package controllers.oauth

import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.oauth._
import services.UserService
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api._
import models.Formats.signatoryFormat

/**
 * Twitter login provider
 */
object TwitterController extends Controller {
  lazy val Key = (for {
    app <- Play.maybeApplication
    authKey <- app.configuration.getString("twitter.authKey")
    authSecret <- app.configuration.getString("twitter.authSecret")
  } yield {
    ConsumerKey(authKey, authSecret)
  }).getOrElse(throw new IllegalStateException("Could not load twitter creds"))

  lazy val Twitter = OAuth(ServiceInfo(
    "https://api.twitter.com/oauth/request_token",
    "https://api.twitter.com/oauth/access_token",
    "https://api.twitter.com/oauth/authorize", Key),
    use10a = true)

  def authenticate = Action { implicit request =>

    // See if this is a redirect
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      Twitter.retrieveAccessToken(tokenPair, verifier) match {
        case Right(token) => {

          // We received the authorized tokens in the OAuth object - use them to find the details of the user
          AsyncResult(WS.url("https://api.twitter.com/1.1/account/verify_credentials.json")
            .sign(OAuthCalculator(Key, token)).get().flatMap { response =>

            // Check if response is ok
            if (response.status == 200) {
              val id = (response.json \ "id").as[Long]
              val screenName = (response.json \ "screen_name").as[String]
              val name = (response.json \ "name").as[String]
              val avatar = (response.json \ "profile_image_url").asOpt[String]
              val userInfo = UserService.OAuthUser(models.Twitter(id, screenName), name, avatar)
              UserService.findOrSaveUser(userInfo).map { signatory =>
                // Log the user in and return their details
                Ok(views.html.popup()).withSession("user" -> signatory.id.stringify)
              }
            } else {
              Logger.error("Unable to get user details from Twitter, got response: " +
                response.status + " " + response.body)
              Future.successful(Forbidden(Json.toJson(Json.obj("error" -> "Twitter rejected credentials"))))
            }
          })
        }
        case Left(e) => {
          Logger.error("Failed to retrieve access token", e)
          NotFound(Json.toJson(Json.obj("error" -> "Failed to retrieve access token")))
        }
      }
    }.getOrElse(
      Twitter.retrieveRequestToken(routes.TwitterController.authenticate().absoluteURL()) match {
        case Right(t) => {
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          Redirect(Twitter.redirectUrl(t.token))
            .withSession("token" -> t.token, "secret" -> t.secret)
        }
        case Left(e) => {
          Logger.error("Failed to retrieve request token", e)
          NotFound(Json.toJson(Json.obj("error" -> "Failed to retrieve request token")))
        }
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

