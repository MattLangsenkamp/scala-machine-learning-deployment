package config

import ciris.*
import ciris.circe.circeConfigDecoder
import io.circe.Decoder
import java.nio.file.Paths

final case class OAuthConfig(key: String, secret: Secret[String])

object OAuthConfig:

  given oAuthDecoder: Decoder[OAuthConfig] = Decoder.instance { h =>
    for
      key    <- h.get[String]("key")
      secret <- h.get[String]("secret")
    yield OAuthConfig(key, Secret(secret))
  }

  given oAuthConfigDecoder: ConfigDecoder[String, OAuthConfig] = circeConfigDecoder("OAuthConfig")

  val conf = file(Paths.get("src/main/resources/oAuthConfig.json")).as[OAuthConfig]
