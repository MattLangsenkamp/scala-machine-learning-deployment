package com.mattlangsenkamp.server.domain

import org.http4s.*
import org.http4s.circe.*
import com.mattlangsenkamp.core.ImageClassification.ModelInfos
import cats.effect.Concurrent

object ImageClassification:

  given modelInfosEncoder[F[_]: Concurrent]: EntityEncoder[F, ModelInfos] = jsonEncoderOf[F, ModelInfos]
