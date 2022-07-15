package com.contusfly.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.contusfly.isTextMessage
import com.contusfly.repository.MessageRepository
import com.contusfly.utils.Constants
import com.contusfly.utils.LogMessage
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.utils.SharedPreferenceManager
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseTextMessage
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestion
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

class ChatParentViewModel @Inject
constructor(private val messageRepository: MessageRepository) : ViewModel() {

    /**
     * random generate string for user differntiation
     */
    private val remoteUserId = UUID.randomUUID().toString()

    /**
     * list of smart reply suggestion
     */
    private val suggestions = MediatorLiveData<List<SmartReplySuggestion>>()

    /**
     * input list of message provided to the ML smart reply model
     */
    private val messageList = MutableLiveData<List<ChatMessage>>()

    private var toUser: String? = null

    val groupParticipantsName = MutableLiveData<String>()

    /**
     * get the message given to the smart reply
     *
     * @return
     */
    val messages: LiveData<List<ChatMessage>>
        get() = messageList

    /**
     * constructor for the class
     *
     *
     * This class is to initiate suggestion generator
     */
    init {
        initSuggestionsGenerator()
    }

    companion object {
        private val SMART_REPLY_EXCEPTION = "Not running smart reply!"
    }

    /**
     * get the suggestion from the ML smart reply api
     *
     * @return
     */
    fun getSuggestions(): LiveData<List<SmartReplySuggestion>> {
        return suggestions
    }

    /**
     * add message to the Ml model
     *
     * @param message add the received and sent messages
     */
    fun addMessage(message: ChatMessage?, toUser: String) {
        this.toUser = toUser
        var list: MutableList<ChatMessage>? = messageList.value as MutableList<ChatMessage>?
        if (list == null)
            list = ArrayList()
        if (message != null) {
            list.add(message)
            messageList.postValue(list)
        }
    }

    /**
     * clear the suggestions
     */
    fun clearSuggestions() {
        suggestions.postValue(ArrayList())
    }

    fun removeMessages() {
        messageList.postValue(ArrayList())
    }

    /**
     * initialise the suggestion generator
     */
    private fun initSuggestionsGenerator() {
        suggestions.addSource(messageList) { list ->
            if (list.isNotEmpty())
                generateReplies(list).addOnSuccessListener { result ->
                    suggestions.postValue(result)
                }
        }
    }

    /**
     * task for generating the suggestions
     *
     * @param messages list of messages given to the ml kit
     * @return reply suggestions
     */
    private fun generateReplies(messages: List<ChatMessage>?): Task<List<SmartReplySuggestion>> {
        if (messages != null && messages.isNotEmpty()) {
            LogMessage.d("TAG", messages.size.toString())
            val lastMessage = messages[messages.size - 1]
            if (lastMessage.isMessageSentByMe()
                    || !lastMessage.isTextMessage()
                    || lastMessage.isMessageRecalled()) {
                LogMessage.d("smartReply", SMART_REPLY_EXCEPTION)
                return Tasks.forException(Exception(SMART_REPLY_EXCEPTION))
            } else if (lastMessage.getChatUserJid() != null && lastMessage.getChatUserJid() == toUser) {
                return createSmartReply(lastMessage)
            }
        }
        return Tasks.forException(Exception(SMART_REPLY_EXCEPTION))
    }

    private fun createSmartReply(lastMessage: ChatMessage): Task<List<SmartReplySuggestion>> {
        val chatHistory = ArrayList<FirebaseTextMessage>()
        if (lastMessage.getChatUserJid() == SharedPreferenceManager.getCurrentUserJid() && lastMessage.isMessageSentByMe())
            chatHistory.add(FirebaseTextMessage.createForLocalUser(lastMessage.messageTextContent, System.currentTimeMillis()))
        else
            chatHistory.add(FirebaseTextMessage.createForRemoteUser(lastMessage.messageTextContent, System.currentTimeMillis(), remoteUserId))
        return FirebaseNaturalLanguage.getInstance().smartReply
            .suggestReplies(chatHistory).continueWith { task -> task.result!!.suggestions }
    }

    private lateinit var toUserJid: String

    fun setUnSentMessageForAnUser(jid: String, unsentMessage: String) = FlyMessenger
            .saveUnsentMessage(jid, unsentMessage)

    fun getUnSentMessageForAnUser(jid: String) = FlyMessenger.getUnsentMessageOfAJid(jid)


    fun handleActionMenuVisibility(messageIds: ArrayList<String>): HashMap<String, Boolean> = messageRepository
            .handleActionMenuVisibilityValidation(messageIds)

    fun setUserJid(jid: String) {
        toUserJid = jid
    }

    fun getMessagesAfterThisMessage(jid: String, time: Long, messageList: ArrayList<ChatMessage>): List<ChatMessage> {
        return messageRepository.getMessagesAfter(jid, time, messageList)
    }

    fun hasUserStarredAnyMessage(jid: String) = messageRepository.hasUserStarredAnyMessage(jid)

    fun isMessagesCanBeRecalled(messageIds: ArrayList<String>) = messageRepository.isRecallAvailableForGivenMessages(messageIds)

    fun getMessageForId(jid: String) = messageRepository.getMessageForId(jid)

    fun getMessageForReply(jid: String) = messageRepository.getMessageForReply(jid)

    fun getRecalledMessageForThisUser(jid: String): List<String> = messageRepository.getRecalledMessageOfAnUser(jid)

    fun deleteUnreadMessageSeparator(jid: String) = messageRepository.deleteUnreadMessageSeparator(jid)

    fun getProfileDetails(jid: String): ProfileDetails? = ProfileDetailsUtils.getProfileDetails(jid)

    fun isGroupUserExist(groupId: String, jid: String): Boolean = GroupManager.isMemberOfGroup(groupId, jid)

    fun getParticipantsNameAsCsv(jid: String) {
        GroupManager.getGroupMembersList(false, jid) { isSuccess, throwable, data ->
            if (isSuccess) {
                var participantsNameList: MutableList<String> = ArrayList()
                var groupsMembersProfileList: MutableList<ProfileDetails> = data[Constants.SDK_DATA] as MutableList<ProfileDetails>
                groupsMembersProfileList = ProfileDetailsUtils.sortGroupProfileList(groupsMembersProfileList)
                groupsMembersProfileList.forEach {
                    if(!it.jid.equals(SharedPreferenceManager.getCurrentUserJid()))
                        participantsNameList.add(it.name)
                }
                groupParticipantsName.value = participantsNameList.sorted().joinToString(",")
            }
        }
    }

}