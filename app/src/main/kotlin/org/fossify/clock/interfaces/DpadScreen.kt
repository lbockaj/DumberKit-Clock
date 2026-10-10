package org.fossify.clock.interfaces

import android.view.KeyEvent

/** A screen opened from the home list: its own keys and the items of the Menu key. */
interface DpadScreen {
    /** Items of the Menu key popup, as (title string id, action). */
    fun getDpadMenuItems(): List<Pair<Int, () -> Unit>> = emptyList()

    /**
     * Focuses the screen's first item when it's opened from the home list, right away (or once its items are loaded,
     * nothing else gets the focus meanwhile). False lets the screen take the focus itself.
     */
    fun focusFirst(): Boolean = false

    /** Keys the screen handles itself before the focused view gets them; true if consumed. */
    fun onDpadKey(event: KeyEvent): Boolean = false
}
