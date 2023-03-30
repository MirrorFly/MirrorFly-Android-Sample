package com.contusfly.starredMessages.presenter

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
interface IStarredMessagesInteractor {
    /**
     * Attach the chat view into the presenter for the communication between Activity and the
     * Presenter
     *
     * @param iChatInteractor Instance of the IStarredMessagesInteractor
     */
    fun attach(iChatInteractor: com.contusfly.starredMessages.view.IStarredMessagesInteractor?)

    /**
     * Handle the common alert dialog response
     */
    fun handleDialogResponse()

    /**
     * Alert dialog to show while the user wants to delete message(s)
     *
     * @return boolean True if user wants to delete message
     */
    fun deleteMessageAlert(): Boolean
}