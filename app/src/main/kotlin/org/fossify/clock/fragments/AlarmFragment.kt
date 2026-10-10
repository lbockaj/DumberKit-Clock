package org.fossify.clock.fragments

import androidx.recyclerview.widget.RecyclerView
import org.fossify.clock.helpers.focusedAdapterPosition
import org.fossify.clock.helpers.focusItem
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.clock.R
import org.fossify.clock.interfaces.DpadScreen
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.clock.activities.MainActivity
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.adapters.AlarmsAdapter
import org.fossify.clock.databinding.FragmentAlarmBinding
import org.fossify.clock.dialogs.ChangeAlarmSortDialog
import org.fossify.clock.dialogs.EditAlarmDialog
import org.fossify.clock.extensions.alarmController
import org.fossify.clock.extensions.cancelAlarmClock
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.createNewAlarm
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.firstDayOrder
import org.fossify.clock.extensions.handleFullScreenNotificationsPermission
import org.fossify.clock.extensions.updateWidgets
import org.fossify.clock.helpers.DEFAULT_ALARM_MINUTES
import org.fossify.clock.helpers.SORT_BY_ALARM_TIME
import org.fossify.clock.helpers.SORT_BY_DATE_AND_TIME
import org.fossify.clock.helpers.getTomorrowBit
import org.fossify.clock.interfaces.ToggleAlarmInterface
import org.fossify.clock.models.Alarm
import org.fossify.clock.models.AlarmEvent
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_CREATED
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.AlarmSound
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AlarmFragment : Fragment(), ToggleAlarmInterface, DpadScreen {
    private var wereAlarmsLoaded = false
    private var focusFirstWhenLoaded = false
    private var alarms = ArrayList<Alarm>()
    private var currentEditAlarmDialog: EditAlarmDialog? = null

    private lateinit var binding: FragmentAlarmBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setupViews()
    }

    fun showSortingDialog() {
        ChangeAlarmSortDialog(activity as SimpleActivity) {
            setupAlarms()
        }
    }

    private fun setupViews() {
        binding.apply {
            requireContext().updateTextColors(alarmFragment)
            // the scroll view would take the focus itself, only the alarms and the "+" row should
            alarmFragment.isFocusable = false
            alarmsAdd.dpadAddRowLabel.setText(R.string.add_alarm)
            alarmsAdd.dpadAddRowIcon.applyColorFilter(requireContext().getProperTextColor())
            alarmsAdd.root.applyDpadFocusHighlight()
            alarmsAdd.root.setOnClickListener { addAlarm() }
        }

        setupAlarms()
    }

    /** The first alarm (or the "+" row without alarms); opened before they're loaded, it waits for them. */
    override fun focusFirst(): Boolean {
        if (!wereAlarmsLoaded || view == null) {
            focusFirstWhenLoaded = true
            return true
        }

        binding.alarmFragment.scrollTo(0, 0)
        if (alarms.isEmpty() || !binding.alarmsList.focusItem(0)) {
            binding.alarmsAdd.root.requestFocus()
        }
        return true
    }

    /** Delete for the focused alarm (OK opens it, left / right switch it), then add and sort. */
    override fun getDpadMenuItems(): List<Pair<Int, () -> Unit>> {
        val items = ArrayList<Pair<Int, () -> Unit>>()
        val focusedAlarm = alarms.getOrNull(binding.alarmsList.focusedAdapterPosition())
        val adapter = binding.alarmsList.adapter as? AlarmsAdapter
        if (focusedAlarm != null && adapter != null) {
            items += org.fossify.commons.R.string.delete to { adapter.confirmDelete(focusedAlarm) }
        }
        items += R.string.add_alarm to ::addAlarm
        items += R.string.dpad_sort to ::showSortingDialog
        return items
    }

    fun addAlarm() {
        val newAlarm = binding.root.context.createNewAlarm(DEFAULT_ALARM_MINUTES, 0)
        newAlarm.isEnabled = true
        newAlarm.days = getTomorrowBit()
        openEditAlarm(newAlarm)
    }

    private fun getSortedAlarms(callback: (alarms: ArrayList<Alarm>) -> Unit) {
        val safeContext = context ?: return
        ensureBackgroundThread {
            var newAlarms = context?.dbHelper?.getAlarms()
            if (newAlarms == null) {
                activity?.runOnUiThread {
                    callback(arrayListOf())
                }
                return@ensureBackgroundThread
            }

            when (safeContext.config.alarmSort) {
                SORT_BY_ALARM_TIME -> newAlarms.sortBy { it.timeInMinutes }
                SORT_BY_DATE_CREATED -> newAlarms.sortBy { it.id }
                SORT_BY_DATE_AND_TIME -> newAlarms.sortWith(compareBy<Alarm> {
                    safeContext.firstDayOrder(it.days)
                }.thenBy {
                    it.timeInMinutes
                })

                SORT_BY_CUSTOM -> {
                    val customAlarmsSortOrderString = activity?.config?.alarmsCustomSorting
                    if (customAlarmsSortOrderString == "") {
                        newAlarms.sortBy { it.id }
                    } else {
                        val customAlarmsSortOrder: List<Int> =
                            customAlarmsSortOrderString?.split(", ")?.map { it.toInt() }!!
                        val alarmsIdValueMap = newAlarms.associateBy { it.id }

                        val sortedAlarms: ArrayList<Alarm> = ArrayList()
                        customAlarmsSortOrder.map { id ->
                            if (alarmsIdValueMap[id] != null) {
                                sortedAlarms.add(alarmsIdValueMap[id] as Alarm)
                            }
                        }

                        newAlarms =
                            (sortedAlarms + newAlarms.filter { it !in sortedAlarms }) as ArrayList<Alarm>
                    }
                }
            }

            activity?.runOnUiThread {
                callback(newAlarms)
            }
        }
    }

    private fun setupAlarms() {
        getSortedAlarms { sortedAlarms ->
            val oldAlarms = alarms
            alarms = sortedAlarms
            val safeActivity = activity as? SimpleActivity ?: return@getSortedAlarms
            var currAdapter = binding.alarmsList.adapter as? AlarmsAdapter
            if (currAdapter == null) {
                currAdapter = AlarmsAdapter(
                    activity = safeActivity,
                    alarms = alarms,
                    toggleAlarmInterface = this,
                    recyclerView = binding.alarmsList
                ) {
                    openEditAlarm(it as Alarm)
                }.apply {
                    binding.alarmsList.adapter = this
                }
            } else {
                // a refresh (e.g. after switching an alarm on) rebinds every row and the focus would jump to the
                // first alarm: keep it on the same alarm
                val focusedAlarmId = (binding.alarmsList.adapter as? AlarmsAdapter)
                    ?.let { binding.alarmsList.focusedAdapterPosition() }
                    ?.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { oldAlarms.getOrNull(it)?.id }
                currAdapter.apply {
                    updatePrimaryColor()
                    updateBackgroundColor(safeActivity.getProperBackgroundColor())
                    updateTextColor(safeActivity.getProperTextColor())
                    updateItems(alarms)
                }
                val newPosition = alarms.indexOfFirst { it.id == focusedAlarmId }
                if (newPosition != -1) {
                    binding.alarmsList.post { binding.alarmsList.focusItem(newPosition) }
                }
            }
            binding.alarmsPlaceholder.beVisibleIf(alarms.isEmpty())

            wereAlarmsLoaded = true
            if (focusFirstWhenLoaded) {
                focusFirstWhenLoaded = false
                focusFirst()
            }
        }
    }

    private fun openEditAlarm(alarm: Alarm) {
        currentEditAlarmDialog = EditAlarmDialog(activity as SimpleActivity, alarm) {
            alarm.id = it
            currentEditAlarmDialog = null
            setupAlarms()
            checkAlarmState(alarm)
        }
    }

    override fun alarmToggled(id: Int, isEnabled: Boolean) {
        (activity as SimpleActivity).handleFullScreenNotificationsPermission { granted ->
            if (granted) {
                if (requireContext().dbHelper.updateAlarmEnabledState(id, isEnabled)) {
                    val alarm = alarms.firstOrNull { it.id == id }
                        ?: return@handleFullScreenNotificationsPermission
                    alarm.isEnabled = isEnabled
                    checkAlarmState(alarm)
                    if (!alarm.isEnabled && alarm.oneShot) {
                        requireContext().dbHelper.deleteAlarms(arrayListOf(alarm))
                        setupAlarms()
                    }
                } else {
                    requireActivity().toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                requireContext().updateWidgets()
            } else {
                setupAlarms()
            }
        }
    }

    private fun checkAlarmState(alarm: Alarm) {
        val activity = activity as? MainActivity ?: return
        if (alarm.isEnabled) {
            activity.alarmController.scheduleNextOccurrence(alarm = alarm, showToasts = true)
        } else {
            activity.cancelAlarmClock(alarm)
        }
        activity.updateClockTabAlarm()
    }

    fun updateAlarmSound(alarmSound: AlarmSound) {
        currentEditAlarmDialog?.updateSelectedAlarmSound(alarmSound)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(@Suppress("unused") event: AlarmEvent.Refresh) {
        setupAlarms()
    }
}
