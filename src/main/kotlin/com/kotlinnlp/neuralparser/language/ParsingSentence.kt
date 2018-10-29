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
import com.kotlinnlp.linguisticdescription.GrammaticalConfiguration
import com.kotlinnlp.linguisticdescription.morphology.ScoredMorphology
import com.kotlinnlp.linguisticdescription.sentence.MorphoSentence
import com.kotlinnlp.linguisticdescription.sentence.MorphoSynSentence
import com.kotlinnlp.linguisticdescription.sentence.SentenceIdentificable
import com.kotlinnlp.linguisticdescription.sentence.properties.datetime.DateTime
import com.kotlinnlp.linguisticdescription.sentence.properties.MultiWords
import com.kotlinnlp.linguisticdescription.sentence.token.MorphoSynToken
import com.kotlinnlp.neuralparser.parsers.lhrparser.neuralmodules.labeler.selector.LabelerSelector

/**
 * The sentence used as input of the [com.kotlinnlp.neuralparser.NeuralParser].
 *
 * @property tokens the list of tokens of the sentence
 * @property multiWords the list of multi-words expressions recognized in the sentence (can be empty)
 * @property dateTimes the list of date-times expressions recognized in the sentence (can be empty)
 */
class ParsingSentence(
  override val tokens: List<ParsingToken>,
  override val multiWords: List<MultiWords> = emptyList(),
  override val dateTimes: List<DateTime> = emptyList()
) : MorphoSentence<ParsingToken>, SentenceIdentificable<ParsingToken>() {

  /**
   * TODO: set missing properties
   *
   * @param dependencyTree the dependency tree from which to extract the dependency relations
   * @param labelerSelector a labeler prediction selector
   *
   * @return a new [MorphoSynSentence]
   */
  fun toMorphoSynSentence(dependencyTree: DependencyTree, labelerSelector: LabelerSelector): MorphoSynSentence {

    var nextAvailableId: Int = this.tokens.last().id + 1

    return MorphoSynSentence(
      id = 0,
      confidence = 0.0,
      dateTimes = if (this.dateTimes.isNotEmpty()) this.dateTimes else null,
      entities = null,
      tokens = this.tokens.mapIndexed { i, it ->

        val config: GrammaticalConfiguration = dependencyTree.getConfiguration(it.id)!!

        // TODO: set the morphologies scores adding the labeler prediction scores of configurations with the same pos
        val morphoSynToken: MorphoSynToken = it.toMorphoSynToken(
          nextAvailableId = nextAvailableId,
          governorId = dependencyTree.getHead(it.id),
          attachmentScore = dependencyTree.getAttachmentScore(it.id),
          config = config,
          morphologies = labelerSelector.getValidMorphologies(sentence = this, tokenIndex = i, configuration = config)
            .map { ScoredMorphology(components = it.components, score = 1.0) })

        if (morphoSynToken is MorphoSynToken.Composite)
          nextAvailableId += morphoSynToken.components.size

        morphoSynToken
      }
    )
  }
}
