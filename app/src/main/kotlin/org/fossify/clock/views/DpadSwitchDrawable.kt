package org.fossify.clock.views

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * On / off switch drawn without any text: on is a filled pill with a white knob on the right holding a "→",
 * off an outlined pill with the knob on the left holding a "←", the arrow pointing the way the knob is pushed.
 */
class DpadSwitchDrawable(
    private val isOn: Boolean,
    private val onColor: Int,
    private val offColor: Int,
    private val knobColor: Int,
    private val density: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = RectF()
    private val arrow = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val stroke = 2 * density
        pill.set(b.left + stroke, b.top + stroke, b.right - stroke, b.bottom - stroke)
        val radius = pill.height() / 2

        // the pill
        paint.style = if (isOn) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = if (isOn) onColor else offColor
        canvas.drawRoundRect(pill, radius, radius, paint)

        // the knob, right when on, left when off
        val knobRadius = radius - 3 * density
        val cx = if (isOn) pill.right - radius else pill.left + radius
        val cy = pill.centerY()
        paint.style = Paint.Style.FILL
        paint.color = if (isOn) knobColor else offColor
        canvas.drawCircle(cx, cy, knobRadius, paint)

        // the arrow in the knob
        val half = knobRadius * 0.5f
        val head = knobRadius * 0.35f
        val dir = if (isOn) 1 else -1
        arrow.reset()
        arrow.moveTo(cx - dir * half, cy)
        arrow.lineTo(cx + dir * half, cy)
        arrow.moveTo(cx + dir * half - dir * head, cy - head)
        arrow.lineTo(cx + dir * half, cy)
        arrow.lineTo(cx + dir * half - dir * head, cy + head)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (isOn) onColor else knobColor
        canvas.drawPath(arrow, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
