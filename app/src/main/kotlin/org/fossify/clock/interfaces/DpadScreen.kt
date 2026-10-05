package org.fossify.clock.interfaces

import android.view.KeyEvent

/** A screen opened from the home list: its own keys and the items of the Menu key. */
interface DpadScreen {
    /** Items of the Menu key popup, as (title string id, action). */
    fun getDpadMenuItems(): List<Pair<Int, () -> Unit>> = emptyList()

    /** Keys the screen handles itself before the focused view gets them; true if consumed. */
    fun onDpadKey(event: KeyEvent): Boolean = false
}
