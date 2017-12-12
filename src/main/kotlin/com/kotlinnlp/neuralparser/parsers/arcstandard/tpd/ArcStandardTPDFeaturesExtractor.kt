/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.parsers.arcstandard.tpd

import com.kotlinnlp.neuralparser.templates.inputcontexts.TokensAmbiguousPOSContext
import com.kotlinnlp.neuralparser.templates.featuresextractor.EmbeddingsFeaturesExtractor
import com.kotlinnlp.neuralparser.templates.supportstructure.compositeprediction.TPDSupportStructure
import com.kotlinnlp.syntaxdecoder.modules.featuresextractor.FeaturesExtractor
import com.kotlinnlp.syntaxdecoder.transitionsystem.models.arcstandard.ArcStandardTransition
import com.kotlinnlp.syntaxdecoder.transitionsystem.state.StateView
import com.kotlinnlp.syntaxdecoder.transitionsystem.state.templates.StackBufferState
import com.kotlinnlp.syntaxdecoder.utils.getItemOrNull

/**
 * The [EmbeddingsFeaturesExtractor] that extracts features combining word Embeddings and ambiguous POS vectors for the
 * ArcStandard transition system.
 */
class ArcStandardTPDFeaturesExtractor : EmbeddingsFeaturesExtractor<
  StackBufferState,
  ArcStandardTransition,
  TokensAmbiguousPOSContext,
  TPDSupportStructure>() {

  /**
   * Beat the occurrence of a new example.
   */
  override fun newExample() = Unit

  /**
   * Beat the occurrence of a new batch.
   */
  override fun newBatch() = Unit

  /**
   * Beat the occurrence of a new epoch.
   */
  override fun newEpoch() = Unit

  /**
   * Update the trainable components of this [FeaturesExtractor].
   */
  override fun update() = Unit

  /**
   * Get the tokens window respect to a given state
   *
   * @param stateView a view of the state
   *
   * @return the tokens window as list of Int
   */
  override fun getTokensWindow(stateView: StateView<StackBufferState>) = listOf(
    stateView.state.stack.getItemOrNull(-3),
    stateView.state.stack.getItemOrNull(-2),
    stateView.state.stack.getItemOrNull(-1),
    stateView.state.buffer.getItemOrNull(0))
}
