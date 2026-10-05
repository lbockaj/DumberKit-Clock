package org.fossify.clock.dialogs

import org.fossify.clock.helpers.setupDpadSaveCancelRows
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogMyTimePickerBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff

class MyTimePickerDialogDialog(val activity: SimpleActivity, val initialSeconds: Int, val callback: (result: Int) -> Unit) {
    private val binding = DialogMyTimePickerBinding.inflate(activity.layoutInflater)

    init {
        binding.apply {
            val textColor = activity.getProperTextColor()
            arrayOf(myTimePickerHours, myTimePickerMinutes, myTimePickerSeconds).forEach {
                it.textColor = textColor
                it.selectedTextColor = textColor
                it.dividerColor = textColor
            }

            myTimePickerHours.value = initialSeconds / 3600
            myTimePickerMinutes.value = (initialSeconds) / 60 % 60
            myTimePickerSeconds.value = initialSeconds % 60
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    val saveRow = setupDpadSaveCancelRows(
                        rows = binding.myTimePickerRows.root,
                        onCancel = alertDialog::dismiss,
                        onSave = {
                            dialogConfirmed()
                            alertDialog.dismiss()
                        }
                    )
                    val keypad = binding.run {
                        KeypadNumberColumns(listOf(myTimePickerHours, myTimePickerMinutes, myTimePickerSeconds)) { saveRow }
                    }
                    keypad.focusFirst()
                    alertDialog.setOnKeyListener { _, keyCode, event -> keypad.onKey(alertDialog.currentFocus, keyCode, event) }
                }
            }
    }

    private fun dialogConfirmed() {
        binding.apply {
            val hours = myTimePickerHours.value
            val minutes = myTimePickerMinutes.value
            val seconds = myTimePickerSeconds.value
            callback(hours * 3600 + minutes * 60 + seconds)
        }
    }
}
