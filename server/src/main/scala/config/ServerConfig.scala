package config

import com.comcast.ip4s.*
import ciris.*
import ciris.circe.circeConfigDecoder
import io.circe.Decoder
import java.nio.file.Paths

final case class ServerConfig(port: Port, host: Host)

object ServerConfig:

  given serverDecoder: Decoder[ServerConfig] = Decoder.instance { h =>
    for
      port <- h.get[String]("port")
      host <- h.get[String]("host")
    yield ServerConfig(
      Port.fromString(port).getOrElse(port"8080"),
      Host.fromString(host).getOrElse(host"localhost")
    )
  }

  given serverConfigDecoder: ConfigDecoder[String, ServerConfig] = circeConfigDecoder("ServerConfig")

  val conf = file(Paths.get("src/main/resources/serverConfig.json")).as[ServerConfig]
