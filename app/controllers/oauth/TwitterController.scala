package controllers.oauth

import org.slf4j.LoggerFactory
import play.api.mvc._
import play.api.libs.ws.WSClient
import play.api.libs.oauth._
import services.{OAuthConfig, UserInfoProvider, UserService}

import scala.concurrent.ExecutionContext
import play.api.libs.json.{JsError, JsResultException, Json}

/**
 * Twitter login provider
 */
class TwitterController(components: ControllerComponents, config: OAuthConfig, ws: WSClient, userService: UserService,
  userInfoProvider: UserInfoProvider)(implicit ec: ExecutionContext) extends AbstractController(components) {

  private val log = LoggerFactory.getLogger(classOf[TwitterController])

  def authenticate = Action.async { implicit request =>

    import scala.concurrent.Future.{successful => sync}

    // See if this is a redirect
    request.getQueryString("oauth_verifier").map { verifier =>
      val tokenPair = sessionTokenPair(request).get
      // We got the verifier; now get the access token, store it and back to index
      config.twitter.retrieveAccessToken(tokenPair, verifier) match {
        case Right(token) =>

          userInfoProvider.lookupTwitterCurrentUser(token).flatMap { userInfo =>
            userService.findOrSaveUser(userInfo).map { signatory =>
              // Log the user in and return their details
              Ok(views.html.popup()).withSession("user" -> signatory.id.stringify)
            }
          }.recover {
            case JsResultException(errors) =>
              InternalServerError(views.html.jserror("Twitter", messagesApi.preferred(request), JsError(errors)))
            case e =>
              log.warn("Error logging in user to twitter", e)
              Forbidden("Twitter rejected credentials")
          }

        case Left(e) =>
          log.error("Failed to retrieve access token", e)
          sync(NotFound(Json.toJson(Json.obj("error" -> "Failed to retrieve access token"))))
      }
    }.getOrElse(
      config.twitter.retrieveRequestToken(routes.TwitterController.authenticate().absoluteURL(true)) match {
        case Right(t) =>
          // We received the unauthorized tokens in the OAuth object - store it before we proceed
          sync(Redirect(config.twitter.redirectUrl(t.token))
            .withSession("token" -> t.token, "secret" -> t.secret))

        case Left(e) =>
          log.error("Failed to retrieve request token", e)
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

