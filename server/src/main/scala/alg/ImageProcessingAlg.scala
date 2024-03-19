package alg

import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.opencv.core.CvType
import org.bytedeco.opencv.global.opencv_imgcodecs._
import org.bytedeco.opencv.global.opencv_imgproc.{cvtColor, resize, COLOR_BGR2RGB}
import org.bytedeco.javacpp.indexer.{UByteRawIndexer, FloatRawIndexer}
import org.bytedeco.javacpp.BytePointer
import java.nio.ByteBuffer

import org.typelevel.otel4s.trace.Tracer
import cats.effect.*, cats.effect.syntax.*, cats.effect.implicits.*

trait ImageProcessingAlg[F[_]]:

  type Rep

  def readFromBytes(bytes: List[Byte]): F[Rep]

  def rep2Vec(rep: Rep): F[Vector[Float]]

object ImageProcessingAlg:
  def makeOpenCV[F[_]: Sync: Tracer]: ImageProcessingAlg[F] = new ImageProcessingAlg[F]:
    type Rep = Mat
    def readFromBytes(bytes: List[Byte]): F[Mat] =
      Tracer[F].span("readFromBytes").surround {
        Sync[F].blocking {
          val bb = ByteBuffer.wrap(bytes.toArray)
          imdecode(
            new Mat(new BytePointer(bb)),
            IMREAD_ANYDEPTH | IMREAD_ANYCOLOR
          )
        }
      }

    def rep2Vec(rep: Mat): F[Vector[Float]] =
      Tracer[F].span("rep2Vec").surround {
        Sync[F].blocking {
          val intMat = new Mat()
          resize(rep, intMat, new Size(224, 224))
          val floatMat = new Mat()
          intMat.convertTo(floatMat, CvType.CV_32FC3)
          val mat = new Mat()
          cvtColor(floatMat, mat, COLOR_BGR2RGB)
          val rows             = mat.rows
          val cols             = mat.cols
          val channels         = mat.channels()
          val pixelsPerChannel = rows * cols

          val resultArray = new Array[Float](rows * cols * channels)
          val indexer     = mat.createIndexer[FloatRawIndexer]()
          val data        = new Array[Float](channels)
          for (r <- 0 until rows)
            for (c <- 0 until cols)
              indexer.get(r, c, data)
              val channelPixel = rows * c + r
              val rPixelIndex  = channelPixel
              val gPixelIndex  = channelPixel + pixelsPerChannel
              val bPixelIndex  = channelPixel + 2 * pixelsPerChannel
              resultArray(rPixelIndex) = data(0) / 255
              resultArray(gPixelIndex) = data(1) / 255
              resultArray(bPixelIndex) = data(2) / 255
          resultArray.toVector
        }
      }
