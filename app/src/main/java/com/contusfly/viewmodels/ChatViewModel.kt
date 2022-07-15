package com.contusfly.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contusfly.repository.MessageRepository
import com.contusfly.utils.ProfileDetailsUtils
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatViewModel @Inject
constructor(private val messageRepository: MessageRepository) : ViewModel() {

    val initialMessageList = MutableLiveData<ArrayList<ChatMessage>>()

    val userRoster: MutableLiveData<ProfileDetails> = MutableLiveData()

    /**
     * contact refreshing status
     */
    private var isRefreshing: Boolean = false

    private lateinit var toUserJid: String

    fun clearChat() {
        initialMessageList.value?.clear()
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.Default) {
            //post value from bkg thread
            initialMessageList.postValue(ArrayList(messageRepository.getAllMessages(toUserJid)))
        }
    }

    fun setUserJid(jid: String) {
        toUserJid = jid
    }

    fun getProfileDetails() {
        userRoster.value = ProfileDetailsUtils.getProfileDetails(toUserJid)
    }

    fun getMessage(messageId: String): ChatMessage? = FlyMessenger.getMessageOfId(messageId)

    fun deleteUnreadMessageSeparator(jid: String) = messageRepository.deleteUnreadMessageSeparator(jid)

    fun getAllMessages(jid: String) = messageRepository.getAllMessages(jid)

    fun getBroadcastMessageID(messageId: String) = messageRepository.getBroadcastMessageID(messageId)

}