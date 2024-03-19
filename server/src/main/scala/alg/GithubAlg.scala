package alg

import com.mattlangsenkamp.core.OAuth.{*, given}
import com.mattlangsenkamp.server.domain.OAuth.given
import cats.Applicative
import org.http4s.client.Client
import org.http4s.ember.client.*
import config.OAuthConfig
import org.http4s.UrlForm
import org.http4s.Method
import org.http4s.implicits.uri
import org.http4s.Headers
import org.http4s.MediaType
import org.http4s.headers.Accept
import io.circe.generic.auto._
import org.http4s.Request
import org.http4s.EntityDecoder
import cats.effect.Concurrent
import org.http4s.Credentials
import org.http4s.headers.Authorization
import org.http4s.AuthScheme
import org.typelevel.otel4s.trace.Tracer

trait GithubAlg[F[_]]:

  def getAccessToken(code: String): F[AccessTokenResponse]

  def getUserInfo(accessTokenResp: AccessTokenResponse): F[GithubUserInfoResponse]

object GithubAlg:
  def make[F[_]: Applicative: Concurrent: Tracer](cfg: OAuthConfig, client: Client[F]): GithubAlg[F] =
    new GithubAlg[F]:

      given accessTokenResponseDecoder: EntityDecoder[F, AccessTokenResponse] = entityDecoder[F]

      def getAccessToken(code: String): F[AccessTokenResponse] =
        Tracer[F].span("getAccessToken").surround {
          val form = UrlForm(
            "client_id"     -> cfg.key,
            "client_secret" -> cfg.secret.value,
            "code"          -> code
          )

          val req = Request[F](
            Method.POST,
            uri"https://github.com/login/oauth/access_token",
            headers = Headers(Accept(MediaType.application.json))
          ).withEntity(form)

          client.expect[AccessTokenResponse](req)
        }

      def getUserInfo(accessTokenResp: AccessTokenResponse): F[GithubUserInfoResponse] =
        Tracer[F].span("getUserInfo").surround {
          val req = Request[F](
            Method.GET,
            uri"https://api.github.com/user/emails",
            headers = Headers(
              Accept(MediaType.application.json),
              Authorization(Credentials.Token(AuthScheme.Bearer, accessTokenResp.accessToken))
            )
          )

          client.expect[GithubUserInfoResponse](req)
        }
