package config

import ciris.*
import ciris.circe.circeConfigDecoder
import io.circe.Decoder
import java.nio.file.Paths
import cats.*
import cats.syntax.*
import cats.implicits.*

final case class OAuthConfig(key: String, secret: Secret[String])

object OAuthConfig:

  val conf: ConfigValue[Effect, OAuthConfig] = (
    env("KEY").as[String],
    env("SECRET").secret
  ).parMapN(OAuthConfig.apply)
