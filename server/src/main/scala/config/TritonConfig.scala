package config

import cats.syntax.*
import cats.implicits.*
import com.comcast.ip4s.*
import ciris.*
import ciris.http4s.*
import ciris.circe.circeConfigDecoder
import io.circe.Decoder
import java.nio.file.Paths

final case class TritonConfig(port: String, host: String)

object TritonConfig:

  val conf: ConfigValue[Effect, TritonConfig] = (
    env("TRITON_PORT").as[String].default("8001"),
    env("TRITON_HOST").as[String].default("127.0.0.1")
  ).parMapN(TritonConfig.apply)
