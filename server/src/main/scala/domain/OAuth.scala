package domain

import io.circe.*
import io.circe.syntax.*
import io.circe.*
import io.circe.generic.semiauto.*
import cats.effect.Concurrent
import org.http4s.*
import org.http4s.circe.*
import org.http4s.EntityDecoder

object OAuth:

  final case class AccessTokenResponse(accessToken: String, tokenType: String, scope: String)
  final case class GithubUserInfo(email: String, primary: Boolean, verified: Boolean, visibility: String)
  final case class GenericUser(email: String)

  type GithubUserInfoResponse = List[GithubUserInfo]

  given accessTokenResponseDecoder: Decoder[AccessTokenResponse] =
    Decoder.forProduct3("access_token", "token_type", "scope")(AccessTokenResponse.apply)

  given entityDecoder[F[_]: Concurrent]: EntityDecoder[F, AccessTokenResponse] =
    jsonOf[F, AccessTokenResponse]

  given githubUserInfoDecoder: Decoder[GithubUserInfo] = deriveDecoder[GithubUserInfo]

  given githubUserInfoEncoder: Encoder[GithubUserInfo] = deriveEncoder[GithubUserInfo]

  given githubUserInfoEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GithubUserInfo] =
    jsonOf[F, GithubUserInfo]

  given GithubUserInfoResponseEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GithubUserInfoResponse] =
    jsonOf[F, GithubUserInfoResponse]

  given genericUserCodec: Codec[GenericUser] = deriveCodec[GenericUser]

  given genericUserEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GenericUser] =
    jsonOf[F, GenericUser]
