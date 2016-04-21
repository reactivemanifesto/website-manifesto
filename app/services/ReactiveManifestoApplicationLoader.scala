package services

import actors.SignatoriesCache
import akka.actor.Props
import controllers.admin.AdminController
import controllers.oauth.{LinkedInController, GitHubController, GoogleController, TwitterController}
import controllers.{Assets, CurrentUserController, SignatoriesController, Application}
import play.api.http.HttpErrorHandler
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{BuiltInComponentsFromContext, ApplicationLoader}
import play.api.ApplicationLoader.Context
import play.modules.reactivemongo.{DefaultReactiveMongoApi, ReactiveMongoComponents}
import router.Routes
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ReactiveManifestoApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {

    val components = new BuiltInComponentsFromContext(context) with I18nComponents with AhcWSComponents with ReactiveMongoComponents {
      lazy val router = new Routes(httpErrorHandler, applicationController, signatoriesController,
        currentUserController, twitterController, googleController, gitHubController, linkedInController,
        adminController, assets)

      lazy val applicationController = new Application(messagesApi)
      lazy val signatoriesController = new SignatoriesController(signatoriesActor)
      lazy val currentUserController = new CurrentUserController(userService)
      lazy val adminController = new AdminController(oauthConfig, oauth2, userService, wsClient)(executionContext, messagesApi)
      lazy val assets = new Assets(httpErrorHandler)

      lazy val twitterController = new TwitterController(oauthConfig, wsClient, userService)
      lazy val googleController = new GoogleController(oauthConfig, wsClient, oauth2, userService)
      lazy val gitHubController = new GitHubController(oauthConfig, wsClient, oauth2, userService)
      lazy val linkedInController = new LinkedInController(oauthConfig, wsClient, oauth2, userService)

      lazy val oauthConfig = OAuthConfig.fromConfiguration(configuration)
      lazy val userService = new UserService(reactiveMongoApi)
      lazy val oauth2 = new OAuth2(wsClient)
      implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher

      lazy val reactiveMongoApi = new DefaultReactiveMongoApi(actorSystem, configuration, applicationLifecycle)

      lazy val signatoriesActor = {
        val actorRef = actorSystem.actorOf(Props(new SignatoriesCache(userService)), "signatories")
        actorSystem.scheduler.schedule(2.seconds, 10.minutes, actorRef, SignatoriesCache.Reload)
        actorRef
      }

      override lazy val httpErrorHandler: HttpErrorHandler = new ReactiveManifestoErrorHandler(environment,
        configuration, sourceMapper, Some(router))
    }

    // Make sure the actor is eager loaded
    components.signatoriesActor

    components.application
  }
}
