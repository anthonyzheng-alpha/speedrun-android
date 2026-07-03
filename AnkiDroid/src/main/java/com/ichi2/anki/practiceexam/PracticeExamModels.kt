// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import androidx.annotation.StringRes
import com.ichi2.anki.R

/**
 * The four MCAT sections a practice exam can cover. [key] must match the value
 * stored in the `topic` field of each question in `assets/practice_exam/questions.json`.
 */
enum class ExamTopic(
    val key: String,
    @StringRes val labelRes: Int,
) {
    BIOLOGY_BIOCHEMISTRY("biology_biochemistry", R.string.practice_exam_topic_biology),
    CHEMISTRY_PHYSICS("chemistry_physics", R.string.practice_exam_topic_chemistry),
    PSYCHOLOGY_SOCIOLOGY("psychology_sociology", R.string.practice_exam_topic_psychology),
    CARS("cars", R.string.practice_exam_topic_cars),
    ;

    companion object {
        fun fromKey(key: String): ExamTopic? = entries.firstOrNull { it.key == key }
    }
}

/** A single multiple-choice question loaded from the hardcoded question bank. */
data class PracticeQuestion(
    val id: String,
    val topic: ExamTopic,
    val passage: String?,
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val explanation: String?,
)

/** The full bank of questions parsed from the assets file. */
data class QuestionBank(
    val questions: List<PracticeQuestion>,
) {
    /** How many questions are available for the given set of enabled topics. */
    fun countAvailable(enabledTopics: Set<ExamTopic>): Int = questions.count { it.topic in enabledTopics }
}

/** A question in a live exam, together with the answer the user has selected (if any). */
data class ExamItem(
    val question: PracticeQuestion,
    var selectedIndex: Int? = null,
) {
    val isCorrect: Boolean
        get() = selectedIndex == question.answerIndex
}

/** Which screen of the practice exam flow is currently shown. */
enum class ExamPhase {
    CONFIG,
    LOADING,
    IN_PROGRESS,
    RESULTS,
}
