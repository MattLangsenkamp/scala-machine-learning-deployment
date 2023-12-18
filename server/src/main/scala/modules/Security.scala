package modules

import config.Config
import dev.profunktor.auth.jwt.{JwtAuth, JwtToken}
import pdi.jwt.JwtAlgorithm
import alg.AuthAlg
import org.http4s.client.Client
import cats.{Applicative, ApplicativeThrow}

object Security:

  def make[F[_]: Applicative: ApplicativeThrow](config: Config, httpClient: Client[F]) =
    new Security[F](config, httpClient) {}

sealed abstract class Security[F[_]: Applicative: ApplicativeThrow] private (
    config: Config,
    httpClient: Client[F]
) {
  val auth = JwtAuth
    .hmac(
      config.oAuthConfig.secret.value,
      JwtAlgorithm.HS256
    )

  val alg = JwtAlgorithm.HS256

  val authAlg = AuthAlg.make[F](httpClient, auth, alg)
}
