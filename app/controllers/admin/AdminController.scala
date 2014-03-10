package controllers.admin

import controllers.oauth.GitHubController
import play.api.mvc._
import services.{UserService, OAuth2}
import scala.util.control.NonFatal
import play.api.Logger
import scala.concurrent.Future
import play.api.libs.ws.WS
import models._
import play.api.libs.concurrent.Execution.Implicits._
import org.joda.time.format.DateTimeFormat
import scala.util.control.Exception._

object AdminController extends Controller {

  case class FormattedSignatory(id: String, name: String, provider: String, providerId: String,
                                providerScreenName: String, signed: String, avatarUrl: String)

  lazy val settings = GitHubController.settings

  val Expires = 7200000

  def redirectUri(implicit req: RequestHeader) = routes.AdminController.authenticate().absoluteURL()

  def login = Action { implicit req =>
    val state = OAuth2.generateState
    Redirect(OAuth2.signInUrl(settings, redirectUri, state)).withSession("state" -> state)
  }

  def authenticate = Action.async { implicit req =>

    import scala.concurrent.Future.{successful => sync}

    req.queryString.get("code").flatMap(_.headOption) match {

      case Some(code) => {
        // It's an access token request.  Get the state from the session and from the query string.
        (for {
          sessionState <- req.session.get("state")
          queryStateValues <- req.queryString.get("state")
          queryState <- queryStateValues.headOption
        } yield {
          // Verify that the state matches.
          if (queryState == sessionState) {
            (for {
              // Get the access token from the OAuth service
              accessToken <- OAuth2.requestAccessToken(settings, redirectUri, code)
              // And the user organisation
              userOrgs <- getUserOrgs(accessToken)
            } yield {
              // Check that the user is an admin
              if (userOrgs.contains("typesafehub")) {
                Redirect(routes.AdminController.index()).withSession("admin" -> "true",
                  "timestamp" -> System.currentTimeMillis().toString)
              } else {
                Forbidden("Not a member of Typesafe")
              }
            }).recover {
              case NonFatal(t) => {
                Logger.warn("Error logging in user", t)
                Forbidden
              }
            }
          } else {
            // The state doesn't match, reject the request.
            sync(Forbidden("State doesn't match"))
          }
        }).getOrElse(sync(NotFound("State not found")))

      }
      case None => sync(BadRequest)
    }
  }

  def isAuthenticated(req: RequestHeader) = {
    (for {
      admin <- req.session.get("admin")
      if admin == "true"
      timestampStr <- req.session.get("timestamp")
      timestamp <- catching(classOf[NumberFormatException]).opt(timestampStr.toLong)
      if timestamp + Expires > System.currentTimeMillis()
    } yield true).getOrElse(false)
  }

  object Authenticated extends ActionBuilder[Request] {
    protected def invokeBlock[A](request: Request[A], block: Request[A] => Future[SimpleResult]) = {
      if (isAuthenticated(request)) {
        block(request)
      } else {
        Future.successful(Redirect(routes.AdminController.index()))
      }
    }
  }

  def logout = Action {
    Redirect(routes.AdminController.index()).withNewSession
  }

  def index = Action { req =>
    if (isAuthenticated(req)) {
      Ok(views.html.admin.index())
    } else {
      Ok(views.html.admin.login())
    }
  }

  def getFormatedSigs: Future[List[FormattedSignatory]] = {
    UserService.loadSignatories().map(_.map { sig =>
      val (provider, id, screenName) = sig.provider match {
        case Twitter(id, screenName) => ("twitter", id.toString, screenName)
        case GitHub(id, screenName) => ("github", id.toString, screenName)
        case Google(id) => ("google", id, "")
        case LinkedIn(id) => ("linkedin", id, "")
      }
      val signed = sig.signed.map(_.toString(DateTimeFormat.forPattern("yyyy/MM/dd HH:mm"))).getOrElse("")
      val avatarUrl = sig.avatarUrl.getOrElse("")
      FormattedSignatory(sig.id.stringify, sig.name, provider, id, screenName, signed, avatarUrl)
    })
  }

  def list = Authenticated.async { req =>
    getFormatedSigs.map { sigs =>
      Ok(views.html.admin.list(sigs))
    }
  }

  def csv = Authenticated.async { req =>
    getFormatedSigs.map { sigs =>
      Ok(sigs.map { sig =>
        s"""${sig.id},"${sig.name}",${sig.provider},${sig.providerId},"${sig.providerScreenName}",${sig.signed},"${sig.avatarUrl}""""
      }.mkString("\n")).as("text/csv").withHeaders("Content-Disposition" -> "attachment;filename=signatories.csv")
    }
  }

  def getUserOrgs(accessToken: String): Future[Seq[String]] = {
    WS.url("https://api.github.com/user/orgs")
      .withQueryString("access_token" -> accessToken).get().map { response =>
      if (response.status == 200) {
        (response.json \\ "login").map(_.as[String])
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

}
