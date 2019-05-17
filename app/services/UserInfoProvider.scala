package services

import models._
import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.oauth.{OAuthCalculator, RequestToken}
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.util.{Failure, Success}


class UserInfoProvider(ws: WSClient, oauthConfig: OAuthConfig)(implicit ec: ExecutionContext) {

  private val linkedInLog = Logger("linkedin-migration")

  import UserInfoProvider._

  def lookupGitHubCurrentUser(accessToken: String): Future[OAuthUser] = {
    makeGitHubUserRequest(ws.url("https://api.github.com/user")
      .addQueryStringParameters("access_token" -> accessToken))
      .map(_.getOrElse(throw new RuntimeException("User not found")))
  }

  def lookupGitHubUser(id: Long): Future[Option[OAuthUser]] = {
    makeGitHubUserRequest(ws.url(s"https://api.github.com/user/$id")
      .addQueryStringParameters(
        "client_id" -> oauthConfig.github.clientId,
        "client_secret" -> oauthConfig.github.clientSecret
      ))
  }

  private def makeGitHubUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>
      if (response.status == 200) {
        Some(response.json.as(githubOauthUserReads))
      } else if (response.status == 404) {
        None
      } else {
        throw new RuntimeException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

  def lookupGoogleCurrentUser(accessToken: String): Future[OAuthUser] = {
    ws.url("https://www.googleapis.com/oauth2/v2/userinfo")
      .addHttpHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $accessToken")
      .get()
      .map { response =>
        if (response.status == 200) {
          response.json.as(googleOAuthUserInfoReads)
        } else if (response.status == 404) {
          throw new RuntimeException("Current Google OAuth user not found")
        } else {
          throw new IllegalArgumentException("Error looking up Google userinfo information, status was: " + response.status + " " + response.body)
        }
      }
  }

  def lookupGoogleUser(id: String, existingName: String): Future[Option[OAuthUser]] = {
    ws.url(s"https://people.googleapis.com/v1/people/$id")
      .addQueryStringParameters(
        "personFields" -> "names,photos",
        "key" -> oauthConfig.googleApiKey
      ).get()
      .map { response =>
        if (response.status == 200) {
          val (name, avatar) = response.json.as(googlePeopleReads)
          Some(OAuthUser(Google(id), name.getOrElse(existingName), avatar))
        } else if (response.status == 404) {
          None
        } else {
          throw new IllegalArgumentException(s"Error looking up Google people information for $id, status was: ${response.status} ${response.body}")
        }
      }
  }

  def lookupTwitterCurrentUser(token: RequestToken): Future[OAuthUser] = {
    makeTwitterUserRequest(ws.url("https://api.twitter.com/1.1/account/verify_credentials.json")
      .sign(OAuthCalculator(oauthConfig.twitter.info.key, token)))
      .map(_.getOrElse(throw new RuntimeException("User not found")))
  }

  def lookupTwitterUser(id: Long): Future[Option[OAuthUser]] = {
    makeTwitterUserRequest(ws.url("https://api.twitter.com/1.1/users/show.json")
      .addQueryStringParameters("user_id" -> id.toString)
      .addHttpHeaders("Authorization" -> s"Bearer ${oauthConfig.twitterBearerToken}"))
  }

  private def makeTwitterUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>

      // Check if response is ok
      if (response.status == 200) {
        Some(response.json.as(twitterOauthUserReads))
      } else if (response.status == 404) {
        None
      } else {
        throw new IllegalArgumentException("Unable to get user details from Twitter, got response: " +
          response.status + " " + response.body)
      }
    }
  }

  def lookupLinkedInCurrentUser(accessToken: String): Future[OAuthUser] = {
    val v1Future = lookupLinkedInV1CurrentUser(accessToken)
      .transform(Success(_))
    val v2Future = lookupLinkedInV2CurrentUser(accessToken)
      .transform(Success(_))

    for {
      v1Try <- v1Future
      v2Try <- v2Future
    } yield {
      (v1Try, v2Try) match {
        case (Failure(error), Success(v2User)) =>
          linkedInLog.info(s"Failure loading ${v2User.provider} ${v2User.name} from V1 API, using V2 API instead: $error")
          v2User
        case (Success(v1User), Success(v2User)) if v2User == v1User =>
          linkedInLog.info(s"LinkedIn V1 and V2 API user objects for ${v1User.provider} ${v1User.name} match")
          v2User
        case (Success(v1User), Success(v2User)) =>
          linkedInLog.info(s"LinkedIn V1 and V2 API users don't match, v1: $v1User, v2: $v2User")
          v1User
        case (Success(v1User), Failure(error)) =>
          linkedInLog.info(s"Error retrieving LinkedIn V2 user for ${v1User.provider} ${v1User.name}: $error")
          v1User
        case (Failure(error), v2User) =>
          throw error
      }
    }
  }

  def lookupLinkedInV1CurrentUser(accessToken: String): Future[OAuthUser] = {
    ws.url("https://api.linkedin.com/v1/people/~:(id,picture-url,formatted-name)")
      .addQueryStringParameters("oauth2_access_token" -> accessToken)
      .addHttpHeaders("X-Li-Format" -> "json", "Accept" -> "application/json").get().map { response =>
      if (response.status == 200) {
        try {
          response.json.as(linkedinV1OauthUserReads)
        } catch {
          case NonFatal(e) => throw new IllegalArgumentException(s"Error parsing profile information: $e, ${response.body}")
        }
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

  def lookupLinkedInV2CurrentUser(accessToken: String): Future[OAuthUser] = {
    ws.url("https://api.linkedin.com/v2/me?projection=(id,localizedFirstName,localizedLastName,profilePicture(displayImage~:playableStreams))")
      .addHttpHeaders("Authorization" -> s"Bearer $accessToken").get().map { response =>
      if (response.status == 200) {
        try {
          response.json.as(linkedinV2OauthUserReads)
        } catch {
          case NonFatal(e) => throw new IllegalArgumentException(s"Error parsing profile information: $e, ${response.body}")
        }
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

}

object UserInfoProvider {
  val githubOauthUserReads: Reads[OAuthUser] = (
    (__ \ "id").read[Long] and
      (__ \ "login").read[String] and
      (__ \ "name").readNullable[String] and
      (__ \ "avatar_url").readNullable[String]
    ).apply((id, login, name, avatar) => OAuthUser(GitHub(id, login), name.getOrElse(login), avatar))

  val googleOAuthUserInfoReads: Reads[OAuthUser] = (
    (__ \ "id").read[String] and
      (__ \ "name").read[String] and
      (__ \ "picture").readNullable[String]
    ).apply((id, name, avatar) => OAuthUser(Google(id), name, avatar))

  private implicit class JsPathReadOptIfMissing(path: JsPath) {
    def readOptIfMissing[T: Reads]: Reads[Option[T]] = Reads { jsValue =>
      path.asSingleJson(jsValue) match {
        case JsDefined(value) => value.validate[T].map(Some.apply)
        case JsUndefined() => JsSuccess(None)
      }
    }
  }

  val googlePeopleReads: Reads[(Option[String], Option[String])] = (
    (__ \ "names" \ 0 \ "displayName").readOptIfMissing[String] and
      (__ \ "photos" \ 0 \ "url").readOptIfMissing[String]
    ).tupled

  val twitterOauthUserReads: Reads[OAuthUser] = (
    (__ \ "id").read[Long] and
      (__ \ "screen_name").read[String] and
      (__ \ "name").read[String] and
      (__ \ "profile_image_url_https").readNullable[String]
    ).apply((id, screenName, name, avatar) => OAuthUser(Twitter(id, screenName), name, avatar))

  val linkedinV1OauthUserReads: Reads[OAuthUser] = (
    (__ \ "id").read[String] and
      (__ \ "formattedName").read[String] and
      (__ \ "pictureUrl").readNullable[String]
    ).apply((id, name, avatar) => OAuthUser(LinkedIn(id), name, avatar))

  private case class LinkedInImageElement(url: String, size: Int)

  private object LinkedInImageElement {
    implicit val reads: Reads[LinkedInImageElement] = (
      (__ \ "identifiers" \ 0 \ "identifier").read[String] and
        (__ \ "data" \ "com.linkedin.digitalmedia.mediaartifact.StillImage" \ "displaySize" \ "width").read[Int]
    ) (LinkedInImageElement.apply _)
  }

  val linkedinV2OauthUserReads: Reads[OAuthUser] = (
    (__ \ "id").read[String] and
      (__ \ "localizedFirstName").read[String] and
      (__ \ "localizedLastName").read[String] and
      // omg.... linkedin.... what have you done.... hardest to use API ever.
      (__ \ "profilePicture" \ "displayImage~" \ "elements").readOptIfMissing[List[JsObject]]
    ) { (id, firstName, lastName, images) =>

    val avatar = images.getOrElse(Nil)
      .collect(Function.unlift(_.asOpt[LinkedInImageElement]))
        .filter(_.size >= 50)
        .sortBy(_.size)
        .map(_.url)
        .headOption

    OAuthUser(LinkedIn(id), s"$firstName $lastName", avatar)
  }


}
