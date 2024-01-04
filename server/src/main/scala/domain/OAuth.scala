package com.mattlangsenkamp.server.domain

import io.circe.*
import io.circe.syntax.*
import io.circe.*
import io.circe.generic.semiauto.*
import cats.effect.Concurrent
import org.http4s.*
import org.http4s.circe.*
import org.http4s.EntityDecoder
import com.mattlangsenkamp.core.OAuth.*

object OAuth:

  given entityDecoder[F[_]: Concurrent]: EntityDecoder[F, AccessTokenResponse] =
    jsonOf[F, AccessTokenResponse]

  given githubUserInfoEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GithubUserInfo] =
    jsonOf[F, GithubUserInfo]

  given GithubUserInfoResponseEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GithubUserInfoResponse] =
    jsonOf[F, GithubUserInfoResponse]

  given genericUserEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, GenericUser] =
    jsonOf[F, GenericUser]
