/* Copyright 2017-present The KotlinNLP Authors. All Rights Reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ------------------------------------------------------------------*/

package evaluation

import buildSentencePreproessor
import com.kotlinnlp.neuralparser.NeuralParser
import com.kotlinnlp.neuralparser.NeuralParserFactory
import com.kotlinnlp.neuralparser.NeuralParserModel
import com.kotlinnlp.neuralparser.helpers.Validator
import com.kotlinnlp.neuralparser.parsers.transitionbased.models.GenericTransitionBasedParser
import com.kotlinnlp.neuralparser.utils.Timer
import com.kotlinnlp.neuralparser.utils.loadSentences
import com.kotlinnlp.syntaxdecoder.BeamDecoder
import com.xenomachina.argparser.mainBody
import java.io.File
import java.io.FileInputStream

/**
 * Evaluate the model of a generic [NeuralParser].
 *
 * Launch with the '-h' option for help about the command line arguments.
 */
fun main(args: Array<String>) = mainBody {

  val parsedArgs = CommandLineArguments(args)

  require(parsedArgs.beamSize > 0 && parsedArgs.threads > 0)

  println("Loading model from '${parsedArgs.modelPath}'.")

  val parser: NeuralParser<*> = NeuralParserFactory(
    model = NeuralParserModel.load(FileInputStream(File(parsedArgs.modelPath))),
    beamSize = parsedArgs.beamSize,
    maxParallelThreads = parsedArgs.threads)

  val validator = Validator(
    neuralParser = parser,
    sentences = loadSentences(
      type = "validation",
      filePath = parsedArgs.validationSetPath,
      maxSentences = null,
      skipNonProjective = false),
    sentencePreprocessor = buildSentencePreproessor(
      morphoDictionaryPath = parsedArgs.morphoDictionaryPath,
      language = parser.model.language))

  println("\nBeam size = ${parsedArgs.beamSize}, MaxParallelThreads = ${parsedArgs.threads}\n")

  val timer = Timer()
  val evaluation = validator.evaluate()

  println("\n$evaluation")
  println("\nElapsed time: ${timer.formatElapsedTime()}")

  if (parser is GenericTransitionBasedParser && parsedArgs.beamSize > 1) {
    (parser.syntaxDecoder as BeamDecoder).close()
  }
}
