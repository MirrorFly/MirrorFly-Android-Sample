package com.contusfly.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.contus.flycommons.ChatType
import com.contus.webrtc.api.CallLogManager
import com.contus.webrtc.utils.GroupCallUtils
import com.contusfly.*
import com.contusfly.activities.parent.DashboardParent
import com.contusfly.adapters.ViewPagerAdapter
import com.contusfly.call.CallNotificationUtils
import com.contusfly.call.calllog.CallHistoryFragment
import com.contusfly.databinding.ActivityDashboardBinding
import com.contusfly.fragments.RecentChatListFragment
import com.contusfly.interfaces.RecentChatEvent
import com.contusfly.utils.*
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.api.models.RecentChat
import com.google.android.material.appbar.AppBarLayout
import dagger.android.AndroidInjection

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class DashboardActivity : DashboardParent(), ViewPager.OnPageChangeListener, View.OnClickListener, ActionMode.Callback, CommonAlertDialog.CommonDialogClosedListener{

    private var TAG = DashboardActivity::class.java.simpleName


    private lateinit var chatTitleTextView: TextView
    private lateinit var callTitleTextView: TextView
    private lateinit var unReadChatCountTextView: TextView
    private lateinit var missedCallCountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        dashboardBinding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(dashboardBinding.root)

        setSupportActionBar(dashboardBinding.toolbar)
        supportActionBar?.title = Constants.EMPTY_STRING
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        mAdapter = ViewPagerAdapter(supportFragmentManager, addFragmentsToViewPagerAdapter(),
                arrayOf(getString(R.string.chats_label), getString(R.string.calls_label)))
        tabLayout = dashboardBinding.tabs
        mViewPager = dashboardBinding.viewPager
        swipeRefreshLayout = dashboardBinding.swipeToRefreshLayout
        dashboardBinding.newChatFab.setOnClickListener(this)
        setListeners()
        setObservers()
        setUpViewPager()
        setUpTabLayout()
        setUpTabColors(0)
        setupTabPosition()
        checkEnableSafeChat()
    }

    private fun checkEnableSafeChat() {
        if (intent.getBooleanExtra(Constants.IS_FOR_SAFE_CHAT, false)){
            SafeChatUtils.changeSafeChatStatus(this,null)
        }
        AppShortCuts.dynamicAppShortcuts(this)
    }

    /**
     * When a new notification message is received the recent chat has to be updated and set a view to show the latest message...
     *
     * @param message Instance of the Message
     */
    override fun onGroupNotificationMessage(message: ChatMessage) {
        if((message.messageTextContent.contains("removed you") || message.messageTextContent.contains("added you")) &&
            viewModel.selectedRecentChats.isNotEmpty())
            recentClick(viewModel.selectedRecentChats, false)
        //viewModel.getRecentChatOfUser(message.getChatUserJid(), RecentChatEvent.GROUP_EVENT)
        viewModel.unreadChatCountLiveData.value = FlyMessenger.getUnreadMessagesCount()
    }

    private fun addFragmentsToViewPagerAdapter(): ArrayList<Fragment> {
        val fragmentsArray = ArrayList<Fragment>(2)
        recentChatFragment = RecentChatListFragment()
        callHistoryFragment = CallHistoryFragment()
        fragmentsArray.add(recentChatFragment)
        fragmentsArray.add(callHistoryFragment)
        return fragmentsArray
    }

    private fun setUpTabColors(position: Int) {
        when (position) {
            0 -> {
                chatTitleTextView.setTextColor(ContextCompat.getColor(this, R.color.color_tab_text_indicator))
                callTitleTextView.setTextColor(ContextCompat.getColor(this, R.color.dashboard_toolbar_text_color))
            }
            1 -> {
                chatTitleTextView.setTextColor(ContextCompat.getColor(this, R.color.dashboard_toolbar_text_color))
                callTitleTextView.setTextColor(ContextCompat.getColor(this, R.color.color_tab_text_indicator))
            }
        }
    }

    private fun setupTabPosition() {
        if (intent.getBooleanExtra(GroupCallUtils.IS_CALL_NOTIFICATION, false) || GroupCallUtils.isCallsTabToBeShown()) {
            callHistoryFragment.isLoadDataOnMainThread = true
            mViewPager.currentItem = 1
            GroupCallUtils.setCallsTabToBeShown(false)
        }
    }

    private fun setUpViewPager() {
        mViewPager.offscreenPageLimit = 2
        mViewPager.adapter = mAdapter
        mViewPager.addOnPageChangeListener(this)
    }

    @SuppressLint("InflateParams")
    private fun setUpTabLayout() {
        tabLayout.show()
        tabLayout.setupWithViewPager(mViewPager)

        val viewChats = layoutInflater.inflate(R.layout.custom_tabs, null)
        val viewCalls = layoutInflater.inflate(R.layout.custom_tabs, null)

        tabLayout.getTabAt(0)?.customView = viewChats
        tabLayout.getTabAt(1)?.customView = viewCalls

        chatTitleTextView = viewChats.findViewById(R.id.text)
        callTitleTextView = viewCalls.findViewById(R.id.text)

        unReadChatCountTextView = viewChats.findViewById(R.id.text_unseen_count)
        missedCallCountTextView = viewCalls.findViewById(R.id.text_unseen_count)

        chatTitleTextView.text = getString(R.string.chats_label)
        callTitleTextView.text = getString(R.string.calls_label)

        viewModel.updateUnReadChatCount()
        validateMissedCallsCount()
    }

    fun getViewPagerCurrentPosition() = mViewPager.currentItem

    /*
    * Toolbar Menu Settings */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dashboard_main, menu)
        val menuItem = menu?.findItem(R.id.action_search)
        mSearchView = menu?.findItem(R.id.action_search)?.actionView as SearchView

        /* Check if user searched in recent or contact. */
        if (!mSearchView.isIconified)
            dashboardBinding.toolbar.collapseActionView()

        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(searchString: String): Boolean {
                filterResult(searchString.trim())
                return true
            }
        })

        menuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                //make toolbar un-collapsible
                val params: AppBarLayout.LayoutParams = dashboardBinding.toolbar.layoutParams as AppBarLayout.LayoutParams
                params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP

                tabLayout.gone()
                mSearchView.maxWidth = Integer.MAX_VALUE
                closeOption(menu)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                //make toolbar collapsible
                val params: AppBarLayout.LayoutParams = dashboardBinding.toolbar.layoutParams as AppBarLayout.LayoutParams
                params.scrollFlags = (AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                        or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS)

                tabLayout.show()
                mRecentChatListType = if (searchKey.isEmpty()) RecentChatListType.RECENT else RecentChatListType.SEARCH
                openOption(menu)
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_group_chat -> {
                startActivity(Intent(this, NewGroupActivity::class.java))
                true
            }
            R.id.action_web_settings -> {
                SharedPreferenceManager.setBoolean(Constants.IS_WEBCHAT_LOGGED_IN, false)
                webConnect()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onUserBlockedOrUnblockedBySomeone(userJid: String, blockType: Boolean) {
        viewModel.setBlockUnBlockJID(userJid, blockType)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        LogMessage.d(TAG, position.toString())
    }

    override fun onPageSelected(position: Int) {
        isPageChanged = true
        callHistoryFragment.clearSelectedMessages()
        recentChatFragment.clearSelectedMessages()
        invalidateOptionsMenu()
        actionMode?.finish()
        when (position) {
            0 -> {
                swipeRefreshLayout.isEnabled = false
                dashboardBinding.newChatFab.visibility = View.VISIBLE
            }

            1 -> {
                swipeRefreshLayout.isEnabled = false
                dashboardBinding.newChatFab.visibility = View.GONE
                //mark missed calls as read
                CallLogManager.markAllUnreadMissedCallsAsRead()
                validateMissedCallsCount()
                CallNotificationUtils.setUnreadBadgeCount(
                    this,
                    CallLogManager.getUnreadMissedCallCount(),
                    null
                )
            }
        }
        setUpTabColors(position)
        searchKey = Constants.EMPTY_STRING
    }

    override fun onPageScrollStateChanged(state: Int) {
        //No Implementation
    }

    // Action Mode Callbacks
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        onClickAction(item!!.itemId)
        return true
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        configureActionMode(mode!!, menu!!)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        cabOpen = false
        when (mViewPager.currentItem) {
            0 -> recentChatFragment.clearSelectedMessages()
            1 -> callHistoryFragment.clearSelectedMessages()
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            dashboardBinding.newChatFab -> {
                startActivity(Intent(this, NewContactsActivity::class.java))
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        LogMessage.d("DashboardActivity", "onRestart")

        tabLayout.visibility = if (mRecentChatListType == RecentChatListType.SEARCH) View.GONE else View.VISIBLE
        if (recentChatFragment.isRecentListInitialized()) {
            recentChatFragment.setAdapterBasedOnSearchType()
            if (searchItemClickedPosition.isValidIndex())
                recentChatFragment.updateSearchAdapter(searchItemClickedPosition)
        }
        viewModel.getRecentChats()
        viewModel.getArchivedChatStatus()
        viewModel.updateUnReadChatCount()
        validateMissedCallsCount()
        callLogviewModel.uploadUnSyncedCallLogs()
    }

    override fun onResume() {
        super.onResume()
        viewModel.getRecentChats()
        viewModel.getArchivedChatStatus()
        viewModel.updateUnReadChatCount()
        if (SharedPreferenceManager.getBoolean(Constants.SHOW_LABEL))
            netConditionalCall({ dashboardBinding.dashboardXmppConnectionStatusLayout.gone() }, { dashboardBinding.dashboardXmppConnectionStatusLayout.show() })
        else dashboardBinding.dashboardXmppConnectionStatusLayout.gone()
        if(SafeChatUtils.updateAlert){
            SafeChatUtils.updateAlert = false
            SafeChatUtils.safeChatEnabledPrompt(this) {
                AppShortCuts.dynamicAppShortcuts(this)
                finishAffinity()
            }
        }
    }

    fun recentClick(recentList: List<RecentChat>, startActionMode: Boolean) {
        if (recentList.isEmpty()) {
            actionMode?.finish()
        } else {
            if (recentList.size == 1) {
                menuValidationForSingleItem(recentList, startActionMode)
            } else {
                actionModeMenu.findItem(R.id.action_info).isVisible = false
                actionModeMenu.findItem(R.id.action_add_chat_shortcuts).isVisible = false
                menuValidationForPinIcon(recentList)
                menuValidationForDeleteIcon(recentList)
                menuValidationForMuteUnMuteIcon(recentList)
                menuValidationForMarkReadUnReadIcon(recentList)
            }
            actionModeMenu.findItem(R.id.action_archive_chat).isVisible = true
            actionMode?.title = recentList.size.toString()
        }
        if(SafeChatUtils.updateAlert){
            SafeChatUtils.updateAlert = false
            SafeChatUtils.safeChatEnabledPrompt(this) {
                finishAffinity()
            }
        }
    }

    private fun menuValidationForSingleItem(recentList: List<RecentChat>, startActionMode: Boolean) {
        if (startActionMode)
            setActionMode()
        else
            actionModeMenu.findItem(R.id.action_info).isVisible = true

        actionModeMenu.findItem(R.id.action_un_pin).isVisible = recentList[0].isChatPinned
        actionModeMenu.findItem(R.id.action_pin).isVisible = !recentList[0].isChatPinned

        if (ChatType.TYPE_BROADCAST_CHAT != recentList[0].getChatType()) {
            actionModeMenu.findItem(R.id.action_unmute).isVisible = recentList[0].isMuted
            actionModeMenu.findItem(R.id.action_mute).isVisible = !recentList[0].isMuted
            actionModeMenu.findItem(R.id.action_add_chat_shortcuts).isVisible = true
        } else {
            actionModeMenu.findItem(R.id.action_unmute).isVisible = false
            actionModeMenu.findItem(R.id.action_mute).isVisible = false
            actionModeMenu.findItem(R.id.action_add_chat_shortcuts).isVisible = false
        }

        actionModeMenu.findItem(R.id.action_mark_as_read).isVisible = recentList[0].isConversationUnRead
        actionModeMenu.findItem(R.id.action_mark_as_unread).isVisible = !recentList[0].isConversationUnRead


        var value = ChatType.TYPE_GROUP_CHAT != recentList[0].getChatType()
        if(ChatType.TYPE_GROUP_CHAT == recentList[0].getChatType()){
            value = !GroupManager.isMemberOfGroup(recentList[0].jid,SharedPreferenceManager.getCurrentUserJid())
        }
        actionModeMenu.findItem(R.id.action_delete).isVisible = value
    }

    private fun menuValidationForPinIcon(recentList: List<RecentChat>) {
        val checkListForPinIcon = ArrayList<Boolean>()
        for (i in recentList.indices)
            checkListForPinIcon.add(recentList[i].isChatPinned)
        if (checkListForPinIcon.contains(false)) {
            actionModeMenu.findItem(R.id.action_pin).isVisible = true
            actionModeMenu.findItem(R.id.action_un_pin).isVisible = false
        } else {
            actionModeMenu.findItem(R.id.action_pin).isVisible = false
            actionModeMenu.findItem(R.id.action_un_pin).isVisible = true
        }
    }

    private fun menuValidationForMarkReadUnReadIcon(recentList: List<RecentChat>) {
        com.contus.flycommons.LogMessage.d(TAG, "MarkUnread - menuValidationForMarkReadUnReadIcon")
        val list = ArrayList<Boolean>()
        for (i in recentList.indices)
            list.add(recentList[i].isConversationUnRead)

        if (!list.contains(true)) {
            actionModeMenu.findItem(R.id.action_mark_as_read).isVisible = false
            actionModeMenu.findItem(R.id.action_mark_as_unread).isVisible = true
        } else {
            actionModeMenu.findItem(R.id.action_mark_as_read).isVisible = true
            actionModeMenu.findItem(R.id.action_mark_as_unread).isVisible = false
        }
    }

    private fun menuValidationForDeleteIcon(recentList: List<RecentChat>) {
        actionModeMenu.findItem(R.id.action_delete).isVisible = showDeleteOption(recentList)
    }

    private fun showDeleteOption(recentList: List<RecentChat>):Boolean{
        for(item in recentList){
            if((item.getChatType() == ChatType.TYPE_GROUP_CHAT) && GroupManager.isMemberOfGroup(item.jid,SharedPreferenceManager.getCurrentUserJid()))
                return false
        }
        return true
    }

    private fun menuValidationForMuteUnMuteIcon(recentList: List<RecentChat>) {
        val checkListForMuteUnMuteIcon = ArrayList<Boolean>()

        for (i in recentList.indices)
            if (!recentList[i].isBroadCast)
                checkListForMuteUnMuteIcon.add(recentList[i].isMuted)

        when {
            checkListForMuteUnMuteIcon.contains(false) -> {
                actionModeMenu.findItem(R.id.action_mute).isVisible = true
                actionModeMenu.findItem(R.id.action_unmute).isVisible = false
            }
            checkListForMuteUnMuteIcon.contains(true) -> {
                actionModeMenu.findItem(R.id.action_mute).isVisible = false
                actionModeMenu.findItem(R.id.action_unmute).isVisible = true
            }
            else -> {
                actionModeMenu.findItem(R.id.action_mute).isVisible = false
                actionModeMenu.findItem(R.id.action_unmute).isVisible = false
            }
        }
    }

    /**
     * When a new message is received the recent chat has to be updated and set a view to show the latest message...
     *
     * @param message Instance of the Message
     */
    override fun onMessageReceived(message: ChatMessage) {
        viewModel.setReceivedMsg(message)
        if (viewModel.selectedRecentChats.isNotEmpty())
            Handler(Looper.getMainLooper()).postDelayed({
                recentClick(viewModel.selectedRecentChats, false)
            }, 1000)
    }

    override fun onMessageStatusUpdated(messageId: String) {
        viewModel.setMessageStatus(messageId)
        viewModel.updateUnReadChatCount()
        viewModel.getArchivedChatStatus()
    }

    override fun onMessagesClearedOrDeleted(messageIds: ArrayList<String>, jid: String) {
        if (messageIds.isNotEmpty())
            viewModel.updateRecentMessage(messageIds)
        else if (jid.isNotEmpty())
            viewModel.setClearedMessagesView(jid)
    }

    override fun onStop() {
        super.onStop()
        viewModel.clearTypingStatusList()
    }

    private fun setObservers() {
        viewModel.unreadChatCountLiveData.observe(this, {
            validateUnreadChatUsers(it)
        })
    }

    /**
     * Update the recent chat unread users count
     */
    private fun validateUnreadChatUsers(unReadChatCount: Int) {
        // Check the count of an unread message
        if (unReadChatCount > 0) {
            unReadChatCountTextView.show()
            unReadChatCountTextView.text = unReadChatCount.toString()
        } else unReadChatCountTextView.gone()
    }

    private fun setListeners() {
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
        }
        commonAlertDialog.setOnDialogCloseListener(this)
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (!isSuccess)
            return
        if (mRecentChatListType == RecentChatListType.RECENT)
            deleteSelectedRecent(getJidFromList(viewModel.selectedRecentChats))
    }

    override fun listOptionSelected(position: Int) {
        LogMessage.d(TAG, position.toString())
    }

    /**
     * Keep track of whether the contextual action bar is open
     */
    private var cabOpen = false

    private fun setActionMode() {
        if (!cabOpen) {
            /*
             Note: Each time startActionMode is called onDestroyActionMode will be called on any existing CAB
            */
            actionMode = dashboardBinding.toolbar.startActionMode(this)
            cabOpen = true
        }
    }

    fun startActionModeForCallLogs(count: Int, startActionMode: Boolean) {
        if (count > 0) {
            if (startActionMode)
                setActionMode()
            with(actionModeMenu) {
                hideMenu(get(R.id.action_reply), get(R.id.action_forward),
                    get(R.id.action_favourite), get(R.id.action_unfavourite),
                    get(R.id.action_copy), get(R.id.action_delete),
                    get(R.id.action_info), get(R.id.action_mark_as_read),
                    get(R.id.action_mark_as_unread), get(R.id.action_archive_chat),
                    get(R.id.action_add_chat_shortcuts))
                get(R.id.action_delete).show()
            }
            actionMode?.title = count.toString()
        } else actionMode?.finish()
    }

    fun validateMissedCallsCount() {
        // Check the count of an unread missed calls
        val unreadCount = CallLogManager.getUnreadMissedCallCount()
        if (unreadCount > 0) {
            missedCallCountTextView.show()
            missedCallCountTextView.text = unreadCount.toString()
        } else
            missedCallCountTextView.gone()
    }

    override fun restoreCompleted() {
        viewModel.getRecentChats()
        viewModel.getArchivedChatStatus()
    }

    override fun userDetailsUpdated() {
        super.userDetailsUpdated()
        viewModel.getRecentChats()
        viewModel.getArchivedChatStatus()
    }

    override fun onGroupProfileFetched(groupJid: String) {
        super.onGroupProfileFetched(groupJid)
        viewModel.getRecentChatOfUser(groupJid, RecentChatEvent.GROUP_EVENT)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            when (requestCode) {
                RequestCode.CAMERA_PERMISSION_CODE -> { startActivity(Intent(this, QrCodeScannerActivity::class.java)) }
            }
        } else {
            when (requestCode) {
                RequestCode.CAMERA_PERMISSION_CODE -> {
                    val showCameraRationale = MediaPermissions.canRequestPermission(this, Manifest.permission.CAMERA)
                    if (!showCameraRationale) {
                        SharedPreferenceManager.setBoolean(Constants.CAMERA_PERMISSION_ASKED, true)
                    }
                }
            }
        }
    }
}