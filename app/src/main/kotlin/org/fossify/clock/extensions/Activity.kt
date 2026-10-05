package org.fossify.clock.extensions

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import org.fossify.clock.R
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.toast
import org.fossify.clock.BuildConfig
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.openFullScreenIntentSettings
import org.fossify.commons.extensions.openNotificationSettings

fun BaseSimpleActivity.handleFullScreenNotificationsPermission(
    notificationsCallback: (granted: Boolean) -> Unit,
) {
    handleNotificationPermission { granted ->
        if (granted) {
            if (canUseFullScreenIntent()) {
                notificationsCallback(true)
                askForAlarmOverOtherApps()
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = org.fossify.commons.R.string.allow_full_screen_notifications_reminders,
                    positiveActionCallback = {
                        openFullScreenIntentSettings(BuildConfig.APPLICATION_ID)
                    },
                    negativeActionCallback = {
                        notificationsCallback(false)
                    }
                )
            }
        } else {
            PermissionRequiredDialog(
                activity = this,
                textId = org.fossify.commons.R.string.allow_notifications_reminders,
                positiveActionCallback = {
                    openNotificationSettings()
                },
                negativeActionCallback = {
                    notificationsCallback(false)
                }
            )
        }
    }
}

/**
 * DumberOS turns pop-up notifications off, so with the phone unlocked a ringing alarm only sounds: its full screen
 * (snooze / dismiss with the keys) can't show. With "Display over other apps" the app opens that screen itself
 * (see AlarmService). Asked once, when an alarm is switched on or saved.
 */
fun BaseSimpleActivity.askForAlarmOverOtherApps() {
    if (Settings.canDrawOverlays(this) || config.dpadOverlayAsked) {
        return
    }

    config.dpadOverlayAsked = true
    ConfirmationDialog(
        activity = this,
        message = getString(R.string.dpad_overlay_explanation),
        positive = org.fossify.commons.R.string.ok,
        negative = R.string.dpad_not_now
    ) {
        openOverOtherAppsSettings()
    }
}

/** The system page of "Display over other apps" for this app (on DumberOS a touch is needed there). */
fun BaseSimpleActivity.openOverOtherAppsSettings() {
    try {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    } catch (ignored: Exception) {
        toast(org.fossify.commons.R.string.no_app_found)
    }
}
