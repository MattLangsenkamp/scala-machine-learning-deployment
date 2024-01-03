package config

import cats.syntax.*
import cats.implicits.*
import com.comcast.ip4s.*
import ciris.*
import ciris.http4s.*
import ciris.circe.circeConfigDecoder
import io.circe.Decoder
import java.nio.file.Paths

final case class ServerConfig(port: Port, host: Host, labelsDir: String)

object ServerConfig:

  val conf: ConfigValue[Effect, ServerConfig] = (
    env("SERVER_PORT").as[Port].default(port"8080"),
    env("SERVER_HOST").as[Host].default(host"127.0.0.1"),
    env("LABELS_DIR").as[String]
  ).parMapN(ServerConfig.apply)
