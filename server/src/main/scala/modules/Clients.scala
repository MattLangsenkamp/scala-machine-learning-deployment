package modules

import domain.OAuth.GenericUser

import cats.effect.Async
import cats.effect.kernel.Resource

import config.Config

import io.grpc.*
import io.grpc.Metadata
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import inference.grpc_service.GRPCInferenceServiceFs2Grpc
import fs2.grpc.syntax.all.*

import org.http4s.client.Client
import org.http4s.server.AuthMiddleware
import org.http4s.ember.client.EmberClientBuilder
import dev.profunktor.auth.JwtAuthMiddleware

object Clients:

  def make[F[_]: Async](config: Config): Clients[F] = new Clients[F](config) {}

sealed abstract class Clients[F[_]: Async] private (config: Config) {
  val grpcStub: Resource[F, GRPCInferenceServiceFs2Grpc[F, Metadata]] = NettyChannelBuilder
    .forAddress("127.0.0.1", 8001)
    .usePlaintext()
    .resource[F]
    .flatMap(GRPCInferenceServiceFs2Grpc.stubResource[F])

  val httpClient: Resource[F, Client[F]] = EmberClientBuilder
    .default[F]
    .build

}
