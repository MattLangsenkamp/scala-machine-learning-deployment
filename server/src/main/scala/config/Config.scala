package config

import ciris.{ConfigValue, Effect}
import cats.syntax.all.*
import inference.grpc_service.GRPCInferenceServiceGrpc.GRPCInferenceServiceStub

final case class Config(serverConfig: ServerConfig, oAuthConfig: OAuthConfig)

object Config:

  val conf: ConfigValue[Effect, Config] =
    (
      ServerConfig.conf,
      OAuthConfig.conf
    ).parMapN(Config.apply)
