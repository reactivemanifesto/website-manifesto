package models

import java.time.Instant

/**
 * A user that has logged in using OAuth.
 *
 * @param provider The service specific identifier for the user.
 * @param name The name of the user.
 * @param avatar The users avatar, if they have one.
 * @param signed When the user signed the manifesto, if they signed it.
 */
case class OAuthUser(provider: Provider, name: String, avatar: Option[String], signed: Option[Instant] = None)
