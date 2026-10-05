package org.fossify.clock.dialogs

import org.fossify.clock.helpers.setupDpadSaveCancelRows
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogAlarmTimePickerBinding
import org.fossify.clock.extensions.config
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import java.text.DateFormatSymbols

/**
 * Time of an alarm picked with the keys: hours and minutes columns (plus AM/PM in the 12-hour format), see
 * [KeypadNumberColumns]. Replaces the touch-only clock face and Android's time picker.
 */
class AlarmTimePickerDialog(
    val activity: SimpleActivity,
    val initialMinutes: Int,
    val callback: (hours: Int, minutes: Int) -> Unit,
) {
    private val binding = DialogAlarmTimePickerBinding.inflate(activity.layoutInflater)
    private val use24Hour = activity.config.use24HourFormat

    init {
        binding.apply {
            val textColor = activity.getProperTextColor()
            arrayOf(alarmTimePickerHours, alarmTimePickerMinutes, alarmTimePickerAmPm).forEach {
                it.textColor = textColor
                it.selectedTextColor = textColor
                it.dividerColor = textColor
            }

            val hours = initialMinutes / 60
            alarmTimePickerMinutes.value = initialMinutes % 60
            if (use24Hour) {
                alarmTimePickerHours.value = hours
            } else {
                alarmTimePickerHours.minValue = 1
                alarmTimePickerHours.maxValue = 12
                alarmTimePickerHours.value = if (hours % 12 == 0) 12 else hours % 12
                alarmTimePickerAmPm.displayedValues = DateFormatSymbols.getInstance().amPmStrings
                alarmTimePickerAmPm.value = if (hours < 12) 0 else 1
                alarmTimePickerAmPm.beVisible()
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    val saveRow = setupDpadSaveCancelRows(
                        rows = binding.alarmTimePickerRows.root,
                        onCancel = alertDialog::dismiss,
                        onSave = {
                            dialogConfirmed()
                            alertDialog.dismiss()
                        }
                    )
                    val columns = listOf(binding.alarmTimePickerHours, binding.alarmTimePickerMinutes) +
                        listOfNotNull(binding.alarmTimePickerAmPm.takeIf { !use24Hour })
                    val keypad = KeypadNumberColumns(columns) { saveRow }
                    keypad.focusFirst()
                    alertDialog.setOnKeyListener { _, keyCode, event -> keypad.onKey(alertDialog.currentFocus, keyCode, event) }
                }
            }
    }

    private fun dialogConfirmed() {
        binding.apply {
            var hours = alarmTimePickerHours.value
            if (!use24Hour) {
                hours = hours % 12 + if (alarmTimePickerAmPm.value == 1) 12 else 0
            }
            callback(hours, alarmTimePickerMinutes.value)
        }
    }
}
