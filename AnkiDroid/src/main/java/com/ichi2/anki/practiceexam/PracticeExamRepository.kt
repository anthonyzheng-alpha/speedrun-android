// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import android.content.Context
import org.json.JSONObject
import timber.log.Timber

/**
 * Loads the MCAT question banks from assets and assembles randomised practice
 * exams. Problems are never generated on-device: the hardcoded bank lives at
 * `assets/practice_exam/questions.json` (hand-editable) and the AI-generated,
 * pre-vetted bank at `assets/practice_exam/generated-questions.json` (produced
 * offline by tools/generate_practice_problems.mjs).
 */
object PracticeExamRepository {
    private const val ASSET_PATH = "practice_exam/questions.json"
    private const val GENERATED_ASSET_PATH = "practice_exam/generated-questions.json"

    /** Reads and parses the hardcoded question bank. Malformed entries are skipped. */
    fun loadBank(context: Context): QuestionBank = readBank(context, ASSET_PATH)

    /**
     * Reads the AI-generated bank. Returns an empty bank if the asset is missing
     * or empty (e.g. before the generator has been run).
     */
    fun loadGeneratedBank(context: Context): QuestionBank = readBank(context, GENERATED_ASSET_PATH)

    private fun readBank(
        context: Context,
        assetPath: String,
    ): QuestionBank {
        val raw =
            try {
                context.assets
                    .open(assetPath)
                    .bufferedReader()
                    .use { it.readText() }
            } catch (e: Exception) {
                Timber.w(e, "Could not read question bank asset: %s", assetPath)
                return QuestionBank(emptyList())
            }
        return parseBank(raw)
    }

    /** Parses a `{ questions: [...] }` bank string. Malformed entries are skipped. */
    fun parseBank(raw: String): QuestionBank {
        val root = JSONObject(raw)
        val array = root.optJSONArray("questions") ?: return QuestionBank(emptyList())

        val questions = mutableListOf<PracticeQuestion>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val topic = ExamTopic.fromKey(obj.optString("topic"))
            if (topic == null) {
                Timber.w("Skipping practice question with unknown topic: %s", obj.optString("topic"))
                continue
            }
            val choicesArray = obj.optJSONArray("choices") ?: continue
            val choices = (0 until choicesArray.length()).map { choicesArray.getString(it) }
            if (choices.isEmpty()) continue

            questions.add(
                PracticeQuestion(
                    id = obj.optString("id"),
                    topic = topic,
                    passage = obj.optString("passage").takeIf { it.isNotBlank() },
                    question = obj.optString("question"),
                    choices = choices,
                    answerIndex = obj.optInt("answerIndex", 0).coerceIn(0, choices.size - 1),
                    explanation = obj.optString("explanation").takeIf { it.isNotBlank() },
                ),
            )
        }
        return QuestionBank(questions)
    }

    /**
     * Picks up to [count] random questions restricted to [enabledTopics]. If fewer
     * questions are available than requested, all available questions are used.
     */
    fun buildExam(
        bank: QuestionBank,
        count: Int,
        enabledTopics: Set<ExamTopic>,
    ): List<ExamItem> {
        val pool = bank.questions.filter { it.topic in enabledTopics }
        return pool
            .shuffled()
            .take(count.coerceAtLeast(0))
            .map { ExamItem(it) }
    }
}
