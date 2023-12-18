package routes

import cats.{Monad, MonadThrow}
import cats.implicits.*

import cats.effect.std.Console
import cats.effect.syntax.*
import cats.effect.implicits.*
import cats.effect.{Concurrent, Async}

import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*

import fs2.Stream

import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.multipart.Multipart
import org.http4s.HttpRoutes
import org.http4s.headers.Cookie
import org.http4s.circe.*

import alg.ImageClassificationInferenceAlg

import domain.OAuth.GenericUser
import domain.ImageClassification.*

import inference.grpc_service.{
  GRPCInferenceServiceFs2Grpc,
  ModelInferRequest,
  ModelInferResponse,
  InferTensorContents
}
import org.http4s.server.AuthMiddleware

final case class InferenceRoutes[F[_]: MonadThrow: Concurrent: Monad: Async](
    imgCls: ImageClassificationInferenceAlg[F, TritonBatch, ModelInferResponse]
) extends Http4sDsl[F]:
  private[routes] val prefixPath = "/infer"

  private object ModelQueryParamMatcher extends QueryParamDecoderMatcher[String]("model")

  private object TopKQueryParamMatcher extends QueryParamDecoderMatcher[Int]("top_k")

  val authedRoutes: AuthedRoutes[GenericUser, F] = AuthedRoutes.of {
    case req @ POST -> Root / "infer"
        :? ModelQueryParamMatcher(model)
        :? TopKQueryParamMatcher(topK)
        as user =>
      imgCls.modelExist(model, "1").flatMap { i =>
        if i then NotFound("Model not found")
        else

          req.req.decode[Multipart[F]] { m =>
            Stream(m.parts*)
              .evalMap(imgCls.upload)
              .through(imgCls.preProcessPipe(16))
              .evalMap(v => imgCls.infer(v, 16, model))
              .evalMap(v => imgCls.postProcess(v, 16, 10))
              .compile
              .toList
              .flatMap(l => Ok(l.asJson))
          }
      }
  }

  def routes(authMiddleware: AuthMiddleware[F, GenericUser]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(authedRoutes)
  )
