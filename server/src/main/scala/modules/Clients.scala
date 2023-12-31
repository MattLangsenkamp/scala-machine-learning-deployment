package modules

import com.mattlangsenkamp.core.OAuth.GenericUser

import cats.syntax.apply.*
import cats.implicits.*

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
    .forTarget(s"${config.tritonConfig.host}:${config.tritonConfig.port}")
    .usePlaintext()
    .resource[F]
    .flatMap(GRPCInferenceServiceFs2Grpc.stubResource[F])

  val httpClient: Resource[F, Client[F]] = EmberClientBuilder
    .default[F]
    .build

  // val clients = (httpClient, grpcStub).tupled
}
