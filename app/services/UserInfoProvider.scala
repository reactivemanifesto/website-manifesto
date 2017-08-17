package services

import models.{GitHub, Google, LinkedIn, OAuthUser}
import play.api.libs.oauth.{OAuthCalculator, RequestToken}
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

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

  private def makeGitHubUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[Long]
        val login = (response.json \ "login").as[String]
        val name = (response.json \ "name").as[String]
        val avatar = (response.json \ "avatar_url").asOpt[String]
        Some(OAuthUser(GitHub(id, login), name, avatar))
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

  private def makeGoogleUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>
      if (response.status == 200) {
        val id = (response.json \ "id").as[String]
        val name = (response.json \ "displayName").as[String]
        val avatar = (response.json \ "image" \ "url").asOpt[String]
        Some(OAuthUser(Google(id), name, avatar))
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

  private def makeTwitterUserRequest(request: WSRequest): Future[Option[OAuthUser]] = {
    request.get().map { response =>

      // Check if response is ok
      if (response.status == 200) {
        val id = (response.json \ "id").as[Long]
        val screenName = (response.json \ "screen_name").as[String]
        val name = (response.json \ "name").as[String]
        val avatar = (response.json \ "profile_image_url").asOpt[String]
        Some(OAuthUser(models.Twitter(id, screenName), name, avatar))
      } else if (response.status == 404) {
        None
      } else {
        throw new IllegalArgumentException("Unable to get user details from Twitter, got response: " +
          response.status + " " + response.body)
      }
    }
  }

  def lookupLinkedInCurrentUser(accessToken: String): Future[OAuthUser] = {
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
