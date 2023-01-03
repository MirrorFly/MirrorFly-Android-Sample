package com.contusfly.starredMessages.presenter

import com.contusfly.TAG
import com.contusfly.starredMessages.view.IStarredMessagesView
import com.contusfly.utils.LogMessage
import com.contusflysdk.api.models.ChatMessage

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object Utils {

    /**
     * Check the message id and add it to the list for the particular message ID
     *
     * @param chatMsgId Message id of the message item.
     * @param message   Instance of the Message
     */
    fun checkMsgID(starredMessagesView: IStarredMessagesView, chatMsgId: String?, message: ChatMessage) {
        try {
            var i = 0
            val length: Int = starredMessagesView.getChatMessages().size
            while (i < length) {
                val item: ChatMessage = starredMessagesView.getChatMessages()[i]
                if (item.getMessageId().equals(chatMsgId, ignoreCase = true)) {
                    starredMessagesView.getChatMessages().set(i, message)
                    break
                }
                i++
            }
        } catch (e: Exception) {
            LogMessage.e(TAG, e)
        }
    }
}