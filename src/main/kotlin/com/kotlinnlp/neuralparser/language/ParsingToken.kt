/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.language

import com.kotlinnlp.linguisticdescription.GrammaticalConfiguration
import com.kotlinnlp.linguisticdescription.POSTag
import com.kotlinnlp.linguisticdescription.morphology.*
import com.kotlinnlp.linguisticdescription.sentence.token.*
import com.kotlinnlp.linguisticdescription.sentence.token.properties.SyntacticRelation
import com.kotlinnlp.linguisticdescription.sentence.token.properties.Position
import kotlin.reflect.KClass

/**
 * The token of the [ParsingSentence].
 *
 * @property id the id of the token, unique within its sentence
 * @property form the form
 * @property morphologies the list of possible morphologies of the token
 * @property pos the list of part-of-speech tags associated to the token (more for composite tokens, can be null)
 * @property position the position of the token in the text (null if it is a trace)
 */
data class ParsingToken(
  override val id: Int,
  override val form: String,
  override val morphologies: List<Morphology>,
  override val pos: List<POSTag>? = null, // TODO: find a better solution
  val position: Position?
) : MorphoToken, FormToken, TokenIdentificable {

  /**
   * @param nextAvailableId the next id that can be assigned to a new token of the sentence (as component)
   * @param governorId the governor id
   * @param attachmentScore the attachment score
   * @param config the grammatical configuration of this token
   * @param morphologies the possible morphologies of this token
   *
   * @return a new morpho syntactic token
   */
  fun toMorphoSynToken(nextAvailableId: Int,
                       governorId: Int?,
                       attachmentScore: Double,
                       config: GrammaticalConfiguration,
                       morphologies: List<ScoredMorphology>): MorphoSynToken {

    require(morphologies.all { it.components.size == config.components.size }) {
      "The selected morphologies must have the same number of components of the given grammatical configuration."
    }

    return if (config.components.size == 1)
      this.buildSingleToken(
        id = this.id,
        governorId = governorId,
        attachmentScore = attachmentScore,
        grammaticalComponent = config.components.single(),
        morphologies = morphologies.map { it.toSingle() })
    else
      this.buildCompositeToken(
        nextAvailableId = nextAvailableId,
        governorId = governorId,
        attachmentScore = attachmentScore,
        config = config,
        morphologies = morphologies)
  }

  /**
   * @param id the id of the new token
   * @param governorId the governor id
   * @param attachmentScore the attachment score
   * @param grammaticalComponent the grammatical configuration of the token as single component
   * @param morphologies the list of possible scored morphologies of the token
   *
   * @return a new single token
   */
  private fun buildSingleToken(id: Int,
                               governorId: Int?,
                               attachmentScore: Double,
                               grammaticalComponent: GrammaticalConfiguration.Component,
                               morphologies: List<ScoredSingleMorphology>): MorphoSynToken.Single {

    val syntacticRelation = SyntacticRelation(
      governor = governorId,
      attachmentScore = attachmentScore,
      dependency = grammaticalComponent.syntacticDependency)

    return if (this.position != null)
      Word(
        id = id,
        form = this.form,
        position = this.position,
        pos = grammaticalComponent.pos,
        morphologies = morphologies,
        contextMorphologies = listOf(), // TODO: set it
        syntacticRelation = syntacticRelation,
        coReferences = null, // TODO: set it
        semanticRelations = null) // TODO: set it
    else
      WordTrace(
        id = id,
        form = this.form,
        pos = grammaticalComponent.pos,
        morphologies = morphologies,
        contextMorphologies = listOf(), // TODO: set it
        syntacticRelation = syntacticRelation,
        coReferences = null, // TODO: set it
        semanticRelations = null)
  }

  /**
   * @param nextAvailableId the next id that can be assigned to a new token of the sentence (as component)
   * @param governorId the governor id
   * @param attachmentScore the attachment score
   * @param config the grammatical configuration of the token
   * @param morphologies the list of possible scored morphologies of the token
   *
   * @return a new composite token
   */
  private fun buildCompositeToken(nextAvailableId: Int,
                                  governorId: Int?,
                                  attachmentScore: Double,
                                  config: GrammaticalConfiguration,
                                  morphologies: List<ScoredMorphology>): MorphoSynToken.Composite {

    val isPrepArt: Boolean = config.isPrepArt()
    val isVerbEnclitic: Boolean = config.isVerbEnclitic()

    return MorphoSynToken.Composite(
      id = this.id,
      form = this.form,
      position = checkNotNull(this.position) { "Composite words must have a position." },
      components = config.components.mapIndexed { i, component ->
        this.buildSingleToken(
          id = nextAvailableId + i,
          governorId = when {
            i == 0 || isPrepArt -> governorId
            isVerbEnclitic -> nextAvailableId // the ID of the first component
            else -> null
          },
          attachmentScore = attachmentScore,
          grammaticalComponent = component,
          morphologies = morphologies.map { ScoredSingleMorphology(value = it.components[i], score = it.score) }
        ) as Word
      }
    )
  }

  /**
   * @return true if this configuration defines a composite PREP + ART, otherwise false
   */
  private fun GrammaticalConfiguration.isPrepArt(): Boolean =
    this.components.size == 2 &&
      this.components[0].pos.isSubTypeOf(POS.Prep) &&
      this.components[1].pos.isSubTypeOf(POS.Art)

  /**
   * @return true if this configuration defines a composite VERB + PRON, otherwise false
   */
  private fun GrammaticalConfiguration.isVerbEnclitic(): Boolean =
    this.components.size >= 2 &&
      this.components[0].pos.isSubTypeOf(POS.Verb) &&
      this.components.subList(1, this.components.size).all { it.pos.isSubTypeOf(POS.Pron) }

  /**
   * @param pos a POS type
   *
   * @return true if the type of this POS tag is a subtype of the given POS, otherwise false
   */
  private fun POSTag?.isSubTypeOf(pos: POS): Boolean = this is POSTag.Base && this.type.isComposedBy(pos)
}
