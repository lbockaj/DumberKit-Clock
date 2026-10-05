package org.fossify.clock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import org.fossify.commons.activities.CustomizationActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.views.MyAppBarLayout
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.clock.helpers.dpadCompactTopBar
import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.getOpenTimerTabIntent
import org.fossify.clock.extensions.getTimerNotification
import org.fossify.clock.extensions.hideNotification
import org.fossify.clock.extensions.timerHelper
import org.fossify.clock.helpers.Stopwatch
import org.fossify.clock.helpers.ignoreTouches
import org.fossify.clock.helpers.Stopwatch.State
import org.fossify.clock.models.TimerEvent
import org.fossify.clock.models.TimerState
import org.fossify.clock.services.StopwatchStopService
import org.fossify.clock.services.TimerStopService
import org.fossify.clock.services.startStopwatchService
import org.fossify.clock.services.startTimerService
import org.fossify.commons.FossifyApp
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.checkAppIconColor
import org.fossify.commons.helpers.IS_SYSTEM_THEME_ENABLED
import org.fossify.commons.helpers.PREFS_KEY
import org.fossify.commons.helpers.SIDELOADING_FALSE
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.showErrorToast
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

private const val PURPLE_ICON_SET = "dumberkit_purple_icon"

class App : FossifyApp(), LifecycleObserver {

