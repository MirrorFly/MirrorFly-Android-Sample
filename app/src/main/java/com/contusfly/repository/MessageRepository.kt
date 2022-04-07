package com.contusfly.repository

import android.util.Log
import com.contus.flycommons.LogMessage
import com.contus.flycommons.models.MessageType
import com.contusfly.TAG
import com.contusfly.getSenderJid
import com.contusfly.isMediaMessage
import com.contusfly.isMediaUploadOrDownload
import com.contusfly.utils.ChatTimeOperations
import com.contusfly.utils.ChatViewUtils
import com.contusfly.utils.Constants
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.models.ChatMessage
import java.text.DateFormatSymbols
import java.util.*
import javax.inject.Singleton
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

@Singleton
class MessageRepository {


    private var containsRecalled = false
    private var containsRecalledSet = false
    private var canBeStarred = true
    private var canBeUnStarred = true
    private var canBeShared = true
    private var canBeSharedSet = false
    private var canBeForwarded = true
    private var canBeForwardedSet = false
    private var canBeDeleted = true
    private var canBeDeletedSet = true
    private var canBeReplied = true
    private var canBeCopied = true
    private var canBeCopiedSet = false
    private var canShowInfo = true

    private fun getMessageListWithDate(messageList: List<ChatMessage>): List<ChatMessage> {
        val dateList = ArrayList<ChatMessage>()
        for (position in messageList.indices) {
            if (position == 0 || isDateChanged(position, messageList)) {
                val message = addDateHeaderMessage(messageList[position])
                if (message.messageId != null)
                    dateList.add(message)
            }
            dateList.add(messageList[position])
        }
        return dateList
    }

    /**
     * Checks the current header id with previous header id
     *
     * @param position Position of the current item
     * @return boolean True if header changed, else false
     */
    private fun isDateChanged(position: Int, mChatData: List<ChatMessage>): Boolean {
        val currentHeaderId: Long = getDateID(mChatData[position])
        val previousHeaderId: Long = getDateID(mChatData[position - 1])
        return currentHeaderId != previousHeaderId
    }

    /**
     * Returns header Id of the
     *
     * @param message message object
     * @return long Header id of the list
     */
    private fun getDateID(message: ChatMessage): Long {
        val chatTimeOperations = ChatTimeOperations()
        return chatTimeOperations.getDateInMilli(message.messageSentTime)
    }

    private fun addDateHeaderMessage(item: ChatMessage): ChatMessage {
        val date: String
        val messageDate = ChatViewUtils().getDateFromTimestamp(item.messageSentTime)
        val calendar = Calendar.getInstance()
        val monthNumber = calendar[Calendar.MONTH]
        val month: String = getMonthForInt(monthNumber)
        val yesterdayDate = calendar[Calendar.DATE] - 1
        val today = month + " " + checkTwoDigitsForDate(calendar[Calendar.DATE]) + "," + " " + +calendar[Calendar.YEAR]
        val yesterday = month + " " + checkTwoDigitsForDate(yesterdayDate) + "," + " " + +calendar[Calendar.YEAR]
        var dateHeaderMessage = ChatMessage()
        if (messageDate.compareTo(today) == 0) {
            date = "Today"
            dateHeaderMessage = createDateHeaderMessageWithDate(date, item)
        } else if (messageDate.compareTo(yesterday) == 0) {
            date = "Yesterday"
            dateHeaderMessage = createDateHeaderMessageWithDate(date, item)
        } else if (!messageDate.contains("1970"))
            dateHeaderMessage = createDateHeaderMessageWithDate(messageDate, item)
        return dateHeaderMessage
    }

    private fun getMonthForInt(num: Int): String {
        var month = ""
        val dateFormatSymbols = DateFormatSymbols()
        val months = dateFormatSymbols.months
        if (num in 0..11)
            month = months[num]
        return month
    }

    private fun checkTwoDigitsForDate(date: Int): String {
        val formatDate: String
        return if (date.toString().length != 2) {
            formatDate = "0$date"
            formatDate
        } else date.toString()
    }

    /**
     * Create date message
     *
     * @param dateText Text to show
     */
    private fun createDateHeaderMessageWithDate(dateText: String, message: ChatMessage): ChatMessage {
        val dateMessageId = "M${message.getSenderJid() + dateText}"
        val dateHeaderMessage = ChatMessage()
        dateHeaderMessage.chatUserJid = message.chatUserJid
        dateHeaderMessage.messageId = dateMessageId + dateText
        dateHeaderMessage.messageSentTime = message.messageSentTime - 100
        dateHeaderMessage.messageTextContent = dateText
        dateHeaderMessage.messageType = MessageType.NOTIFICATION
        return dateHeaderMessage
    }

