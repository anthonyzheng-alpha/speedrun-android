// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.app.DatePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import anki.stats.ExamCoverageResponse
import anki.stats.ExamMetricsResponse
import anki.stats.MetricEstimate
import com.ichi2.anki.R
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Shows the global performance (chance of answering a new exam-style question
 * correctly) and readiness (projected MCAT score) metrics, overall and per
 * MCAT section, each with its range/confidence honesty envelope, plus how much
 * of the exam has been studied so far (coverage) and the target exam date the
 * metrics are projected to.
 */
fun Context.showExamMetricsDialog(
    metrics: ExamMetricsResponse,
    coverage: ExamCoverageResponse? = null,
    examDateSecs: Long? = null,
    onExamDatePicked: ((Long) -> Unit)? = null,
) {
    AlertDialog.Builder(this).show {
        title(R.string.exam_metrics_title)
        message(
            text = buildExamMetricsMessage(this@showExamMetricsDialog, metrics, coverage, examDateSecs),
        )
        positiveButton(R.string.dialog_ok)
        if (onExamDatePicked != null) {
            neutralButton(R.string.exam_metrics_set_exam_date) {
                this@showExamMetricsDialog.showExamDatePicker(examDateSecs, onExamDatePicked)
            }
        }
    }
}

/** Opens a date picker (seeded with the current exam date) and reports the
 * chosen day back as a unix timestamp in seconds at local midnight. Past dates
 * cannot be selected. */
private fun Context.showExamDatePicker(
    currentSecs: Long?,
    onPicked: (Long) -> Unit,
) {
    val today =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    val seed = Calendar.getInstance()
    if (currentSecs != null && currentSecs > 0) {
        seed.timeInMillis = currentSecs * 1000L
    }
    if (seed.before(today)) {
        seed.timeInMillis = today.timeInMillis
    }
    DatePickerDialog(
        this,
        { _, year, month, dayOfMonth ->
            val picked =
                Calendar.getInstance().apply {
                    clear()
                    set(year, month, dayOfMonth)
                }
            if (picked.before(today)) {
                return@DatePickerDialog
            }
            onPicked(picked.timeInMillis / 1000L)
        },
        seed.get(Calendar.YEAR),
        seed.get(Calendar.MONTH),
        seed.get(Calendar.DAY_OF_MONTH),
    ).apply {
        datePicker.minDate = today.timeInMillis
    }.show()
}

/**
 * Builds the body for the metrics dialog: a short explanation of what each
 * metric means, the exam-coverage figure, and the performance/readiness
 * numbers. When there isn't enough data to predict, the explanation and
 * coverage are still shown alongside the honest "not enough data" note.
 */
fun buildExamMetricsMessage(
    context: Context,
    metrics: ExamMetricsResponse,
    coverage: ExamCoverageResponse? = null,
    examDateSecs: Long? = null,
): CharSequence =
    buildString {
        // Always explain what the metrics mean.
        append(context.getString(R.string.exam_metrics_performance_explanation))
        append("\n\n")
        append(context.getString(R.string.exam_metrics_readiness_explanation))

        // Target exam date the metrics are projected to.
        append("\n\n")
        if (examDateSecs != null && examDateSecs > 0) {
            val dateStr = DateFormat.getDateFormat(context).format(Date(examDateSecs * 1000L))
            append(context.getString(R.string.exam_metrics_exam_date, dateStr))
        } else {
            append(context.getString(R.string.exam_metrics_exam_date_unset))
        }

        // Exam coverage, when available.
        if (coverage != null && coverage.topicsTotal > 0) {
            append("\n\n")
            append(
                context.getString(
                    R.string.exam_metrics_coverage,
                    coverage.overallPercent.roundToInt(),
                ),
            )
        }

        val performanceOverall = metrics.performanceOverall
        append("\n\n")
        if (!performanceOverall.hasEnoughData) {
            append(
                performanceOverall.justification.ifBlank {
                    context.getString(R.string.exam_metrics_insufficient)
                },
            )
            return@buildString
        }

        val overallLabel = context.getString(R.string.exam_metrics_overall)

        // Performance breakdown: overall, then each eligible section.
        append('\n')
        append(context.getString(R.string.exam_metrics_performance)).append('\n')
        appendPerformanceLine(context, overallLabel, performanceOverall)
        for (performanceSection in metrics.performanceSectionsList) {
            if (!performanceSection.estimate.hasEnoughData) continue
            appendPerformanceLine(context, performanceSection.section, performanceSection.estimate)
        }

        // Readiness (projected MCAT) breakdown: overall, then each eligible section.
        append('\n')
        append(context.getString(R.string.exam_metrics_readiness)).append('\n')
        appendReadinessLine(overallLabel, metrics.readinessOverall)
        for (readinessSection in metrics.readinessSectionsList) {
            if (!readinessSection.estimate.hasEnoughData) continue
            appendReadinessLine(readinessSection.section, readinessSection.estimate)
        }

        if (performanceOverall.justification.isNotBlank()) {
            append('\n')
            append(performanceOverall.justification)
        }
    }

/** Appends one "label: NN% (lo%-hi%, NN% confidence)" performance line. */
private fun StringBuilder.appendPerformanceLine(
    context: Context,
    label: String,
    estimate: MetricEstimate,
) {
    append("  ")
        .append(label)
        .append(": ")
        .append("${estimate.score.roundToInt()}%")
        .append(
            " (${estimate.rangeMin.roundToInt()}%\u2013${estimate.rangeMax.roundToInt()}%, ",
        ).append(context.getString(R.string.exam_metrics_confidence_suffix, estimate.confidence.roundToInt()))
        .append(")\n")
}

/** Appends one "label: NNN (lo-hi)" projected-MCAT line. */
private fun StringBuilder.appendReadinessLine(
    label: String,
    estimate: MetricEstimate,
) {
    append("  ")
        .append(label)
        .append(": ")
        .append("${estimate.score.roundToInt()}")
        .append(" (${estimate.rangeMin.roundToInt()}\u2013${estimate.rangeMax.roundToInt()})\n")
}
