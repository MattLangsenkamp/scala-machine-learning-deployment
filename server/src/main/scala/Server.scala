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
implicit val logging: LoggerFactory[IO] = Slf4jFactory.create[IO]

object Server extends IOApp.Simple:

  private val policy = CORS.policy
    .withAllowCredentials(true)
    .withAllowOriginHost(
      Set(
        Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(5173)),
        Origin.Host(Uri.Scheme.http, Uri.RegName("127.0.0.1"), Some(5173))
      )
    )

  private val algo              = JwtAlgorithm.HS256
  val jwtAuth: JwtSymmetricAuth = JwtAuth.hmac("53cr3t", JwtAlgorithm.HS256)

  def mkRoutes(client: Client[IO], config: Config) =
    val githubAlg = GithubAlg.make[IO](config.oAuthConfig, client)
    val authAlg   = AuthAlg.make[IO](client, jwtAuth, algo)
    policy(OAuthRoutes[IO](githubAlg, authAlg).routes)

  val client = EmberClientBuilder
    .default[IO]
    .build

  def server(serverConfig: ServerConfig, app: HttpApp[IO]) = EmberServerBuilder
    .default[IO]
    .withPort(serverConfig.port)
    .withHost(serverConfig.host)
    .withHttpApp(app)
    .build

  def run: IO[Unit] =
    val liveServer = for
      client <- EmberClientBuilder
        .default[IO]
        .build
      config <- Config.conf.load[IO].toResource
      realRoutes = mkRoutes(client, config)
      srv <- server(config.serverConfig, realRoutes.orNotFound)
    yield srv

    liveServer.evalMap(srv => IO.println(f"server running at ${srv.address}")).useForever