    private var countDownTimers = mutableMapOf<Int, CountDownTimer>()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        EventBus.getDefault().register(this)
        usePurpleIconByDefault()
        useSystemThemeByDefault()
        // Commons calls any build without one of its unused images (removed by the release shrinker) a "fake version"
        // and sends people to its website; this app is built from its own source on purpose
        baseConfig.appSideloadingStatus = SIDELOADING_FALSE
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                // keys only: touches are ignored in every screen of the app
                activity.ignoreTouches()
                // Commons' screens we can't change directly: clear focus highlight and a focused first row
                if (activity is CustomizationActivity) {
                    activity.window.decorView.post {
                        setupCustomizationForKeys(activity)
                        prepareForKeys(activity)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Compact always-colored top bar, and Cancel / Save rows at the end instead of the toolbar's check icon. */
    private fun setupCustomizationForKeys(activity: CustomizationActivity) {
        val appBar = activity.findViewById<MyAppBarLayout>(org.fossify.commons.R.id.app_bar)
        val scrollView = activity.findViewById<NestedScrollView>(org.fossify.commons.R.id.customization_nested_scrollview)
        if (appBar != null) {
            activity.dpadCompactTopBar(appBar, scrollView)
        }

        val holder = activity.findViewById<ViewGroup>(org.fossify.commons.R.id.customization_holder) ?: return
        if (holder.findViewById<View>(R.id.dpad_save_cancel_rows) == null) {
            val rows = LayoutInflater.from(activity).inflate(R.layout.dpad_save_cancel_rows, holder, false)
            holder.addView(rows)

            val toolbar = activity.findViewById<MaterialToolbar>(org.fossify.commons.R.id.customization_toolbar)
            rows.findViewById<View>(R.id.dpad_save_row_holder).setOnClickListener {
                toolbar?.menu?.performIdentifierAction(org.fossify.commons.R.id.save, 0)
            }
            // cancel = leave without saving, like choosing "discard" when going back
            rows.findViewById<View>(R.id.dpad_cancel_row_holder).setOnClickListener { activity.finish() }

            // Commons puts the back arrow and the check icon back after every color change, hide them again each time;
            // the hidden save item still works for the Save row (menus only check if an item is enabled)
            toolbar?.viewTreeObserver?.addOnGlobalLayoutListener {
                if (toolbar.navigationIcon != null) {
                    toolbar.navigationIcon = null
                }
                toolbar.menu.findItem(org.fossify.commons.R.id.save)?.takeIf { it.isVisible }?.isVisible = false
            }

            // a theme picked here (not saved yet) recolors only Commons' own labels, follow them
            val themeLabel = activity.findViewById<TextView>(org.fossify.commons.R.id.customization_theme_label)
            val labels = listOf(R.id.dpad_save_row_label, R.id.dpad_cancel_row_label).mapNotNull { rows.findViewById<TextView>(it) }
            holder.viewTreeObserver.addOnPreDrawListener {
                val color = themeLabel?.currentTextColor
                if (color != null) {
                    labels.filter { it.currentTextColor != color }.forEach { it.setTextColor(color) }
                }
                true
            }
        }

        holder.findViewById<ImageView>(R.id.dpad_save_row_icon)?.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_save_green))
        holder.findViewById<ImageView>(R.id.dpad_cancel_row_icon)?.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_cancel_red))
    }

    private fun prepareForKeys(activity: Activity) {
        val rows = ArrayList<View>()
        fun collect(view: View) {
            // hidden rows too: some only show up after picking a theme (text, background, primary color)
            if (view !is EditText && view.isClickable && view.id != View.NO_ID &&
                activity.resources.getResourceEntryName(view.id).endsWith("_holder")
            ) {
                rows.add(view)
                return
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collect(view.getChildAt(i))
                }
            }
        }
        collect(activity.window.decorView)

        rows.forEach {
            it.isFocusable = true
            it.applyDpadFocusHighlight()
        }

        val focused = activity.currentFocus
        if (focused == null || focused is ScrollView || focused is NestedScrollView) {
            rows.firstOrNull { it.isShown }?.requestFocusFromTouch()
        }
    }

    /**
     * The purple launcher entry is the default one (see the manifest), so Commons' icon color setting starts as purple
     * too, otherwise its color picker and the launcher icon wouldn't match. Done once, the user's own choice later stays.
     */
    private fun usePurpleIconByDefault() {
        // Commons' check for old installs with an orange icon switches the icon to green, never wanted here
        baseConfig.wasOrangeIconChecked = true

        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        if (prefs.getBoolean(PURPLE_ICON_SET, false)) {
            return
        }

        val purple = ContextCompat.getColor(this, org.fossify.commons.R.color.md_purple_700)
        baseConfig.appIconColor = purple
        prefs.edit().putBoolean(PURPLE_ICON_SET, true).apply()
        if (baseConfig.appId.isEmpty()) {
            // fresh install: the purple launcher entry is already the enabled one (see the manifest)
            baseConfig.lastIconColor = purple
        } else {
            // another entry is enabled: let Commons switch the launcher entries over to the purple one
            baseConfig.lastIconColor = 0
            checkAppIconColor()
        }
    }

    /** The "System" color theme unless one was chosen; Commons only defaults to it on Android 12+. */
    private fun useSystemThemeByDefault() {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        if (!prefs.contains(IS_SYSTEM_THEME_ENABLED)) {
            baseConfig.isSystemThemeEnabled = true
        }
    }

    override fun onTerminate() {
        EventBus.getDefault().unregister(this)
        super.onTerminate()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun onAppBackgrounded() {
        timerHelper.getTimers { timers ->
            if (timers.any { it.state is TimerState.Running }) {
                startTimerService(this)
            }
        }
        if (Stopwatch.state == State.RUNNING) {
            startStopwatchService(this)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun onAppForegrounded() {
        EventBus.getDefault().post(TimerStopService)
        timerHelper.getTimers { timers ->
            val runningTimers = timers.filter { it.state is TimerState.Running }
            runningTimers.forEach { timer ->
                if (countDownTimers[timer.id] == null) {
                    EventBus.getDefault().post(
                        TimerEvent.Start(
                            timerId = timer.id!!,
                            duration = (timer.state as TimerState.Running).tick
                        )
                    )
                }
            }
        }
        if (Stopwatch.state == State.RUNNING) {
            EventBus.getDefault().post(StopwatchStopService)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Reset) {
        updateTimerState(event.timerId, TimerState.Idle)
        countDownTimers[event.timerId]?.cancel()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Delete) {
        countDownTimers[event.timerId]?.cancel()
        timerHelper.deleteTimer(event.timerId) {
            EventBus.getDefault().post(TimerEvent.Refresh)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Start) {
        val countDownTimer = object : CountDownTimer(event.duration, 1000) {
            override fun onTick(tick: Long) {
                updateTimerState(event.timerId, TimerState.Running(event.duration, tick))
            }

            override fun onFinish() {
                EventBus.getDefault().post(TimerEvent.Finish(event.timerId, event.duration))
                EventBus.getDefault().post(TimerStopService)
            }
        }.start()
        countDownTimers[event.timerId] = countDownTimer
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Finish) {
        timerHelper.getTimer(event.timerId) { timer ->
            val pendingIntent = getOpenTimerTabIntent(event.timerId)
            val notification = getTimerNotification(timer, pendingIntent)

            try {
                notificationManager.notify(event.timerId, notification)
            } catch (e: Exception) {
                showErrorToast(e)
            }

            updateTimerState(event.timerId, TimerState.Finished)
            Handler(Looper.getMainLooper()).postDelayed({
                hideNotification(event.timerId)
            }, config.timerMaxReminderSecs * 1000L)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: TimerEvent.Pause) {
        timerHelper.getTimer(event.timerId) { timer ->
            updateTimerState(
                event.timerId,
                TimerState.Paused(event.duration, (timer.state as TimerState.Running).tick)
            )
            countDownTimers[event.timerId]?.cancel()
        }
    }

    private fun updateTimerState(timerId: Int, state: TimerState) {
        timerHelper.getTimer(timerId) { timer ->
            val newTimer = timer.copy(state = state)
            if (newTimer.oneShot && state is TimerState.Idle) {
                timerHelper.deleteTimer(newTimer.id!!) {
                    EventBus.getDefault().post(TimerEvent.Refresh)
                }
            } else {
                timerHelper.insertOrUpdateTimer(newTimer) {
                    EventBus.getDefault().post(TimerEvent.Refresh)
                }
            }
        }
    }
}
