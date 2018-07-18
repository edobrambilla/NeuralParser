/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package com.kotlinnlp.neuralparser.helpers

import com.kotlinnlp.dependencytree.DependencyTree
import com.kotlinnlp.dependencytree.Deprel
import com.kotlinnlp.dependencytree.POSTag
import com.kotlinnlp.linguisticdescription.sentence.token.properties.Position
import com.kotlinnlp.neuralparser.language.ParsingSentence
import com.kotlinnlp.neuralparser.language.ParsingToken
import com.kotlinnlp.conllio.Sentence as CoNLLSentence
import com.kotlinnlp.conllio.Token as CoNLLToken

/**
 * Transform this CoNLL sentence into a [ParsingSentence].
 *
 * @return a parsing sentence
 */
fun CoNLLSentence.toParsingSentence(): ParsingSentence {

  var end = -2

  return ParsingSentence(tokens = this.tokens.map {

    val start = end + 2 // each couple of consecutive words is separated by a spacing char
    end = start + it.form.length - 1

    ParsingToken(
      id = it.id,
      form = it.form,
      position = Position(index = it.id - 1, start = start, end = end),
      posTag = it.pos)
  })
}

/**
 * Return the [DependencyTree] of this sentence.
 *
 * @return the dependency tree
 */
internal fun CoNLLSentence.getDependencyTree(): DependencyTree {

  val dependencyTree = DependencyTree(size = this.tokens.size)

  this.tokens.forEach { token -> dependencyTree.addArc(token) }

  return dependencyTree
}

/**
 * Add the arc defined by the id, head and deprel of a given [token].
 *
 * @param token a token
 */
private fun DependencyTree.addArc(token: CoNLLToken) {

  val id = token.id - 1
  val head = if (token.head!! == 0) null else token.head!! - 1

  val deprel = Deprel(
    label = token.deprel,
    direction = when {
      head == null -> Deprel.Position.ROOT
      head > id -> Deprel.Position.LEFT
      else -> Deprel.Position.RIGHT
    })

  if (head != null) {
    this.setArc(dependent = id, governor = head, deprel = deprel, posTag = POSTag(label = token.pos))
  } else {
    this.setDeprel(dependent = id, deprel = deprel)
    this.setPosTag(dependent = id, posTag = POSTag(label = token.pos))
  }
}