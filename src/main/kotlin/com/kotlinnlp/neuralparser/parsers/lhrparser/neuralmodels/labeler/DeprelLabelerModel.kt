/* Copyright 2018-present LHRParser Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * -----------------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.labeler

import com.kotlinnlp.dependencytree.DependencyTree
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.labeler.utils.LossCriterion
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodels.labeler.utils.LossCriterionType
import com.kotlinnlp.dependencytree.Deprel
import com.kotlinnlp.simplednn.core.functionalities.activations.Softmax
import com.kotlinnlp.simplednn.core.functionalities.activations.Tanh
import com.kotlinnlp.simplednn.core.layers.LayerInterface
import com.kotlinnlp.simplednn.core.layers.LayerType
import com.kotlinnlp.simplednn.core.neuralnetwork.NeuralNetwork
import com.kotlinnlp.simplednn.simplemath.ndarray.dense.DenseNDArray
import com.kotlinnlp.utils.DictionarySet
import java.io.Serializable

/**
 * The model of the [DeprelLabeler].
 *
 * @property contextEncodingSize the size of the token encoding vectors
 * @property deprels the dictionary set of all possible deprels
 * @property lossCriterionType the training mode
 */
class DeprelLabelerModel(
  val contextEncodingSize: Int,
  val deprels: DictionarySet<Deprel>,
  val lossCriterionType: LossCriterionType
) : Serializable {

  companion object {

    /**
     * Private val used to serialize the class (needed by Serializable).
     */
    @Suppress("unused")
    private const val serialVersionUID: Long = 1L
  }

  /**
   * The Network model that predicts the Deprels
   */
  val networkModel: NeuralNetwork = NeuralNetwork(
    LayerInterface(sizes = listOf(this.contextEncodingSize, this.contextEncodingSize)),
    LayerInterface(
      size = this.contextEncodingSize,
      connectionType = LayerType.Connection.Affine,
      activationFunction = Tanh()),
    LayerInterface(
      type = LayerType.Input.Dense,
      size = this.deprels.size,
      dropout = 0.0,
      connectionType = LayerType.Connection.Feedforward,
      activationFunction = when (this.lossCriterionType) {
        LossCriterionType.Softmax -> Softmax()
        LossCriterionType.HingeLoss -> null
      })
  )

  /**
   * Return the errors of a given labeler predictions, respect to a gold dependency tree.
   * Errors are calculated comparing the last predictions done with the given gold deprels.
   *
   * @param predictions the current network predictions
   * @param goldTree the gold tree of the sentence
   *
   * @return a list of predictions errors
   */
  fun calculateLoss(predictions: List<DenseNDArray>, goldTree: DependencyTree): List<DenseNDArray> {

    val errorsList = mutableListOf<DenseNDArray>()

    predictions.forEachIndexed { tokenIndex, prediction ->

      val tokenId: Int = goldTree.elements[tokenIndex]
      val goldDeprel: Deprel = goldTree.getDeprel(tokenId)!!
      val goldDeprelIndex: Int = this.deprels.getId(goldDeprel)!!

      errorsList.add(LossCriterion(this.lossCriterionType).getPredictionErrors(
        prediction = prediction, goldIndex = goldDeprelIndex))
    }

    return errorsList
  }
}
