package org.fossify.clock.dialogs

import org.fossify.commons.helpers.EVERY_DAY_BIT
import org.fossify.clock.extensions.getSelectedDaysString
import org.fossify.clock.helpers.setupDpadSaveCancelRows
import android.media.AudioManager
import android.media.RingtoneManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogEditAlarmBinding
import org.fossify.clock.extensions.checkAlarmsWithDeletedSoundUri
import org.fossify.clock.extensions.colorCompoundDrawable
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.extensions.handleFullScreenNotificationsPermission
import org.fossify.clock.helpers.PICK_AUDIO_FILE_INTENT_ID
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.clock.helpers.moveFocusOnUpDown
import org.fossify.clock.helpers.getCurrentDayMinutes
import org.fossify.clock.helpers.updateNonRecurringAlarmDay
import org.fossify.clock.models.Alarm
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDefaultAlarmSound
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.models.AlarmSound

class EditAlarmDialog(
    val activity: SimpleActivity,
    val alarm: Alarm,
    val onDismiss: () -> Unit = {},
    val callback: (alarmId: Int) -> Unit,
) {
    private val binding = DialogEditAlarmBinding.inflate(activity.layoutInflater)
    private val textColor = activity.getProperTextColor()

    init {
        restoreLastAlarm()
        updateAlarmTime()

        binding.apply {
            editAlarmTime.setOnClickListener {
                AlarmTimePickerDialog(activity, alarm.timeInMinutes, ::timePicked)
            }

            editAlarmSound.colorCompoundDrawable(textColor)
            editAlarmSound.text = alarm.soundTitle
            editAlarmSound.setOnClickListener {
                SelectAlarmSoundDialog(
                    activity = activity,
                    currentUri = alarm.soundUri,
                    audioStream = AudioManager.STREAM_ALARM,
                    pickAudioIntentId = PICK_AUDIO_FILE_INTENT_ID,
                    type = RingtoneManager.TYPE_ALARM,
                    loopAudio = true,
                    onAlarmPicked = {
                        if (it != null) {
                            updateSelectedAlarmSound(it)
                        }
                    },
                    onAlarmSoundDeleted = {
                        if (alarm.soundUri == it.uri) {
                            val defaultAlarm =
                                root.context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
                            updateSelectedAlarmSound(defaultAlarm)
                        }
                        activity.checkAlarmsWithDeletedSoundUri(it.uri)
                    })
            }

            editAlarmVibrateIcon.setColorFilter(textColor)
            editAlarmVibrate.isChecked = alarm.vibrate
            editAlarmVibrateHolder.setOnClickListener {
                editAlarmVibrate.toggle()
                alarm.vibrate = editAlarmVibrate.isChecked
            }

            editAlarmLabelImage.applyColorFilter(textColor)
            editAlarm.setText(alarm.label)

            editAlarmRepeatIcon.applyColorFilter(textColor)
            updateRepeatDays()
            editAlarmRepeatHolder.setOnClickListener {
                SelectDaysDialog(activity, alarm.days) { days ->
                    alarm.days = days
                    updateRepeatDays()
                    checkDaylessAlarm()
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setOnDismissListener { onDismiss() }
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    setupForKeys()
                    lateinit var saveRow: android.view.View
                    saveRow = setupDpadSaveCancelRows(binding.editAlarmRows.root, onCancel = alertDialog::dismiss) {
                        if (!activity.config.wasAlarmWarningShown) {
                            ConfirmationDialog(
                                activity = activity,
                                messageId = org.fossify.commons.R.string.alarm_warning,
                                positive = org.fossify.commons.R.string.ok,
                                negative = 0
                            ) {
                                activity.config.wasAlarmWarningShown = true
                                saveRow.performClick()
                            }

                            return@setupDpadSaveCancelRows
                        }

                        updateNonRecurringAlarmDay(alarm)

                        alarm.label = binding.editAlarm.value
                        alarm.isEnabled = true
                        alarm.oneShot = false

                        var alarmId = alarm.id
                        activity.handleFullScreenNotificationsPermission { granted ->
                            if (granted) {
                                if (alarm.id == 0) {
                                    alarmId = activity.dbHelper.insertAlarm(alarm)
                                    if (alarmId == -1) {
                                        activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                } else {
                                    if (!activity.dbHelper.updateAlarm(alarm)) {
                                        activity.toast(org.fossify.commons.R.string.unknown_error_occurred)
                                    }
                                }

                                activity.config.alarmLastConfig = alarm
                                callback(alarmId)
                                alertDialog.dismiss()
                            }
                        }
                    }
                }
            }
    }

    /** Every row gets the focus highlight, the time is focused first; up/down leave the label field. */
    private fun setupForKeys() {
        binding.apply {
            listOf(editAlarmTime, editAlarmRepeatHolder, editAlarmVibrateHolder, editAlarmSound).forEach {
                it.isFocusable = true
                it.applyDpadFocusHighlight()
            }
            editAlarm.moveFocusOnUpDown()
            editAlarmTime.post { editAlarmTime.requestFocusFromTouch() }
        }
    }

    private fun restoreLastAlarm() {
        if (alarm.id == 0) {
            activity.config.alarmLastConfig?.let { lastConfig ->
                alarm.label = lastConfig.label
                alarm.days = lastConfig.days
                alarm.soundTitle = lastConfig.soundTitle
                alarm.soundUri = lastConfig.soundUri
                alarm.timeInMinutes = lastConfig.timeInMinutes
                alarm.vibrate = lastConfig.vibrate
            }
        }
    }

    private fun timePicked(hours: Int, minutes: Int) {
        alarm.timeInMinutes = hours * 60 + minutes
        updateAlarmTime()
    }

    private fun updateAlarmTime() {
        binding.editAlarmTime.text = activity.getFormattedTime(
            passedSeconds = alarm.timeInMinutes * 60,
            showSeconds = false,
            makeAmPmSmaller = true
        )
        checkDaylessAlarm()
    }

    private fun checkDaylessAlarm() {
        if (!alarm.isRecurring()) {
            val textId = if (alarm.timeInMinutes > getCurrentDayMinutes()) {
                org.fossify.commons.R.string.today
            } else {
                org.fossify.commons.R.string.tomorrow
            }

            binding.editAlarmDaylessLabel.text = "(${activity.getString(textId)})"
        }
        binding.editAlarmDaylessLabel.beVisibleIf(!alarm.isRecurring())
    }

    /** "Mon, Tue…", "Every day" or "Does not repeat" under the Repeat row. */
    private fun updateRepeatDays() {
        binding.editAlarmRepeatDays.text = when {
            !alarm.isRecurring() -> activity.getString(R.string.dpad_repeat_once)
            alarm.days == EVERY_DAY_BIT -> activity.getString(org.fossify.commons.R.string.every_day)
            else -> activity.getSelectedDaysString(alarm.days)
        }
    }

    fun updateSelectedAlarmSound(alarmSound: AlarmSound) {
        alarm.soundTitle = alarmSound.title
        alarm.soundUri = alarmSound.uri
        binding.editAlarmSound.text = alarmSound.title
    }
}
