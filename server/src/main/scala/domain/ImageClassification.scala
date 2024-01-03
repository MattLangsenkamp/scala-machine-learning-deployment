package domain
import io.circe.syntax.*
import io.circe.*
import io.circe.generic.auto.*
import cats.kernel.Monoid
import os.{GlobSyntax, /, read, pwd}

import cats.*
import cats.syntax.*
import cats.implicits.*

object ImageClassification:

  type Filename    = String
  type Label       = String
  type Probability = Float

  type LabelProbabilities = Map[Label, Probability]

  type ClassificationOutput = Map[Filename, LabelProbabilities]

  type Index    = Int
  type LabelMap = Map[Index, Label]

  def loadLabelMap(path: String): LabelMap =
    io.circe.parser
      .decode[LabelMap](read(os.Path(path)))
      .getOrElse(throw new Exception("Could not parse label map"))

  type ImageUpload = (Filename, List[Byte])
  type TritonBatch = (Vector[Filename], Vector[Float])

  given tritonBatchMonoid: Monoid[TritonBatch] = new Monoid[TritonBatch]:
    def empty: TritonBatch = (Vector.empty[Filename], Vector.empty[Float])

    def combine(x: TritonBatch, y: TritonBatch): TritonBatch =
      (x._1 ++ y._1, x._2 ++ y._2)

  type Inferred[A] = (Vector[Filename], A)

  given classificationOutputMonoid: Monoid[ClassificationOutput] = new Monoid[ClassificationOutput]:

    def empty: ClassificationOutput = Map[Filename, LabelProbabilities]()

    def combine(x: ClassificationOutput, y: ClassificationOutput): ClassificationOutput =
      x |+| y
