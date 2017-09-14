package services

import actors.SignatoriesCache
import akka.actor.{ActorRef, Props}
import controllers.admin.AdminController
import controllers.oauth.{GitHubController, GoogleController, LinkedInController, TwitterController}
import controllers._
import play.api.http.HttpErrorHandler
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import play.api.ApplicationLoader.Context
import play.modules.reactivemongo.{DefaultReactiveMongoApi, ReactiveMongoComponents}
import router.Routes

import scala.concurrent.duration._
import com.softwaremill.macwire._

class ReactiveManifestoApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {

    val components = new BuiltInComponentsFromContext(context)
      with I18nComponents
      with AhcWSComponents
      with AssetsComponents {

      // see https://github.com/ReactiveMongo/Play-ReactiveMongo/issues/245
      lazy val reactiveMongoApi = new DefaultReactiveMongoApi(configuration, applicationLifecycle)

      lazy val oauthConfig = OAuthConfig.fromConfiguration(configuration)
      lazy val userService = wire[UserService]
      lazy val userInfoProvider = wire[UserInfoProvider]
      lazy val oauth2 = wire[OAuth2]

      lazy val signatoriesActor: ActorRef = actorSystem.actorOf(Props(new SignatoriesCache(userService, userInfoProvider)), "signatories")

      lazy val applicationController = wire[Application]
      lazy val signatoriesController = wire[SignatoriesController]
      lazy val currentUserController = wire[CurrentUserController]
      lazy val adminController = wire[AdminController]

      lazy val twitterController = wire[TwitterController]
      lazy val googleController = wire[GoogleController]
      lazy val gitHubController = wire[GitHubController]
      lazy val linkedInController = wire[LinkedInController]


      override lazy val httpErrorHandler: HttpErrorHandler = new ReactiveManifestoErrorHandler(environment,
        configuration, sourceMapper, Some(router))

      override lazy val router = {
        val prefix: String = "/"
        wire[Routes]
      }

      override lazy val httpFilters = Seq(wire[ReactiveManifestoFilter])
    }

    // Make sure the actor is eager loaded
    components.signatoriesActor

    components.application
  }
}
