package com.mattlangsenkamp.core

import io.circe.syntax.*
import io.circe.*
import io.circe.generic.semiauto.*
import cats.kernel.Monoid

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

  case class ModelInfo(modelName: String, batchSize: Int)

  case class ModelInfos(modelInfos: List[ModelInfo])

  given modelInfoCodec: Codec[ModelInfo] = deriveCodec[ModelInfo]

  given modelInfosCodec: Codec[ModelInfos] = deriveCodec[ModelInfos]
