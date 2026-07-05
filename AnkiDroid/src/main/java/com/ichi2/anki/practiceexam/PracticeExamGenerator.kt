// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.practiceexam

import com.ichi2.anki.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Generates MCAT practice questions in real time via the OpenAI API, then
 * verifies each with a blind independent-solve pass, keeping only the questions
 * the model can re-answer correctly. Runs entirely off the main thread.
 *
 * The API key is injected at build time via [BuildConfig.OPENAI_API_KEY]; when
 * it is blank, generation is skipped and callers fall back to the bundled banks.
 */
object PracticeExamGenerator {
    private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
    private const val MODEL = "gpt-4o"
    private const val MAX_QUESTIONS = 50

    /** Each MCAT section can be covered by several Jack Westin books. */
    private val TOPIC_BOOKS =
        mapOf(
            ExamTopic.BIOLOGY_BIOCHEMISTRY to listOf("biology", "biochemistry"),
            ExamTopic.CHEMISTRY_PHYSICS to listOf("general-chemistry", "organic-chemistry", "physics"),
            ExamTopic.PSYCHOLOGY_SOCIOLOGY to listOf("behavioral-sciences"),
            ExamTopic.CARS to listOf("cars"),
        )

    private val TOPIC_LABELS =
        mapOf(
            ExamTopic.BIOLOGY_BIOCHEMISTRY to "Biology & Biochemistry",
            ExamTopic.CHEMISTRY_PHYSICS to "Chemistry & Physics",
            ExamTopic.PSYCHOLOGY_SOCIOLOGY to "Psychology & Sociology",
            ExamTopic.CARS to "CARS",
        )

    private val TOPIC_ID_PREFIX =
        mapOf(
            ExamTopic.BIOLOGY_BIOCHEMISTRY to "live-bb",
            ExamTopic.CHEMISTRY_PHYSICS to "live-cp",
            ExamTopic.PSYCHOLOGY_SOCIOLOGY to "live-ps",
            ExamTopic.CARS to "live-cars",
        )

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** True when an OpenAI key was baked into the build. */
    val isConfigured: Boolean
        get() = BuildConfig.OPENAI_API_KEY.isNotBlank()

    /**
     * Generate and verify up to [count] questions across [topics]. Returns an
     * empty list on any failure so the caller can fall back to bundled banks.
     * When [logDir] is provided, the verify accuracy of the generated problems
     * is appended to a plain-text eval log there.
     */
    suspend fun generate(
        count: Int,
        topics: Set<ExamTopic>,
        logDir: File? = null,
    ): List<PracticeQuestion> =
        withContext(Dispatchers.IO) {
            val key = BuildConfig.OPENAI_API_KEY
            if (key.isBlank()) {
                Timber.w("OPENAI_API_KEY not configured; skipping live generation")
                return@withContext emptyList()
            }
            val enabled = topics.ifEmpty { ExamTopic.entries.toSet() }
            val wanted = count.coerceIn(1, MAX_QUESTIONS)
            val perTopic = ceil(wanted.toDouble() / enabled.size).toInt().coerceAtLeast(1)

            val candidates = mutableListOf<PracticeQuestion>()
            for (topic in enabled) {
                candidates += generateForTopic(key, topic, ceil(perTopic * 1.5).toInt())
            }

            val verified = verify(key, candidates)
            PracticeExamEvalLog.record(logDir, candidates, verified)
            val vetted = verified.shuffled()
            val counters = HashMap<ExamTopic, Int>()
            vetted.take(wanted).map { q ->
                val n = (counters[q.topic] ?: 0) + 1
                counters[q.topic] = n
                val prefix = TOPIC_ID_PREFIX[q.topic] ?: "live"
                q.copy(id = "$prefix-${n.toString().padStart(3, '0')}")
            }
        }

    private fun chatJson(
        key: String,
        messages: JSONArray,
        temperature: Double,
    ): JSONObject {
        val payload =
            JSONObject()
                .put("model", MODEL)
                .put("temperature", temperature)
                .put("response_format", JSONObject().put("type", "json_object"))
                .put("messages", messages)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request =
            Request
                .Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer $key")
                .post(body)
                .build()
        client.newCall(request).execute().use { resp ->
            val respBody = resp.body.string()
            if (!resp.isSuccessful) {
                throw RuntimeException("OpenAI HTTP ${resp.code}")
            }
            val content =
                JSONObject(respBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            return JSONObject(content)
        }
    }

    private fun generateForTopic(
        key: String,
        topic: ExamTopic,
        count: Int,
    ): List<PracticeQuestion> {
        val data =
            try {
                chatJson(key, generationMessages(topic, count), 0.8)
            } catch (e: Exception) {
                Timber.w(e, "generation failed for %s", topic.key)
                return emptyList()
            }
        val arr = data.optJSONArray("problems") ?: return emptyList()
        val out = mutableListOf<PracticeQuestion>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseCandidate(obj, topic)?.let { out.add(it) }
        }
        return out
    }