    fun getMessagesAfter(jid: String, time: Long, messageList: ArrayList<ChatMessage>): List<ChatMessage> {
        val newMessageList = mutableListOf<ChatMessage>()
        val mainList = getMessages(jid)
        val previousMessageIds = mutableListOf<String>()
        messageList.forEach { previousMessageIds.add(it.getMessageId()) }
        for (item in mainList) {
            if (item.getMessageSentTime() > time && !previousMessageIds.contains(item.messageId))
                newMessageList.add(item)
        }
        return newMessageList
    }

    fun getAllMessages(jid: String) = getMessageListWithDate(getMessages(jid))

    private fun initBoolean() {
        containsRecalled = false
        containsRecalledSet = false
        canBeShared = true
        canBeSharedSet = false
        canBeForwarded = true
        canBeForwardedSet = false
        canBeDeleted = true
        canBeDeletedSet = true
        canBeReplied = true
        canBeCopiedSet = false
        canShowInfo = true
    }

    fun handleActionMenuVisibilityValidation(messages: ArrayList<String>): HashMap<String, Boolean> {
        val menuHashMap = HashMap<String, Boolean>()
        initBoolean()

        val availableMessageActions = ChatManager.getMessageActions(messages)
        canBeStarred = !availableMessageActions.canBeFavourite && !availableMessageActions.canBeUnFavourite ||
            availableMessageActions.canBeFavourite && !availableMessageActions.canBeUnFavourite
        canBeUnStarred = availableMessageActions.canBeUnFavourite
        canBeCopied = availableMessageActions.canBeCopied
        canBeForwarded = availableMessageActions.canBeForwarded
        canBeShared = availableMessageActions.canBeShared
        canBeReplied = availableMessageActions.canBeReplied
        canShowInfo = availableMessageActions.canShowMessageInfo

        for (messageId in messages)
            validateMessage(FlyMessenger.getMessageOfId(messageId))

        menuHashMap[Constants.RECALL] = containsRecalled
        menuHashMap[Constants.STAR] = canBeStarred
        menuHashMap[Constants.UNSTAR] = canBeUnStarred
        menuHashMap[Constants.SHARE] = canBeShared
        menuHashMap[Constants.FORWARD] = canBeForwarded
        menuHashMap[Constants.DELETE] = canBeDeleted
        menuHashMap[Constants.REPLY] = canBeReplied
        menuHashMap[Constants.COPY] = canBeCopied
        menuHashMap[Constants.INFO] = canShowInfo
        return menuHashMap
    }

    private fun validateMessage(message: ChatMessage?) {
        message?.let {
            //Recalled Validation
            if (!containsRecalledSet && message.isMessageRecalled()) {
                containsRecalled = true
                containsRecalledSet = true
            }
            //Delete Validation
            if (canBeDeletedSet && message.isMediaUploadOrDownload()){
                canBeDeletedSet = false
                canBeDeleted = false
            }
        }
    }

    fun getMessageForId(mid: String) = FlyMessenger.getMessageOfId(mid)

    fun getMessageForReply(mid: String) = FlyMessenger.getMessageOfId(mid)

    fun deleteUnreadMessageSeparator(jid: String) = FlyMessenger.deleteUnreadMessageSeparatorOfAConversation(jid)

    fun hasUserStarredAnyMessage(jid: String): Boolean {
        val messageList = getMessages(jid)
        messageList.forEach {
            if (it.isMessageStarred())
                return true
        }
        return false
    }

    fun isRecallAvailableForGivenMessages(messages: ArrayList<String>): Pair<Boolean , Boolean> {
        var recallTimeBound = 30
        recallTimeBound *= 1000
        val currentTime = System.currentTimeMillis()
        val recallTimeDifference = (currentTime - recallTimeBound.toLong()) * 1000L
        val messageList = FlyMessenger.getMessagesUsingIds(messages)
        return Pair(messageList.all { it.isMessageSentByMe() && !it.isMessageRecalled() && it.messageSentTime > recallTimeDifference },
            messageList.any { it.isMediaMessage() && it.mediaChatMessage.mediaLocalStoragePath.isNotEmpty() })
    }

    fun getRecalledMessageOfAnUser(jid: String): MutableList<String> {
        val messages = FlyMessenger.getRecalledMessagesOfAConversation(jid)
        val messageIds = mutableListOf<String>()
        messages.forEach {
            messageIds.add(it.messageId)
        }
        return messageIds
    }

    fun getBroadcastMessageID(mid: String): String = mid

    fun getMessages(userJid: String): List<ChatMessage> {
        LogMessage.i(TAG, "getMessages working in ${Thread.currentThread().name}")
        val startTime = System.currentTimeMillis()
        val messageList = FlyMessenger.getMessagesOfJid(userJid)
        LogMessage.i(TAG, "getMessages took: ${System.currentTimeMillis() - startTime} ms")
        return messageList
    }

}