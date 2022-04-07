package com.contusfly.starredMessages.view

import android.app.Activity
import android.view.ActionMode
import android.view.Menu
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.contusfly.starredMessages.adapter.StarredMessagesAdapter
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CustomRecyclerView
import com.contusflysdk.api.models.ChatMessage

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
interface IStarredMessagesView {

    /**
     * Get the Context of the activity.
     *
     * @return Activity Instance of the Context
     */
    fun getContext(): Activity?

    /**
     * Get the list  of messages for the chat view
     *
     * @return List<Message> List of Messages
    </Message> */
    fun getChatMessages(): MutableList<ChatMessage>

    /**
     * Set the chat messages in the chat view
     *
     * @param messages List of Messages
     */
    fun setChatMessages(messages: MutableList<ChatMessage>)

    /**
     * Get the Recycler view for the chat view
     *
     * @return CustomRecyclerView Instance of the CustomRecyclerView
     */
    fun getChatRecylerView(): CustomRecyclerView?

    /**
     * Get the Adapter of the chat view
     *
     * @return StarredMessagesAdapter Instance of the StarredMessagesAdapter
     */
    fun getChatAdapter(): StarredMessagesAdapter?

    /**
     * Get the Instance of the CommonAlertDialog
     *
     * @return CommonAlertDialog Instance of the CommonAlertDialog
     */
    fun getAlertDialog(): CommonAlertDialog?

    /**
     * Get the root layout of the page
     *
     * @return RelativeLayout Instance of the RelativeLayout
     */
    fun getRootLayout(): RelativeLayout?

    /**
     * Get the clicked messages list
     *
     * @return List<String> List of Message id
    </String> */
    fun getClickedStarredMessages(): List<String>?

    /**
     * Get the clicked action mode
     *
     * @return ActionMode Instance of ActionMode
     */
    fun getActionMode(): ActionMode?

    /**
     * Get the menu in chat view
     *
     * @return Menu Instance of the Menu
     */
    fun getMenu(): Menu?

    /**
     * Get the linear layout manager
     *
     * @return LinearLayoutManager Layout manager
     */
    fun getLayoutManager(): LinearLayoutManager?
}