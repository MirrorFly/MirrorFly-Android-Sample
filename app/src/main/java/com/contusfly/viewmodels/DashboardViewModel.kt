package com.contusfly.viewmodels

import android.content.Context
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import com.contus.flycommons.Constants
import com.contus.flycommons.FlyCallback
import com.contus.flycommons.LogMessage
import com.contus.call.database.model.CallLog
import com.contusfly.TAG
import com.contusfly.diffCallBacks.ProfileDiffCallback
import com.contusfly.diffCallBacks.RecentChatDiffCallback
import com.contusfly.getChatType
import com.contusfly.interfaces.RecentChatEvent
import com.contusfly.isValidIndex
import com.contusfly.sortProfileList
import com.contusfly.utils.AppChatShortCuts.dynamicAppShortcuts
import com.contusfly.utils.Constants.Companion.SDK_DATA
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.utils.SharedPreferenceManager
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.api.models.RecentChat
import com.contusflysdk.models.RecentSearch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class DashboardViewModel @Inject
constructor() : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        println("Coroutine Exception ${TAG}:  ${exception.printStackTrace()}")
    }

    /**
     * List to add position of the clicked chats for pinning
     */
    val pinnedListPosition = ArrayList<Int>()
    private var recentPinnedCount = 0
    private val _showMessage = MutableLiveData<String>()
    val showMessage: LiveData<String>
        get() = _showMessage

    val chatList = MutableLiveData<LinkedList<RecentChat>>()
    val recentChatList = MutableLiveData<LinkedList<RecentChat>>()
    val chats = MutableLiveData<Triple<String, Int, Int>>()
    val recentChat = MutableLiveData<Triple<String, Int, Int>>()
    val unreadChatCountLiveData = MutableLiveData<Int>()
    val searchKeyLiveData = MutableLiveData<String>()
    val profileUpdatedLiveData = MutableLiveData<String>()
    val blockedProfilesLiveData = MutableLiveData<List<String>>()
    val clearChatList = MutableLiveData<ArrayList<String>>()
    val recentDeleteChatPosition = MutableLiveData<Int>()
    val archiveChatStatus = MutableLiveData<Triple<Boolean, Boolean, Int>>()
    val archivedSettingsStatus = MutableLiveData<Boolean>()

    /**
     * Selected recent chats when long press
     */
    val selectedChats by lazy { ArrayList<RecentChat>() }
    val selectedRecentChats: ArrayList<RecentChat> by lazy { ArrayList<RecentChat>() }

    /**
     * Recent Chat Adapter Value
     */
    val chatAdapter by lazy { LinkedList<RecentChat>() }
    val recentChatAdapter: LinkedList<RecentChat> by lazy { LinkedList<RecentChat>() }
    val filterArchivedChatList = MutableLiveData<List<RecentChat>>()

    /* = = = = = = = = Profile Data = = = = = = = = */
    val profileDetailsList = MutableLiveData<List<ProfileDetails>>()
    val profileListAdapter: ArrayList<ProfileDetails> by lazy { ArrayList<ProfileDetails>() }
    val profileDiffResult = MutableLiveData<DiffUtil.DiffResult>()
    val isContactSyncSuccess = MutableLiveData<Boolean>()
    val callsSearchKey = MutableLiveData<String>()

    val typingAndGoneStatus by lazy { ArrayList<Triple<String, String, Boolean>>() }

    /**
     * Recent Chat [DiffUtil.DiffResult]
     */
    val chatDiffResult = MutableLiveData<DiffUtil.DiffResult>()
    val recentChatDiffResult = MutableLiveData<DiffUtil.DiffResult>()

    // = = = = = = = = RecentChat Data = = = = = = = =
    val updateMessageStatus = MutableLiveData<String>()
    val groupCreatedLiveData = MutableLiveData<String>()
    val groupUpdatedLiveData = MutableLiveData<String>()
    val groupNewUserAddedLiveData = MutableLiveData<String>()
    val groupUserRemovedLiveData = MutableLiveData<String>()
    val groupAdminChangedLiveData = MutableLiveData<String>()
    val refreshTheRecentChatList = MutableLiveData<Boolean>()

    // = = = = = = = = Search Data = = = = = = = =
    val filterRecentChatList = MutableLiveData<List<RecentChat>>()
    val messageList = MutableLiveData<Pair<Int, List<RecentSearch>>>()
    val filterProfileList = MutableLiveData<List<ProfileDetails>>()

    val isUserBlockedUnblockedMe = MutableLiveData<Pair<String, Boolean>>()

    init {
        viewModelScope.launch {
            recentPinnedCount = 0
        }

    }

    private val _changedReadUnReadPosition = MutableLiveData<Int>()
    val changedReadUnReadPosition: LiveData<Int>
        get() = _changedReadUnReadPosition

    private val _changedPinPosition = MutableLiveData<Int>()
    val changedPinPosition: LiveData<Int>
        get() = _changedPinPosition

    fun setBlockUnBlockJID(jid: String, isBlocked: Boolean) {
        isUserBlockedUnblockedMe.value = Pair(jid, isBlocked)
    }


    // = = = = = = = = CallLogs Data = = = = = = = =
    val callLogList = MutableLiveData<List<CallLog>>()
    val callLogListAdapter: ArrayList<CallLog> by lazy { ArrayList() }
    val selectedCallLogs: ArrayList<String> by lazy { ArrayList() }
    val callLogDiffResult = MutableLiveData<DiffUtil.DiffResult>()

    /*
    * Get Profile List */
    fun getProfileDetailsList() {
        viewModelScope.launch {
            FlyCore.getFriendsList(false, FlyCallback { isSuccess, throwable, data ->
                if (isSuccess) {
                    val profileDetails = data[SDK_DATA] as MutableList<ProfileDetails>
                    profileDetailsList.value = sortProfileList(profileDetails)
                    getProfileDiffResult()
                }
            })
        }
    }

    /*
    * Get Recent Chats list */
    fun getRecentChats() {
        LogMessage.d(TAG, "getRecentChats() called to update the UI")
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (recentChatList.value == null && !SharedPreferenceManager.getBoolean(com.contusfly.utils.Constants.PIN_SCREEN)) {
                recentChatList.value = LinkedList(FlyCore.getRecentChatList())
                recentChatList.value!!.add(0, RecentChat()) // Recent Chat Header
                recentChatList.value!!.add(recentChatList.value!!.size, RecentChat()) // Recent Chat Footer
                recentChatAdapter.clear()
                recentChatAdapter.addAll(recentChatList.value!!)
                recentChatDiffResult.value = null
            } else {
                FlyCore.getRecentChatList { isSuccess, _, data ->
                    if (isSuccess) {
                        recentChatList.value = LinkedList(data[SDK_DATA] as MutableList<RecentChat>)
                        recentChatList.value!!.add(0, RecentChat()) // Recent Chat Header
                        recentChatList.value!!.add(recentChatList.value!!.size, RecentChat()) // Recent Chat Footer
                        getRecentChatDiffResult()
                    }
                }
            }
        }
    }

    fun setTypingStatus(typingStatus: Triple<String, String, Boolean>) {
        if (typingStatus.third) {
            val index = typingAndGoneStatus.indexOfFirst { it.first == typingStatus.first && it.second == typingStatus.second }
            if (index == -1)
                typingAndGoneStatus.add(0,typingStatus)
        } else {
            val index = typingAndGoneStatus.indexOfFirst { it.first == typingStatus.first && it.second == typingStatus.second }
            if (index.isValidIndex())
                typingAndGoneStatus.removeAt(index)
        }
    }

    private fun getProfileDiffResult() {
        viewModelScope.launch {
            val diffResult = getDiffUtilResult(ProfileDiffCallback(profileListAdapter, profileDetailsList.value!!))
            profileListAdapter.clear()
            profileListAdapter.addAll(profileDetailsList.value!!)
            profileDiffResult.value = diffResult
        }
    }

    fun getChatDiffResult() {
        viewModelScope.launch {
            val diffResult = getDiffUtilResult(RecentChatDiffCallback(chatAdapter, chatList.value!!))
            chatAdapter.clear()
            chatAdapter.addAll(chatList.value!!)
            chatDiffResult.value = diffResult
        }
    }

    fun getRecentChatDiffResult() {
        viewModelScope.launch {
            val diffResult = DiffUtil.calculateDiff(RecentChatDiffCallback(recentChatAdapter, recentChatList.value!!))
            recentChatAdapter.clear()
            recentChatAdapter.addAll(recentChatList.value!!)
            recentChatDiffResult.value = diffResult
        }
    }

    private suspend fun getDiffUtilResult(diffUtilCallback: DiffUtil.Callback): DiffUtil.DiffResult = withContext(Dispatchers.IO) {
        DiffUtil.calculateDiff(diffUtilCallback)
    }

    fun filterContactsList(searchKey: String, jidList: ArrayList<String>) {
        viewModelScope.launch {
            FlyCore.getFriendsList(false) { isSuccess, _, data ->
                if (isSuccess) {
                    val profileDetails = data[SDK_DATA] as MutableList<ProfileDetails>
                    filterProfileList.value = profileDetails.filter { !jidList.contains(it.jid) && it.name.contains(searchKey, true) }.sortedBy { it.name }
                }
            }
        }
    }

    fun getRecentChatOfUser(jid: String, @RecentChatEvent event: String) {
        viewModelScope.launch {
            val recent = FlyCore.getRecentChatOf(jid)
            if (recent != null && !recent.isChatArchived) {
                //update view model list
                val index = recentChatAdapter.indexOfFirst { it.jid == recent.jid }
                val positionToAdd = getRecentPosition(recent.jid, recent, event)
                if (index.isValidIndex()) {
                    recentChatList.value!!.removeAt(index)
                    recentChatList.value!!.add(positionToAdd, recent)
                    recentChatAdapter.removeAt(index)
                    recentChatAdapter.add(positionToAdd, recent)
                } else {
                    recentChatList.value!!.add(positionToAdd, recent)
                    recentChatAdapter.add(positionToAdd, recent)
                }
                recentChat.value = Triple(event, index, positionToAdd)
            } else {
                //update view model list
                val index = recentChatAdapter.indexOfFirst { it.jid == jid }
                if (index.isValidIndex()) {
                    recentChatList.value!!.removeAt(index)
                    recentChatAdapter.removeAt(index)
                    recentDeleteChatPosition.value = index
                }
            }
        }
    }

    /**
     * This method will return the position of chat
     */
    private fun getRecentPosition(jid: String, recent: RecentChat, @RecentChatEvent event: String): Int {
        return if (event == RecentChatEvent.MESSAGE_RECEIVED) {
            if (recent.isChatPinned) {
                val index = this.recentChatAdapter.indexOfFirst { it.jid == jid }
                if (index.isValidIndex()) index else 1 //Recent Chat header will be always 0
            } else
                recentPinnedCount + 1 //Recent Chat header will be always 0
        } else if (event == RecentChatEvent.ARCHIVE_EVENT) {
            getArchiveRecentPosition(recent)
        } else {
            val index = this.recentChatAdapter.indexOfFirst { it.jid == jid }
            if (index.isValidIndex() && recent.isChatPinned) index else 1 //Recent Chat header will be always 0
        }
    }

    private fun getArchiveRecentPosition(recent: RecentChat): Int {
        val index = this.recentChatAdapter.indexOfFirst { !it.jid.isNullOrBlank() && it.lastMessageTime <= recent.lastMessageTime }
        return if (index.isValidIndex()) index else 1 //Recent Chat header will be always 0
    }

    fun filterRecentChatList(searchKey: String) {
        viewModelScope.launch {
            val recentChatList = mutableListOf<RecentChat>()
            val recentChatListWithArchived = FlyCore.getRecentChatListIncludingArchived()
            for (recentChat in recentChatListWithArchived)
                if (recentChat.profileName != null && recentChat.profileName.contains(searchKey, true))
                    recentChatList.add(recentChat)
            filterRecentChatList.value = recentChatList
        }
    }

    /**
     * Validating the selected chat count and updating db
     */
    fun updatePinnedRecentChats(): Boolean {
        recentPinnedCount = FlyCore.recentChatPinnedCount()
        var currentPinnedCount = 0

        if (isSelectedPositionsValidForPin()) {
            for (position in pinnedListPosition) {
                val selectedChat: RecentChat = recentChatAdapter[position]
                if (!selectedChat.isChatPinned) {
                    FlyCore.updateRecentChatPinStatus(selectedChat.jid, true)
                    // _changedPinPosition.value = position
                    recentPinnedCount++
                    currentPinnedCount++
                } else {
                    LogMessage.d(TAG, "selected chat is already pinned")
                }
            }
        } else {
            _showMessage.value = "You can only pin upto 3 chats"
            return false
        }
        if (currentPinnedCount == 1) _showMessage.value = "Chat pinned"
        else if (currentPinnedCount in 2..3) _showMessage.value = "Chats pinned"
        //Reset the recent items
        recentChatList.value = null
        getRecentChats()
        pinnedListPosition.clear()
        return true
    }

    private fun isSelectedPositionsValidForPin(): Boolean {
        if ((recentPinnedCount + pinnedListPosition.size) <= 3) {
            return true
        }
        var validPositions = 0 //selected non pinned items
        for (position in pinnedListPosition) {
            if (position >= recentPinnedCount) // check, is non pinned item
                validPositions++
        }
        if ((recentPinnedCount + validPositions) <= 3) {
            return true
        }
        return false
    }

    /**
     * Updating db once the pinned chat is unpinned
     */
    fun updateUnPinnedRecentChats() {
        for (position in pinnedListPosition) {
            val selectedChats: RecentChat = recentChatList.value!![position]
            FlyCore.updateRecentChatPinStatus(selectedChats.jid, false)
            //  _changedPinPosition.value = position
        }
        if (pinnedListPosition.size == 1) _showMessage.value = "Chat unpinned"
        else if (pinnedListPosition.size <= 3) _showMessage.value = "Chats unpinned"
        //Reset the recent items
        recentChatList.value = null
        getRecentChats()
        pinnedListPosition.clear()
    }

    /**
     * Updating db once the recent chat is read
     */
    fun markAsReadRecentChats() {
        val jidList = ArrayList<String>()
        for (selectedRecentChat in selectedRecentChats) {
            jidList.add(selectedRecentChat.jid)
        }
        FlyCore.markConversationAsRead(jidList)
        jidList.clear()
        for (selectedRecentChat in selectedRecentChats) {
            val recentListPosition = recentChatList.value!!.indexOfFirst { it.jid == selectedRecentChat.jid }
            val recent = FlyCore.getRecentChatOf(selectedRecentChat.jid)
            if (recent != null) {
                recentChatList.value!![recentListPosition] = recent
                recentChatAdapter[recentListPosition] = recent
                _changedReadUnReadPosition.value = recentListPosition
            }
        }
        //update unread count in tab
        updateUnReadChatCount()
        if (selectedRecentChats.size == 1) _showMessage.value = "Chat marked as read"
        else if (selectedRecentChats.size > 1) _showMessage.value = "Chats marked as read"
    }

    fun markAsUnreadRecentChats() {
        val jidList = ArrayList<String>()
        for (selectedRecentChat in selectedRecentChats) {
            jidList.add(selectedRecentChat.jid)
        }
        FlyCore.markConversationAsUnread(jidList)
        jidList.clear()
        for (selectedRecentChat in selectedRecentChats) {
            val recentListPosition = recentChatList.value!!.indexOfFirst { it.jid == selectedRecentChat.jid }
            val recent = FlyCore.getRecentChatOf(selectedRecentChat.jid)
            if (recent != null) {
                recentChatList.value!![recentListPosition] = recent
                recentChatAdapter[recentListPosition] = recent
                _changedReadUnReadPosition.value = recentListPosition
            }
        }
        //update unread count in tab
        updateUnReadChatCount()
        if (selectedRecentChats.size == 1) _showMessage.value = "Chat marked as unread"
        else if (selectedRecentChats.size > 1) _showMessage.value = "Chats marked as unread"
    }

    fun filterMessageList(searchKey: String) {
        viewModelScope.launch {
            FlyCore.searchConversation(searchKey, Constants.EMPTY_STRING, true) { isSuccess, throwable, data ->
                if (isSuccess) {
                    val mRecentSearchList = ArrayList<RecentSearch>()
                    val result = data[SDK_DATA] as ArrayList<ChatMessage>
                    var i = 0
                    result.forEach { message ->
                        val searchMessageItem = RecentSearch(message.getChatUserJid(), message.getMessageId(),
                                Constants.TYPE_SEARCH_MESSAGE, message.getMessageChatType().toString(), true)
                        mRecentSearchList.add(0, searchMessageItem)
                        i++
                    }
                    messageList.value = Pair(i, mRecentSearchList)

                }
            }
        }
    }

    fun updateUnReadChatCount() {
        viewModelScope.launch {
            unreadChatCountLiveData.value = FlyMessenger.getUnreadMessagesCount()
        }
    }

    fun setReceivedMsg(msg: ChatMessage?) {
        getRecentChatOfUser(msg!!.getChatUserJid(), RecentChatEvent.MESSAGE_RECEIVED)
        updateUnReadChatCount()
        getArchivedChatStatus()
    }

    fun setMessageStatus(messageId: String) {
        updateMessageStatus.value = messageId
    }

    fun setClearedMessagesView(jid: String?) {
        getRecentChatOfJid(jid!!, RecentChatEvent.MESSAGE_RECEIVED)
        updateUnReadChatCount()
    }

    fun getRecentChatOfJid(jid: String, @RecentChatEvent event: String) {
        viewModelScope.launch {
            val recent = FlyCore.getRecentChatOf(jid)
            if (recent != null) {
                //update view model list
                val index = recentChatAdapter.indexOfFirst { it.jid == recent.jid }
                if (index.isValidIndex()) {
                    recentChatList.value!![index] = recent
                    recentChatAdapter[index] = recent
                }
            } else refreshTheRecentChatList.value = true
        }
    }

    fun getLiveDataForBlockedContacts(jidList: List<String>) {
        viewModelScope.launch {
            blockedProfilesLiveData.value = jidList
        }
    }

    fun updateRecentMessage(messageIds: ArrayList<String>?) {
        if (messageIds != null)
            for (mid in messageIds) {
                val index = recentChatAdapter.indexOfFirst { it.lastMessageId == mid }
                if (index.isValidIndex()) {
                    val recent = recentChatAdapter[index]
                    getRecentChatOfUser(recent.jid, RecentChatEvent.MESSAGE_UPDATED)
                    setMessageStatus(recent.lastMessageId)
                }
            }
    }

    fun clearTypingStatusList() {
        typingAndGoneStatus.clear()
    }

    fun updateMuteNotification(type: String) {
        for (i in selectedRecentChats.indices) {
            val recentChat = selectedRecentChats[i]
            try {
                if (!recentChat.isBroadCast) {
                    FlyCore.updateChatMuteStatus(recentChat.jid, type == Constants.MUTE_NOTIFY)
                }
            } catch (e: Exception) {
                LogMessage.e(Constants.TAG, e)
            }
        }
    }

    fun createPinShortcutForRecentChat(context: Context) {
        for (i in selectedRecentChats.indices)
            if (!selectedRecentChats[i].isBroadCast) {
                dynamicAppShortcuts(context, selectedRecentChats[i].jid, selectedRecentChats[i].getChatType())
            }
    }

    fun getArchivedChats() {
        LogMessage.d(TAG, "getAllChats() called to update the UI")
        viewModelScope.launch(Dispatchers.Main.immediate) {
            FlyCore.getArchivedChatList(FlyCallback { isSuccess, throwable, data ->
                if (isSuccess) {
                    chatList.value = LinkedList(data["data"] as MutableList<RecentChat>)
                    chatList.value!!.add(0, RecentChat()) // Recent Chat Header
                    chatList.value!!.add(chatList.value!!.size, RecentChat()) // Recent Chat Footer
                    getChatDiffResult()
                }
            })
        }
    }

    fun getArchiveChatOfUser(jid: String, @RecentChatEvent event: String) {
        viewModelScope.launch {
            val recent = FlyCore.getRecentChatOf(jid)
            if (recent != null && recent.isChatArchived) {
                //update view model list
                val index = chatList.value!!.indexOfFirst { it.jid == recent.jid }
                val positionToAdd = getArchivePosition(recent.jid, event)
                if (index.isValidIndex()) {
                    chatAdapter.removeAt(index)
                    chatAdapter.add(positionToAdd, recent)
                    chatList.value!!.removeAt(index)
                    chatList.value!!.add(positionToAdd, recent)
                } else {
                    chatList.value!!.add(positionToAdd, recent)
                    chatAdapter.add(positionToAdd, recent)
                }

                chats.value = Triple(event, index, positionToAdd)
            }
        }
    }

    /**
     * This method will return the position of chat
     */
    private fun getArchivePosition(jid: String, @RecentChatEvent event: String): Int {
        return if (event == RecentChatEvent.MESSAGE_RECEIVED) {
            1 //Recent Chat header will be always 0
        } else {
            val index = this.chatAdapter.indexOfFirst { it.jid == jid }
            if (index.isValidIndex()) index else 1 //Recent Chat header will be always 0
        }
    }

    fun updateArchivedMuteNotification(type: String) {
        for (i in selectedChats.indices) {
            try {
                val recentChat = selectedChats[i]
                if (!recentChat.isBroadCast) {
                    FlyCore.updateChatMuteStatus(recentChat.jid, type == Constants.MUTE_NOTIFY)
                }
            } catch (e: Exception) {
                LogMessage.e(Constants.TAG, e)
            }
        }
    }

    /**
     * Updating archived chats when search key updated
     */
    fun filterArchivedChatList(searchKey: String) {
        viewModelScope.launch {
            val archivedChatList = mutableListOf<RecentChat>()
            for (archivedChat in chatAdapter)
                if (archivedChat.profileName != null && archivedChat.profileName.contains(searchKey, true))
                    archivedChatList.add(archivedChat)
            filterArchivedChatList.value = archivedChatList
        }
    }

    fun clearUnreadCount(item: RecentChat, itemPos: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.isConversationUnRead) {
                item.unreadMessageCount = 0
                item.isConversationUnRead = false
                recentChatList.value!![itemPos] = item
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    getRecentChatDiffResult() }, 100)
            }
        }
    }


    fun getArchivedChatStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            FlyCore.getArchivedChatList { isSuccess, _, data ->
                if (isSuccess) {
                    val archiveChats = data["data"] as MutableList<RecentChat>
                    if (archiveChats.isNotEmpty()) {
                        val isArchiveSettingsEnable = FlyCore.isArchivedSettingsEnabled()
                        archiveChatStatus.postValue(Triple(first = true, second = isArchiveSettingsEnable, third = getArchivedChatCount(archiveChats, isArchiveSettingsEnable)))
                    } else {
                        archiveChatStatus.postValue(Triple(first = false, second = false, third = 0))
                    }
                }
            }
        }
    }

    private fun getArchivedChatCount(archiveChats: MutableList<RecentChat>, isArchiveSettingsEnable: Boolean): Int {
        var unreadCount = 0
        if (isArchiveSettingsEnable)
            archiveChats.forEach { if (it.isConversationUnRead) unreadCount++ }
        else
            unreadCount = archiveChats.size
        return unreadCount
    }

    fun getArchivedSettingsStatus(status: Boolean) {
        viewModelScope.launch {
            archivedSettingsStatus.value = status
        }
    }
}