package com.contusfly.starredMessages.presenter

import com.contus.flycommons.models.MessageType
import com.contusfly.R
import com.contusfly.starredMessages.view.IStarredMessagesView
import com.contusfly.utils.MediaChecker
import com.contusfly.utils.MediaChecker.isMediaAvailable
import com.contusflysdk.api.FlyMessenger.getMessageOfId

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object PresenterMediaUtils {

    /**
     * Prepare the single item menu for differentiate media end text to copyFiles or share.
     */
    fun prepareSingleMenuItem(starredMessagesView: IStarredMessagesView) {
        val message = getMessageOfId(starredMessagesView.getClickedStarredMessages()!![0]!!)

        /**
         * Set copyFiles or share in long press.
         */
        val getActionCopy = MessageType.TEXT == message!!.getMessageType()
        starredMessagesView.getMenu()!!.findItem(R.id.action_copy).isVisible = getActionCopy
        val getActionShare = MediaChecker.isMediaType(message.getMessageType()) && isMediaAvailable(message)
        starredMessagesView.getMenu()!!.findItem(R.id.action_share).isVisible = getActionShare
        if (MediaChecker.isMediaType(message.getMessageType()) && !isMediaAvailable(message))
            starredMessagesView.getMenu()!!.findItem(R.id.action_forward).isVisible = false
    }
}