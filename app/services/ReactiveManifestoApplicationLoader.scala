package services

import actors.SignatoriesCache
import akka.actor.{ActorRef, Props}
import controllers.admin.AdminController
import controllers.oauth.{GitHubController, GoogleController, LinkedInController, TwitterController}
import controllers._
import play.api.http.HttpErrorHandler
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator, Mode}
import play.api.ApplicationLoader.Context
import play.modules.reactivemongo.DefaultReactiveMongoApi
import router.Routes
import com.softwaremill.macwire._
import reactivemongo.api.MongoConnection

class ReactiveManifestoApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {

    val components = new BuiltInComponentsFromContext(context)
      with I18nComponents
      with AhcWSComponents
      with AssetsComponents {

      LoggerConfigurator(environment.classLoader).foreach(_.configure(environment))

      // see https://github.com/ReactiveMongo/Play-ReactiveMongo/issues/245
      lazy val mongodbUri: MongoConnection.ParsedURI = MongoConnection.parseURI(configuration.underlying.getString("mongodb.uri")).get
      lazy val reactiveMongoApi = new DefaultReactiveMongoApi(
        name = "default",
        parsedUri = mongodbUri,
        dbName = mongodbUri.db.getOrElse(sys.error("Could not parse database name from mongodb.uri: " + mongodbUri)),
        strictMode = false,
        configuration = configuration,
        applicationLifecycle = applicationLifecycle
      )

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

      override lazy val httpFilters = {
        if (environment.mode == Mode.Prod) {
          Seq(wire[ReactiveManifestoFilter])
        } else {
          Nil
        }
      }
    }

    // Make sure the actor is eager loaded
    components.signatoriesActor

    components.application
  }
}
