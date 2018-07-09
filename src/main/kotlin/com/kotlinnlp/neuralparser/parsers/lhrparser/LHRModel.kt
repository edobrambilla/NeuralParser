/* Copyright 2018-present LHRParser Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * -----------------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.parsers.lhrparser

import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.contextencoder.ContextEncoderModel
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.headsencoder.HeadsEncoderModel
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.labeler.DeprelLabelerModel
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.labeler.utils.LossCriterionType
import com.kotlinnlp.simplednn.core.arrays.UpdatableDenseArray
import com.kotlinnlp.simplednn.core.functionalities.initializers.GlorotInitializer
import com.kotlinnlp.simplednn.core.embeddings.Embedding
import com.kotlinnlp.simplednn.simplemath.ndarray.Shape
import com.kotlinnlp.neuralparser.NeuralParserModel
import com.kotlinnlp.neuralparser.language.CorpusDictionary
import com.kotlinnlp.simplednn.deeplearning.birnn.BiRNNConfig
import com.kotlinnlp.tokensencoder.TokensEncoderModel
import com.kotlinnlp.utils.Serializer
import java.io.InputStream

/**
 * The model of the [LHRParser].
 *
 * @property langCode the language code
 * @property corpusDictionary a corpus dictionary
 * @property tokensEncoderModel the model of the TokensEncoder
 * @property contextBiRNNConfig the configuration of the ContextEncoder BiRNN (if null the ContextEncoder is not used)
 * @property headsBiRNNConfig the configuration of the HeadsEncoder BiRNN
 * @property useLabeler whether to use the labeler
 * @property lossCriterionType the training mode of the labeler
 * @property predictPosTags whether to predict the POS tags together with the Deprels
 */
class LHRModel(
  val langCode: String,
  val corpusDictionary: CorpusDictionary,
  val tokensEncoderModel: TokensEncoderModel,
  val contextBiRNNConfig: BiRNNConfig,
  val headsBiRNNConfig: BiRNNConfig,
  val useLabeler: Boolean,
  val lossCriterionType: LossCriterionType,
  val predictPosTags: Boolean
) : NeuralParserModel() {

  companion object {

    /**
     * Private val used to serialize the class (needed by Serializable).
     */
    @Suppress("unused")
    private const val serialVersionUID: Long = 1L

    /**
     * Read a [LHRModel] (serialized) from an input stream and decode it.
     *
     * @param inputStream the [InputStream] from which to read the serialized [LHRModel]
     *
     * @return the [LHRModel] read from [inputStream] and decoded
     */
    fun load(inputStream: InputStream): LHRModel = Serializer.deserialize(inputStream)
  }

  /**
   * The model of the ContextEncoder.
   */
  val contextEncoderModel = ContextEncoderModel(
    tokenEncodingSize = this.tokensEncoderModel.tokenEncodingSize,
    connectionType = this.contextBiRNNConfig.connectionType,
    hiddenActivation = this.contextBiRNNConfig.hiddenActivation,
    numberOfLayers = this.contextBiRNNConfig.numberOfLayers,
    dropout = 0.0,
    biasesInitializer = null)

  /**
   * The size of the context vectors.
   */
  private val contextVectorsSize: Int = this.contextEncoderModel.contextEncodingSize

  /**
   * The model of the HeadsEncoder.
   */
  val headsEncoderModel = HeadsEncoderModel(
    tokenEncodingSize = this.contextVectorsSize,
    connectionType = this.headsBiRNNConfig.connectionType,
    hiddenActivation = this.headsBiRNNConfig.hiddenActivation,
    recurrentDropout = 0.0)

  /**
   * The embeddings vector that represents the root token of a sentence.
   */
  val rootEmbedding = Embedding(id = 0, array = UpdatableDenseArray(Shape(this.contextVectorsSize)))

  /**
   * The model of the Labeler.
   */
  val labelerModel: DeprelLabelerModel? = if (this.useLabeler)
    DeprelLabelerModel(
      contextEncodingSize = this.contextVectorsSize,
      deprels = this.corpusDictionary.deprelTags,
      lossCriterionType = this.lossCriterionType)
  else
    null

  /**
   * Initialize the root embedding.
   */
  init {
    GlorotInitializer().initialize(this.rootEmbedding.array.values)
  }

  /**
   * @return the string representation of this model
   */
  override fun toString(): String = """
    %-33s : %s
    %-33s : %s
    %-33s : %s
    %-33s : %s
    %-33s : %s
  """.trimIndent().format(
    this.tokensEncoderModel::class.simpleName, this.tokensEncoderModel,
    "Context Encoder", this.contextBiRNNConfig,
    "Heads Encoder", this.headsBiRNNConfig,
    "Labeler training mode", this.lossCriterionType,
    "Predict POS tags", this.predictPosTags
  )
}