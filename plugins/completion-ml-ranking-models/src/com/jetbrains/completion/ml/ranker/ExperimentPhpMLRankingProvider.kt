package com.jetbrains.completion.ml.ranker

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.ModelMetadata
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.internal.ml.completion.JarCompletionModelProvider
import com.intellij.lang.Language
import com.jetbrains.completion.ranker.model.php.exp.ExperimentalMLGlassBox

class ExperimentPhpMLRankingProvider: JarCompletionModelProvider(CompletionRankingModelsBundle.message("ml.completion.experiment.model.php"), "php_features_exp"), ExperimentModelProvider {

  override fun createModel(metadata: ModelMetadata): DecisionFunction {
    return object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray?): Double = ExperimentalMLGlassBox.makePredict(features)
    }
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("PHP", ignoreCase = true) == 0

  override fun experimentGroupNumber(): Int = 13
}