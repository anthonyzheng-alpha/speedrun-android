// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.ActivityPracticeExamBinding

/**
 * Hosts the MCAT practice exam flow: configure the exam, answer the questions
 * one by one, then review a score breakdown. The three phases share this single
 * activity and are toggled by view visibility, backed by [PracticeExamViewModel].
 */
class PracticeExamActivity : AnkiActivity() {
    private val viewModel: PracticeExamViewModel by viewModels()
    private lateinit var binding: ActivityPracticeExamBinding

    private val topicSwitches by lazy {
        listOf(
            binding.switchBiology to ExamTopic.BIOLOGY_BIOCHEMISTRY,
            binding.switchChemistry to ExamTopic.CHEMISTRY_PHYSICS,
            binding.switchPsychology to ExamTopic.PSYCHOLOGY_SOCIOLOGY,
            binding.switchCars to ExamTopic.CARS,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeExamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableToolbar()
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.practice_exam_title)
        }

        binding.reviewList.layoutManager = LinearLayoutManager(this)

        setupConfigControls()
        setupExamControls()
        render()
    }

    private fun setupConfigControls() {
        binding.countInput.setText(viewModel.requestedCount.toString())
        binding.countInput.doAfterTextChanged { text ->
            val parsed = text?.toString()?.toIntOrNull()
            if (parsed != null) {
                viewModel.requestedCount = parsed.coerceAtLeast(1)
                updateAvailability()
            }
        }

        for ((switch, topic) in topicSwitches) {
            switch.isChecked = topic in viewModel.enabledTopics
            switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.enabledTopics.add(topic)
                } else {
                    viewModel.enabledTopics.remove(topic)
                }
                updateAvailability()
            }
        }

        binding.startButton.setOnClickListener {
            val parsed = binding.countInput.text?.toString()?.toIntOrNull()
            if (parsed != null) {
                viewModel.requestedCount = parsed.coerceAtLeast(1)
            }
            if (viewModel.startExam()) {
                render()
            } else {
                showThemedToast(this, getString(R.string.practice_exam_no_questions), false)
            }
        }
    }

    private fun setupExamControls() {
        binding.backButton.setOnClickListener {
            viewModel.goToPrevious()
            render()
        }
        binding.nextButton.setOnClickListener {
            viewModel.advance()
            render()
        }
        binding.retakeButton.setOnClickListener {
            viewModel.retake()
            render()
        }
    }

    private fun updateAvailability() {
        val available = viewModel.availableCount()
        val effective = viewModel.requestedCount.coerceAtMost(available)
        binding.availableLabel.text =
            getString(R.string.practice_exam_available, available, effective)
        binding.startButton.isEnabled = available > 0
    }

    private fun render() {
        binding.configView.visibility = visibleIf(viewModel.phase == ExamPhase.CONFIG)
        binding.questionView.visibility = visibleIf(viewModel.phase == ExamPhase.IN_PROGRESS)
        binding.resultsView.visibility = visibleIf(viewModel.phase == ExamPhase.RESULTS)

        when (viewModel.phase) {
            ExamPhase.CONFIG -> updateAvailability()
            ExamPhase.IN_PROGRESS -> renderQuestion()
            ExamPhase.RESULTS -> renderResults()
        }
    }

    private fun renderQuestion() {
        val item = viewModel.currentItem
        val question = item.question

        binding.progressLabel.text =
            getString(
                R.string.practice_exam_progress,
                viewModel.currentIndex + 1,
                viewModel.items.size,
            ) + "  \u00b7  " + getString(question.topic.labelRes)

        binding.passageText.apply {
            if (question.passage.isNullOrBlank()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = question.passage
            }
        }

        binding.questionStem.text = question.question

        val group = binding.choicesGroup
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        question.choices.forEachIndexed { index, choice ->
            val button =
                RadioButton(this).apply {
                    id = View.generateViewId()
                    text = "${'A' + index}. $choice"
                    layoutParams =
                        RadioGroup.LayoutParams(
                            RadioGroup.LayoutParams.MATCH_PARENT,
                            RadioGroup.LayoutParams.WRAP_CONTENT,
                        )
                }
            group.addView(button)
        }
        item.selectedIndex?.let { selected ->
            (group.getChildAt(selected) as? RadioButton)?.isChecked = true
        }
        group.setOnCheckedChangeListener { radioGroup, checkedId ->
            if (checkedId != -1) {
                val index = radioGroup.indexOfChild(radioGroup.findViewById(checkedId))
                if (index >= 0) {
                    viewModel.selectAnswer(index)
                }
            }
        }

        binding.backButton.isEnabled = viewModel.currentIndex > 0
        binding.nextButton.setText(
            if (viewModel.isLastQuestion) R.string.practice_exam_finish else R.string.practice_exam_next,
        )
    }

    private fun renderResults() {
        val correct = viewModel.correctCount()
        val total = viewModel.items.size
        val percent = if (total > 0) Math.round(correct * 100.0 / total).toInt() else 0
        binding.scoreText.text = getString(R.string.practice_exam_score, correct, total, percent)

        binding.breakdownText.text =
            viewModel
                .scoreByTopic()
                .entries
                .joinToString("\n") { (topic, score) ->
                    getString(
                        R.string.practice_exam_topic_score,
                        getString(topic.labelRes),
                        score.first,
                        score.second,
                    )
                }

        binding.reviewList.adapter = PracticeExamReviewAdapter(viewModel.items)
    }

    private fun visibleIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE
}
