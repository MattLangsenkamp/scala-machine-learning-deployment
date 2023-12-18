package modules

import alg.{ImageClassificationInferenceAlg, GithubAlg}
import inference.grpc_service.ModelInferResponse
import org.http4s.client.Client
import config.Config
import domain.ImageClassification.{LabelMap, TritonBatch}

import cats.effect.{Sync, Concurrent, Async}
import cats.{Applicative, ApplicativeThrow}
import inference.grpc_service.GRPCInferenceServiceFs2Grpc
import io.grpc.Metadata
import cats.effect.kernel.Sync

object Algebras:

  def make[F[_]: Applicative: ApplicativeThrow: Async](
      config: Config,
      labelMap: LabelMap,
      httpClient: Client[F],
      grpcStub: GRPCInferenceServiceFs2Grpc[F, Metadata],
      security: Security[F]
  ): Algebras[F] =
    new Algebras[F](
      githubAlg = GithubAlg.make[F](config.oAuthConfig, httpClient),
      tritonImgClsAlg = ImageClassificationInferenceAlg.makeTriton[F](labelMap, grpcStub)
    ) {}

sealed abstract class Algebras[F[_]] private (
    val githubAlg: GithubAlg[F],
    val tritonImgClsAlg: ImageClassificationInferenceAlg[F, TritonBatch, ModelInferResponse]
) {}
