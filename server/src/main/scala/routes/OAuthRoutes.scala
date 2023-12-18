package routes

import cats.Monad
import cats.implicits.*
import cats.effect.std.Console
import cats.effect.syntax.*
import cats.effect.implicits.*
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import alg.GithubAlg
import alg.AuthAlg
import org.http4s.headers.Cookie
import domain.OAuth.GenericUser

final case class OAuthRoutes[F[_]: Monad](
    githubAlg: GithubAlg[F],
    authAlg: AuthAlg[F]
) extends Http4sDsl[F]:
  private[routes] val prefixPath = "/auth"

  private object CodeQueryParamMatcher extends QueryParamDecoderMatcher[String]("code")

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "access_token" :? CodeQueryParamMatcher(code) =>
      val signedToken = for
        accessTokenResp <- githubAlg.getAccessToken(code)
        userInfo        <- githubAlg.getUserInfo(accessTokenResp)
        jwt             <- authAlg.signJwt(GenericUser(userInfo.head.email))
      yield jwt.value
      signedToken.flatMap { st =>
        Ok("successfully logged in").map(
          _.addCookie(
            ResponseCookie(
              "Authorization",
              st,
              httpOnly = false,
              sameSite = Some(SameSite.Strict),
              secure = false,
              path = Some("/")
            )
          )
        )
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
