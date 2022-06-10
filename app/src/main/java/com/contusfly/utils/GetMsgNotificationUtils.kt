package com.contusfly.utils

import android.content.Context
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import com.contus.flycommons.models.MessageType
import com.contusfly.R
import com.contusfly.utils.NotifyRefererUtils.getGroupUserAppendedText
import com.contusfly.utils.NotifyRefererUtils.hasMultipleSenders
import com.contusflysdk.api.contacts.ContactManager.getProfileDetails
import com.contusflysdk.api.models.ChatMessage
import java.util.*

/**
 * This Class contains all the common operations for creating and showing notification
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 2.0
 */
object GetMsgNotificationUtils {
    private const val deleted_message = "This message was deleted"

    /**
     * Returns the message summary
     *
     * @param message Instance of message
     * @return String Summary of the message
     */
    private fun getMessageSummary(message: ChatMessage): String {
        return if (MessageType.TEXT == message.getMessageType() || MessageType.NOTIFICATION == message.getMessageType()) if (message.isMessageRecalled()) deleted_message else message.getMessageTextContent() else if (MessageType.IMAGE == message.getMessageType() || MessageType.VIDEO == message.getMessageType()) {
            if (message.getMediaChatMessage().getMediaCaptionText().isNotEmpty()) message.getMediaChatMessage().getMediaCaptionText() else message.getMessageType().name.toUpperCase(Locale.getDefault())
        } else message.getMessageType().name.toUpperCase(Locale.getDefault())
    }

    /**
     * Returns the message summary
     *
     * @param message Instance of message
     * @return String Summary of the message
     */
    internal fun getMessageSummary(context: Context, message: ChatMessage): String {
        return if (MessageType.TEXT == message.getMessageType() || MessageType.NOTIFICATION == message.getMessageType()) if (message.isMessageRecalled()) deleted_message else message.getMessageTextContent() else if (message.isMessageRecalled()) deleted_message else getMediaMessageContent(context, message)
    }

    private fun getMediaMessageContent(context: Context, message: ChatMessage): String {
        val contentBuilder = StringBuilder()
        when (message.getMessageType()) {
            MessageType.AUDIO -> contentBuilder.append(context.resources
                    .getString(R.string.audio_emoji)).append(" ").append("Audio")
            MessageType.CONTACT ->
                contentBuilder.append(context.resources
                        .getString(R.string.contact_emoji)).append(" ").append("Contact")
            MessageType.DOCUMENT ->
                contentBuilder.append(context.resources
                        .getString(R.string.file_emoji)).append(" ").append("File")
            MessageType.IMAGE -> contentBuilder.append(context.resources.getString(R.string.image_emoji)).append(" ")
                    .append(if (message.getMediaChatMessage().getMediaCaptionText() != null && message.getMediaChatMessage().getMediaCaptionText().isNotEmpty()) message.getMediaChatMessage().getMediaCaptionText() else "Image")
            MessageType.LOCATION ->
                contentBuilder.append(context.resources
                        .getString(R.string.location_emoji)).append(" ").append("Location")
            MessageType.VIDEO -> contentBuilder.append(context.resources.getString(R.string.video_emoji)).append(" ")
                    .append(if (message.getMediaChatMessage().getMediaCaptionText() != null && message.getMediaChatMessage().getMediaCaptionText().isNotEmpty()) message.getMediaChatMessage().getMediaCaptionText() else "Video")
            else -> {
                // No Implementation Needed
            }
        }
        return contentBuilder.toString()
    }

    /**
     * Loads messages in inbox style
     *
     * @param context        Instance of Context
     * @param notBuilder     Instance of NotificationCompat.Builder
     * @param unseenMessages List of unread messages
     * @return String Jid of the user if single sender
     */
    internal fun getMessagesInboxNotification(context: Context,
                                              notBuilder: NotificationCompat.Builder,
                                              unseenMessages: List<ChatMessage>): String {
        val unseenMessagesForNotification = getLastNMessages(unseenMessages, 7)
        val senderDisplayNames: MutableMap<String, String?> = HashMap()
        val hasMultipleSenders = hasMultipleSenders(unseenMessagesForNotification)
        val inboxStyle = NotificationCompat.InboxStyle()
        val size = unseenMessagesForNotification.size
        for (i in 0 until size) {
            val message = unseenMessagesForNotification[i]
            val messageFrom = message.getChatUserJid()
            if (!isMuteCheck(messageFrom)) {
                if (hasMultipleSenders) {
                    var sender: String?
                    if (senderDisplayNames.containsKey(messageFrom)) {
                        sender = senderDisplayNames[messageFrom]
                    } else {
                        val profileDetails = getProfileDetails(messageFrom)
                        sender = profileDetails!!.name
                        senderDisplayNames[messageFrom] = sender
                    }
                    val messageContent = sender + " : " + getMessageSummary(message)
                    inboxStyle.addLine(getGroupUserAppendedText(message,
                            messageContent, "@"))
                } else {
                    val messageContent = getMessageSummary(context, message)
                    inboxStyle.addLine(getGroupUserAppendedText(message,
                            messageContent, ":"))
                }
            }
        }
        val summaryText = String.format(context.getString(R.string.unseen_message), getUnreadMsgCountExcludingMuteChat(unseenMessages))
        inboxStyle.setSummaryText(summaryText)
        notBuilder.setContentText(summaryText)
        var jid = Constants.EMPTY_STRING
        if (hasMultipleSenders) {
            val title = context.getString(R.string.title_app_name)
            inboxStyle.setBigContentTitle(title)
            notBuilder.setContentTitle(title)
        } else {
            jid = unseenMessagesForNotification[0].getChatUserJid()
            val profileDetails = getProfileDetails(jid)
            val title = profileDetails!!.name
            inboxStyle.setBigContentTitle(title)
            notBuilder.setContentTitle(title)
        }
        notBuilder.setStyle(inboxStyle)
        return jid
    }

    private fun isMuteCheck(jid: String?): Boolean {
        if (!TextUtils.isEmpty(jid)) {
            val profileDetails = getProfileDetails(jid!!)
            return profileDetails != null && profileDetails.isMuted
        }
        return false
    }

    private fun getUnreadMsgCountExcludingMuteChat(unseenMessages: List<ChatMessage>): Int {
        var count = 0
        for (i in unseenMessages.indices) {
            if (!isMuteCheck(unseenMessages[i].getChatUserJid())) {
                count++
            }
        }
        return if (count > 0) {
            count
        } else unseenMessages.size
    }

    private fun getLastNMessages(messages: List<ChatMessage>, maxMessagesCount: Int): List<ChatMessage> {
        val size = messages.size
        return if (size < maxMessagesCount) {
            messages
        } else {
            messages.subList(size - maxMessagesCount, size)
        }
    }
}