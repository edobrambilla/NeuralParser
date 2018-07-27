/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.language

import com.kotlinnlp.conllio.Sentence as CoNLLSentence
import com.kotlinnlp.conllio.Token as CoNLLToken
import com.kotlinnlp.dependencytree.DependencyTree
import com.kotlinnlp.linguisticdescription.sentence.MorphoSyntacticSentence
import com.kotlinnlp.linguisticdescription.sentence.Sentence
import com.kotlinnlp.linguisticdescription.sentence.token.properties.DependencyRelation
import com.kotlinnlp.linguisticdescription.sentence.token.properties.Position
import com.kotlinnlp.morphologicalanalyzer.MorphologicalAnalysis
import com.kotlinnlp.morphologicalanalyzer.MorphologicalAnalyzer

/**
 * @property tokens the tokens
 */
class ParsingSentence(
  override val tokens: List<ParsingToken>
) : Sentence<ParsingToken> {

  companion object {

    /**
     * Transform a CoNLL sentence into a [ParsingSentence].
     *
     * @param sentence a CoNLL sentence
     * @return a parsing sentence
     */
    fun fromCoNLL(sentence: CoNLLSentence, morphologicalAnalyzer: MorphologicalAnalyzer? = null): ParsingSentence {

      val baseTokens = sentence.tokens.toBaseTokens()

      val morphoAnalysis: MorphologicalAnalysis? = morphologicalAnalyzer?.analyze(
        BaseSentence(
          tokens = baseTokens,
          position = Position(
            index = 0,
            start = baseTokens.first().position.start,
            end = baseTokens.last().position.end)))

      return ParsingSentence(tokens = baseTokens.map {
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

  /**
   * Check token ids.
   */
  init {

    require(this.tokens.first().id == 0) { "Tokens ids must start from 0" }

    require((1 until this.tokens.size).all { i -> this.tokens[i].id == this.tokens[i - 1].id + 1 }) {
      "Tokens ids must be incremental and sequential"
    }
  }

  /**
   * TODO: set all properties expect for tokens
   *
   * @param dependencyTree the dependency tree from which to extract the dependency relations
   *
   * @return a new [MorphoSyntacticSentence]
   */
  fun toMorphoSyntacticSentence(dependencyTree: DependencyTree) = MorphoSyntacticSentence(
    id = 0,
    confidence = 0.0,
    dateTimes = null,
    entities = null,
    tokens = this.tokens.map {
      it.toMorphoSyntacticToken(
        dependencyRelation = DependencyRelation(
          governor = dependencyTree.heads[it.id],
          deprel = dependencyTree.deprels[it.id]?.label ?: "_",
          attachmentScore = 0.0) // TODO: set it
      )
    }
  )
}
