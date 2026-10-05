package org.fossify.clock.dialogs

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.adapters.SelectTimeZonesAdapter
import org.fossify.clock.databinding.DialogSelectTimeZonesBinding
import org.fossify.clock.extensions.config
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.clock.helpers.focusItem
import org.fossify.clock.helpers.focusedAdapterPosition
import org.fossify.clock.helpers.getAllTimeZones
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import java.util.TimeZone

class AddTimeZonesDialog(val activity: SimpleActivity, private val callback: () -> Unit) {
    private val binding = DialogSelectTimeZonesBinding.inflate(activity.layoutInflater)
    private val timeZones = getAllTimeZones()
    private var dialog: AlertDialog? = null
    private var lastListPosition = 0

    init {
        binding.selectTimeZonesList.adapter = SelectTimeZonesAdapter(activity, timeZones)

        // keypad phones: Save / Cancel are the green check and red cross on the right instead of buttons at the bottom
        // of a long list (Back cancels too)
        setupSideActions()
        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                    lastListPosition = getStartPosition()
                    binding.selectTimeZonesList.post {
                        // the start row a third down the list rather than at its very bottom
                        val list = binding.selectTimeZonesList
                        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(lastListPosition, list.height / 3)
                        list.post { list.focusItem(lastListPosition) }
                    }
                    alertDialog.setOnKeyListener { _, keyCode, event -> handleSideKeys(keyCode, event) }
                }
            }
    }

    /** The first picked time zone, or the phone's own one (or one with its offset), so the long list doesn't have to be walked from the top. */
    private fun getStartPosition(): Int {
        val selected = activity.config.selectedTimeZones
        val firstSelected = timeZones.indexOfFirst { selected.contains(it.id.toString()) }
        if (firstSelected != -1) {
            return firstSelected
        }

        // the phone's zone itself (e.g. Europe/Bratislava) is often not in the list, then one with the same offset
        val ownZone = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val sameZone = timeZones.indexOfFirst { it.zoneName == ownZone.id }
        if (sameZone != -1) {
            return sameZone
        }
        val ownOffset = ownZone.getOffset(now)
        return timeZones.indexOfFirst { TimeZone.getTimeZone(it.zoneName).getOffset(now) == ownOffset }.coerceAtLeast(0)
    }

    private fun setupSideActions() {
        binding.apply {
            selectTimeZonesSave.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_save_green))
            selectTimeZonesCancel.applyColorFilter(ContextCompat.getColor(activity, R.color.dpad_cancel_red))
            listOf(selectTimeZonesSave, selectTimeZonesCancel).forEach { it.applyDpadFocusHighlight() }

            selectTimeZonesSave.setOnClickListener {
                dialogConfirmed()
                dialog?.dismiss()
            }
            selectTimeZonesCancel.setOnClickListener { dialog?.dismiss() }
        }
    }

    /** Right from any row jumps to Save, left from there back to the row. */
    private fun handleSideKeys(keyCode: Int, event: KeyEvent): Boolean {
        val list = binding.selectTimeZonesList
        val onSide = binding.selectTimeZonesSave.isFocused || binding.selectTimeZonesCancel.isFocused
        val target = when {
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && list.hasFocus() -> binding.selectTimeZonesSave
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && onSide -> return true
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT && onSide -> null
            else -> return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (target != null) {
                lastListPosition = list.focusedAdapterPosition().coerceAtLeast(0)
                target.requestFocus()
            } else {
                list.focusItem(lastListPosition)
            }
        }
        return true
    }

    private fun dialogConfirmed() {
        val adapter = binding.selectTimeZonesList.adapter as? SelectTimeZonesAdapter
        val selectedTimeZones = adapter?.selectedKeys?.map { it.toString() }?.toHashSet() ?: LinkedHashSet()
        activity.config.selectedTimeZones = selectedTimeZones
        callback()
    }
}
