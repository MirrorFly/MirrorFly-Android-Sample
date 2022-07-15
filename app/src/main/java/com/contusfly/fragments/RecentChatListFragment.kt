package com.contusfly.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.flycommons.*
import com.contus.flycommons.TAG
import com.contus.flycommons.getMessage
import com.contus.flycommons.returnEmptyIfNull
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.*
import com.contusfly.R
import com.contusfly.activities.ArchivedChatsActivity
import com.contusfly.activities.ChatActivity
import com.contusfly.activities.DashboardActivity
import com.contusfly.activities.parent.DashboardParent
import com.contusfly.adapters.RecentChatListAdapter
import com.contusfly.adapters.RecentChatSearchAdapter
import com.contusfly.databinding.FragmentRecentChatBinding
import com.contusfly.interfaces.RecentChatEvent
import com.contusfly.utils.AppConstants
import com.contusfly.utils.Constants
import com.contusfly.utils.LogMessage
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.viewmodels.DashboardViewModel
import com.contusfly.views.CustomRecyclerView
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.RecentChat
import com.contusflysdk.models.RecentSearch
import com.contusflysdk.utils.ItemClickSupport
import com.contusflysdk.utils.Utils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

/**
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class RecentChatListFragment : Fragment(), CoroutineScope, View.OnTouchListener{

    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        println("Coroutine Exception ${TAG}:  ${exception.printStackTrace()}")
    }

    private lateinit var recentChatBinding: FragmentRecentChatBinding

    /**
     * Display the recent list and searched list in the recycler view
     */
    private lateinit var listRecent: CustomRecyclerView

    private lateinit var emptyView: TextView

    private  var dialogFragment:ProfileDialogFragment? = null

    private  var item:RecentChat? = null

    private val mAdapter by lazy {
        RecentChatListAdapter(requireContext(), viewModel.recentChatAdapter, viewModel.selectedRecentChats, viewModel.typingAndGoneStatus)
    }

    private var mRecentChatListType = DashboardParent.RecentChatListType.RECENT

    private val viewModel by lazy {
        ViewModelProvider(requireActivity()).get(DashboardViewModel::class.java)
    }

    private val mRecentSearchList = ArrayList<RecentSearch>()

    private lateinit var searchKey: String

    /**
     * Store onclick time to avoid double click
     */
    private var lastClickTime: Long = 0

    private val mSearchAdapter by lazy { RecentChatSearchAdapter(requireContext(), mRecentSearchList) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        recentChatBinding = FragmentRecentChatBinding.inflate(inflater, container, false)
        initView(recentChatBinding)
        setListeners()
        setObservers()
        return recentChatBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getRecentChats()
    }

    private fun isListTypeRecentChat() = mRecentChatListType == DashboardParent.RecentChatListType.RECENT

    private fun initView(recentChatBinding: FragmentRecentChatBinding) {
        emptyView = recentChatBinding.emptyList.textEmptyView
        emptyView.text = getString(R.string.msg_no_results)
        emptyView.setTextColor(ResourcesCompat.getColor(resources, R.color.color_text_no_list, null))
        listRecent = recentChatBinding.viewListContacts
        mAdapter.setHasStableIds(true)
        setRecentChatAdapter()

        mAdapter.onProfileClickedCallback { itemPosition ->
            item = viewModel.recentChatList.value!![itemPosition]
            if (item!!.isAdminBlocked && ChatType.TYPE_GROUP_CHAT == item!!.getChatType()) {
                (activity as DashboardActivity).recentClickOnAdminBlockedUser()
                return@onProfileClickedCallback
            }
            if (SystemClock.elapsedRealtime() - lastClickTime < 1000)
                return@onProfileClickedCallback
            lastClickTime = SystemClock.elapsedRealtime()
            dialogFragment = ProfileDialogFragment.newInstance(ProfileDetailsUtils.getProfileDetails(item!!.jid)!!)
            val ft = childFragmentManager.beginTransaction()
            val prev = childFragmentManager.findFragmentByTag("dialog")
            if (prev != null)
                ft.remove(prev)
            ft.addToBackStack(null)
            dialogFragment!!.show(ft, "dialog")
        }
    }

    private fun setListeners() {
        val clickSupport = ItemClickSupport.addTo(listRecent)


        clickSupport.setOnItemClickListener { _, position, _ ->
            if (mRecentChatListType == DashboardParent.RecentChatListType.RECENT && position.isValidIndex())
                if (position > 0 && position < viewModel.recentChatList.value!!.size-1)
                    handleOnItemClicked(position)
                else
                    startActivity(Intent(context, ArchivedChatsActivity::class.java))
        }

        clickSupport.setOnItemLongClickListener { _, position, _ ->
            if (mRecentChatListType == DashboardParent.RecentChatListType.RECENT && position > 0 && position < viewModel.recentChatList.value!!.size-1) {
                handleOnItemLongClicked(position)
            }
            true
        }

        mSearchAdapter.searchItemClickedCallback {
            handleOnSearchItemClicked(it)
        }
    }

    private fun setObservers() {
        viewModel.updateMessageStatus.observe(viewLifecycleOwner, Observer { updateMessageUpdate(it) })

        viewModel.groupCreatedLiveData.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "groupCreatedLiveData observed")
        })

        viewModel.groupUpdatedLiveData.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "groupUpdatedLiveData observed")
            onGroupUpdated(it)
        })

        viewModel.groupNewUserAddedLiveData.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "groupNewUserAddedLiveData observed")
            onGroupNewUserAdded(it)
        })

        viewModel.groupUserRemovedLiveData.observe(viewLifecycleOwner, Observer { onGroupUserRemoved(it) })

        viewModel.groupAdminChangedLiveData.observe(viewLifecycleOwner, Observer { onGroupAdminChanged(it) })

        viewModel.searchKeyLiveData.observe(viewLifecycleOwner, Observer { doSearch(it) })

        viewModel.refreshTheRecentChatList.observe(viewLifecycleOwner, { viewModel.getRecentChats() })

        viewModel.profileUpdatedLiveData.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "profileUpdatedLiveData observed")
            onProfileUpdated(it)
            updateProfileDialog(it)
        })

        viewModel.isContactSyncSuccess.observe(viewLifecycleOwner, { viewModel.getRecentChats() })

        viewModel.isUserBlockedUnblockedMe.observe(viewLifecycleOwner, Observer {
            val index = viewModel.recentChatList.value!!.indexOfFirst { recent -> recent.jid ?: Constants.EMPTY_STRING == it.first.trim() }
            if (index.isValidIndex()) {
                mAdapter.mainlist.get(index).isBlockedMe = !mAdapter.mainlist.get(index).isBlockedMe
                val bundle = Bundle()
                bundle.putInt(Constants.NOTIFY_PROFILE_ICON, 2)
                mAdapter.notifyItemChanged(index, bundle)
                mSearchAdapter.notifyItemChanged(index, bundle)

            }
            updateProfileDialog(it.first.trim())
        })

        viewModel.isUserBlockedByAdmin.observe(viewLifecycleOwner, Observer {
            try {
                val index = viewModel.recentChatList.value!!.indexOfFirst { recent -> recent.jid ?: Constants.EMPTY_STRING == it.first.trim() }
                if (index.isValidIndex()) {
                    viewModel.recentChatList.value!![index].isAdminBlocked = it.second
                    val bundle = Bundle()
                    bundle.putInt(Constants.NOTIFY_PROFILE_ICON, 2)
                    mAdapter.notifyItemChanged(index, bundle)
                    mSearchAdapter.notifyItemChanged(index, bundle)
                }
                updateProfileDialog(it.first.trim())
            } catch (e: Exception) {
                LogMessage.d(TAG, "#admin blocked status exception: ${e.message}")
            }
        })

        viewModel.recentChatList.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "updateRecentChatList observed")
        })

        viewModel.recentChatDiffResult.observe(viewLifecycleOwner, Observer {
            LogMessage.i(TAG, "recentChatDiffResult observed")
            initRecentChatAdapter(it)
        })

        viewModel.recentDeleteChatPosition.observe(viewLifecycleOwner, Observer {
            mAdapter.notifyItemRemoved(it)
        })

        viewModel.recentChat.observe(viewLifecycleOwner, Observer { recentPair ->
            LogMessage.i(TAG, "recentChat observed")
            /**
             * Here we're passing pinned chat count (viewModel.recentPinnedCount) as index value
             * because if new message is received it should placed under pinned chat list
             */
            if (recentPair.second.isValidIndex()) {
                val bundle = Bundle()
                when (recentPair.first) {
                    RecentChatEvent.MESSAGE_RECEIVED, RecentChatEvent.MESSAGE_UPDATED, RecentChatEvent.ARCHIVE_EVENT -> {
                        bundle.putInt(Constants.NOTIFY_MESSAGE, 1)
                    }
                    RecentChatEvent.GROUP_EVENT -> {
                        bundle.putInt(Constants.NOTIFY_MESSAGE, 1)
                        bundle.putInt(Constants.NOTIFY_USER_NAME, 1)
                        bundle.putInt(Constants.NOTIFY_PROFILE_ICON, 1)
                    }
                    RecentChatEvent.MUTE_EVENT -> {
                        bundle.putInt(Constants.NOTIFY_MUTE_UNMUTE, 1)
                    }
                    RecentChatEvent.PIN_EVENT -> {
                        bundle.putInt(Constants.NOTIFY_PINNED_ICON, 6)
                        bundle.putInt(Constants.NOTIFY_SELECTION, 4)
                    }
                }
                mAdapter.notifyItemRangeChanged(recentPair.third, recentPair.second + 1, bundle)
            } else initRecentChatAdapter(null)
        })

        viewModel.filterRecentChatList.observe(viewLifecycleOwner, Observer { observeFilteredRecentChatList(it) })
        viewModel.filterProfileList.observe(viewLifecycleOwner, Observer { observeFilteredContactsList(it) })
        viewModel.messageList.observe(viewLifecycleOwner, Observer { observeFilteredMessageList(it.first, it.second) })
        viewModel.changedPinPosition.observe(viewLifecycleOwner, Observer {
            getRecentChatFor(viewModel.recentChatAdapter[it].jid, RecentChatEvent.PIN_EVENT)
        })
        viewModel.changedReadUnReadPosition.observe(viewLifecycleOwner, Observer {
            val bundle = Bundle()
            bundle.putInt(Constants.NOTIFY_UNREAD_ICON, 0)
            bundle.putInt(Constants.NOTIFY_SELECTION, 4)
            mAdapter.notifyItemChanged(it, bundle)
        })

        viewModel.showMessage.observe(viewLifecycleOwner, Observer { showMessage(it) })

        viewModel.archiveChatStatus.observe(viewLifecycleOwner, {
            LogMessage.i(TAG, "archiveChatStatus observed")
            mAdapter.setArchiveStatus(it)
        })

        viewModel.archiveChatUpdated.observe(viewLifecycleOwner, {
            updateArchiveChatsStatus(it.first, it.second)
        })

        viewModel.selectedArchiveChats.observe(viewLifecycleOwner, {
            updateArchiveChatsList(it)
        })
    }

    private fun updateProfileDialog(jid: String) {
        if(item != null && dialogFragment != null && dialogFragment!!.context != null && dialogFragment!!.profileDetails.jid == jid){
            dialogFragment!!.profileDetails = ProfileDetailsUtils.getProfileDetails(jid)!!
            dialogFragment!!.refreshView()
        }
    }

    private fun showMessage(message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleOnSearchItemClicked(position: Int) {
        (activity as DashboardActivity).searchItemClickedPosition = position
        requireContext().launchActivity<ChatActivity> {
            putExtra(LibConstants.JID, mRecentSearchList[position].jid.returnEmptyIfNull())
            putExtra(Constants.CHAT_TYPE, mRecentSearchList[position].chatType)
        }
    }

    fun updateSearchAdapter(position: Int) {
        mSearchAdapter.notifyItemChanged(position)
    }

    private fun updateSearchAdapter(jid: String) {
        val index = viewModel.recentChatList.value!!.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == jid }
        if (index.isValidIndex())
            mAdapter.notifyDataSetChanged()
    }

    private fun updateRecentChatAdapter(jid: String, payloads: Bundle? = null) {
        val index = viewModel.recentChatList.value?.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == jid }
        if (index?.isValidIndex() == true) {
            val recent = FlyCore.getRecentChatOf(jid)
            recent?.let {
                viewModel.recentChatList.value!![index] = recent
                viewModel.recentChatAdapter[index] = recent
            }
            mAdapter.notifyItemChanged(index, payloads)
        }
    }


    private fun updateArchiveChatsStatus(jid: String, archiveStatus: Boolean) {
        if (isListTypeRecentChat()) {
            if (archiveStatus) {
                val index = viewModel.recentChatList.value!!.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == jid }
                if (index.isValidIndex()) {
                    viewModel.recentChatList.value!!.removeAt(index)
                    viewModel.recentChatAdapter.removeAt(index)
                    mAdapter.notifyItemRemoved(index)
                }
            } else {
                getRecentChatFor(jid, RecentChatEvent.ARCHIVE_EVENT)
            }
        } else {
            updateSearchAdapter(jid)
            if (archiveStatus) {
                updateRecentChatAdapter(jid)
            } else {
                getRecentChatFor(jid, RecentChatEvent.ARCHIVE_EVENT)
            }
        }
    }


    fun refreshRecentChatList(){
        viewModel.getRecentChats()
    }

    /**
     * Handle the click operation the recycler view in the recent chats
     *
     * @param position Position of the list view
     */
    private fun handleOnItemClicked(position: Int) {
        if (viewModel.selectedRecentChats.isEmpty())
            openChatView(position)
        else {
            val bundle = Bundle()
            bundle.putInt(Constants.NOTIFY_SELECTION, 4)

            val selectedChats = viewModel.recentChatList.value!![position]
            if (viewModel.selectedRecentChats.any { it.jid == selectedChats.jid }) {
                viewModel.selectedRecentChats.remove(viewModel.selectedRecentChats.first { it.jid == selectedChats.jid })
                if (viewModel.pinnedListPosition.contains(position))
                    viewModel.pinnedListPosition.remove(position)
            } else if (!selectedChats.isGroupInOfflineMode && (!selectedChats.isGroup && !viewModel.selectedRecentChats[0].isGroup
                        || viewModel.selectedRecentChats.isNotEmpty())) {
                viewModel.selectedRecentChats.add(selectedChats)
                viewModel.pinnedListPosition.add(position)
            }
            mAdapter.notifyItemChanged(position, bundle)
            (activity as DashboardActivity).recentClick(viewModel.selectedRecentChats, false)
        }
    }

    /**
     * Handle the long click on the recent chat
     *
     * @param position Position of the list view
     */
    private fun handleOnItemLongClicked(position: Int) {
        val selectedChats = viewModel.recentChatList.value!![position]
        if (!selectedChats.isGroupInOfflineMode && !viewModel.selectedRecentChats.contains(selectedChats)) {
            if (!viewModel.pinnedListPosition.contains(position))
                viewModel.pinnedListPosition.add(position)
            viewModel.selectedRecentChats.add(selectedChats)
            (activity as DashboardActivity).recentClick(viewModel.selectedRecentChats, true)
            val bundle = Bundle()
            bundle.putInt(AppConstants.NOTIFY_SELECTION, 4)
            mAdapter.notifyItemChanged(position, bundle)
        }
    }

    private fun getRecentChatFor(jId: String, @RecentChatEvent event: String) {
        viewModel.getRecentChatOfUser(jId, event)
    }

    private fun doSearch(searchKey: String) {
        if ((activity as DashboardActivity).getViewPagerCurrentPosition() != 0)
            return

        if (searchKey.isEmpty()) {
            mRecentChatListType = DashboardParent.RecentChatListType.RECENT
            setAdapterBasedOnSearchType()
        } else {
            this.searchKey = searchKey
            mRecentChatListType = DashboardParent.RecentChatListType.SEARCH
            mRecentSearchList.clear()
            viewModel.filterRecentChatList(this.searchKey)
            val index = viewModel.filterRecentChatList.value!!.indexOfFirst { recent -> recent.jid.trim() == searchKey }
            if (index.isValidIndex()){
                mAdapter.mainlist.get(index).isBlockedMe = !mAdapter.mainlist.get(index).isBlockedMe
                val bundle = Bundle()
                bundle.putInt(Constants.NOTIFY_PROFILE_ICON, 2)
                mAdapter.notifyItemChanged(index, bundle)
            }

        }
    }
    private fun updateMessageUpdate(messageId: String) {
        val index = viewModel.recentChatList.value?.indexOfFirst { it.lastMessageId == messageId }
        if (index?.isValidIndex() == true) {
            getRecentChatFor(viewModel.recentChatList.value!![index].jid, RecentChatEvent.MESSAGE_UPDATED)
        }
    }

    fun onTypingAndGoneStatusUpdate(singleOrGroupJid: String) {
        val bundle = Bundle()
        bundle.putInt(Constants.NOTIFY_MSG_TYPING, 3)
        updateRecentChatAdapter(singleOrGroupJid, bundle)
    }

    private fun onGroupUpdated(groupId: String) =
        if (isListTypeRecentChat())
            getRecentChatFor(groupId, RecentChatEvent.GROUP_EVENT)
        else updateSearchAdapter(groupId)

    private fun onProfileUpdated(groupId: String) =
        if (isListTypeRecentChat()) {
            val bundle = Bundle()
            bundle.putInt(Constants.NOTIFY_PROFILE_ICON, 2)
            updateRecentChatAdapter(groupId, bundle)
        } else {
            updateSearchAdapter(groupId)
            updateRecentChatAdapter(groupId)
        }

    private fun onGroupNewUserAdded(groupId: String) {
        // time msg txt
        if (isListTypeRecentChat())
            getRecentChatFor(groupId, RecentChatEvent.MESSAGE_RECEIVED)
        else updateSearchAdapter(groupId)
    }

    private fun onGroupUserRemoved(groupId: String) {
        // time msg txt
        if (isListTypeRecentChat())
            getRecentChatFor(groupId, RecentChatEvent.MESSAGE_RECEIVED)
        else updateSearchAdapter(groupId)
    }

    private fun onGroupAdminChanged(groupId: String) {
        // time msg txt
        if (isListTypeRecentChat())
            getRecentChatFor(groupId, RecentChatEvent.MESSAGE_RECEIVED)
        else updateSearchAdapter(groupId)
    }

    private fun initRecentChatAdapter(diffUtilResult: DiffUtil.DiffResult?) {
        if (diffUtilResult == null) {
            mAdapter.notifyDataSetChanged()
        } else {
            // Save Current Scroll state to retain scroll position after DiffUtils Applied
            val previousState = listRecent.layoutManager?.onSaveInstanceState() as Parcelable
            diffUtilResult.dispatchUpdatesTo(mAdapter)
            listRecent.layoutManager?.onRestoreInstanceState(previousState)
        }
        /*
        * Hide empty view */
        if (viewModel.recentChatAdapter.isNotEmpty()) {
            if (viewModel.recentChatAdapter.size == 2 &&
                viewModel.recentChatAdapter[0].jid == null && viewModel.recentChatAdapter[1].jid == null) {
                emptyViewVisibleOrGone()
            } else {
                recentChatBinding.noMessageView.root.visibility = View.GONE
            }
        }
        val listPositions = findAndReplaceNewItem(viewModel.recentChatAdapter, viewModel.selectedRecentChats)
        if (listPositions.first.isValidIndex() && listPositions.second.isValidIndex())
            viewModel.selectedRecentChats[listPositions.second] = viewModel.recentChatAdapter[listPositions.first]
        else mAdapter.notifyDataSetChanged()
    }

    private fun emptyViewVisibleOrGone() {
        var archiveChats: MutableList<RecentChat>? = null
        FlyCore.getArchivedChatList { _, _, data ->
            archiveChats = (data["data"] as MutableList<RecentChat>)
            recentChatBinding.noMessageView.root.visibility = if (archiveChats?.size == 0) View.VISIBLE else View.GONE
        }
    }

    @Synchronized
    private fun findAndReplaceNewItem(recyclerList: List<RecentChat>, selectedList: List<RecentChat>): Pair<Int, Int> {
        if (selectedList.isNotEmpty()) {
            for (i in recyclerList.indices)
                for (j in selectedList.indices)
                    if (selectedList[j].jid == recyclerList[i].jid)
                        return Pair(i, j)
        }
        return Pair(-1, -1)
    }

    private fun setRecentChatAdapter() {
        listRecent.apply {
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(10)
            setHasFixedSize(false)
            setEmptyView(emptyView)
            itemAnimator = null
            adapter = mAdapter
        }
    }

    fun isRecentListInitialized(): Boolean {
        return ::listRecent.isInitialized
    }

    fun setAdapterBasedOnSearchType() {
        if (this::listRecent.isInitialized && mRecentChatListType == DashboardParent.RecentChatListType.RECENT
            && (listRecent.adapter is RecentChatSearchAdapter)) {
            listRecent.adapter = mAdapter
            if (viewModel.recentChatAdapter.isNotEmpty()) {
                if (viewModel.recentChatAdapter.size == 2 &&
                    viewModel.recentChatAdapter[0].jid == null && viewModel.recentChatAdapter[1].jid == null) {
                    recentChatBinding.noMessageView.root.visibility = View.VISIBLE
                } else {
                    recentChatBinding.noMessageView.root.visibility = View.GONE
                }
            } else if (viewModel.recentChatAdapter.isNullOrEmpty())
                recentChatBinding.noMessageView.root.visibility = View.VISIBLE
        } else if (this::listRecent.isInitialized && mRecentChatListType == DashboardParent.RecentChatListType.SEARCH
            && (listRecent.adapter is RecentChatListAdapter)) {
            listRecent.adapter = mSearchAdapter
        }
    }

    private fun observeFilteredRecentChatList(list: List<RecentChat>) {
        val jidList = ArrayList<String>()
        removeSearchListItemOfType(Constants.TYPE_SEARCH_RECENT)
        mSearchAdapter.setRecentChatCount(list.size)
        for (recent in list) {
            val recentSearchItem = RecentSearch(recent.jid, recent.lastMessageId,
                Constants.TYPE_SEARCH_RECENT, recent.getChatTypeEnum().toString(), true)
            mRecentSearchList.add(recentSearchItem)
            jidList.add(recent.jid)
        }
        viewModel.filterContactsList(searchKey, jidList)
        mSearchAdapter.setRecentSearch(mRecentSearchList, searchKey)
        setAdapterBasedOnSearchType()
        setEmptyView(mRecentSearchList)
    }

    private fun setEmptyView(mRecentSearchList: java.util.ArrayList<RecentSearch>) {
        if (mRecentSearchList.isNullOrEmpty())
            recentChatBinding.noMessageView.root.visibility = View.GONE
    }

    private fun observeFilteredContactsList(list: List<ProfileDetails>) {
        removeSearchListItemOfType(Constants.TYPE_SEARCH_CONTACT)
        mSearchAdapter.setRecentContactCount(list.size)
        for (profile in list) {
            if (!profile.isAdminBlocked) {
                val searchContactItem = RecentSearch(profile.jid, null,
                    Constants.TYPE_SEARCH_CONTACT, profile.getChatTypeEnum().toString(), true)
                mRecentSearchList.add(searchContactItem)
            }
        }
        viewModel.filterMessageList(searchKey)
        mSearchAdapter.setRecentSearch(mRecentSearchList, searchKey)
        setAdapterBasedOnSearchType()
        mSearchAdapter.notifyItemRangeInserted(mRecentSearchList.size - list.size, mRecentSearchList.size)
    }

    private fun observeFilteredMessageList(messageCount: Int, messageList: List<RecentSearch>) {
        LogMessage.i(TAG, "observeFilteredMessageList")
        //There can be delay by the time messageList is fetched and user starts new search and in the end both
        //search results may get added to list. So remove all of TYPE and added afresh in list.
        removeSearchListItemOfType(Constants.TYPE_SEARCH_MESSAGE)

        mRecentSearchList.addAll(messageList)
        mSearchAdapter.setRecentMessageCount(messageCount)
        // update search adapter
        mSearchAdapter.setRecentSearch(mRecentSearchList, searchKey)
        setAdapterBasedOnSearchType()
        mSearchAdapter.notifyDataSetChanged()
    }

    /**
     * Remove items of a Type of a previous search.
     * @param type : item of type Recent,Contact,Message
     */
    private fun removeSearchListItemOfType(type: String) {
        for (i in mRecentSearchList.indices.reversed()) {
            if (type == mRecentSearchList[i].searchType)
                mRecentSearchList.removeAt(i)
        }
    }

    /**
     * Open chat view for particular roster.
     *
     * @param itemPos the item pos
     */
    private fun openChatView(itemPos: Int) {
        try {
            NotificationManagerCompat.from(requireContext()).cancel(Constants.NOTIFICATION_ID)
            val item = viewModel.recentChatList.value!![itemPos]
            val jid = Utils.returnEmptyStringIfNull(item.jid)
            if (!item.isGroupInOfflineMode) {
                if (item.isAdminBlocked && ChatType.TYPE_GROUP_CHAT == item.getChatType()) {
                    LogMessage.d(TAG, "#onAdminBlockedStatus click status = ${item.isAdminBlocked}")
                    (activity as DashboardActivity).recentClickOnAdminBlockedUser()
                } else {
                    viewModel.clearUnreadCount(item, itemPos)
                    val intent = Intent(context, ChatActivity::class.java)
                    startActivity(intent.putExtra(LibConstants.JID, jid)
                        .putExtra(Constants.CHAT_TYPE, item.getChatType())
                        .putExtra(Constants.POSITION, itemPos.toString()))
                }
            } else {
                GroupManager.createOfflineGroupInOnline(jid, FlyCallback { isSuccess, throwable, data ->
                    if (!isSuccess)
                        showMessage(data.getMessage())
                })
            }
        } catch (e: Exception) {
            LogMessage.e(TAG, e)
        }
    }

    fun updateAdapter() {
        for (item in viewModel.selectedRecentChats) {
            val index = viewModel.recentChatList.value!!.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == item.jid }
            if (index.isValidIndex()) {
                viewModel.recentChatList.value!!.removeAt(index)
                viewModel.recentChatAdapter.removeAt(index)
                mAdapter.notifyItemRemoved(index)
            }
        }
        viewModel.selectedRecentChats.clear()
        viewModel.pinnedListPosition.clear()
    }

    fun updateArchiveChatsList(selectedJids: MutableList<String>) {
        if (activity == null)
            return
        for (jid in selectedJids) {
            val index = viewModel.recentChatList.value!!.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == jid }
            if (index.isValidIndex()) {
                viewModel.recentChatList.value!!.removeAt(index)
                viewModel.recentChatAdapter.removeAt(index)
                mAdapter.notifyItemRemoved(index)
            }
        }
        viewModel.selectedRecentChats.clear()
        viewModel.pinnedListPosition.clear()
    }

    fun clearSelectedMessages() {
        LogMessage.d(TAG, "clearSelectedMessages")
        if (isAdded) {
            if (viewModel.selectedRecentChats.isNotEmpty()) {
                val bundle = Bundle()
                bundle.putInt(Constants.NOTIFY_SELECTION, 4)
                for (item in viewModel.selectedRecentChats) {
                    updateListAdapter(bundle,item)
                }
                viewModel.selectedRecentChats.clear()
                viewModel.pinnedListPosition.clear()
            }
        } else {
            LogMessage.e(TAG, "Recent fragment not added yet")
        }
    }

    private fun updateListAdapter(bundle: Bundle, item: RecentChat){
        if (viewModel.recentChatList.value != null) {
            val index =
                viewModel.recentChatList.value!!.indexOfFirst { it.jid ?: Constants.EMPTY_STRING == item.jid }
            if (index.isValidIndex())
                mAdapter.notifyItemChanged(index, bundle)
        }
    }

    fun updateRecentItem(mutedStatus: Boolean) {
        if (viewModel.selectedRecentChats.isNotEmpty()) {
            for (item in viewModel.selectedRecentChats) {
                val index = viewModel.recentChatAdapter.indexOfFirst { it.jid == item.jid }
                if (index.isValidIndex()) {
                    mAdapter.mainlist[index].isMuted = mutedStatus
                    val bundle = Bundle()
                    bundle.putInt(Constants.NOTIFY_MUTE_UNMUTE, 1)
                    mAdapter.notifyItemChanged(index, bundle)
                    mSearchAdapter.notifyItemChanged(index, bundle)
                }
            }
            viewModel.selectedRecentChats.clear()
            viewModel.pinnedListPosition.clear()
        }
    }

    companion object {
        /**
         * The constructor used to create and initialize a new instance of this class object, with the
         * specified initialization parameters.
         *
         * @return a new object created by calling the constructor of this object representation.
         */
        @JvmStatic
        fun newInstance(): RecentChatListFragment {
            return RecentChatListFragment()
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + Job()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(p0: View?, p1: MotionEvent?): Boolean {
        return true
    }

}