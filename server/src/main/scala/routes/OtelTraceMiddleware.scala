package routes

import cats.*, cats.syntax.*, cats.implicits.*
import cats.effect.*, cats.effect.syntax.*, cats.effect.implicits.*
import cats.data.Kleisli
import org.http4s.{Request, HttpApp, Header}

import org.typelevel.otel4s.trace.{Tracer, SpanKind, Status}
import org.typelevel.otel4s.Attribute
import org.typelevel.ci.CIString

object OtelTraceMiddleware:

  def apply[F[_]: Sync: Async: Tracer](app: HttpApp[F]) = Kleisli((req: Request[F]) =>
    val endpoint = req.uri.path.segments.slice(0, 2).map(s => s.toString).mkString("/")
    Tracer[F]
      .spanBuilder(s"Request:${req.method.name} $endpoint")
      .addAttribute(Attribute("http.method", req.method.name))
      .addAttribute(Attribute("http.url", req.uri.renderString))
      .withSpanKind(SpanKind.Server)
      .build
      .use { span =>
        for {
          response <- app(req)
          _        <- span.addAttribute(Attribute("http.status-code", response.status.code.toLong))
          _ <- {
            if (response.status.isSuccess) span.setStatus(Status.Ok) else span.setStatus(Status.Error)
          }
        } yield {
          val traceIdHeader = Header.Raw(CIString("traceId"), span.context.traceIdHex)
          response.putHeaders(traceIdHeader)
        }
      }
  )
