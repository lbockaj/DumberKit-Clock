package org.fossify.clock.adapters

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.ItemAlarmBinding
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.clock.helpers.dpadPopupMenu
import org.fossify.clock.helpers.updateNonRecurringAlarmDay
import org.fossify.clock.interfaces.ToggleAlarmInterface
import org.fossify.clock.models.Alarm
import org.fossify.clock.views.DpadSwitchDrawable
import org.fossify.clock.models.AlarmEvent
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.clock.extensions.getSelectedDaysString
import org.fossify.commons.extensions.move
import org.fossify.commons.helpers.EVERY_DAY_BIT
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.interfaces.StartReorderDragListener
import org.fossify.commons.views.MyRecyclerView
import org.greenrobot.eventbus.EventBus

private const val DISABLED_ALARM_ALPHA = 0.45f

class AlarmsAdapter(
    activity: SimpleActivity,
    private var alarms: ArrayList<Alarm>,
    private val toggleAlarmInterface: ToggleAlarmInterface,
    recyclerView: MyRecyclerView,
    private val editAlarm: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, editAlarm), ItemTouchHelperContract {

    private var startReorderDragListener: StartReorderDragListener

    init {
        setupDragListener(true)
        val touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_alarms

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> deleteItems()
        }
    }

    override fun getSelectableItemCount() = alarms.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = alarms.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = alarms.indexOfFirst { it.id == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {}

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemAlarmBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.bindView(
            any = alarm,
            allowSingleClick = true,
            allowLongClick = false
        ) { itemView, _ ->
            setupView(itemView, alarm, holder)
        }
        bindViewHolder(holder)

        // keypad: OK opens the alarm (the click set up by bindView), holding OK shows its actions instead of Commons'
        // touch selection mode; left / right switch it off / on
        holder.itemView.setOnLongClickListener {
            showAlarmActions(holder.itemView, alarm)
            true
        }
    }

    /** Holding OK on an alarm: switch it on / off, delete it. */
    private fun showAlarmActions(itemView: View, alarm: Alarm) {
        activity.dpadPopupMenu(itemView).apply {
            menu.add(0, 0, 0, if (alarm.isEnabled) R.string.dpad_turn_off else R.string.dpad_turn_on)
            menu.add(0, 1, 1, org.fossify.commons.R.string.delete)
            setOnMenuItemClickListener {
                if (it.itemId == 0) {
                    setAlarmEnabled(ItemAlarmBinding.bind(itemView), alarm, !alarm.isEnabled)
                } else {
                    confirmDelete(alarm)
                }
                true
            }
            show()
        }
    }

    /** Left/right on an alarm switch it off/on, like moving its switch. */
    private fun setAlarmEnabled(binding: ItemAlarmBinding, alarm: Alarm, enabled: Boolean) {
        if (alarm.isEnabled == enabled) {
            return
        }
        binding.alarmSwitch.isChecked = enabled
        toggleAlarm(binding = binding, alarm = alarm)
        showAlarmState(binding, enabled)
    }

    /** The on / off switch picture (see [DpadSwitchDrawable]), and an off alarm's time and days dimmed. */
    private fun showAlarmState(binding: ItemAlarmBinding, enabled: Boolean) {
        val density = resources.displayMetrics.density
        binding.alarmState.apply {
            background = null
            clearColorFilter()
            setImageDrawable(
                DpadSwitchDrawable(
                    isOn = enabled,
                    onColor = properPrimaryColor,
                    offColor = textColor.adjustAlpha(DISABLED_ALARM_ALPHA),
                    knobColor = if (enabled) properPrimaryColor.getContrastColor() else backgroundColor,
                    density = density
                )
            )
            contentDescription = activity.getString(if (enabled) R.string.dpad_turn_off else R.string.dpad_turn_on)
        }

        val alpha = if (enabled) 1f else DISABLED_ALARM_ALPHA
        listOf(binding.alarmTime, binding.alarmDays, binding.alarmLabel).forEach { it.alpha = alpha }
    }

    fun confirmDelete(alarm: Alarm) {
        ConfirmationDialog(activity) {
            alarms.remove(alarm)
            notifyDataSetChanged()
            activity.dbHelper.deleteAlarms(arrayListOf(alarm))
            EventBus.getDefault().post(AlarmEvent.Refresh)
        }
    }

    override fun getItemCount() = alarms.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: ArrayList<Alarm>) {
        alarms = newItems
        notifyDataSetChanged()
        finishActMode()
    }

    private fun deleteItems() {
        val alarmsToRemove = ArrayList<Alarm>()
        val positions = getSelectedItemPositions()
        getSelectedItems().forEach {
            alarmsToRemove.add(it)
        }

        alarms.removeAll(alarmsToRemove)
        removeSelectedItems(positions)
        activity.dbHelper.deleteAlarms(alarmsToRemove)
        EventBus.getDefault().post(AlarmEvent.Refresh)
    }

    private fun getSelectedItems(): ArrayList<Alarm> {
        return alarms.filter { selectedKeys.contains(it.id) } as ArrayList<Alarm>
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, alarm: Alarm, holder: ViewHolder) {
        val isSelected = selectedKeys.contains(alarm.id)
        ItemAlarmBinding.bind(view).apply {
            alarmHolder.isSelected = isSelected
            alarmDragHandle.beVisibleIf(selectedKeys.isNotEmpty())
            alarmDragHandle.applyColorFilter(textColor)
            alarmDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }
            alarmTime.text = activity.getFormattedTime(
                passedSeconds = alarm.timeInMinutes * 60,
                showSeconds = false,
                makeAmPmSmaller = true
            )
            alarmTime.setTextColor(textColor)

            alarmDays.text = getAlarmSelectedDaysString(alarm)
            alarmDays.setTextColor(textColor)

            alarmLabel.text = alarm.label
            alarmLabel.setTextColor(textColor)
            alarmLabel.beVisibleIf(alarm.label.isNotEmpty())

            alarmSwitch.isChecked = alarm.isEnabled
            alarmSwitch.setColors(textColor, properPrimaryColor, backgroundColor)
            // the whole row is focused: OK or left/right switch the alarm, the badge shows its state
            showAlarmState(this, alarm.isEnabled)
            alarmHolder.applyDpadFocusHighlight()
            alarmHolder.setOnKeyListener { _, keyCode, event ->
                val enable = when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> false
                    KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> return@setOnKeyListener false
                }
                // on key up: the first switch shows Commons' warning dialog, opened during a key press it gets no keys
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    setAlarmEnabled(this, alarm, enable)
                }
                true
            }
        }
    }

    private fun toggleAlarm(binding: ItemAlarmBinding, alarm: Alarm) {
        when {
            alarm.isRecurring() -> {
                if (activity.config.wasAlarmWarningShown) {
                    toggleAlarmInterface.alarmToggled(alarm.id, binding.alarmSwitch.isChecked)
                } else {
                    ConfirmationDialog(
                        activity = activity,
                        messageId = org.fossify.commons.R.string.alarm_warning,
                        positive = org.fossify.commons.R.string.ok,
                        negative = 0
                    ) {
                        activity.config.wasAlarmWarningShown = true
                        toggleAlarmInterface.alarmToggled(alarm.id, binding.alarmSwitch.isChecked)
                    }
                }
            }

            else -> {
                updateNonRecurringAlarmDay(alarm)
                activity.dbHelper.updateAlarm(alarm)
                binding.alarmDays.text = getAlarmSelectedDaysString(
                    alarm = alarm, isEnabled = binding.alarmSwitch.isChecked
                )
                toggleAlarmInterface.alarmToggled(alarm.id, binding.alarmSwitch.isChecked)
            }
        }
    }

    private fun getAlarmSelectedDaysString(
        alarm: Alarm, isEnabled: Boolean = alarm.isEnabled,
    ): String {
        if (alarm.isRecurring()) {
            return if (alarm.days == EVERY_DAY_BIT) {
                activity.getString(org.fossify.commons.R.string.every_day)
            } else {
                activity.getSelectedDaysString(alarm.days)
            }
        }

        return when {
            !isEnabled -> resources.getString(R.string.not_scheduled)
            alarm.isToday() -> resources.getString(org.fossify.commons.R.string.today)
            else -> resources.getString(org.fossify.commons.R.string.tomorrow)
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        alarms.move(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        saveAlarmsCustomOrder(alarms)
        if (activity.config.alarmSort != SORT_BY_CUSTOM) {
            activity.config.alarmSort = SORT_BY_CUSTOM
        }
    }

    private fun saveAlarmsCustomOrder(alarms: ArrayList<Alarm>) {
        val alarmsCustomSortingIds = alarms.map { it.id }

        activity.config.alarmsCustomSorting = alarmsCustomSortingIds.joinToString { it.toString() }
    }
}
