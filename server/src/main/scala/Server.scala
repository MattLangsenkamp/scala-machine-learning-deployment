package com.mattlangsenkamp.server

import cats.effect.*
import cats.effect.implicits.*
import cats.implicits.*
import org.http4s.*
import org.http4s.server.*
import org.http4s.implicits.*
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s.*
import config.Config
import routes.OAuthRoutes

import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.*
import config.ServerConfig
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.CORS
import org.http4s.headers.Origin
import alg.AuthAlg
import alg.GithubAlg
import org.http4s.client.Client
import pdi.jwt.JwtAlgorithm
import dev.profunktor.auth.jwt.JwtAuth
import dev.profunktor.auth.jwt.JwtSymmetricAuth
import modules.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import os.{GlobSyntax, /, read, pwd}
import com.mattlangsenkamp.core.ImageClassification.*
import org.typelevel.otel4s.context.syntax.toContextSyntax
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.metrics.Meter

object Server extends IOApp.Simple:

  def loadLabelMap(path: String): LabelMap =
    io.circe.parser
      .decode[LabelMap](read(os.Path(path)))
      .getOrElse(throw new Exception("Could not parse label map"))

  given logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def server(serverConfig: ServerConfig, app: HttpApp[IO]) = EmberServerBuilder
    .default[IO]
    .withPort(serverConfig.port)
    .withHost(serverConfig.host)
    .withHttpApp(app)
    .build

  def run: IO[Unit] =
    val liveServer = for
      config      <- Config.conf.load[IO].toResource
      labelMap    <- IO.blocking(loadLabelMap(config.serverConfig.labelsDir)).toResource
      modelCacheR <- Ref[IO].of(Map[(String, String), ModelInfo]()).toResource
      clients = Clients.make[IO](config)
      httpClient  <- clients.httpClient
      grpcStub    <- clients.grpcStub
      otel        <- OpenTelemetry.make[IO]
      instruments <- otel.instruments
      given Tracer[IO] = instruments._1
      given Meter[IO]  = instruments._2
      security         = Security.make[IO](config, httpClient)
      algebras <- Algebras.make(config, labelMap, httpClient, grpcStub, security, modelCacheR).toResource
      routes = Routes.make[IO](algebras, security)
      srv <- server(config.serverConfig, routes.httpApp)
    yield srv

    liveServer.evalMap(srv => IO.println(f"server running at ${srv.address}")).useForever
