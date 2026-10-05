package org.fossify.clock.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import com.duolingo.open.rtlviewpager.RtlViewPager

/**
 * The screens are opened from the home list one at a time: no swiping between them, and the left/right arrows only move
 * the focus, they never switch the page.
 */
class DpadViewPager(context: Context, attrs: AttributeSet?) : RtlViewPager(context, attrs) {
    init {
        // the pager holds the focus on screens without focusable items (stopwatch, clock without time zones),
        // Android would grey out the whole screen for it
        defaultFocusHighlightEnabled = false
    }

    override fun executeKeyEvent(event: KeyEvent) = false

    override fun onInterceptTouchEvent(event: MotionEvent) = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = false
}
