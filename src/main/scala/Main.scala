import cats.implicits.*
import cats.effect.*
import cats.effect.IO.*
import cats.effect.implicits.*
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.*
import fs2.grpc.syntax.all.*
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}
import inference.grpc_service.{
  GRPCInferenceServiceFs2Grpc,
  ModelInferRequest,
  ModelInferResponse,
  InferTensorContents
}
import inference.grpc_service.ModelInferRequest.InferInputTensor
import os.{GlobSyntax, /, read, pwd}
import org.bytedeco.opencv.global.opencv_imgcodecs._
import org.bytedeco.opencv.global.opencv_imgproc.resize
import org.bytedeco.opencv.opencv_core.{Mat, Size}
import org.opencv.core.CvType
import OpenCVUtils.mat2Seq

import java.nio.ByteOrder

object GrpcClient extends IOApp.Simple:

  val grpcStub: Resource[IO, GRPCInferenceServiceFs2Grpc[IO, Metadata]] =
    NettyChannelBuilder
      .forAddress("127.0.0.1", 8001)
      .usePlaintext()
      .resource[IO]
      .flatMap(GRPCInferenceServiceFs2Grpc.stubResource[IO])

  type LabelMap = Map[Int, String]

  val labelMap: LabelMap = io.circe.parser
    .decode[Map[String, String]](read(pwd / "labels.json"))
    .getOrElse(
      // if we dont have labels nothing else matters, better to fail fast
      throw new Exception("Could not parse label map")
    )
    .map((k, v) => (k.toInt, v))

  def getImageStream(batchSize: Int) = Files[IO]
    .readAll(Path((pwd / "images.txt").toString))
    .through(text.utf8.decode)
    .through(text.lines)
    .map(l =>
      val a = l.split(' ')
      (a(0), a(1))
    )
    .evalMap((path, label) =>
      IO.blocking {
        val img = imread((pwd / os.RelPath[String](path)).toString)
        (mat2Seq(img), label)
      }
    )
    .chunkN(batchSize, allowFewer = true)
    .map(c =>
      c.foldLeft(Vector.empty[Float], Vector.empty[String]) { case ((lf, ls), (lf2, s)) =>
        (lf ++ lf2, ls :+ s)
      }
    )
    .map((seq, labels) => (makeModelInferRequest(seq, batchSize), labels))

  def makeModelInferRequest(floatVec: Vector[Float], batchSize: Int): ModelInferRequest =
    val ic = InferTensorContents(fp32Contents = floatVec)
    val it = InferInputTensor("images", "FP32", Seq(batchSize, 3, 224, 224), contents = Some(ic))
    ModelInferRequest(s"yolov8_$batchSize", "1", inputs = Seq(it))

  def decodeModelInferResponse(
      response: ModelInferResponse,
      batchSize: Int,
      topK: Int,
      labelMap: LabelMap
  ) =
    IO.blocking {
      val arr = new Array[Float](batchSize * 1000)
      response.rawOutputContents.head
        .asReadOnlyByteBuffer()
        .order(ByteOrder.LITTLE_ENDIAN)
        .asFloatBuffer()
        .get(arr)

      val arr2 = Vector.unfold(arr.toList) { k =>
        if (k.length > 0) Some(k.splitAt(1000))
        else None
      }

      arr2.map(
        _.zipWithIndex
          .sortBy(_._1)(Ordering.Float.IeeeOrdering.reverse)
          .slice(0, topK)
          .map((score, ind) => (score, labelMap(ind)))
          .toList
      )
    }

  def createBatchInferenceString(predList: Vector[List[(Float, String)]], labels: Vector[String]) =
    predList
      .zip(labels)
      .foldLeft("") { case (s, (preds, label)) =>
        s + s"label: ${label}\n" + preds
          .map((score, predLabel) => f"$predLabel: $score%2.2f")
          .mkString("\t", ", ", "\n")
      }

  def run =
    val batchSize = 16 // 1 or 16
    grpcStub.use(s =>
      getImageStream(batchSize)
        .evalMap((mir, labels) => s.modelInfer(mir, new Metadata()).map((_, labels)))
        .evalMap((infResp, labels) =>
          decodeModelInferResponse(infResp, batchSize, 10, labelMap).map((_, labels))
        )
        .evalTap((predList, labels) => IO.println(createBatchInferenceString(predList, labels)))
        .compile
        .drain
    )
