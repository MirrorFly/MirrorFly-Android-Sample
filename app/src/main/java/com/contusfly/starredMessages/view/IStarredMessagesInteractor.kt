package com.contusfly.starredMessages.view

import android.app.Activity
import android.content.Context
import android.view.ActionMode
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.api.models.ChatMessage

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
interface IStarredMessagesInteractor {

    /**
     * Get instance of an activity
     *
     * @return Activity Instance of an activity
     */
    fun getActivity(): Context?

    /**
     * Get instance common alert dialog
     *
     * @return CommonAlertDialog Instance common alert dialog
     */
    fun getAlertDialog(): CommonAlertDialog?

    /**
     * Get the clicked action mode
     *
     * @return ActionMode Instance of ActionMode
     */
    fun getActionMode(): ActionMode?

    /**
     * Get the clicked messages list
     *
     * @return List<String> List of Message id
    </String> */
    fun getClickedStarredMessages(): MutableList<String>

    /**
     * Get the selected messages list
     *
     * @return List<ChatMessage> List of Messages
    </ChatMessage> */
    fun getSelectedStarredMessages(): MutableList<ChatMessage>?

    /**
     * Get the list  of messages for the chat view
     *
     * @return List<ChatMessage> List of Messages
    </ChatMessage> */
    fun getChatMessages(): MutableList<ChatMessage>

    /**
     * Update chat adapter
     */
    fun updateAdapter()
}