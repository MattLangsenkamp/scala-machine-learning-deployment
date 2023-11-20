import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Size
import org.opencv.core.CvType
import org.bytedeco.opencv.global.opencv_imgcodecs._
import org.bytedeco.opencv.global.opencv_imgproc.{cvtColor, resize, COLOR_BGR2RGB}
import org.bytedeco.javacpp.indexer.{UByteRawIndexer, FloatRawIndexer}

object OpenCVUtils {

  def mat2Seq(loadedMat: Mat): Vector[Float] =
    val intMat = new Mat()
    resize(loadedMat, intMat, new Size(224, 224))
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
