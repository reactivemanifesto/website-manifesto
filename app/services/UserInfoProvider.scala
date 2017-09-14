package services

import models._
import play.api.libs.oauth.{OAuthCalculator, RequestToken}
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import play.api.libs.json._
import play.api.libs.functional.syntax._


class UserInfoProvider(ws: WSClient, oauthConfig: OAuthConfig)(implicit ec: ExecutionContext) {

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

  private val githubOauthUserReads = (
    (__ \ "id").read[Long] and
    (__ \ "login").read[String] and
    (__ \ "name").readNullable[String] and
    (__ \ "avatar_url").readNullable[String]
  ).apply((id, login, name, avatar) => OAuthUser(GitHub(id, login), name.getOrElse(login), avatar))

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
    makeGoogleUserRequest(ws.url("https://www.googleapis.com/plus/v1/people/me")
      .addQueryStringParameters("access_token" -> accessToken))
      .map(_.getOrElse(throw new RuntimeException("User not found")))
  }

  def lookupGoogleUser(id: String): Future[Option[OAuthUser]] = {
    makeGoogleUserRequest(ws.url(s"https://www.googleapis.com/plus/v1/people/$id")
      .addQueryStringParameters("key" -> oauthConfig.googleApiKey))
  }

  private val googleOauthUserReads = (
    (__ \ "id").read[String] and
    (__ \ "displayName").read[String] and
    (__ \ "image" \ "url").readNullable[String]
  ).apply((id, name, avatar) => OAuthUser(Google(id), name, avatar))

  private def makeGoogleUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>
      if (response.status == 200) {
        Some(response.json.as(googleOauthUserReads))
      } else if (response.status == 404) {
        None
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
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

  private val twitterOauthUserReads = (
    (__ \ "id").read[Long] and
    (__ \ "screen_name").read[String] and
    (__ \ "name").read[String] and
    (__ \ "profile_image_url_https").readNullable[String]
  ).apply((id, screenName, name, avatar) => OAuthUser(Twitter(id, screenName), name, avatar))

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

  private val linkedinOauthUserReads = (
    (__ \ "id").read[String] and
    (__ \ "formattedName").read[String] and
    (__ \ "pictureUrl").readNullable[String]
  ).apply((id, name, avatar) => OAuthUser(LinkedIn(id), name, avatar))

  def lookupLinkedInCurrentUser(accessToken: String): Future[OAuthUser] = {
    ws.url("https://api.linkedin.com/v1/people/~:(id,picture-url,formatted-name)")
      .addQueryStringParameters("oauth2_access_token" -> accessToken)
      .addHttpHeaders("X-Li-Format" -> "json", "Accept" -> "application/json").get().map { response =>
      if (response.status == 200) {
        try {
          response.json.as(linkedinOauthUserReads)
        } catch {
          case NonFatal(e) => throw new IllegalArgumentException("Error parsing profile information: " + response.body)
        }
      } else {
        throw new IllegalArgumentException("Error looking up profile information, status was: " + response.status + " " + response.body)
      }
    }
  }

}
