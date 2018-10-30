/* Copyright 2018-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package constraints.utils

import com.kotlinnlp.conllio.Sentence as CoNLLSentence
import com.kotlinnlp.dependencytree.DependencyTree
import com.kotlinnlp.linguisticdescription.GrammaticalConfiguration
import com.kotlinnlp.linguisticdescription.POSTag
import com.kotlinnlp.linguisticdescription.morphology.Morphology
import com.kotlinnlp.linguisticdescription.morphology.POS
import com.kotlinnlp.linguisticdescription.morphology.ScoredMorphology
import com.kotlinnlp.linguisticdescription.morphology.SingleMorphology
import com.kotlinnlp.linguisticdescription.sentence.token.MorphoSynToken
import com.kotlinnlp.neuralparser.constraints.Constraint
import com.kotlinnlp.neuralparser.constraints.SingleConstraint
import com.kotlinnlp.neuralparser.helpers.preprocessors.MorphoPreprocessor
import com.kotlinnlp.neuralparser.language.BaseSentence
import com.kotlinnlp.neuralparser.helpers.MorphoSynBuilder
import com.kotlinnlp.neuralparser.language.ParsingSentence
import com.kotlinnlp.neuralparser.language.ParsingToken
import com.kotlinnlp.utils.notEmptyOr
import com.kotlinnlp.utils.progressindicator.ProgressIndicatorBar

/**
 * A helper that verifies constraints on sentences.
 *
 * @param constraints the list of constraints to verify
 * @param sentences the list of sentence to validate
 * @param morphoPreprocessor a morphological sentence preprocessor
 */
