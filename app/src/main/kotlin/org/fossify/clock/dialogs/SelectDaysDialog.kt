package org.fossify.clock.dialogs

import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogSelectDaysBinding
import org.fossify.clock.databinding.ItemDpadCheckRowBinding
import org.fossify.clock.extensions.rotateWeekdays
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff

/**
 * The days an alarm repeats on, as a list of day names with checkboxes (from the first day of the week): OK toggles a
 * day, right jumps to Save (green check) or Cancel (red cross), left goes back. [days] is the alarm's day bit mask,
 * 0 or less for a one-time alarm; the callback gets the new mask, 0 for "doesn't repeat".
 */
class SelectDaysDialog(val activity: SimpleActivity, days: Int, val callback: (days: Int) -> Unit) {
    private val binding = DialogSelectDaysBinding.inflate(activity.layoutInflater)
    private var selectedDays = days.coerceAtLeast(0)
    private var dialog: AlertDialog? = null
    private var lastRow: View? = null

    init {
        val dayNames = activity.resources.getStringArray(org.fossify.commons.R.array.week_days)
        val textColor = activity.getProperTextColor()
        val rows = activity.rotateWeekdays((0..6).toList()).map { dayIndex ->
            val bit = 1 shl dayIndex
            ItemDpadCheckRowBinding.inflate(activity.layoutInflater, binding.selectDaysList, true).apply {
                dpadCheckRowTitle.text = dayNames[dayIndex]
                dpadCheckRowTitle.setTextColor(textColor)
                dpadCheckRowCheckbox.setColors(textColor, activity.getProperPrimaryColor(), activity.getProperBackgroundColor())
                dpadCheckRowCheckbox.isChecked = selectedDays and bit != 0
                dpadCheckRowHolder.applyDpadFocusHighlight()
                dpadCheckRowHolder.setOnClickListener {
                    dpadCheckRowCheckbox.toggle()
                    selectedDays = if (dpadCheckRowCheckbox.isChecked) selectedDays or bit else selectedDays and bit.inv()
                }
            }.root
        }

        binding.apply {
            selectDaysSave.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_save_green))
            selectDaysCancel.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_cancel_red))
            listOf(selectDaysSave, selectDaysCancel).forEach { it.applyDpadFocusHighlight() }
            selectDaysSave.setOnClickListener {
                callback(selectedDays)
                dialog?.dismiss()
            }
            selectDaysCancel.setOnClickListener { dialog?.dismiss() }
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(binding.root, this, titleId = R.string.dpad_repeat) { alertDialog ->
                dialog = alertDialog
                rows.first().post { rows.first().requestFocusFromTouch() }
                alertDialog.setOnKeyListener { _, keyCode, event -> handleSideKeys(alertDialog.currentFocus, keyCode, event) }
            }
        }
    }

    /** Right from any day jumps to Save, left from Save / Cancel back to that day. */
    private fun handleSideKeys(focused: View?, keyCode: Int, event: KeyEvent): Boolean {
        val onSide = focused == binding.selectDaysSave || focused == binding.selectDaysCancel
        val target = when {
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !onSide -> binding.selectDaysSave
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> return true
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && onSide -> lastRow
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!onSide) {
                lastRow = focused
            }
            target?.requestFocus()
        }
        return true
    }
}
