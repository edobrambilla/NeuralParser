/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.parsers.arcstandard.attention

import com.kotlinnlp.neuralparser.utils.actionsembeddings.ActionsVectorsOptimizer
import com.kotlinnlp.neuralparser.parsers.arcstandard.tpdjoint.ArcStandardTPDJointActionsScorer
import com.kotlinnlp.neuralparser.templates.inputcontexts.TokensAmbiguousPOSContext
import com.kotlinnlp.neuralparser.templates.parsers.birnn.ambiguouspos.BiRNNAmbiguousPOSParser
import com.kotlinnlp.neuralparser.utils.features.DenseFeatures
import com.kotlinnlp.neuralparser.templates.supportstructure.OutputErrorsInit
import com.kotlinnlp.neuralparser.templates.supportstructure.compositeprediction.AttentionDecodingSupportStructure
import com.kotlinnlp.neuralparser.templates.supportstructure.compositeprediction.AttentionDecodingStructureFactory
import com.kotlinnlp.neuralparser.utils.features.DenseFeaturesErrors
import com.kotlinnlp.neuralparser.utils.items.DenseItem
import com.kotlinnlp.simplednn.core.functionalities.activations.Softmax
import com.kotlinnlp.simplednn.core.functionalities.updatemethods.adam.ADAMMethod
import com.kotlinnlp.simplednn.core.optimizer.ParamsOptimizer
import com.kotlinnlp.syntaxdecoder.BeamDecoder
import com.kotlinnlp.syntaxdecoder.transitionsystem.ActionsGenerator
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.ArcStandard
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.ArcStandardTransition
import com.kotlinnlp.syntaxdecoder.GreedyDecoder
import com.kotlinnlp.syntaxdecoder.modules.actionsscorer.ActionsScorer
import com.kotlinnlp.syntaxdecoder.modules.bestactionselector.BestActionSelector
import com.kotlinnlp.syntaxdecoder.modules.bestactionselector.HighestScoreActionSelector
import com.kotlinnlp.syntaxdecoder.modules.bestactionselector.MultiActionsSelector
import com.kotlinnlp.syntaxdecoder.modules.bestactionselector.MultliActionsSelectorByScore
import com.kotlinnlp.syntaxdecoder.modules.featuresextractor.FeaturesExtractor
import com.kotlinnlp.syntaxdecoder.modules.supportstructure.SupportStructureFactory
import com.kotlinnlp.syntaxdecoder.transitionsystem.Transition
import com.kotlinnlp.syntaxdecoder.transitionsystem.TransitionSystem
import com.kotlinnlp.syntaxdecoder.transitionsystem.state.templates.StackBufferState

/**
 * A NeuralParser based on the ArcStandard transition system.
 * It uses Embeddings and ambiguous POS vectors to encode the tokens of a sentence through a BiRNN.
 * The features for the actions scoring are composed by the decoding of the token encodings and the embedding vectors
 * of the last applied action through a recurrent system.
 * Actions are scored combining the result of specialized neural networks that score singularly the Transition+Deprel
 * (a multitask joint network), and the POS tag.
 *
 * If the beamSize is 1 then a [GreedyDecoder] is used, a [BeamDecoder] otherwise.
 *
 * @property model the parser model
 * @param wordDropoutCoefficient the word embeddings dropout coefficient (default = 0.0)
 * @param beamSize the max size of the beam (default = 1)
 * @param maxParallelThreads the max number of threads that can run in parallel (default = 1, ignored if beamSize is 1)
 */
