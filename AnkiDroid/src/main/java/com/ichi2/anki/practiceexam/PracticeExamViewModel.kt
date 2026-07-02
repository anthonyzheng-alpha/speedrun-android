// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import android.app.Application
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
    private val bank: QuestionBank by lazy { PracticeExamRepository.loadBank(getApplication()) }

    var phase: ExamPhase = ExamPhase.CONFIG
        private set

    /** Config screen state, persisted so it survives rotation. */
    var requestedCount: Int = DEFAULT_QUESTION_COUNT
    val enabledTopics: MutableSet<ExamTopic> = ExamTopic.entries.toMutableSet()

    var items: List<ExamItem> = emptyList()
        private set
    var currentIndex: Int = 0
        private set

    fun availableCount(): Int = bank.countAvailable(enabledTopics)

    val currentItem: ExamItem
        get() = items[currentIndex]

    val isLastQuestion: Boolean
        get() = currentIndex == items.size - 1

    /** Builds a fresh exam and moves to the in-progress phase. Returns false if empty. */
    fun startExam(): Boolean {
        val exam = PracticeExamRepository.buildExam(bank, requestedCount, enabledTopics)
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
    }
}
