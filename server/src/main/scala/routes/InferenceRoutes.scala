package routes

import cats.{Monad, MonadThrow}
import cats.*
import cats.syntax.*
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

import com.mattlangsenkamp.core.OAuth.GenericUser
import com.mattlangsenkamp.core.ImageClassification.{given, *}
import com.mattlangsenkamp.server.domain.ImageClassification.given

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

  private object BatchSizeQueryParamMatcher extends QueryParamDecoderMatcher[Int]("batch_size")

  val authedRoutes: AuthedRoutes[GenericUser, F] = AuthedRoutes.of {

    case GET -> Root / "model_info" as user => imgCls.getModelInfos.flatMap(p => Ok(p.asJson))

    case req @ POST -> Root / "infer"
        :? ModelQueryParamMatcher(model)
        :? TopKQueryParamMatcher(topK)
        :? BatchSizeQueryParamMatcher(batchSize)
        as user =>
      imgCls.modelExist(model, "1").flatMap { modelFound =>
        if !modelFound then NotFound("Model not found")
        else
          req.req.decode[Multipart[F]] { m =>
            Stream(m.parts*)
              .evalMap(imgCls.upload)
              .through(imgCls.preProcessPipe(batchSize))
              .evalMap(v => imgCls.infer(v, batchSize, model))
              .evalMap(v => imgCls.postProcess(v, batchSize, topK))
              .compile
              .toList
              .flatMap(l => Ok(l.reduce(_ |+| _).asJson))
          }
      }
  }

  def routes(authMiddleware: AuthMiddleware[F, GenericUser]): HttpRoutes[F] = Router(
    prefixPath -> authMiddleware(authedRoutes)
  )
