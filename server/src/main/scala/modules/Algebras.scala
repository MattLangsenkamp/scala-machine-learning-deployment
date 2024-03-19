package modules

import alg.{ImageClassificationInferenceAlg, GithubAlg, ImageProcessingAlg}
import inference.grpc_service.ModelInferResponse
import org.http4s.client.Client
import config.Config
import com.mattlangsenkamp.core.ImageClassification.{LabelMap, TritonBatch}

import cats.effect.{Sync, Concurrent, Async}
import cats.*, cats.syntax.*, cats.implicits.*
import inference.grpc_service.GRPCInferenceServiceFs2Grpc
import io.grpc.Metadata
import cats.effect.kernel.{Sync, Ref}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import cats.Parallel
import com.mattlangsenkamp.core.ImageClassification.ModelInfo
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer

object Algebras:

  def make[F[_]: Parallel: Applicative: ApplicativeThrow: Async: Logger: Meter: Tracer](
      config: Config,
      labelMap: LabelMap,
      httpClient: Client[F],
      grpcStub: GRPCInferenceServiceFs2Grpc[F, Metadata],
      security: Security[F],
      modelCacheR: Ref[F, Map[(String, String), ModelInfo]]
  ): F[Algebras[F]] =
    val imgProc = ImageProcessingAlg.makeOpenCV[F]
    ImageClassificationInferenceAlg
      .makeTriton[F](labelMap, grpcStub, modelCacheR, imgProc)
      .map { imgAlg =>
        new Algebras[F](
          githubAlg = GithubAlg.make[F](config.oAuthConfig, httpClient),
          tritonImgClsAlg = imgAlg
        ) {}
      }

sealed abstract class Algebras[F[_]] private (
    val githubAlg: GithubAlg[F],
    val tritonImgClsAlg: ImageClassificationInferenceAlg[F, TritonBatch, ModelInferResponse]
) {}
