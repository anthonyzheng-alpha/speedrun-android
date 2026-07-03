// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import anki.stats.RecordPracticeExamRequest
import com.ichi2.anki.CollectionManager.withCol
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Holds the state of a practice exam across configuration changes: the chosen
 * settings, the current set of questions, and which question is being shown.
 */
class PracticeExamViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val hardcodedBank: QuestionBank by lazy { PracticeExamRepository.loadBank(getApplication()) }
    private val generatedBank: QuestionBank by lazy { PracticeExamRepository.loadGeneratedBank(getApplication()) }

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var phase: ExamPhase = ExamPhase.CONFIG
        private set

    /** Config screen state, persisted so it survives rotation. */
    var requestedCount: Int = DEFAULT_QUESTION_COUNT
    val enabledTopics: MutableSet<ExamTopic> = ExamTopic.entries.toMutableSet()

    /** Whether AI-generated problems are included in the pool. Persisted. */
    var useGenerated: Boolean = prefs.getBoolean(PREF_USE_GENERATED, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(PREF_USE_GENERATED, value).apply()
        }

    /** True once at least one AI-generated problem is bundled. */
    val hasGenerated: Boolean
        get() = generatedBank.questions.isNotEmpty()

    var items: List<ExamItem> = emptyList()
        private set
    var currentIndex: Int = 0
        private set

    /** Hardcoded questions, plus generated ones when the toggle is on. */
    private fun activeBank(): QuestionBank {
        val questions =
            if (useGenerated) {
                hardcodedBank.questions + generatedBank.questions
            } else {
                hardcodedBank.questions
            }
        return QuestionBank(questions)
    }

    fun availableCount(): Int = activeBank().countAvailable(enabledTopics)

    val currentItem: ExamItem
        get() = items[currentIndex]

    val isLastQuestion: Boolean
        get() = currentIndex == items.size - 1

    /** Builds a fresh exam and moves to the in-progress phase. Returns false if empty. */
    fun startExam(): Boolean {
        val exam = PracticeExamRepository.buildExam(activeBank(), requestedCount, enabledTopics)
        if (exam.isEmpty()) return false
        items = exam
        currentIndex = 0
        phase = ExamPhase.IN_PROGRESS
        return true
    }

    fun selectAnswer(choiceIndex: Int) {
        items[currentIndex].selectedIndex = choiceIndex
    }

    fun goToPrevious() {
        if (currentIndex > 0) currentIndex -= 1
    }

    /** Advances to the next question, or moves to results if on the last one. */
    fun advance() {
        if (currentIndex < items.size - 1) {
            currentIndex += 1
        } else {
            phase = ExamPhase.RESULTS
            persistResults()
        }
    }

    /**
     * Save the completed exam's per-topic results so they feed the
     * performance/readiness metrics. Failures are non-fatal.
     */
    private fun persistResults() {
        val results =
            scoreByTopic().map { (topic, tally) ->
                RecordPracticeExamRequest.TopicResult
                    .newBuilder()
                    .setTopic(topic.key)
                    .setCorrect(tally.first)
                    .setTotal(tally.second)
                    .build()
            }
        if (results.isEmpty()) return
        viewModelScope.launch {
            try {
                withCol { recordPracticeExam(results) }
            } catch (e: Exception) {
                Timber.w(e, "failed to record practice exam")
            }
        }
    }

    fun retake() {
        items = emptyList()
        currentIndex = 0
        phase = ExamPhase.CONFIG
    }

    fun correctCount(): Int = items.count { it.isCorrect }

    /** Correct/total counts per topic, only for topics that appeared in the exam. */
    fun scoreByTopic(): Map<ExamTopic, Pair<Int, Int>> {
        val result = linkedMapOf<ExamTopic, Pair<Int, Int>>()
        for (topic in ExamTopic.entries) {
            val forTopic = items.filter { it.question.topic == topic }
            if (forTopic.isEmpty()) continue
            result[topic] = Pair(forTopic.count { it.isCorrect }, forTopic.size)
        }
        return result
    }

    companion object {
        const val DEFAULT_QUESTION_COUNT = 5
        private const val PREFS_NAME = "practice_exam"
        private const val PREF_USE_GENERATED = "use_generated"
    }
}
