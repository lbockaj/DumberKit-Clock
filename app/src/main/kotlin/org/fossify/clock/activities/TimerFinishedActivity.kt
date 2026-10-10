package org.fossify.clock.activities

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.AnimationUtils
import org.fossify.clock.R
import org.fossify.clock.databinding.ActivityTimerFinishedBinding
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.clock.extensions.hideTimerNotification
import org.fossify.clock.extensions.secondsToMillis
import org.fossify.clock.extensions.timerHelper
import org.fossify.clock.helpers.INVALID_TIMER_ID
import org.fossify.clock.helpers.TIMER_ID
import org.fossify.clock.models.Timer
import org.fossify.clock.models.TimerEvent
import org.fossify.clock.models.TimerState
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.isOreoMr1Plus
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Full screen "time expired" for keypad phones, like the ringing alarm: a finished timer otherwise only rings through
 * its notification, hard to reach with keys. Left starts the timer again, right (or OK, or Back) stops it.
 */
class TimerFinishedActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityTimerFinishedBinding::inflate)
    private var timerId = INVALID_TIMER_ID
    private var timer: Timer? = null
    private var handled = false
    private var sawFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        showOverLockscreen()
        updateTextColors(binding.root)
        setupViews()
        EventBus.getDefault().register(this)
        loadTimer()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handled = false
        loadTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    private fun setupViews() {
        val textColor = getProperTextColor()
        listOf(
            binding.timerFinishedRestart,
            binding.timerFinishedIcon,
            binding.timerFinishedStop,
            binding.timerFinishedLeftHint,
            binding.timerFinishedRightHint
        ).forEach { it.applyColorFilter(textColor) }
        binding.timerFinishedIconBackground.applyColorFilter(getProperPrimaryColor())
        binding.timerFinishedIconBackground.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulsing_animation))
    }

    private fun loadTimer() {
        timerId = intent.getIntExtra(TIMER_ID, INVALID_TIMER_ID)
        timerHelper.tryGetTimer(timerId) { timer ->
            runOnUiThread {
                // the finished state may still be on its way to the database, only an idle timer is really over
                if (timer == null || timer.state is TimerState.Idle) {
                    finishActivity()
                    return@runOnUiThread
                }

                this.timer = timer
                sawFinished = sawFinished || timer.state is TimerState.Finished
                binding.timerFinishedTime.text = timer.seconds.getFormattedDuration()
                binding.timerFinishedLabel.text = timer.label.ifEmpty { getString(R.string.time_expired) }
            }
        }
    }

    /** Acted on key up, like the alarm screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val restart = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> true
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> false
            else -> return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled && !handled) {
            handled = true
            val timer = timer
            if (timer?.id == null) {
                finishActivity()
            } else if (restart) {
                EventBus.getDefault().post(TimerEvent.Start(timer.id!!, timer.seconds.secondsToMillis))
                hideTimerNotification(timer.id!!)
                finishActivity()
            } else {
                EventBus.getDefault().post(TimerEvent.Reset(timer.id!!))
                hideTimerNotification(timer.id!!)
                finishActivity()
            }
        }
        return true
    }

    /** Stopped some other way (the notification, the timer screen, auto silence): nothing left to show. */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(@Suppress("unused") event: TimerEvent.Refresh) {
        if (handled || timerId == INVALID_TIMER_ID) {
            return
        }
        timerHelper.tryGetTimer(timerId) { timer ->
            runOnUiThread {
                when {
                    timer == null || timer.state is TimerState.Idle || timer.state is TimerState.Paused -> finishActivity()
                    timer.state is TimerState.Finished -> sawFinished = true
                    // started again elsewhere after it finished
                    sawFinished -> finishActivity()
                }
            }
        }
    }

    private fun showOverLockscreen() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun finishActivity() {
        if (!isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
