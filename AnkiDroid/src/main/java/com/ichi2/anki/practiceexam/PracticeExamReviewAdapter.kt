// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemPracticeExamReviewBinding

/** Renders the per-question review shown on the results screen. */
class PracticeExamReviewAdapter(
    private val items: List<ExamItem>,
) : RecyclerView.Adapter<PracticeExamReviewAdapter.ReviewViewHolder>() {
    class ReviewViewHolder(
        val binding: ItemPracticeExamReviewBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ReviewViewHolder {
        val binding =
            ItemPracticeExamReviewBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ReviewViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: ReviewViewHolder,
        position: Int,
    ) {
        val context = holder.binding.root.context
        val item = items[position]
        val question = item.question

        holder.binding.reviewTopic.text = context.getString(question.topic.labelRes)

        holder.binding.reviewPassage.apply {
            if (question.passage.isNullOrBlank()) {
                visibility = ViewGroup.GONE
            } else {
                visibility = ViewGroup.VISIBLE
                text = question.passage
            }
        }

        holder.binding.reviewQuestion.text = question.question

        val container = holder.binding.reviewChoices
        container.removeAllViews()
        question.choices.forEachIndexed { index, choice ->
            val letter = ('A' + index)
            val row = TextView(context)
            val builder = StringBuilder("$letter. $choice")
            when {
                index == question.answerIndex -> {
                    builder.append("  ").append(context.getString(R.string.practice_exam_correct_marker))
                    row.setTextColor(CORRECT_COLOR)
                }
                index == item.selectedIndex -> {
                    builder.append("  ").append(context.getString(R.string.practice_exam_your_answer))
                    row.setTextColor(INCORRECT_COLOR)
                }
            }
            row.text = builder.toString()
            container.addView(row)
        }

        holder.binding.reviewExplanation.apply {
            val parts = mutableListOf<String>()
            if (item.selectedIndex == null) {
                parts.add(context.getString(R.string.practice_exam_unanswered))
            }
            if (!question.explanation.isNullOrBlank()) {
                parts.add(question.explanation)
            }
            if (parts.isEmpty()) {
                visibility = ViewGroup.GONE
            } else {
                visibility = ViewGroup.VISIBLE
                text = parts.joinToString("\n")
            }
        }
    }

    companion object {
        private val CORRECT_COLOR = Color.parseColor("#2E7D32")
        private val INCORRECT_COLOR = Color.parseColor("#C62828")
    }
}
