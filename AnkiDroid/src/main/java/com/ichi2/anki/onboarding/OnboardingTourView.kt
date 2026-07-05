// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.onboarding

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.utils.dp

/** A single step of the first-run tour: highlight [target] and describe it. */
class OnboardingStep(
    val target: View,
    val titleRes: Int,
    val bodyRes: Int,
)

/**
 * A lightweight, dependency-free coach-mark overlay. It dims the screen, cuts a
 * spotlight hole around each target view in turn, and shows a tooltip card with
 * Skip/Next controls so first-time users learn where the Study, Practice and
 * Metrics areas are. Add it via [start]; it removes itself when finished.
 */
class OnboardingTourView(
    context: Context,
) : FrameLayout(context) {
    private val steps = mutableListOf<OnboardingStep>()
    private var index = 0
    private var onFinished: (() -> Unit)? = null

    private val holeRect = RectF()
    private val holePadding = 8.dp.toPx(context).toFloat()
    private val holeRadius = 8.dp.toPx(context).toFloat()

    private val dimPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3000000.toInt()
        }
    private val holePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

    private val card: MaterialCardView
    private val titleView: TextView
    private val bodyView: TextView
    private val progressView: TextView
    private val nextButton: MaterialButton
    private val skipButton: TextView

    init {
        // Intercept touches so the underlying UI is not interactable during the tour.
        isClickable = true
        setWillNotDraw(false)
        // PorterDuff.CLEAR needs a software layer to punch a transparent hole.
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        val pad = 16.dp.toPx(context)
        titleView =
            TextView(context).apply {
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            }
        bodyView =
            TextView(context).apply {
                textSize = 14f
                setPadding(0, 8.dp.toPx(context), 0, 0)
            }
        progressView =
            TextView(context).apply {
                textSize = 12f
                alpha = 0.7f
            }
        skipButton =
            TextView(context).apply {
                setText(R.string.onboarding_skip)
                setPadding(pad, pad / 2, pad, pad / 2)
                isClickable = true
                isFocusable = true
                alpha = 0.8f
            }
        nextButton =
            MaterialButton(context).apply {
                setText(R.string.onboarding_next)
            }

        val controls =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12.dp.toPx(context), 0, 0)
                addView(
                    progressView,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(skipButton)
                addView(nextButton)
            }

        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(titleView)
                addView(bodyView)
                addView(controls)
            }

        card =
            MaterialCardView(context).apply {
                radius = 12.dp.toPx(context).toFloat()
                useCompatPadding = true
                addView(content)
            }
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        skipButton.setOnClickListener { finish() }
        nextButton.setOnClickListener {
            index += 1
            if (index >= steps.size) {
                finish()
            } else {
                showStep()
            }
        }
    }

    /**
     * Attaches the overlay to [activity] and starts the tour. Steps whose target
     * is not currently laid out are skipped. [onFinished] runs once the tour is
     * completed or skipped (use it to persist that onboarding was shown).
     */
    fun start(
        activity: Activity,
        tourSteps: List<OnboardingStep>,
        onFinished: () -> Unit,
    ) {
        val usable = tourSteps.filter { it.target.width > 0 && it.target.height > 0 }
        if (usable.isEmpty()) {
            onFinished()
            return
        }
        steps.clear()
        steps.addAll(usable)
        this.onFinished = onFinished
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        doOnLayout { showStep() }
    }

    private fun showStep() {
        val step = steps[index]
        val target = step.target
        val loc = IntArray(2)
        target.getLocationInWindow(loc)
        val self = IntArray(2)
        getLocationInWindow(self)
        val x = (loc[0] - self[0]).toFloat()
        val y = (loc[1] - self[1]).toFloat()
        holeRect.set(
            x - holePadding,
            y - holePadding,
            x + target.width + holePadding,
            y + target.height + holePadding,
        )

        titleView.setText(step.titleRes)
        bodyView.setText(step.bodyRes)
        progressView.text = "${index + 1} / ${steps.size}"
        nextButton.setText(
            if (index == steps.size - 1) R.string.onboarding_done else R.string.onboarding_next,
        )

        invalidate()
        positionCard()
    }

    private fun positionCard() {
        val margin = 16.dp.toPx(context)
        val availWidth = (width - margin * 2).coerceAtLeast(0)
        card.measure(
            MeasureSpec.makeMeasureSpec(availWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
        )
        val cardHeight = card.measuredHeight
        val gap = 12.dp.toPx(context)
        val lp = card.layoutParams as LayoutParams
        lp.width = availWidth
        lp.leftMargin = margin
        lp.rightMargin = margin
        val below = holeRect.bottom.toInt() + gap
        lp.topMargin =
            if (below + cardHeight <= height - margin) {
                below
            } else {
                (holeRect.top.toInt() - gap - cardHeight).coerceAtLeast(margin)
            }
        card.layoutParams = lp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        if (!holeRect.isEmpty) {
            canvas.drawRoundRect(holeRect, holeRadius, holeRadius, holePaint)
        }
    }

    private fun finish() {
        (parent as? ViewGroup)?.removeView(this)
        onFinished?.invoke()
        onFinished = null
    }
}

private const val MCAT_ONBOARDING_SHOWN = "McatOnboardingShown"

/** Whether the first-run MCAT tour has already been shown. */
fun Context.hasShownMcatOnboarding(): Boolean = sharedPrefs().getBoolean(MCAT_ONBOARDING_SHOWN, false)

/** Records that the first-run MCAT tour has been shown, so it isn't repeated. */
fun Context.markMcatOnboardingShown() {
    sharedPrefs().edit { putBoolean(MCAT_ONBOARDING_SHOWN, true) }
}
