package modules

import cats.*, cats.syntax.*, cats.implicits.*
import cats.effect.*, cats.effect.syntax.*, cats.effect.implicits.*

import org.typelevel.log4cats.Logger

import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.metrics.Meter

import io.opentelemetry.api.GlobalOpenTelemetry

import cats.*, cats.syntax.*, cats.implicits.*
import io.opentelemetry.sdk.OpenTelemetrySdk

object OpenTelemetry:

  private def otelResource[F[_]: Sync: Async: LiftIO: FlatMap]: Resource[F, Otel4s[F]] =
    Resource
      .eval(Sync[F].delay(GlobalOpenTelemetry.get))
      .evalMap(OtelJava.forAsync[F])

  def make[F[_]: Sync: Async: LiftIO] =
    otelResource.map(otel => new OpenTelemetry(otel) {})

sealed abstract class OpenTelemetry[F[_]: Async] private (otel: Otel4s[F]) {

  val tracer: F[Tracer[F]] = otel.tracerProvider.get("inference-service")
  val meter: F[Meter[F]]   = otel.meterProvider.get("inference-service")

  val instruments = Resource
    .eval((tracer, meter).tupled)

}