    private fun parseCandidate(
        obj: JSONObject,
        topic: ExamTopic,
    ): PracticeQuestion? {
        val choicesArr = obj.optJSONArray("choices") ?: return null
        val choices = (0 until choicesArr.length()).map { choicesArr.optString(it) }
        if (choices.size != 4 || choices.any { it.isBlank() }) return null
        if (choices.map { it.trim().lowercase() }.toSet().size != 4) return null
        val question = obj.optString("question")
        if (question.trim().length < 10) return null
        val answerIndex = obj.optInt("answerIndex", -1)
        if (answerIndex !in 0..3) return null
        return PracticeQuestion(
            id = "",
            topic = topic,
            passage = obj.optString("passage").takeIf { it.isNotBlank() },
            question = question,
            choices = choices,
            answerIndex = answerIndex,
            explanation = obj.optString("explanation").takeIf { it.isNotBlank() },
        )
    }

    /** Blind independent solve: keep only questions the model re-answers correctly. */
    private fun verify(
        key: String,
        candidates: List<PracticeQuestion>,
    ): List<PracticeQuestion> {
        if (candidates.isEmpty()) return emptyList()
        val items = JSONArray()
        candidates.forEachIndexed { i, c ->
            items.put(
                JSONObject()
                    .put("index", i)
                    .put("passage", c.passage ?: "")
                    .put("question", c.question)
                    .put("choices", JSONArray(c.choices)),
            )
        }
        val messages =
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "You are an MCAT expert taking a multiple-choice exam. For each item " +
                                "choose the single best answer using only the passage (if any) and " +
                                "established knowledge. Respond ONLY with JSON: " +
                                "{\"answers\": [{\"index\": <int>, \"answerIndex\": <0-3>}]}.",
                        ),
                ).put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", JSONObject().put("items", items).toString()),
                )
        val data =
            try {
                chatJson(key, messages, 0.0)
            } catch (e: Exception) {
                Timber.w(e, "verification failed")
                return emptyList()
            }
        val answers = HashMap<Int, Int>()
        val arr = data.optJSONArray("answers") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            answers[o.optInt("index", -1)] = o.optInt("answerIndex", -2)
        }
        return candidates.filterIndexed { i, c -> answers[i] == c.answerIndex }
    }

    private fun generationMessages(
        topic: ExamTopic,
        count: Int,
    ): JSONArray {
        val books = TOPIC_BOOKS[topic]?.joinToString(", ").orEmpty()
        val bookNote =
            if (books.isNotEmpty()) {
                "Draw content from the Jack Westin MCAT books for this section: $books.\n"
            } else {
                ""
            }
        val style =
            if (topic == ExamTopic.CARS) {
                "This is CARS (Critical Analysis and Reasoning): each problem MUST include a " +
                    "self-contained `passage` field (a 250-350 word humanities/social-science passage) " +
                    "and a question answerable SOLELY from that passage. Do not test outside knowledge."
            } else {
                "Each problem tests MCAT-level science knowledge. Leave `passage` empty unless a " +
                    "short data/experiment stem is needed."
            }
        val label = TOPIC_LABELS[topic] ?: topic.key
        val system =
            "You are an expert MCAT item writer creating rigorous, exam-accurate multiple-choice " +
                "questions in the style of Kaplan and Jack Westin MCAT prep books. Every question must " +
                "have exactly four choices with a single unambiguous best answer and three plausible " +
                "distractors. Return ONLY JSON."
        val user =
            "Write $count NEW MCAT-style multiple-choice questions for the topic \"$label\".\n" +
                "$bookNote$style\n\n" +
                "Return a JSON object of the form:\n" +
                "{ \"problems\": [ { \"topic\": \"${topic.key}\", \"passage\": \"\", \"question\": \"...\", " +
                "\"choices\": [\"...\",\"...\",\"...\",\"...\"], \"answerIndex\": 0, \"explanation\": \"...\" } ] }\n" +
                "Every problem must set \"topic\" to exactly \"${topic.key}\", have 4 distinct choices, an " +
                "integer \"answerIndex\" between 0 and 3, and a concise explanation."
        return JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
    }
}
