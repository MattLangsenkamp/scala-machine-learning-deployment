package domain
import io.circe.syntax.*
import io.circe.*
import io.circe.generic.auto.*
import cats.kernel.Monoid

object ImageClassification:

  type Filename    = String
  type Label       = String
  type Probability = Float

  type LabelProbabilities = Map[Label, Probability]

  type ClassificationOutput = Map[Filename, LabelProbabilities]

  type Index    = Int
  type LabelMap = Map[Index, Label]

  type ImageUpload = (Filename, List[Byte])
  type TritonBatch = (Vector[Filename], Vector[Float])

  given tritonBatchMonoid: Monoid[TritonBatch] = new Monoid[TritonBatch]:
    def empty: TritonBatch = (Vector.empty[Filename], Vector.empty[Float])

    def combine(x: TritonBatch, y: TritonBatch): TritonBatch =
      (x._1 ++ y._1, x._2 ++ y._2)

  type Inferred[A] = (Vector[Filename], A)
