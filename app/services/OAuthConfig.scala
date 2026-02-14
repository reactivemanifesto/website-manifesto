package services

import play.api.Configuration
import play.api.libs.oauth.{ConsumerKey, OAuth, ServiceInfo}

case class OAuthConfig(twitter: OAuth, google: OAuth2Settings, github: OAuth2Settings, linkedIn: OAuth2Settings,
  googleApiKey: String, twitterBearerToken: String)

object OAuthConfig {


  def fromConfiguration(configuration: Configuration): OAuthConfig = {
    def loadOAuth2(name: String, signInUrl: String, accessTokenUrl: String, scopes: Seq[String] = Nil) = {
      OAuth2Settings(
        clientId = configuration.get[String](s"$name.clientId"),
        clientSecret = configuration.get[String](s"$name.clientSecret"),
        signInUrl = signInUrl,
        accessTokenUrl = accessTokenUrl,
        scopes = scopes
      )
    }

    val google = loadOAuth2(
      name = "google",
      signInUrl = "https://accounts.google.com/o/oauth2/auth",
      accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
      scopes = Seq("openid", "profile")
    )
    val github = loadOAuth2(
      name = "github",
      signInUrl = "https://github.com/login/oauth/authorize",
      accessTokenUrl = "https://github.com/login/oauth/access_token"
    )
    val linkedIn = loadOAuth2(
      name = "linkedin",
      signInUrl = "https://www.linkedin.com/oauth/v2/authorization",
      accessTokenUrl = "https://www.linkedin.com/oauth/v2/accessToken",
      scopes = Seq("profile")
    )

    val twitterKey = ConsumerKey(
      configuration.get[String]("twitter.authKey"),
      configuration.get[String]("twitter.authSecret")
    )

    val twitter = OAuth(ServiceInfo(
      "https://api.twitter.com/oauth/request_token",
      "https://api.twitter.com/oauth/access_token",
      "https://api.twitter.com/oauth/authorize", twitterKey),
      use10a = true
    )

    OAuthConfig(
      twitter = twitter,
      google = google,
      github = github,
      linkedIn = linkedIn,
      configuration.get[String]("google.apiKey"),
      configuration.get[String]("twitter.bearerToken")
    )
  }
}
