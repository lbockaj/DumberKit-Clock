package org.fossify.clock.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.core.widget.NestedScrollView
import me.grantland.widget.AutofitHelper
import org.fossify.clock.BuildConfig
import org.fossify.clock.R
import org.fossify.clock.adapters.ViewPagerAdapter
import org.fossify.clock.databinding.ActivityMainBinding
import org.fossify.clock.extensions.alarmController
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.getClosestEnabledAlarmString
import org.fossify.clock.extensions.getEnabledAlarms
import org.fossify.clock.extensions.getFormattedDuration
import org.fossify.clock.extensions.timerHelper
import org.fossify.clock.helpers.FORMAT_12H
import org.fossify.clock.helpers.FORMAT_24H
import org.fossify.clock.helpers.Stopwatch
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.clock.helpers.dpadCompactTopBar
import org.fossify.clock.helpers.dpadPopupMenu
import org.fossify.clock.models.TimerState
import org.fossify.clock.extensions.handleFullScreenNotificationsPermission
import org.fossify.clock.extensions.updateWidgets
import org.fossify.clock.helpers.INVALID_TIMER_ID
import org.fossify.clock.helpers.OPEN_TAB
import org.fossify.clock.helpers.PICK_AUDIO_FILE_INTENT_ID
import org.fossify.clock.helpers.STOPWATCH_SHORTCUT_ID
import org.fossify.clock.helpers.STOPWATCH_TOGGLE_ACTION
import org.fossify.clock.helpers.TABS_COUNT
import org.fossify.clock.helpers.TAB_ALARM
import org.fossify.clock.helpers.TAB_ALARM_INDEX
import org.fossify.clock.helpers.TAB_CLOCK
import org.fossify.clock.helpers.TAB_CLOCK_INDEX
import org.fossify.clock.helpers.TAB_STOPWATCH
import org.fossify.clock.helpers.TAB_STOPWATCH_INDEX
import org.fossify.clock.helpers.TAB_TIMER
import org.fossify.clock.helpers.TAB_TIMER_INDEX
import org.fossify.clock.helpers.TIMER_ID
import org.fossify.clock.helpers.TOGGLE_STOPWATCH
import org.fossify.commons.databinding.BottomTablayoutItemBinding
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.getBottomNavigationBackgroundColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.onPageChangeListener
import org.fossify.commons.extensions.onTabSelectionChanged
import org.fossify.commons.extensions.shortcutManager
import org.fossify.commons.extensions.storeNewYourAlarmSound
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateBottomTabItemColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import org.fossify.commons.helpers.LICENSE_NUMBER_PICKER
import org.fossify.commons.helpers.LICENSE_RTL
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FAQItem
import java.text.SimpleDateFormat
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

private const val HOME_STATUS_INTERVAL_MS = 1000L

