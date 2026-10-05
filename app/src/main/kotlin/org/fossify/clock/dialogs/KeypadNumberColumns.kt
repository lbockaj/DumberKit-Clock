package org.fossify.clock.dialogs

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import com.shawnlin.numberpicker.NumberPicker
import org.fossify.clock.helpers.applyDpadFocusHighlight

private const val TYPING_TIMEOUT_MS = 1500L

/**
 * Number columns for picking a time (hours / minutes / seconds, or AM/PM) on keypad phones:
 * - up/down change the focused column
 * - OK, right or a fully typed number move on to the next column, after the last one to [afterLast] (Save, Start);
 *   left goes back a column
 * - digits are typed into the focused column, so 0730 sets 07:30
 * Columns with [NumberPicker.getDisplayedValues] (AM/PM) take no digits.
 * Keys reach [onKey] from the dialog's or screen's key handling; the first column is focused by [focusFirst].
 */
class KeypadNumberColumns(private val columns: List<NumberPicker>, private val afterLast: () -> View?) {
    private var typedColumn: NumberPicker? = null
    private var typedDigits = ""
    private var lastDigitTime = 0L

    init {
        columns.forEach {
            it.isFocusable = true
            it.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            it.applyDpadFocusHighlight()
        }
    }

    fun focusFirst() {
        columns.firstOrNull()?.post { columns.first().requestFocusFromTouch() }
    }

    /** Handles a key while [focused] has the focus; true if consumed. Only keys on the columns are handled. */
    fun onKey(focused: View?, keyCode: Int, event: KeyEvent): Boolean {
        val column = focused as? NumberPicker ?: return false
        if (column !in columns) {
            return false
        }

        val isDown = event.action == KeyEvent.ACTION_DOWN
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isDown) {
                    columns.getOrNull(columns.indexOf(column) - 1)?.requestFocus()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isDown) {
                    moveOn(column)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isDown) {
                    step(column, if (keyCode == KeyEvent.KEYCODE_DPAD_UP) 1 else -1)
                }
                return true
            }

            // on key up: the next view would otherwise get the key up and act on it
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    typedDigits = ""
                    moveOn(column)
                }
                return true
            }
        }

        val digit = keyCode - KeyEvent.KEYCODE_0
        if (digit !in 0..9 || column.displayedValues != null) {
            return false
        }
        if (isDown) {
            typeDigit(column, digit)
        }
        return true
    }

    private fun moveOn(column: NumberPicker) {
        val next = columns.getOrNull(columns.indexOf(column) + 1)
        (next ?: afterLast())?.requestFocus()
    }

    private fun step(column: NumberPicker, delta: Int) {
        val size = column.maxValue - column.minValue + 1
        column.value = column.minValue + Math.floorMod(column.value - column.minValue + delta, size)
        typedDigits = ""
    }

    private fun typeDigit(column: NumberPicker, digit: Int) {
        val now = SystemClock.uptimeMillis()
        if (column != typedColumn || now - lastDigitTime > TYPING_TIMEOUT_MS) {
            typedDigits = ""
        }
        typedColumn = column
        lastDigitTime = now
        typedDigits += digit

        val typed = typedDigits.toInt()
        if (typed in column.minValue..column.maxValue) {
            column.value = typed
        } else if (typedDigits.length == 1) {
            // e.g. 0 in the 1–12 hours column: wait for the second digit
            return
        }

        // the number is complete when no further digit could keep it in range (two digits, or 3–9 in hours…)
        if (typedDigits.length >= 2 || typed * 10 > column.maxValue) {
            typedDigits = ""
            val next = columns.getOrNull(columns.indexOf(column) + 1)
            if (next == null) {
                afterLast()?.requestFocus()
            } else if (next.displayedValues == null) {
                next.requestFocus()
            }
        }
    }
}