class BiRNNAttentionArcStandardParser(
  model: BiRNNAttentionArcStandardParserModel,
  wordDropoutCoefficient: Double = 0.0,
  beamSize: Int = 1,
  maxParallelThreads: Int = 1
) :
  BiRNNAmbiguousPOSParser<
    StackBufferState,
    ArcStandardTransition,
    DenseFeaturesErrors,
    DenseFeatures,
    AttentionDecodingSupportStructure,
    BiRNNAttentionArcStandardParserModel>
  (
    model = model,
    wordDropoutCoefficient = wordDropoutCoefficient,
    beamSize = beamSize,
    maxParallelThreads = maxParallelThreads
  )
{

  /**
   * Whether the action scorer uses the Softmax as output activation (all its networks have the same activation).
   */
  private val useSoftmaxOutput: Boolean =
    this.model.transitionScorerNetwork.layersConfiguration.last().activationFunction == Softmax()

  /**
   * @return the [TransitionSystem] used in this parser
   */
  override fun buildTransitionSystem() = ArcStandard()

  /**
   * @return the [ActionsGenerator] used in this parser
   */
  override fun buildActionsGenerator() =
    ActionsGenerator.MorphoSyntacticLabeled<StackBufferState, ArcStandardTransition>(
      deprels = this.model.corpusDictionary.deprelTags.getElementsReversedSet().groupBy { it.direction },
      deprelPosTagCombinations = this.model.corpusDictionary.deprelPosTagCombinations)

  /**
   * @return the [ActionsScorer] used in this parser
   */
  override fun buildActionsScorer() = ArcStandardTPDJointActionsScorer(
    activationFunction = this.model.actionsScoresActivation,
    transitionNetwork = this.model.transitionScorerNetwork,
    posDeprelNetworkModel = this.model.posDeprelScorerNetworkModel,
    transitionOptimizer = ParamsOptimizer(
      params = this.model.transitionScorerNetwork.model,
      updateMethod = ADAMMethod(stepSize = 0.001, beta1 = 0.9, beta2 = 0.999)),
    posDeprelOptimizer = ParamsOptimizer(
      params = this.model.posDeprelScorerNetworkModel.params,
      updateMethod = ADAMMethod(stepSize = 0.001, beta1 = 0.9, beta2 = 0.999)),
    posTags = this.model.corpusDictionary.posTags,
    deprelTags = this.model.corpusDictionary.deprelTags)

  /**
   * @return the [SupportStructureFactory] used in this parser
   */
  override fun buildSupportStructureFactory() = AttentionDecodingStructureFactory(
    actionDecodingNetwork = this.model.actionDecodingNetwork,
    actionAttentionNetworkParams = this.model.actionAttentionNetworkParams,
    transitionNetwork = this.model.transitionScorerNetwork,
    posDeprelNetworkModel = this.model.posDeprelScorerNetworkModel,
    outputErrorsInit = if (this.useSoftmaxOutput){
      OutputErrorsInit.AllErrors
    } else {
      OutputErrorsInit.AllZeros
    }
  )

  /**
   * @return the [FeaturesExtractor] used in this parser
   */
  override fun buildFeaturesExtractor() = ArcStandardAttentionFeaturesExtractor(
    actionsVectors = this.model.actionsVectors,
    actionsVectorsOptimizer = ActionsVectorsOptimizer(
      actionsVectorsMap = this.model.actionsVectors,
      updateMethod = ADAMMethod(stepSize = 0.001, beta1 = 0.9, beta2 = 0.999)),
    actionDecoderOptimizer = ParamsOptimizer(
      params = this.model.actionDecodingNetwork.model,
      updateMethod = ADAMMethod(stepSize = 0.001, beta1 = 0.9, beta2 = 0.999)),
    actionAttentionNetworkOptimizer = ParamsOptimizer(
      params = this.model.actionAttentionNetworkParams,
      updateMethod = ADAMMethod(stepSize = 0.001, beta1 = 0.9, beta2 = 0.999)),
    featuresEncodingSize = this.model.featuresEncodingSize,
    posTags = this.model.corpusDictionary.posTags,
    deprelTags = this.model.corpusDictionary.deprelTags)

  /**
   * @return the [BestActionSelector] used in this parser during greedy decoding
   */
  override fun buildBestActionSelector() = HighestScoreActionSelector<
    StackBufferState,
    ArcStandardTransition,
    DenseItem,
    TokensAmbiguousPOSContext>()

  /**
   * @return the [MultiActionsSelector] used in this parser during beam decoding
   */
  override fun buildMultiActionsSelector() = MultliActionsSelectorByScore<
    StackBufferState,
    ArcStandardTransition,
    DenseItem,
    TokensAmbiguousPOSContext>()

  /**
   * Callback called before applying an action.
   */
  override fun beforeApplyAction(action: Transition<ArcStandardTransition, StackBufferState>.Action,
                                 context: TokensAmbiguousPOSContext) = Unit
}