class MainActivity : SimpleActivity() {
    private var showingHome = true
    private var lastHomeRow: View? = null
    private val homeHandler = Handler(Looper.getMainLooper())
    private val homeStatusUpdater = object : Runnable {
        override fun run() {
            updateHomeStatus()
            homeHandler.postDelayed(this, HOME_STATUS_INTERVAL_MS)
        }
    }
    private var storedTextColor = 0
    private var storedBackgroundColor = 0
    private var storedPrimaryColor = 0
    private val binding: ActivityMainBinding by viewBinding(ActivityMainBinding::inflate)
    private val homeScreens by lazy {
        listOf(
            binding.homeClock to TAB_CLOCK_INDEX,
            binding.homeAlarm to TAB_ALARM_INDEX,
            binding.homeStopwatch to TAB_STOPWATCH_INDEX,
            binding.homeTimer to TAB_TIMER_INDEX
        )
    }
    private val homeRows by lazy { homeScreens.map { it.first } + binding.homeSettings }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.mainTabsHolder))

        storeStateVariables()
        val openTab = intent.hasExtra(OPEN_TAB)
        initFragments()
        setupTabs()
        setupHome()
        if (openTab) {
            showScreen(binding.viewPager.currentItem)
        } else {
            showHome()
        }
        updateWidgets()
        migrateFirstDayOfWeek()
        ensureBackgroundThread {
            alarmController.rescheduleEnabledAlarms()
        }

        getEnabledAlarms { enabledAlarms ->
            // the first start asks for everything an alarm needs (notifications, full screen, over other apps),
            // later only when there are alarms switched on
            val firstStart = !config.dpadPermissionsAsked
            config.dpadPermissionsAsked = true
            if (firstStart || !enabledAlarms.isNullOrEmpty()) {
                handleFullScreenNotificationsPermission {
                    if (!it) {
                        toast(org.fossify.commons.R.string.notifications_disabled)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar)
        dpadCompactTopBar(binding.mainAppbar, null)
        val configTextColor = getProperTextColor()
        if (storedTextColor != configTextColor) {
            getInactiveTabIndexes(binding.viewPager.currentItem).forEach {
                binding.mainTabsHolder.getTabAt(it)?.icon?.applyColorFilter(configTextColor)
            }
        }

        val configBackgroundColor = getProperBackgroundColor()
        if (storedBackgroundColor != configBackgroundColor) {
            binding.mainTabsHolder.background = configBackgroundColor.toDrawable()
        }

        val configPrimaryColor = getProperPrimaryColor()
        if (storedPrimaryColor != configPrimaryColor) {
            binding.mainTabsHolder.setSelectedTabIndicatorColor(getProperPrimaryColor())
            binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.icon
                ?.applyColorFilter(getProperPrimaryColor())
        }

        if (config.preventPhoneFromSleeping) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setupTabColors()
        checkShortcuts()
        updateHomeColors()
        homeHandler.post(homeStatusUpdater)
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val stopWatchShortcutInfo = getLaunchStopwatchShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(stopWatchShortcutInfo)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchStopwatchShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.start_stopwatch)
        val drawable = resources.getDrawable(R.drawable.shortcut_stopwatch)
        (drawable as LayerDrawable)
            .findDrawableByLayerId(R.id.shortcut_stopwatch_background)
            .applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, SplashActivity::class.java).apply {
            putExtra(OPEN_TAB, TAB_STOPWATCH)
            putExtra(TOGGLE_STOPWATCH, true)
            action = STOPWATCH_TOGGLE_ACTION
        }

        return ShortcutInfo.Builder(this, STOPWATCH_SHORTCUT_ID)
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    override fun onPause() {
        super.onPause()
        homeHandler.removeCallbacks(homeStatusUpdater)
        storeStateVariables()
        if (config.preventPhoneFromSleeping) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        config.lastUsedViewPagerPage = binding.viewPager.currentItem
    }

    override fun onNewIntent(intent: Intent) {
        if (intent.extras?.containsKey(OPEN_TAB) == true) {
            val tabToOpen = intent.getIntExtra(OPEN_TAB, TAB_CLOCK)
            showScreen(getTabIndex(tabToOpen))
            if (tabToOpen == TAB_TIMER) {
                val timerId = intent.getIntExtra(TIMER_ID, INVALID_TIMER_ID)
                (binding.viewPager.adapter as ViewPagerAdapter).updateTimerPosition(timerId)
            }
            if (tabToOpen == TAB_STOPWATCH) {
                if (intent.getBooleanExtra(TOGGLE_STOPWATCH, false)) {
                    (binding.viewPager.adapter as ViewPagerAdapter).startStopWatch()
                }
            }
        }
        super.onNewIntent(intent)
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedBackgroundColor = getProperBackgroundColor()
        storedPrimaryColor = getProperPrimaryColor()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        when {
            requestCode == PICK_AUDIO_FILE_INTENT_ID && resultCode == RESULT_OK && resultData != null -> {
                storeNewAlarmSound(resultData)
            }
        }
    }

    private fun storeNewAlarmSound(resultData: Intent) {
        val newAlarmSound = storeNewYourAlarmSound(resultData)

        when (binding.viewPager.currentItem) {
            TAB_ALARM_INDEX -> getViewPagerAdapter()?.updateAlarmTabAlarmSound(newAlarmSound)
            TAB_TIMER_INDEX -> getViewPagerAdapter()?.updateTimerTabAlarmSound(newAlarmSound)
        }
    }

    fun updateClockTabAlarm() {
        getViewPagerAdapter()?.updateClockTabAlarm()
    }

    private fun getViewPagerAdapter() = binding.viewPager.adapter as? ViewPagerAdapter

    private fun initFragments() {
        val viewPagerAdapter = ViewPagerAdapter(supportFragmentManager)
        binding.viewPager.adapter = viewPagerAdapter
        binding.viewPager.onPageChangeListener {
            binding.mainTabsHolder.getTabAt(it)?.select()
        }

        val tabToOpen = intent.getIntExtra(OPEN_TAB, config.defaultTab)
        intent.removeExtra(OPEN_TAB)
        if (tabToOpen == TAB_TIMER) {
            val timerId = intent.getIntExtra(TIMER_ID, INVALID_TIMER_ID)
            viewPagerAdapter.updateTimerPosition(timerId)
        }

        if (tabToOpen == TAB_STOPWATCH) {
            config.toggleStopwatch = intent.getBooleanExtra(TOGGLE_STOPWATCH, false)
        }

        binding.viewPager.offscreenPageLimit = TABS_COUNT - 1
        binding.viewPager.currentItem = getTabIndex(tabToOpen)
    }

    private fun setupTabs() {
        binding.mainTabsHolder.removeAllTabs()
        val tabDrawables = arrayOf(
            R.drawable.ic_clock_vector,
            R.drawable.ic_alarm_vector,
            R.drawable.ic_stopwatch_vector,
            R.drawable.ic_hourglass_vector
        )
        val tabLabels = arrayOf(
            R.string.clock,
            org.fossify.commons.R.string.alarm,
            R.string.stopwatch,
            R.string.timer
        )

        tabDrawables.forEachIndexed { i, drawableId ->
            binding.mainTabsHolder.newTab()
                .setCustomView(org.fossify.commons.R.layout.bottom_tablayout_item)
                .apply tab@{
                    customView?.let { BottomTablayoutItemBinding.bind(it) }?.apply {
                        tabItemIcon.setImageDrawable(getDrawable(drawableId))
                        tabItemLabel.setText(tabLabels[i])
                        AutofitHelper.create(tabItemLabel)
                        binding.mainTabsHolder.addTab(this@tab)
                    }
                }
        }

        binding.mainTabsHolder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(
                    view = it.customView,
                    isActive = false,
                    drawableId = getDeselectedTabDrawableIds()[it.position]
                )
            },
            tabSelectedAction = {
                binding.viewPager.currentItem = it.position
                updateBottomTabItemColors(
                    view = it.customView,
                    isActive = true,
                    drawableId = getSelectedTabDrawableIds()[it.position]
                )
            }
        )
    }

    private fun setupTabColors() {
        val activeView = binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.customView
        updateBottomTabItemColors(
            view = activeView,
            isActive = true,
            drawableId = getSelectedTabDrawableIds()[binding.viewPager.currentItem]
        )

        getInactiveTabIndexes(binding.viewPager.currentItem).forEach { index ->
            val inactiveView = binding.mainTabsHolder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
        }

        binding.mainTabsHolder.getTabAt(binding.viewPager.currentItem)?.select()
        val bottomBarColor = getBottomNavigationBackgroundColor()
        binding.mainTabsHolder.setBackgroundColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int): List<Int> {
        return arrayListOf(0, 1, 2, 3).filter { it != activeIndex }
    }

    private fun getSelectedTabDrawableIds() = arrayOf(
        R.drawable.ic_clock_filled_vector,
        R.drawable.ic_alarm_filled_vector,
        R.drawable.ic_stopwatch_filled_vector,
        R.drawable.ic_hourglass_filled_vector
    )

    private fun getDeselectedTabDrawableIds() = arrayOf(
        org.fossify.commons.R.drawable.ic_clock_vector,
        R.drawable.ic_alarm_vector,
        R.drawable.ic_stopwatch_vector,
        R.drawable.ic_hourglass_vector
    )

    private fun setupHome() {
        // scroll views take the focus themselves (also when the window gets it back), only the rows should
        binding.mainHome.isFocusable = false
        homeScreens.forEach { (row, index) ->
            row.setOnClickListener {
                lastHomeRow = row
                showScreen(index)
            }
        }

        binding.homeSettings.setOnClickListener {
            lastHomeRow = binding.homeSettings
            launchSettings()
        }

        homeRows.forEach { it.applyDpadFocusHighlight() }
    }

    private fun updateHomeColors() {
        val textColor = getProperTextColor()
        updateTextColors(binding.mainHomeHolder)
        listOf(
            binding.homeClockIcon,
            binding.homeAlarmIcon,
            binding.homeStopwatchIcon,
            binding.homeTimerIcon,
            binding.homeSettingsIcon
        ).forEach { it.applyColorFilter(textColor) }
        homeRows.forEach { it.applyDpadFocusHighlight() }
    }

    private fun showHome() {
        showingHome = true
        binding.viewPager.beGone()
        binding.mainHome.beVisible()
        binding.mainToolbar.title = getString(R.string.app_launcher_name)
        updateHomeStatus()
        updateAlarmStatus()
        (lastHomeRow ?: binding.homeClock).requestFocusFromTouch()
    }

    /** On the phone the window gets the focus only after the home list was shown: focus a row then. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && (currentFocus == null || currentFocus is NestedScrollView)) {
            if (showingHome) {
                (lastHomeRow ?: binding.homeClock).requestFocusFromTouch()
            } else {
                binding.viewPager.requestFocus()
            }
        }
    }

    private fun showScreen(index: Int) {
        showingHome = false
        binding.mainHome.beGone()
        binding.viewPager.beVisible()
        binding.viewPager.setCurrentItem(index, false)
        homeScreens.firstOrNull { it.second == index }?.let { lastHomeRow = it.first }
        binding.mainToolbar.title = getString(
            when (index) {
                TAB_ALARM_INDEX -> org.fossify.commons.R.string.alarm
                TAB_STOPWATCH_INDEX -> R.string.stopwatch
                TAB_TIMER_INDEX -> R.string.timer
                else -> R.string.dpad_clock
            }
        )
        binding.viewPager.post { binding.viewPager.requestFocus() }
    }

    /** Current time, running stopwatch and timer, refreshed every second while the list is shown. */
    private fun updateHomeStatus() {
        if (!showingHome) {
            return
        }

        val pattern = if (config.use24HourFormat) FORMAT_24H else FORMAT_12H
        binding.homeClockStatus.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
        binding.homeStopwatchStatus.text = if (Stopwatch.state == Stopwatch.State.STOPPED) {
            ""
        } else {
            Stopwatch.totalTime.getFormattedDuration()
        }

        timerHelper.getTimers { timers ->
            val remaining = timers.mapNotNull {
                when (val state = it.state) {
                    is TimerState.Running -> state.tick
                    is TimerState.Paused -> state.tick
                    else -> null
                }
            }.minOrNull()

            runOnUiThread {
                binding.homeTimerStatus.text = remaining?.getFormattedDuration() ?: ""
            }
        }
    }

    private fun updateAlarmStatus() {
        getClosestEnabledAlarmString { nextAlarm ->
            runOnUiThread {
                binding.homeAlarmStatus.text = nextAlarm
            }
        }
    }

    private fun showScreenMenu() {
        if (showingHome) {
            return
        }

        val items = getCurrentScreen()?.getDpadMenuItems().orEmpty()
        if (items.isEmpty()) {
            return
        }

        dpadPopupMenu(binding.mainToolbar).apply {
            items.forEachIndexed { index, (title, _) -> menu.add(0, index, index, title) }
            setOnMenuItemClickListener {
                items[it.itemId].second()
                true
            }
            show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // act on key up: a popup, dialog or screen opened on key down would get the key up and stop reacting to keys
        val isKeyUp = event.action == KeyEvent.ACTION_UP && !event.isCanceled
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (isKeyUp) {
                    showScreenMenu()
                }
                return true
            }

            KeyEvent.KEYCODE_BACK -> if (!showingHome) {
                if (isKeyUp) {
                    showHome()
                }
                return true
            }

            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_5 -> if (showingHome) {
                if (isKeyUp) {
                    homeRows[event.keyCode - KeyEvent.KEYCODE_1].performClick()
                }
                return true
            }
        }

        if (!showingHome && getCurrentScreen()?.onDpadKey(event) == true) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    private fun getCurrentScreen() = getViewPagerAdapter()?.getScreen(binding.viewPager.currentItem)

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses =
            LICENSE_NUMBER_PICKER or LICENSE_RTL or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_1_title,
                text = R.string.faq_1_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_1_title_commons,
                text = org.fossify.commons.R.string.faq_1_text_commons
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_4_title_commons,
                text = org.fossify.commons.R.string.faq_4_text_commons
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Deprecated("Remove this method in future releases")
    private fun migrateFirstDayOfWeek() {
        if (config.migrateFirstDayOfWeek) {
            config.migrateFirstDayOfWeek = false
            config.firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek.value
        }
    }

    private fun getTabIndex(tabId: Int): Int {
        return when (tabId) {
            TAB_CLOCK -> TAB_CLOCK_INDEX
            TAB_ALARM -> TAB_ALARM_INDEX
            TAB_STOPWATCH -> TAB_STOPWATCH_INDEX
            TAB_TIMER -> TAB_TIMER_INDEX
            else -> config.lastUsedViewPagerPage
        }
    }
}
