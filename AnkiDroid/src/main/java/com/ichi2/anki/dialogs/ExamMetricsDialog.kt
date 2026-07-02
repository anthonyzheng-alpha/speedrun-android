// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import anki.stats.ExamMetricsResponse
import anki.stats.MetricEstimate
import com.ichi2.anki.R
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlin.math.roundToInt

/**
 * Shows the global performance (chance of answering a new exam-style question
 * correctly) and readiness (projected MCAT score) metrics, overall and per
 * MCAT section, each with its range/confidence honesty envelope.
 */
fun Context.showExamMetricsDialog(metrics: ExamMetricsResponse) {
    AlertDialog.Builder(this).show {
        title(R.string.exam_metrics_title)
        message(text = buildExamMetricsMessage(this@showExamMetricsDialog, metrics))
        positiveButton(R.string.dialog_ok)
    }
}

/**
 * Builds the multi-section body for the metrics dialog. When there isn't enough
 * data, only the honest "not enough data" justification is returned.
 */
fun buildExamMetricsMessage(
    context: Context,
    metrics: ExamMetricsResponse,
): CharSequence {
    val performanceOverall = metrics.performanceOverall
    if (!performanceOverall.hasEnoughData) {
        return performanceOverall.justification.ifBlank {
            context.getString(R.string.exam_metrics_insufficient)
        }
    }

    val readinessBySection =
        metrics.readinessSectionsList.associateBy({ it.section }, { it.estimate })

    return buildString {
        appendBlock(
            context,
            context.getString(R.string.exam_metrics_overall),
            performanceOverall,
            metrics.readinessOverall,
        )
        for (performanceSection in metrics.performanceSectionsList) {
            val readiness = readinessBySection[performanceSection.section] ?: continue
            if (!performanceSection.estimate.hasEnoughData) continue
            append('\n')
            appendBlock(context, performanceSection.section, performanceSection.estimate, readiness)
        }
        if (performanceOverall.justification.isNotBlank()) {
            append('\n')
            append(performanceOverall.justification)
        }
    }
}

/** Appends a "heading / performance / readiness" block for one section. */
private fun StringBuilder.appendBlock(
    context: Context,
    heading: String,
    performance: MetricEstimate,
    readiness: MetricEstimate,
) {
    append(heading).append('\n')
    append("  ")
        .append(context.getString(R.string.exam_metrics_performance))
        .append(": ")
        .append("${performance.score.roundToInt()}%")
        .append(
            " (${performance.rangeMin.roundToInt()}%\u2013${performance.rangeMax.roundToInt()}%, ",
        ).append(context.getString(R.string.exam_metrics_confidence_suffix, performance.confidence.roundToInt()))
        .append(")\n")
    append("  ")
        .append(context.getString(R.string.exam_metrics_readiness))
        .append(": ")
        .append("${readiness.score.roundToInt()}")
        .append(" (${readiness.rangeMin.roundToInt()}\u2013${readiness.rangeMax.roundToInt()})\n")
}
