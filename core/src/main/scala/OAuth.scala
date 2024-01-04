package com.mattlangsenkamp.core

import io.circe.*
import io.circe.syntax.*
import io.circe.*
import io.circe.generic.semiauto.*

object OAuth:

  final case class AccessTokenResponse(accessToken: String, tokenType: String, scope: String)
  final case class GithubUserInfo(email: String, primary: Boolean, verified: Boolean, visibility: String)
  final case class GenericUser(email: String)

  type GithubUserInfoResponse = List[GithubUserInfo]

  given accessTokenResponseDecoder: Decoder[AccessTokenResponse] =
    Decoder.forProduct3("access_token", "token_type", "scope")(AccessTokenResponse.apply)

  given githubUserInfoDecoder: Codec[GithubUserInfo] = deriveCodec[GithubUserInfo]

  given genericUserCodec: Codec[GenericUser] = deriveCodec[GenericUser]
