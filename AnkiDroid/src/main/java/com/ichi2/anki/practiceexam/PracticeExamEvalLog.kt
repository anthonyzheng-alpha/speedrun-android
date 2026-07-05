// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends the accuracy of freshly AI-generated practice problems to a plain-text
 * log. "Accuracy" is the share of structurally-valid candidates that the blind
 * independent-solve verification pass re-answered correctly, tracked per MCAT
 * topic and overall, per run plus a cumulative running total.
 *
 * The format mirrors the desktop app's `practice_exam_eval.txt`; each run adds a
 * run line, a cumulative line, and a machine-readable `#STATE` line holding the
 * running totals so the next run can extend them without a sidecar file.
 */
object PracticeExamEvalLog {
    const val FILE_NAME = "practice_exam_eval.txt"
    private const val STATE_PREFIX = "#STATE "

    /**
     * Record one generation run. [candidates] are the structurally-valid problems
     * fed to verification; [verified] are the ones that passed. Never throws.
     */
    fun record(
        logDir: File?,
        candidates: List<PracticeQuestion>,
        verified: List<PracticeQuestion>,
    ) {
        if (logDir == null || candidates.isEmpty()) return
        try {
            // run tallies: topic -> [verified, total]
            val run = HashMap<String, IntArray>()
            for (c in candidates) {
                run.getOrPut(c.topic.key) { intArrayOf(0, 0) }[1] += 1
            }
            for (v in verified) {
                run.getOrPut(v.topic.key) { intArrayOf(0, 0) }[0] += 1
            }

            val file = File(logDir, FILE_NAME)
            val prior = readLastState(file)
            for ((topic, tally) in run) {
                val cum = prior.getOrPut(topic) { intArrayOf(0, 0) }
                cum[0] += tally[0]
                cum[1] += tally[1]
            }

            val runTotal = run.values.sumOf { it[1] }
            val runVerified = run.values.sumOf { it[0] }
            val cumTotal = prior.values.sumOf { it[1] }
            val cumVerified = prior.values.sumOf { it[0] }

            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val block =
                buildString {
                    append("[$ts] run: candidates=$runTotal verified=$runVerified ")
                    append("accuracy=${pct(runVerified, runTotal)} | ${fmtTopics(run)}\n")
                    append("[$ts] cumulative: candidates=$cumTotal verified=$cumVerified ")
                    append("accuracy=${pct(cumVerified, cumTotal)} | ${fmtTopics(prior)}\n")
                    append(STATE_PREFIX + stateJson(prior) + "\n")
                }
            file.appendText(block)
        } catch (e: Exception) {
            Timber.w(e, "practice-exam eval logging failed")
        }
    }

    /** Cumulative totals from the last `#STATE` line, or an empty map. */
    private fun readLastState(file: File): HashMap<String, IntArray> {
        val out = HashMap<String, IntArray>()
        if (!file.exists()) return out
        try {
            var last: String? = null
            file.forEachLine { line ->
                if (line.startsWith(STATE_PREFIX)) last = line
            }
            val stateLine = last ?: return out
            val topics =
                JSONObject(stateLine.substring(STATE_PREFIX.length)).optJSONObject("topics")
                    ?: return out
            for (key in topics.keys()) {
                val arr = topics.optJSONArray(key) ?: continue
                if (arr.length() == 2) {
                    out[key] = intArrayOf(arr.optInt(0), arr.optInt(1))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "could not parse prior eval state")
        }
        return out
    }

    private fun stateJson(topics: Map<String, IntArray>): String {
        val topicsObj = JSONObject()
        for ((k, v) in topics) {
            topicsObj.put(
                k,
                org.json
                    .JSONArray()
                    .put(v[0])
                    .put(v[1]),
            )
        }
        return JSONObject()
            .put("candidates", topics.values.sumOf { it[1] })
            .put("verified", topics.values.sumOf { it[0] })
            .put("topics", topicsObj)
            .toString()
    }

    private fun fmtTopics(topics: Map<String, IntArray>): String =
        topics.keys.sorted().joinToString(", ") { key ->
            val (verified, total) = topics.getValue(key).let { it[0] to it[1] }
            "$key $verified/$total (${pct(verified, total)})"
        }

    private fun pct(
        verified: Int,
        total: Int,
    ): String {
        val value = if (total > 0) 100.0 * verified / total else 0.0
        return String.format(Locale.US, "%.1f%%", value)
    }
}
