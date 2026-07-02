// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import anki.stats.CardMemoryEstimate
import anki.stats.CardStatsResponse
import com.ichi2.anki.R
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shows the "memory model" estimate for a card: the estimated chance the user recalls the fact,
 * with a plausible range, a confidence percentage, the last-updated date and a short justification.
 *
 * When there isn't enough review history to state a number, only an honest "not enough data"
 * message is shown, mirroring the desktop card-info behaviour.
 */
fun Context.showMemoryModelDialog(stats: CardStatsResponse) {
    val message =
        if (stats.hasMemoryEstimate()) {
            buildMemoryModelMessage(this, stats.memoryEstimate)
        } else {
            getString(R.string.memory_model_insufficient)
        }
    AlertDialog.Builder(this).show {
        title(R.string.memory_model_title)
        message(text = message)
        positiveButton(R.string.dialog_ok)
    }
}

/**
 * Formats a [CardMemoryEstimate] into a label/value block for display.
 *
 * If [CardMemoryEstimate.getHasEnoughData] is false, only the justification (or a localized
 * fallback) is returned, since the numeric fields are not meaningful in that case.
 */
fun buildMemoryModelMessage(
    context: Context,
    estimate: CardMemoryEstimate,
): CharSequence {
    if (!estimate.hasEnoughData) {
        return estimate.justification.ifBlank { context.getString(R.string.memory_model_insufficient) }
    }

    val rows =
        listOf(
            context.getString(R.string.memory_model_recall_chance) to "${estimate.score.roundToInt()}%",
            // en-dash between the range bounds, matching the desktop UI
            context.getString(R.string.memory_model_range) to
                "${estimate.rangeMin.roundToInt()}%\u2013${estimate.rangeMax.roundToInt()}%",
            context.getString(R.string.memory_model_confidence) to "${estimate.confidence.roundToInt()}%",
            context.getString(R.string.memory_model_last_updated) to formatLocalDate(estimate.lastUpdated),
        )

    return buildString {
        for ((label, value) in rows) {
            append(label).append(": ").append(value).append('\n')
        }
        if (estimate.justification.isNotBlank()) {
            append('\n')
            append(context.getString(R.string.memory_model_justification)).append(": ")
            append(estimate.justification)
        }
    }
}

/** Formats a Unix timestamp (seconds) as a local `YYYY-MM-DD` date. */
private fun formatLocalDate(unixSeconds: Long): String = DATE_FORMAT.format(Date(unixSeconds * 1000))

private val DATE_FORMAT: SimpleDateFormat
    get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
