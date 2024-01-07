package alg

import cats.Monoid
import cats.syntax.*
import cats.implicits.*
import cats.effect.syntax.*
import cats.effect.{Concurrent, Async}
import cats.effect.kernel.{Ref, Sync}

import com.mattlangsenkamp.core.ImageClassification.{given, *}
import util.OpenCVUtils.*

import io.grpc.Metadata
import inference.grpc_service.{
  ModelInferResponse,
  GRPCInferenceServiceFs2Grpc,
  ModelConfigRequest,
  ModelInferRequest
}
import inference.grpc_service.InferTensorContents
import inference.grpc_service.ModelInferRequest.InferInputTensor
import java.nio.ByteOrder

import org.http4s.multipart.Part
import fs2.*
import fs2.Pipe
import fs2.Chunk

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import scala.util.Try
import inference.grpc_service.ModelConfigResponse
import inference.grpc_service.ServerLiveRequest
import inference.grpc_service.ServerLiveResponse
import inference.grpc_service.RepositoryIndexRequest
import cats.Parallel

trait ImageClassificationInferenceAlg[F[_], INPUT, OUTPUT]:

  def getModelInfos: F[List[ModelInfo]]

  def modelExist(model: String, version: String): F[Boolean]

  def upload(part: Part[F]): F[ImageUpload]

  def preProcess(in: ImageUpload): F[INPUT]

  def preProcessPipe(batchSize: Int): Pipe[F, ImageUpload, INPUT]

  def infer(preprocessed: INPUT, batchSize: Int, model: String): F[Inferred[OUTPUT]]

  def postProcess(inferred: Inferred[OUTPUT], batchSize: Int, topK: Int): F[ClassificationOutput]

object ImageClassificationInferenceAlg:

  def makeTriton[F[_]: Parallel: Sync: Logger](
      labelMap: LabelMap,
      grpcStub: GRPCInferenceServiceFs2Grpc[F, Metadata],
      modelCacheR: Ref[F, Map[(String, String), ModelInfo]]
  ): ImageClassificationInferenceAlg[F, TritonBatch, ModelInferResponse] =
    new ImageClassificationInferenceAlg:

      def getModelInfos: F[List[ModelInfo]] =
        for
          models <- grpcStub.repositoryIndex(RepositoryIndexRequest(), Metadata())
          modelConfigRequests = models.models.map(model =>
            grpcStub.modelConfig(ModelConfigRequest(model.name, "1"), Metadata())
          )
          configs <- modelConfigRequests.parSequence
        // add models to the local cache
        yield configs
          .flatMap(conf =>
            conf.config.map(value => ModelInfo(value.name, value.input.head.dims.head.toInt))
          )
          .toList

      def modelExist(model: String, version: String): F[Boolean] =
        for
          modelExistLocal <- modelCacheR.get.map(_.contains((model, version)))
          modelExists <-
            if modelExistLocal then modelExistLocal.pure[F]
            else
              for modelExistTriton <-
                  grpcStub
                    .modelConfig(
                      new ModelConfigRequest(model, version),
                      new Metadata()
                    )
                    .flatTap(modelConfig =>
                      modelConfig.config
                        .map(conf =>
                          modelCacheR.update(
                            _ + ((model, version) -> ModelInfo(
                              conf.name,
                              conf.input.head.dims.head.toInt
                            ))
                          )
                        )
                        .sequence
                    )
                    .map(modelConfig => modelConfig.config.isDefined)
                    .handleErrorWith(e =>
                      error"${e.getStackTrace().map(_.toString()).mkString("\n")}" *>
                        warn"Could not find model $model with version $version" *>
                        false.pure[F]
                    )
              yield modelExistTriton
        yield modelExists

      def upload(part: Part[F]): F[ImageUpload] =
        (part.filename.get, part.body.compile.toList).sequence

      def preProcess(in: ImageUpload): F[TritonBatch] =
        Sync[F].blocking {
          val mat = readMatFromBytes(in._2)
          (Vector(in._1), mat2Seq(mat))
        }

      def preProcessPipe(batchSize: Int): Pipe[F, ImageUpload, TritonBatch] =
        in =>
          in.chunkN(batchSize, allowFewer = true)
            .evalMap(c =>
              // image processing can be an expensive task, need to make sure it isn't blocking IO stuff
              Sync[F].blocking {
                c.map { imgUp =>
                  val mat = readMatFromBytes(imgUp._2)
                  (Vector(imgUp._1), mat2Seq(mat))
                }.fold
              }
            )

      def infer(
          preprocessed: TritonBatch,
          batchSize: Int,
          model: String
      ): F[Inferred[ModelInferResponse]] =
        def makeModelInferRequest(
            floatVec: Vector[Float],
            batchSize: Int,
            model: String
        ): ModelInferRequest =
          val ic = InferTensorContents(fp32Contents = floatVec)
          val it = InferInputTensor("images", "FP32", Seq(batchSize, 3, 224, 224), contents = Some(ic))
          ModelInferRequest(model, "1", inputs = Seq(it))
        grpcStub
          .modelInfer(
            makeModelInferRequest(preprocessed._2, batchSize, model),
            new Metadata()
          )
          .recoverWith { case e: Exception =>
            warn"$e" *> ModelInferResponse().pure[F]
          }
          .map((preprocessed._1, _))

      def postProcess(
          inferred: Inferred[ModelInferResponse],
          batchSize: Int,
          topK: Int
      ): F[ClassificationOutput] =

        def decodeRaw(response: ModelInferResponse): Array[Float] =
          val rawData = new Array[Float](batchSize * 1000)
          response.rawOutputContents.head
            .asReadOnlyByteBuffer()
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(rawData)
          rawData

        def reshapeIntoBatches(rawData: Array[Float]): Vector[Vector[Float]] =
          Vector.unfold(rawData.toVector) { k =>
            if (k.length > 0) Some(k.splitAt(1000))
            else None
          }

        def createClassificationOutput(batches: Vector[Vector[Float]]): ClassificationOutput =
          def lookupLabels(probabilities: Vector[Float]): LabelProbabilities =
            val labelScorePairs = probabilities.zipWithIndex.map((score, ind) => (labelMap(ind), score))
            val sortedLabelScorePairs = labelScorePairs.sortBy(_._2)(Ordering.Float.IeeeOrdering.reverse)
            Map.from(sortedLabelScorePairs.slice(0, topK))

          Map.from {
            inferred._1.zip(batches).map((fname, probabilities) => (fname, lookupLabels(probabilities)))
          }

        Sync[F].blocking {
          val decoded = decodeRaw(inferred._2)
          val batched = reshapeIntoBatches(decoded)
          createClassificationOutput(batched)
        }
