package org.fossify.clock.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import org.fossify.clock.R
import org.fossify.clock.helpers.applyDpadFocusHighlight
import org.fossify.commons.extensions.applyColorFilter

/**
 * Root of a long Commons list dialog (alarm sounds): on keypad phones its OK / Cancel buttons sit under the whole
 * list. Once the dialog is shown, the list gets a green check (OK) and a red cross (Cancel) on its right instead,
 * like the time zones picker: right from any row jumps to the check, left from there goes back to the row.
 * The dialog's own buttons are hidden and pressed by the icons, so the dialog's logic stays as it is.
 */
class DpadSideActionsScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private var saveIcon: ImageView? = null
    private var cancelIcon: ImageView? = null
    private var lastFocusedRow: View? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // the dialog adds its buttons after the content view
        post { addSideActions() }
    }

    private fun addSideActions() {
        if (saveIcon != null) {
            return
        }

        val okButton = rootView.findViewById<View>(android.R.id.button1) ?: return
        val cancelButton = rootView.findViewById<View>(android.R.id.button2)
        val parent = parent as? ViewGroup ?: return

        val size = resources.getDimensionPixelSize(R.dimen.dpad_side_action_size)
        val padding = resources.getDimensionPixelSize(R.dimen.dpad_side_action_padding)
        fun icon(drawable: Int, color: Int, description: Int, button: View?) = ImageView(context).apply {
            setImageResource(drawable)
            applyColorFilter(ContextCompat.getColor(context, color))
            contentDescription = context.getString(description)
            setPadding(padding, padding, padding, padding)
            isFocusable = true
            applyDpadFocusHighlight()
            setOnClickListener { button?.performClick() }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT) {
                    return@setOnKeyListener false
                }
                if (event.action == KeyEvent.ACTION_DOWN) {
                    lastFocusedRow?.takeIf { it.isShown }?.requestFocus()
                }
                true
            }
        }

        val save = icon(org.fossify.commons.R.drawable.ic_check_vector, R.color.dpad_save_green, org.fossify.commons.R.string.save, okButton)
        val cancel = icon(org.fossify.commons.R.drawable.ic_cross_vector, R.color.dpad_cancel_red, org.fossify.commons.R.string.cancel, cancelButton)
        // up/down only switch between the two icons
        save.id = View.generateViewId()
        cancel.id = View.generateViewId()
        save.nextFocusUpId = cancel.id
        save.nextFocusDownId = cancel.id
        cancel.nextFocusUpId = save.id
        cancel.nextFocusDownId = save.id

        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(save, LinearLayout.LayoutParams(size, size))
            addView(cancel, LinearLayout.LayoutParams(size, size).apply { topMargin = padding })
        }

        // [this list | check / cross], in place of the list
        val index = parent.indexOfChild(this)
        val params = layoutParams
        parent.removeView(this)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(this@DpadSideActionsScrollView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(side, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
            })
        }
        parent.addView(row, index, params)

        (okButton.parent as? View)?.visibility = View.GONE
        saveIcon = save
        cancelIcon = cancel
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val save = saveIcon
        if (save != null && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                lastFocusedRow = findFocus()
                save.requestFocus()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
