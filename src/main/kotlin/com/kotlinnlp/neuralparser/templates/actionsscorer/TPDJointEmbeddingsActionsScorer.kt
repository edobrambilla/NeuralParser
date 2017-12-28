/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.templates.actionsscorer

import com.kotlinnlp.dependencytree.Deprel
import com.kotlinnlp.dependencytree.POSTag
import com.kotlinnlp.neuralparser.templates.inputcontexts.TokensEncodingContext
import com.kotlinnlp.neuralparser.templates.supportstructure.compositeprediction.TPDJointSupportStructure
import com.kotlinnlp.simplednn.utils.DictionarySet
import com.kotlinnlp.neuralparser.utils.features.DenseFeatures
import com.kotlinnlp.neuralparser.utils.features.DenseFeaturesErrors
import com.kotlinnlp.neuralparser.utils.items.DenseItem
import com.kotlinnlp.simplednn.core.functionalities.activations.ActivationFunction
import com.kotlinnlp.simplednn.core.neuralnetwork.NetworkParameters
import com.kotlinnlp.simplednn.core.neuralnetwork.NeuralNetwork
import com.kotlinnlp.simplednn.core.optimizer.ParamsOptimizer
import com.kotlinnlp.simplednn.deeplearning.multitasknetwork.MultiTaskNetwork
import com.kotlinnlp.simplednn.deeplearning.multitasknetwork.MultiTaskNetworkModel
import com.kotlinnlp.simplednn.deeplearning.multitasknetwork.MultiTaskNetworkParameters
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArrayFactory
import com.kotlinnlp.syntaxdecoder.utils.DecodingContext
import com.kotlinnlp.syntaxdecoder.modules.actionsscorer.ActionsScorer
import com.kotlinnlp.syntaxdecoder.modules.actionsscorer.ActionsScorerTrainable
import com.kotlinnlp.syntaxdecoder.transitionsystem.Transition
import com.kotlinnlp.syntaxdecoder.transitionsystem.state.State

/**
 * The Transition+POS+Deprel ActionsScorer.
 * The action score is obtained adding 3 scores coming from 3 different networks: one for the transition, one for the
 * POS and one for the deprel.
 * The POS and the deprel networks share the same input layer in a [MultiTaskNetwork].
 *
 * @param activationFunction the function used to activate the actions scores
 * @param transitionNetwork a neural network to score the transitions
 * @param posDeprelNetworkModel the model of a multi-task network to score POS tags and deprels
 * @param transitionOptimizer the optimizer of the [transitionNetwork] params
 * @param posDeprelOptimizer the optimizer of the [posDeprelNetworkModel] params
 * @param deprelTags the dictionary set of deprels
 */
abstract class TPDJointEmbeddingsActionsScorer<
  StateType : State<StateType>,
  TransitionType : Transition<TransitionType, StateType>,
  InputContextType: TokensEncodingContext<InputContextType>>
