package services

import play.api.libs.ws.WS
import scala.concurrent._
import java.net.URLEncoder
import scala.util.Random
import java.security.SecureRandom

/**
 * Settings for an OAUth2 provider
 *
 * @param clientId The OAuth2 client id
 * @param clientSecret The OAuth2 client secret
 * @param signInUrl The URL to redirect the user to sign in to.  Must not contain a query String.
 * @param accessTokenUrl The URL to use to look up an access token.
 * @param scopes The scopes we want to request for the user.
 */
case class OAuth2Settings(clientId: String,
                          clientSecret: String,
                          signInUrl: String,
                          accessTokenUrl: String,
                          scopes: Seq[String])

/**
 * Provides OAuth2 services
 */
object OAuth2 {

  private val rand = new Random(new SecureRandom())

  /**
   * Generate a cryptographically secure state value.
   */
  def generateState = new String(rand.alphanumeric.take(20).toArray)

  /**
   * Generate the sign in URL.
   *
   * @param settings The OAuth 2 settings.
   * @param redirectUri The URI for the provider to redirect back to.
   * @param state The state.
   * @param extraParams Any extra parameters required in the sign in url by the provider.
   */
  def signInUrl(settings: OAuth2Settings, redirectUri: String, state: String, extraParams: (String, String)*) = {
    val params = Seq(
      "client_id" -> settings.clientId,
      "redirect_uri" -> redirectUri,
      "scope" -> settings.scopes.mkString(" "),
      "state" -> state
    ) ++ extraParams
    settings.signInUrl + "?" + params.map(keyValue => keyValue._1 + "=" + URLEncoder.encode(keyValue._2, "UTF-8")).mkString("&")
  }

  /**
   * Request an access token from the OAuth 2 provider.
   *
   * @param settings The OAuth 2 settings.
   * @param redirectUri The URI the provider had to have redirected back to.
   * @param code The code supplied by the provider.
   * @param executionContext An execution context to handle the response from the provider.
   * @return A future of the access token.
   */
  def requestAccessToken(settings: OAuth2Settings, redirectUri: String, code: String)(implicit executionContext: ExecutionContext): Future[String] = {

    val body = Map(
      "code" -> code,
      "client_id" -> settings.clientId,
      "client_secret" -> settings.clientSecret,
      "redirect_uri" -> redirectUri,
      "grant_type" -> "authorization_code"
    ).mapValues(v => Seq(v))

    WS.url(settings.accessTokenUrl).withHeaders("Accept" -> "application/json").post(body).map { response =>
      if (response.status < 300) {
        (response.json \ "access_token").as[String]
      } else {
        throw new IllegalArgumentException("Bad response: " + response.status + " " + response.body)
      }
    }
  }

}
