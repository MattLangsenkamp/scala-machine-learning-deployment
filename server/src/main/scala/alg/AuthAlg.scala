package alg
import org.http4s.client.Client
import cats.Applicative
import domain.OAuth.{given, *}
import io.circe.syntax.*
import io.circe.parser.*
import cats.implicits._
import dev.profunktor.auth.*
import dev.profunktor.auth.jwt.*
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import java.time.Instant
import cats.ApplicativeThrow

trait AuthAlg[F[_]]:

  def signJwt(userInfo: GenericUser): F[JwtToken]

  def authenticateUser(token: JwtToken)(claim: JwtClaim): F[Option[GenericUser]]

object AuthAlg:

  def make[F[_]: Applicative: ApplicativeThrow](
      client: Client[F],
      jwtAuth: JwtSymmetricAuth,
      jwtAlgorithm: JwtHmacAlgorithm
  ): AuthAlg[F] = new AuthAlg[F]:

    def signJwt(userInfo: GenericUser): F[JwtToken] =
      val c = JwtClaim(
        content = userInfo.asJson.toString,
        expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond)
      )

      jwtEncode[F](c, jwtAuth.secretKey, jwtAlgorithm)

    def authenticateUser(token: JwtToken)(claim: JwtClaim): F[Option[GenericUser]] =
      parse(claim.content).toOption.flatMap(_.as[GenericUser].toOption).pure[F]
