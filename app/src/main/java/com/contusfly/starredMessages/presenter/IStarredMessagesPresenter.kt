package com.contusfly.starredMessages.presenter

import com.contusfly.starredMessages.view.IStarredMessagesView

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
interface IStarredMessagesPresenter {
    /**
     * Attach the chat view into the presenter for the communication between Activity and the
     * Presenter
     *
     * @param iChatView Instance of the IStarredMessagesView
     */
    fun attach(iChatView: IStarredMessagesView?)

    /**
     * Detach the IStarredMessagesView from the presenter
     */
    fun detach()

    /**
     * Sets the recycler chat adapter from the list which we get it from the database.
     */
    fun setChatAdapter()

    /**
     * Update list data in the chat view and can refresh it using this method
     *
     * @param chatMsgId The chat msg id
     */
    fun updateList(chatMsgId: String?)

    /**
     * Prepares Action mode for selected chat items
     */
    fun prepareActionMode()

    /**
     * Refresh the recycler view from the selected messages in the adapter
     */
    fun refreshSelectedMessages()
}