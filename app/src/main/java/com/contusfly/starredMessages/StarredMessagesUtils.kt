package com.contusfly.starredMessages

import android.content.Context
import android.graphics.PorterDuff
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.contusfly.R

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object StarredMessagesUtils {

    /**
     * Set the action menu for the long press menu
     *
     * @param context Context
     * @param mode    Instance of the Alert dialog action mode
     * @param menu    Instance of Menu
     * @return Menu Configured action menu
     */
    fun configureStarredMenuActionMode(context: Context?, mode: ActionMode, menu: Menu): Menu {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.menu_context_mode, menu)
        menu.findItem(R.id.action_delete).isVisible = true
        menu.findItem(R.id.action_info).isVisible = true
        menu.findItem(R.id.action_share).isVisible = true
        menu.findItem(R.id.action_favourite).isVisible = false
        menu.findItem(R.id.action_unfavourite).isVisible = true
        menu.findItem(R.id.action_forward).isVisible = true
        menu.findItem(R.id.action_forward).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_copy).isVisible = true
        menu.findItem(R.id.action_mute).isVisible = false
        menu.findItem(R.id.action_unmute).isVisible = false
        menu.findItem(R.id.action_un_pin).isVisible = false
        menu.findItem(R.id.action_pin).isVisible = false
        menu.findItem(R.id.action_archive_chat).isVisible = false
        menu.findItem(R.id.action_mark_as_read).isVisible = false
        menu.findItem(R.id.action_mark_as_unread).isVisible = false
        menu.findItem(R.id.action_add_chat_shortcuts).isVisible = false
        menu.findItem(R.id.action_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_share).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_unfavourite).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_delete).icon.setColorFilter(ContextCompat.getColor(context!!, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_info).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_share).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_unfavourite).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_forward).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_copy).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        menu.findItem(R.id.action_delete).icon.setColorFilter(ContextCompat.getColor(context, R.color.color_text),
                PorterDuff.Mode.MULTIPLY)
        return menu
    }
}