package alg

import cats.Monoid
import cats.syntax.*
import cats.implicits.*
import cats.effect.syntax.*
import cats.effect.{Concurrent, Async}

import domain.ImageClassification.{given, *}

import org.http4s.multipart.Part
import fs2.Pipe

import io.grpc.Metadata
import inference.grpc_service.{ModelInferResponse, GRPCInferenceServiceFs2Grpc, ModelConfigRequest}
import cats.effect.kernel.Sync
import util.OpenCVUtils.*
import fs2.Chunk
import fs2.*
import inference.grpc_service.ModelInferRequest
import inference.grpc_service.InferTensorContents
import inference.grpc_service.ModelInferRequest.InferInputTensor
import java.nio.ByteOrder

trait ImageClassificationInferenceAlg[F[_], INPUT, OUTPUT]:

  def modelExist(model: String, version: String): F[Boolean]

  def upload(part: Part[F]): F[ImageUpload]

  def preProcess(in: ImageUpload): F[INPUT]

  def preProcessPipe(batchSize: Int): Pipe[F, ImageUpload, INPUT]

  def infer(preprocessed: INPUT, batchSize: Int, model: String): F[Inferred[OUTPUT]]

  def postProcess(inferred: Inferred[OUTPUT], batchSize: Int, topK: Int): F[ClassificationOutput]

object ImageClassificationInferenceAlg:

  def makeTriton[F[_]: Sync](
      labelMap: LabelMap,
      grpcStub: GRPCInferenceServiceFs2Grpc[F, Metadata]
  ): ImageClassificationInferenceAlg[F, TritonBatch, ModelInferResponse] =
    new ImageClassificationInferenceAlg:

      def modelExist(model: String, version: String): F[Boolean] =
        for modelConfig <- grpcStub.modelConfig(new ModelConfigRequest(model, version), new Metadata())
        yield modelConfig.config.isEmpty

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
          .modelInfer(makeModelInferRequest(preprocessed._2, batchSize, model), new Metadata())
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
            Map.from(probabilities.zipWithIndex.map((score, ind) => (labelMap(ind), score)))

          Map.from {
            inferred._1.zip(batches).map((fname, probabilities) => (fname, lookupLabels(probabilities)))
          }

        Sync[F].blocking {
          val decoded = decodeRaw(inferred._2)
          val batched = reshapeIntoBatches(decoded)
          createClassificationOutput(batched)
        }
