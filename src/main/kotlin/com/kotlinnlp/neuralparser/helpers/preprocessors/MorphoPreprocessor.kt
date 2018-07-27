/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.helpers.preprocessors

import com.kotlinnlp.linguisticdescription.sentence.RealSentence
import com.kotlinnlp.linguisticdescription.sentence.token.RealToken
import com.kotlinnlp.linguisticdescription.sentence.token.properties.Position
import com.kotlinnlp.morphologicalanalyzer.MorphologicalAnalysis
import com.kotlinnlp.morphologicalanalyzer.MorphologicalAnalyzer
import com.kotlinnlp.neuralparser.language.BaseSentence
import com.kotlinnlp.neuralparser.language.ParsingSentence
import com.kotlinnlp.neuralparser.language.ParsingToken

/**
 * Pre-process a sentence with a morphological analyzer, before the parsing starts.
 *
 * @param morphologicalAnalyzer a morphological analyzer
 */
class MorphoPreprocessor(private val morphologicalAnalyzer: MorphologicalAnalyzer) : SentencePreprocessor {

  /**
   * Convert a [BaseSentence] to a [ParsingSentence].
   *
   * @param sentence a base sentence
   *
   * @return a sentence ready to be parsed
   */
  override fun process(sentence: BaseSentence): ParsingSentence {

    @Suppress("UNCHECKED_CAST")
    val morphoAnalysis: MorphologicalAnalysis? = this.morphologicalAnalyzer.analyze(sentence as RealSentence<RealToken>)

    return ParsingSentence(tokens = sentence.tokens.map {
      ParsingToken(
        id = it.id,
        form = it.form,
        position = it.position,
        morphologies = morphoAnalysis?.tokens?.get(it.id) ?: emptyList(),
        posTag = it.posTag
      )
    })
  }
}