(
  val activationFunction: ActivationFunction?,
  val transitionNetwork: NeuralNetwork,
  val posDeprelNetworkModel: MultiTaskNetworkModel,
  val transitionOptimizer: ParamsOptimizer<NetworkParameters>,
  val posDeprelOptimizer: ParamsOptimizer<MultiTaskNetworkParameters>,
  val deprelTags: DictionarySet<Deprel>,
  val posTags: DictionarySet<POSTag>
) :
  ActionsScorerTrainable<
    StateType,
    TransitionType,
    InputContextType,
    DenseItem,
    DenseFeaturesErrors,
    DenseFeatures,
    TPDJointSupportStructure>()
{

  /**
   * The deprel network outcome index of this action.
   */
  abstract protected val Transition<TransitionType, StateType>.Action.deprelOutcomeIndex: Int

  /**
   * The POS network outcome index of this action.
   */
  abstract protected val Transition<TransitionType, StateType>.Action.posTagOutcomeIndex: Int

  /**
   * The [transitionNetwork] outcome index of this transition.
   */
  abstract protected val Transition<TransitionType, StateType>.outcomeIndex: Int

  /**
   * Assign scores to the actions contained into the given [decodingContext] using the features contained in it and the
   * given [supportStructure].
   *
   * @param decodingContext the decoding context that contains the actions to score
   * @param supportStructure the decoding support structure
   */
  override fun score(
    decodingContext: DecodingContext<StateType, TransitionType, InputContextType, DenseItem, DenseFeatures>,
    supportStructure: TPDJointSupportStructure) {

    val inputFeatures: DenseNDArray = decodingContext.features.array
    val posDeprelPredictions: List<DenseNDArray> = supportStructure.posDeprelNetwork.forward(inputFeatures)

    val actionsScores = this.combineScores(
      actions = decodingContext.actions,
      transitionPrediction = supportStructure.transitionProcessor.forward(inputFeatures),
      posPrediction = posDeprelPredictions[0],
      deprelPrediction = posDeprelPredictions[1])

    val activatedScores: DenseNDArray = this.activationFunction?.f(actionsScores) ?: actionsScores

    decodingContext.actions.forEachIndexed { i, action -> action.score = activatedScores[i] }
  }

  /**
   * Backward errors through this [ActionsScorer], starting from the scored actions in the given [decodingContext].
   * Errors are required to be already set into the output actions properly.
   *
   * @param decodingContext the decoding context that contains the scored actions
   * @param supportStructure the decoding support structure
   * @param propagateToInput a Boolean indicating whether errors must be propagated to the input items
   */
  override fun backward(
    decodingContext: DecodingContext<StateType, TransitionType, InputContextType, DenseItem, DenseFeatures>,
    supportStructure: TPDJointSupportStructure,
    propagateToInput: Boolean) {

    val (transitionErrors, posErrors, deprelErrors) = this.getOutputErrors(
      actions = decodingContext.actions,
      transitionOutcomes = supportStructure.transitionProcessor.structure.outputLayer.outputArray.values,
      posOutcomes = supportStructure.posDeprelNetwork.outputProcessors[0].structure.outputLayer.outputArray.values,
      deprelOutcomes = supportStructure.posDeprelNetwork.outputProcessors[1].structure.outputLayer.outputArray.values
    )

    supportStructure.transitionProcessor.backward(
      outputErrors = transitionErrors,
      propagateToInput = propagateToInput,
      mePropK = null) // TODO: set 'mePropK' as parameter

    supportStructure.posDeprelNetwork.backward(
      outputErrorsList = listOf(posErrors, deprelErrors),
      propagateToInput = propagateToInput,
      inputMePropK = null, // TODO: set 'inputMePropK' as parameter
      outputMePropK = null) // TODO: set 'outputMePropK' as parameter

    this.transitionOptimizer.accumulate(supportStructure.transitionProcessor.getParamsErrors(copy = false))
    this.posDeprelOptimizer.accumulate(supportStructure.posDeprelNetwork.getParamsErrors(copy = false))
  }

  /**
   * @param decodingContext the decoding context that contains the scored actions
   * @param supportStructure the decoding support structure
   *
   * @return the errors of the features used to score the actions in the given [decodingContext]
   */
  override fun getFeaturesErrors(
    decodingContext: DecodingContext<StateType, TransitionType, InputContextType, DenseItem, DenseFeatures>,
    supportStructure: TPDJointSupportStructure
  ) = DenseFeaturesErrors(
    supportStructure.posDeprelNetwork.getInputErrors(copy = true)
      .assignSum(supportStructure.transitionProcessor.getInputErrors(copy = false))
  )

  /**
   * Method to call every new batch.
   */
  override fun newBatch() {
    this.transitionOptimizer.newBatch()
    this.posDeprelOptimizer.newBatch()
  }

  /**
   * Method to call every new example.
   */
  override fun newExample() {
    this.transitionOptimizer.newExample()
    this.posDeprelOptimizer.newExample()
  }

  /**
   * Method to call every new epoch.
   */
  override fun newEpoch() {
    this.transitionOptimizer.newEpoch()
    this.posDeprelOptimizer.newEpoch()
  }

  /**
   * Update the scorer.
   */
  override fun update() {
    this.transitionOptimizer.update()
    this.posDeprelOptimizer.update()
  }

  /**
   * Combine the TPD scores to obtain the actions scores.
   *
   * @param actions the list of actions to score
   * @param transitionPrediction the outcome array of the transition network prediction
   * @param posPrediction the outcome array of the POS tag network prediction
   * @param deprelPrediction the outcome array of the deprel network prediction
   *
   * @return the array of action scores
   */
  protected open fun combineScores(actions: List<Transition<TransitionType, StateType>.Action>,
                            transitionPrediction: DenseNDArray,
                            posPrediction: DenseNDArray,
                            deprelPrediction: DenseNDArray): DenseNDArray =
    DenseNDArrayFactory.arrayOf(doubleArrayOf(*DoubleArray(
      size = actions.size,
      init = { i ->
        val action = actions[i]

        val t = transitionPrediction[action.transition.outcomeIndex]
        val p = posPrediction[action.posTagOutcomeIndex]
        val d = deprelPrediction[action.deprelOutcomeIndex]

        t + p + d
      }
    )))

  /**
   * Link [actions] errors to the output errors array of the neural network used to score the given [actions].
   *
   * @param actions the list of scored actions (containing their errors)
   * @param transitionOutcomes the array of the network outcomes related to the scoring of the transitions
   * @param deprelOutcomes the array of the network outcomes related to the scoring of the deprels
   * @param posOutcomes the array of the network outcomes related to the scoring of the POS tags
   *
   * @return the array of output errors
   */
  protected open fun getOutputErrors(actions: List<Transition<TransitionType, StateType>.Action>,
                              transitionOutcomes: DenseNDArray,
                              posOutcomes: DenseNDArray,
                              deprelOutcomes: DenseNDArray): Triple<DenseNDArray, DenseNDArray, DenseNDArray> {

    val transitionErrors = DenseNDArrayFactory.zeros(transitionOutcomes.shape)
    val posErrors = DenseNDArrayFactory.zeros(posOutcomes.shape)
    val deprelErrors = DenseNDArrayFactory.zeros(deprelOutcomes.shape)

    actions.forEach { action ->
      transitionErrors[action.transition.outcomeIndex] += action.error
      posErrors[action.posTagOutcomeIndex] += action.error
      deprelErrors[action.deprelOutcomeIndex] += action.error
    }

    return Triple(transitionErrors, posErrors, deprelErrors)
  }
}
