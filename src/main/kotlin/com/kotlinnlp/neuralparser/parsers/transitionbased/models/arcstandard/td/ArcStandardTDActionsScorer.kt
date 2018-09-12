/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.parsers.transitionbased.models.arcstandard.td

import com.kotlinnlp.dependencytree.Deprel
import com.kotlinnlp.neuralparser.parsers.transitionbased.templates.actionsscorer.TDEmbeddingsActionsScorer
import com.kotlinnlp.neuralparser.parsers.transitionbased.templates.inputcontexts.TokensEmbeddingsContext
import com.kotlinnlp.syntaxdecoder.transitionsystem.Transition
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.ArcStandardTransition
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.transitions.Shift
import com.kotlinnlp.syntaxdecoder.transitionsystem.state.templates.StackBufferState
import com.kotlinnlp.syntaxdecoder.syntax.DependencyRelation
import com.kotlinnlp.simplednn.core.neuralnetwork.NetworkParameters
import com.kotlinnlp.simplednn.core.optimizer.ParamsOptimizer
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.transitions.ArcLeft
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.transitions.ArcRight
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.transitions.Root
import com.kotlinnlp.utils.DictionarySet

/**
 * The ArcStandardActionsScorer.
 *
 * @param transitionOptimizer the optimizer of the transition network params
 * @param deprelOptimizer the optimizer of the deprel network params
 * @param deprelTags the dictionary set of deprels
 */
class ArcStandardTDActionsScorer(
  transitionOptimizer: ParamsOptimizer<NetworkParameters>,
  deprelOptimizer: ParamsOptimizer<NetworkParameters>,
  private val deprelTags: DictionarySet<Deprel>
) : TDEmbeddingsActionsScorer<StackBufferState, ArcStandardTransition, TokensEmbeddingsContext>(
    transitionOptimizer = transitionOptimizer, deprelOptimizer = deprelOptimizer) {

  /**
   * The transition network outcome index of this transition.
   */
  override val Transition<ArcStandardTransition, StackBufferState>.outcomeIndex: Int get() = when(this) {
    is Shift -> 0
    is Root -> 1
    is ArcLeft -> 2
    is ArcRight -> 3
    else -> throw RuntimeException("unknown transition")
  }

  /**
   * The deprel network outcome index of this action.
   */
  override val Transition<ArcStandardTransition, StackBufferState>.Action.outcomeIndex: Int get() = when {
    this.transition is Shift -> 0
    this is DependencyRelation -> deprelTags.getId(this.deprel!!)!! + 1 // + shift offset
    else -> throw RuntimeException("unknown action")
  }
}
