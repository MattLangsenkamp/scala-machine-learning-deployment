package modules

import routes.InferenceRoutes
import org.http4s.server.middleware.CORS
import org.http4s.headers.Origin
import org.http4s.Uri
import routes.OAuthRoutes

import cats.{Monad, MonadThrow}
import cats.syntax.*
import cats.implicits.*
import cats.effect.{Concurrent, Async}

import com.mattlangsenkamp.core.OAuth.*
import dev.profunktor.auth.JwtAuthMiddleware
import org.http4s.CacheDirective.`private`
import org.http4s.AuthedRoutes
import org.http4s.HttpRoutes
import org.http4s.HttpApp
import org.http4s.server.middleware.RequestLogger
import org.http4s.server.middleware.ResponseLogger
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax.*

object Routes:

  def make[F[_]: Monad: MonadThrow: Concurrent: Async: Logger](
      algebras: Algebras[F],
      security: Security[F]
  ): Routes[F] = new Routes(algebras, security) {}

sealed abstract class Routes[F[_]: Monad: MonadThrow: Concurrent: Async: Logger] private (
    algebras: Algebras[F],
    security: Security[F]
):

  private val policy = CORS.policy
    .withAllowCredentials(true)
    .withAllowOriginHost(
      Set(
        Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(5173)),
        Origin.Host(Uri.Scheme.http, Uri.RegName("127.0.0.1"), Some(5173))
      )
    )

  private val jwtMiddleware =
    JwtAuthMiddleware[F, GenericUser](security.auth, security.authAlg.authenticateUser)

  private val inferRoutes = InferenceRoutes[F](algebras.tritonImgClsAlg).routes(jwtMiddleware)
  private val authRoutes  = OAuthRoutes[F](algebras.githubAlg, security.authAlg).routes

  private val corsRoutes = policy(inferRoutes <+> authRoutes)

  private val loggers: HttpApp[F] => HttpApp[F] = { (http: HttpApp[F]) =>
    RequestLogger.httpApp(true, true)(http)
  } andThen { (http: HttpApp[F]) =>
    ResponseLogger.httpApp(true, true)(http)
  }

  val httpApp: HttpApp[F] = corsRoutes.orNotFound // loggers( )
