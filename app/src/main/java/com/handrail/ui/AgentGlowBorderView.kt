package com.handrail.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Golden pulsing border shown for the duration of a Takeover run. Added
 * directly to the WindowManager by [com.handrail.perception.HandrailAccessibilityService]
 * as a TYPE_ACCESSIBILITY_OVERLAY — NOT hosted in AssistActivity's window,
 * because that window backgrounds every time the agent taps into another
 * app (see AssistActivity's singleTask note). This view must survive those
 * app switches so the user always has a visible "the agent is still
 * working" cue, which is the whole point of it.
 *
 * Non-interactive by construction (the hosting window is FLAG_NOT_TOUCHABLE
 * / FLAG_NOT_FOCUSABLE) — it only ever draws.
 */
class AgentGlowBorderView(context: Context) : View(context) {

    // Matches HandrailColors.Accent (0xFFB68235) — kept as a raw Int here
    // since this view is built outside Compose.
    private val glowColor = Color.parseColor("#B68235")
    private val strokeWidthPx = 10f * resources.displayMetrics.density
    private val blurRadiusPx = 22f * resources.displayMetrics.density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = glowColor
        maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
    }

    // BlurMaskFilter only renders on a software layer — hardware layers ignore it.
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, paint)
    }

    private val pulse = ValueAnimator.ofInt(MIN_ALPHA, MAX_ALPHA).apply {
        duration = PULSE_DURATION_MS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            paint.alpha = it.animatedValue as Int
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pulse.start()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokeWidthPx / 2f
        canvas.drawRect(inset, inset, width - inset, height - inset, paint)
    }

    private companion object {
        const val PULSE_DURATION_MS = 900L
        const val MIN_ALPHA = 70
        const val MAX_ALPHA = 255
    }
}