internal class ConstraintsValidator(
  private val constraints: List<Constraint>,
  private val sentences: List<CoNLLSentence>,
  private val morphoPreprocessor: MorphoPreprocessor
) {

  /**
   * Information about a constraint that has been violated.
   *
   * @property violations the list of violation tokens info
   * @property violatedSentences the count of sentences in which the constraint has been violated
   */
  private data class ViolationInfo(
    val violations: MutableList<TokenInfo> = mutableListOf(),
    var violatedSentences: Int = 0
  )

  /**
   * Information about the token that caused a violation.
   *
   * @property token the morpho-syntactic token of the violation
   * @property conllTokenId the id of the CoNLL token that generated the morpho-syntactic token
   * @property conllSentence the related CoNLL sentence
   */
  private data class TokenInfo(
    val token: MorphoSynToken.Single,
    val conllTokenId: Int,
    val conllSentence: CoNLLSentence)

  /**
   * The map of violated constraint to the related info.
   */
  private val violationsByConstraint = mutableMapOf<Constraint, ViolationInfo>()

  /**
   * The count of correct sentences.
   */
  private var correct = 0

  /**
   * Verify the [constraints] on the [sentences].
   */
  fun validate() {

    val progress = ProgressIndicatorBar(this.sentences.size)

    println("Verifying constraints...")

    this.sentences.forEach {
      progress.tick()
      this.processSentence(it)
    }

    println("\nValid sentences: %d / %d (%.1f%%).".format(correct, sentences.size, 100.0 * correct / sentences.size))
    this.printConstraintsViolated()
  }

  /**
   * @param sentence a CoNLL sentence to process
   */
  private fun processSentence(sentence: CoNLLSentence) {

    val tokens: List<MorphoSynToken.Single> = this.buildMorphoSynTokens(sentence)
    val validator = SentenceValidator(constraints = this.constraints, tokens = tokens)
    val violated: Map<MorphoSynToken.Single, Set<Constraint>> = validator.validate()

    if (violated.isEmpty()) this.correct++

    violated.values.flatMap { it }.toSet().forEach {
      this.violationsByConstraint.getOrPut(it) { ViolationInfo() }.violatedSentences++
    }

    violated.forEach { token, constraints ->
      constraints.forEach { constraint ->
        this.violationsByConstraint.getValue(constraint).violations.add(
          TokenInfo(
            token = token,
            conllTokenId = getCoNLLTokenId(token = token, tokens = tokens),
            conllSentence = sentence))
      }
    }
  }

  /**
   * @param sentence a CoNLL sentence
   *
   * @return a list of morpho-syntactic tokens built from the given sentence
   */
  private fun buildMorphoSynTokens(sentence: CoNLLSentence): List<MorphoSynToken.Single> {

    val sentenceTree = DependencyTree(sentence)
    var nextAvailableId: Int = sentence.tokens.last().id + 1

    val parsingSentence: ParsingSentence = this.morphoPreprocessor.convert(BaseSentence.fromCoNLL(sentence, index = 0))
    val converter = MorphoSynBuilder(parsingSentence = parsingSentence, dependencyTree = sentenceTree)

    val morphoSynTokens: List<MorphoSynToken> = parsingSentence.tokens.mapIndexed { i, parsingToken ->

      val morphoSynToken = converter.buildToken(
        tokenId = parsingToken.id,
        morphologies = this.getValidMorphologies(
          configuration = sentenceTree.getConfiguration(parsingToken.id)!!,
          tokenIndex = i,
          parsingSentence = parsingSentence
        ).map { ScoredMorphology(components = it.components, score = 1.0) })

      if (morphoSynToken is MorphoSynToken.Composite) nextAvailableId += morphoSynToken.components.size

      morphoSynToken
    }

    return explodeTokens(morphoSynTokens)
  }

  /**
   * Get the valid morphologies of a token respect to a given grammatical configuration.
   *
   * @param configuration a grammatical configuration of a token
   * @param tokenIndex the index of the token within the sentence tokens
   * @param parsingSentence the parsing sentence of the token
   *
   * @return the list of valid morphologies of the token
   */
  private fun getValidMorphologies(configuration: GrammaticalConfiguration,
                                   tokenIndex: Int,
                                   parsingSentence: ParsingSentence): List<Morphology> {

    val token: ParsingToken = parsingSentence.tokens[tokenIndex]
    val tokenMorphologies: List<Morphology> =
      token.morphologies +
        parsingSentence.multiWords.filter { tokenIndex in it.startToken..it.endToken }.flatMap { it.morphologies }

    return tokenMorphologies
      .filter { configuration.isCompatible(it) }
      .notEmptyOr {

        val posTypes: List<POS> = configuration.components.map { (it.pos as POSTag.Base).type }

        if (!posTypes.first().isComposedBy(POS.Noun))
          println("\n[WARNING] No morphology found for token \"${token.form}\" with pos ${posTypes.joinToString("+")}.")

        // A generic morphology is created when the morphological analyzer did not find it
        listOf(Morphology(
          components = posTypes.map { SingleMorphology(lemma = token.form, pos = it, allowIncompleteProperties = true) }
        ))
      }
  }

  /**
   * Print the constraints that have been violated.
   */
  private fun printConstraintsViolated() {

    if (this.violationsByConstraint.isNotEmpty()) {

      println("\nConstraints violated:")
      println(this.violationsByConstraint.keys
        .asSequence()
        .map { it.description }
        .sorted()
        .joinToString("\n") { it.prependIndent("- ") })

      println("\nPer sentence:")

      this.violationsByConstraint.forEach { constraint, info ->

        val perc: Double = 100.0 * info.violatedSentences / this.sentences.size

        println()
        println("------------------------------------------------------------------------------------------")
        println("'$constraint' (violated in ${info.violatedSentences} sentences of ${this.sentences.size} [%.1f%%])"
          .format(perc))
        println("------------------------------------------------------------------------------------------")

        info.violations.forEach { tokenInfo ->
          println("\n" + buildPrintSentence(constraint = constraint, tokenInfo = tokenInfo))
        }
      }

    } else {
      println("\nNo constraints violated.")
    }
  }

  /**
   * @param constraint a constraint violated
   * @param tokenInfo info of the token of the violation
   *
   * @return a string containing info of the violated sentence
   */
  private fun buildPrintSentence(constraint: Constraint, tokenInfo: TokenInfo): String {

    val governorId: Int? = tokenInfo.token.syntacticRelation.governor
    val conllSentences: List<String> = tokenInfo.conllSentence.toCoNLLString(writeComments = false).split("\n")

    return if (constraint is SingleConstraint || governorId == null)
      conllSentences.joinToString("\n") {
        if (Regex("^${tokenInfo.conllTokenId}\t.*").matches(it)) "$it\t<===== X" else it
      }
    else
      conllSentences.joinToString("\n") {
        when {
          Regex("^${tokenInfo.conllTokenId}\t.*").matches(it) -> "$it\t<===== DEP"
          Regex("^$governorId\t.*").matches(it) -> "$it\t<===== GOV"
          else -> it
        }
      }
  }
}
