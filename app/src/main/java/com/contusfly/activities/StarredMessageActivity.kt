package com.contusfly.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.text.TextUtils
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.contus.flycommons.models.MessageType
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.*
import com.contusfly.databinding.ActivityStarredMessageBinding
import com.contusfly.interfaces.OnChatItemClickListener
import com.contusfly.starredMessages.StarredMessagesUtils
import com.contusfly.starredMessages.adapter.StarredMessagesAdapter
import com.contusfly.starredMessages.presenter.IStarredMessagesPresenter
import com.contusfly.starredMessages.presenter.StarredMessagesInteractor
import com.contusfly.starredMessages.presenter.StarredMessagesPresenter
import com.contusfly.starredMessages.view.IStarredMessagesInteractor
import com.contusfly.starredMessages.view.IStarredMessagesView
import com.contusfly.utils.*
import com.contusfly.utils.FirebaseUtils.Companion.setAnalytics
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CommonAlertDialog.CommonDialogClosedListener
import com.contusfly.views.CustomRecyclerView
import com.contusfly.views.PermissionAlertDialog
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatActionListener
import com.contusflysdk.api.ChatManager.updateFavouriteStatus
import com.contusflysdk.api.FlyCore.isBusyStatusEnabled
import com.contusflysdk.api.FlyMessenger.cancelMediaUploadOrDownload
import com.contusflysdk.api.FlyMessenger.downloadMedia
import com.contusflysdk.api.FlyMessenger.getFavouriteMessages
import com.contusflysdk.api.FlyMessenger.uploadMedia
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.api.models.ContactChatMessage
import com.contusflysdk.utils.*
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import com.google.firebase.analytics.FirebaseAnalytics
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

class StarredMessageActivity : BaseActivity(), OnChatItemClickListener,
        CommonDialogClosedListener, ActionMode.Callback, IStarredMessagesView, IStarredMessagesInteractor {

    private lateinit var starredMessageBinding: ActivityStarredMessageBinding

    /**
     * The List of chat messages to display in the adapter
     */
    private var starredMessagesList: MutableList<ChatMessage> = mutableListOf()

    /**
     * The List of chat messages to display in the adapter
     */
    private var selectedStarredMessagesList: MutableList<ChatMessage> = mutableListOf()

    /**
     * The adapter chat data in the to attach in the recycler view list
     */
    private var starredMessagesAdapterAdapterData: StarredMessagesAdapter? = null

    /**
     * Menu of the clicked item
     */
    private var menu: Menu? = null

    /**
     * Presenter for the chat view
     */
    private var starredMessagesViewPresenter: IStarredMessagesPresenter? = null

    /**
     * Store upload/download update elapse time
     */
    private var updateAdapterTime: Long = 0

    private var itemPosition = 0

    /**
     * Recycler view object which used to display the chat view
     */
    private var listStarredMessages: CustomRecyclerView? = null

    /**
     * The List of chat messages to display in the adapter
     */
    private var searchedStarredMessageList: MutableList<ChatMessage> = mutableListOf()

    /**
     * The common alert dialog to display the alert dialogs in the alert view
     */
    private var commonAlertDialog: CommonAlertDialog? = null

    /**
     * Selected messages for the info, delete and forward
     */
    private var clickedStarredMessages: MutableList<String> = mutableListOf()

    /**
     * The action mode of the menu.
     */
    private var actionMode: ActionMode? = null

    /**
     * Root layout of the chat view
     */
    private var rootLayout: RelativeLayout? = null

    private val uploadDownloadProgressObserver = PublishSubject.create<String>()

    val compositeDisposable: CompositeDisposable by lazy { CompositeDisposable() }

    /**
     * Presenter for the chat view
     */
    private var starredMessagesInteractor: com.contusfly.starredMessages.presenter.IStarredMessagesInteractor? = null

    /**
     * The Txt no msg.
     */
    private var txtNoStarredMsg: TextView? = null

    private var emptyView: TextView? = null

    /**
     * The View chat.
     */
    private var viewStarredMessages: LinearLayout? = null

    /**
     * Store onclick time to avoid double click
     */
    private var lastClickTime: Long = 0

    /**
     * contact msg for invite
     */
    private var selectedContactMessage: ContactChatMessage? = null

    private var searchedText = emptyString()
    private var searchEnabled = false

    /**
     * Layout manager
     */
    private var mManager: LinearLayoutManager? = null

    private val permissionAlertDialog: PermissionAlertDialog by lazy { PermissionAlertDialog(this) }

    private val downloadPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        starredMessageBinding = ActivityStarredMessageBinding.inflate(layoutInflater)
        setContentView(starredMessageBinding.root)
        setUpToolbar()
        setAnalytics(FirebaseAnalytics.Event.VIEW_ITEM, "Chat View", "")
        initViews()

        clickedStarredMessages = java.util.ArrayList()
        selectedStarredMessagesList = java.util.ArrayList()
        starredMessagesAdapterAdapterData = StarredMessagesAdapter()
        starredMessagesAdapterAdapterData!!.setAdapterData(this)
        starredMessagesList = java.util.ArrayList()
        searchedStarredMessageList = java.util.ArrayList()

        emptyView = starredMessageBinding.emptyList.textEmptyView
        emptyView!!.text = getString(R.string.no_message_found)
        listStarredMessages!!.setEmptyView(emptyView)

        handleStarredItemClick()

        commonAlertDialog = CommonAlertDialog(this)
        starredMessagesAdapterAdapterData!!.setOnStarredMessageDownloadClickListener(this)
        commonAlertDialog!!.setOnDialogCloseListener(this)
        viewStarredMessages!!.visibility = View.GONE
        txtNoStarredMsg!!.visibility = View.GONE
        initObservers()
    }

    override fun onGroupProfileUpdated(groupJid: String) {
        super.onGroupProfileUpdated(groupJid)
        updateAdapter()
    }

    /**
     * To handle broadcast of any user's profile changes
     * like Profile pic, Profile msg
     */
    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        updateAdapter()
    }

    /**
     * To handle callback of any user's profile deleted
     */
    override fun userDeletedHisProfile(jid: String) {
        super.userDeletedHisProfile(jid)
        updateAdapter()
    }

    private fun setUpToolbar() {
        setSupportActionBar(starredMessageBinding.toolbar)
        supportActionBar?.title = getString(R.string.starred_messages)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        UserInterfaceUtils.initializeCustomToolbar(this, starredMessageBinding.toolbar)
        starredMessageBinding.toolbar.navigationIcon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(ContextCompat.getColor(this, R.color.dashboard_toolbar_text_color), BlendModeCompat.SRC_ATOP)
    }

    /**
     * Handle the list item click and long click from the recycler view of the chat view.
     */
    private fun handleStarredItemClick() {
        ItemClickSupport.addTo(listStarredMessages).setOnItemLongClickListener { recyclerView: RecyclerView?, position: Int, v: View? ->
            onItemLongClick(position)
            true
        }
        ItemClickSupport.addTo(listStarredMessages).setOnItemClickListener { recyclerView: RecyclerView?, position: Int, v: View? ->
            hideKeyboard()
            if (clickedStarredMessages.isNotEmpty()) onItemClick(position)
        }
    }

    override fun onResume() {
        super.onResume()
        if (searchEnabled && searchedText.isNotEmpty()) {
            searchStarredMessage(searchedText)
        } else {
            // Save Current Scroll state to retain scroll position after DiffUtils Applied
            val previousState =  listStarredMessages!!.layoutManager?.onSaveInstanceState() as Parcelable
            listStarredMessages!!.layoutManager?.onRestoreInstanceState(previousState)

            starredMessagesViewPresenter!!.setChatAdapter()
            starredMessagesAdapterAdapterData!!.notifyDataSetChanged()
        }
    }

    override fun onStop() {
        if (getMenu() != null) {
            val menuItem = getMenu()!!.findItem(R.id.action_search)
            if (menuItem != null) menuItem.isVisible = false
        }
        super.onStop()
    }

    private fun initViews() {
        txtNoStarredMsg = starredMessageBinding.viewChatFooter.textNoMsg
        viewStarredMessages = starredMessageBinding.viewChatFooter.viewChat
        rootLayout = starredMessageBinding.rootView
        listStarredMessages = starredMessageBinding.viewChatList
        listStarredMessages!!.isNestedScrollingEnabled = false
        listStarredMessages!!.setHasFixedSize(true)
        listStarredMessages!!.setItemViewCacheSize(20)
        listStarredMessages!!.isDrawingCacheEnabled = true
        listStarredMessages!!.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_LOW
        starredMessagesViewPresenter = StarredMessagesPresenter()
        starredMessagesInteractor = StarredMessagesInteractor()
        starredMessagesInteractor!!.attach(this)
        starredMessagesViewPresenter!!.attach(this)
        starredMessageBinding.viewFooter.visibility = View.GONE

        mManager = LinearLayoutManager(this)
        listStarredMessages!!.layoutManager = mManager
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            menuInflater.inflate(R.menu.menu_search, menu)
            val menuItem = menu.findItem(R.id.action_search)
            val searchView = MenuItemCompat.getActionView(menu.findItem(R.id.action_search)) as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    return false
                }

                override fun onQueryTextChange(searchString: String): Boolean {
                    val searchStr: String = searchString.trim()
                    searchedText = searchStr
                    searchStarredMessage(searchStr)
                    return true
                }
            })
            searchView.setOnCloseListener {
                /**
                 * Handling while back press from search
                 */
                onMessageStatusUpdated("")
                true
            }
            MenuItemCompat.setOnActionExpandListener(menuItem, object : MenuItemCompat.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    setEmptyView(true)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    onMessageStatusUpdated("")
                    setEmptyView(false)
                    return true
                }
            })
        } catch (e: Exception) {
            LogMessage.e(TAG, e)
        }
        return true
    }

    private fun setEmptyView(b: Boolean) {
        searchEnabled = b
        searchedText = emptyString()
        emptyView!!.text = if (b) getString(R.string.msg_no_results) else getString(R.string.no_message_found)
        listStarredMessages!!.setEmptyView(emptyView)
    }

    /**
     * Validate search key for getting message
     *
     * @param filterKey Key to search
     */
    fun searchStarredMessage(filterKey: String?) {
        if (TextUtils.isEmpty(filterKey)) {
            updateAdapter()
            starredMessagesAdapterAdapterData!!.setSearch(searchEnabled, searchedText)
        } else addSearchedMessagesToList(filterKey!!)
    }

    /**
     * Add the message into the search list which are all available from the search key. Search the
     * text in the message list.
     *
     * @param filterKey Key to search
     */
    private fun addSearchedMessagesToList(filterKey: String) {
        searchedStarredMessageList.clear()
        for (message in starredMessagesList) {
            if (isTextMessageContainsFilterKey(message, filterKey)) {
                searchedStarredMessageList.add(message)
            } else if (isImageCaptionContainsFilterKey(message, filterKey))
                searchedStarredMessageList.add(message)
            else if (isVideoCaptionContainsFilterKey(message, filterKey))
                searchedStarredMessageList.add(message)
            else if (MessageType.DOCUMENT == message.getMessageType() && message.mediaChatMessage.mediaFileName.isNotEmpty()
                && message.mediaChatMessage.mediaFileName.toLowerCase().contains(filterKey.toLowerCase()))
                searchedStarredMessageList.add(message)
            else if (MessageType.CONTACT == message.getMessageType() && message.contactChatMessage.contactName.isNotEmpty()
                && message.contactChatMessage.contactName.toLowerCase().contains(filterKey.toLowerCase()))
                searchedStarredMessageList.add(message)
            else if (message.senderUserName.isNotEmpty()
                && message.senderUserName.toLowerCase().contains(filterKey.toLowerCase()))
                searchedStarredMessageList.add(message)
            else if (message.isMessageSentByMe && "You".toLowerCase().contains(filterKey.toLowerCase()))
                searchedStarredMessageList.add(message)
            else if(message.isGroupMessage() && ProfileDetailsUtils.getDisplayName(message.getChatUserJid())!!.toLowerCase().contains(filterKey.toLowerCase()))
                searchedStarredMessageList.add(message)
        }
        starredMessagesAdapterAdapterData!!.setSearch(searchEnabled, searchedText)
        starredMessagesAdapterAdapterData!!.setStarredMessages(searchedStarredMessageList)
        starredMessagesAdapterAdapterData!!.notifyDataSetChanged()
    }

    private fun isVideoCaptionContainsFilterKey(message: ChatMessage, filterKey: String): Boolean {
        return MessageType.VIDEO == message.getMessageType() && message.mediaChatMessage.mediaCaptionText.isNotEmpty()
                && message.mediaChatMessage.mediaCaptionText.toLowerCase().contains(filterKey.toLowerCase())
    }

    private fun isImageCaptionContainsFilterKey(message: ChatMessage, filterKey: String): Boolean {
        return MessageType.IMAGE == message.getMessageType() && message.mediaChatMessage.mediaCaptionText.isNotEmpty()
                && message.mediaChatMessage.mediaCaptionText.toLowerCase().contains(filterKey.toLowerCase())
    }

    private fun isTextMessageContainsFilterKey(message: ChatMessage, filterKey: String): Boolean {
        return MessageType.TEXT == message.getMessageType() &&
                message.getMessageTextContent().toLowerCase().contains(filterKey.toLowerCase())
    }

    override fun onMessageStatusUpdated(messageId: String) {
        super.onMessageStatusUpdated(messageId)
        updateAdapter()
    }

    override fun onUpdateUnStarAllMessages() {
        super.onUpdateUnStarAllMessages()
        updateAdapter()
    }

    private fun initObservers() {
        val uploadDownloadProgressDisposable = uploadDownloadProgressObserver.buffer(5)
            .subscribeOn(Schedulers.io()).map {
                val messageIdAndPositionList = ArrayList<Int>()
                for (item in it.distinct()) {
                    messageIdAndPositionList.add(getMessagePosition(item))
                }
                return@map messageIdAndPositionList
            }.observeOn(AndroidSchedulers.mainThread()).subscribe {
                for (item in it) {
                    if (item != -1) {
                        val bundle = Bundle()
                        bundle.putInt(Constants.NOTIFY_MESSAGE_PROGRESS_CHANGED, 1)
                        starredMessagesAdapterAdapterData!!.notifyItemChanged(item, bundle)
                    }
                }
            }
        compositeDisposable.add(uploadDownloadProgressDisposable)
    }

    private fun getMessagePosition(messageId: String?): Int {
        return starredMessagesList.indexOfFirst { it.messageId == messageId }
    }

    override fun onUploadDownloadProgressChanged(messageId: String, progressPercentage: Int) {
        super.onUploadDownloadProgressChanged(messageId, progressPercentage)
        getMessageByID(messageId)?.mediaChatMessage?.let {
            it.mediaProgressStatus = progressPercentage.toLong()
        }
        uploadDownloadProgressObserver.onNext(messageId)
    }

    private fun getMessageByID(messageId: String): ChatMessage? {
        val index = starredMessagesList.indexOfFirst { it.messageId == messageId }
        return if (index.isValidIndex())
            starredMessagesList[index]
        else null
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    /**
     * Handling the list of starred message
     *
     * @param starredMessagesList list of starred message
     */
    fun setStarredMessagesList(starredMessagesList: MutableList<ChatMessage>) {
        this.starredMessagesList = starredMessagesList
    }

    fun getUpdateAdapterTime(): Long {
        return updateAdapterTime
    }

    fun setUpdateAdapterTime(updateAdapterTime: Long) {
        this.updateAdapterTime = updateAdapterTime
    }

    override fun onMediaStatusUpdated(message: ChatMessage) {
        super.onMediaStatusUpdated(message)
        getstarredMessagesViewPresenter()!!.updateList(message.getMessageId())
        updateAdapter()
    }

    fun getstarredMessagesViewPresenter(): IStarredMessagesPresenter? {
        return starredMessagesViewPresenter
    }

    fun setstarredMessagesViewPresenter(starredMessagesViewPresenter: IStarredMessagesPresenter?) {
        this.starredMessagesViewPresenter = starredMessagesViewPresenter
    }

    override fun onMessagesClearedOrDeleted(messageIds: ArrayList<String>, jid: String) {
        super.onMessagesClearedOrDeleted(messageIds, jid)
        updateAdapter()
    }

    override fun clearActionMenu() {
        super.clearActionMenu()
        getActionMode()!!.finish()
    }

    override fun onDownloadClicked(item: ChatMessage) {
        if (MediaPermissions.isWriteFilePermissionAllowed(this)) {
            if (AppUtils.isNetConnected(this)) {
                downloadMedia(item.getMessageId())
                starredMessagesViewPresenter!!.updateList(item.getMessageId())
            } else CustomToast.show(this, getString(R.string.msg_no_internet))
        } else MediaPermissions.requestStorageAccess(this, permissionAlertDialog, downloadPermissionLauncher)
    }

    override fun onCancelDownloadClicked(messageItem: ChatMessage) {
        if (AppUtils.isNetConnected(this)) {
            cancelMediaUploadOrDownload(messageItem.getMessageId())
            starredMessagesViewPresenter!!.updateList(messageItem.getMessageId())
        } else CustomToast.show(this, getString(R.string.msg_no_internet))
    }

    override fun onCancelUploadClicked(messageItem: ChatMessage) {
        if (AppUtils.isNetConnected(this)) {
            cancelMediaUploadOrDownload(messageItem.getMessageId())
            starredMessagesViewPresenter!!.updateList(messageItem.getMessageId())
        } else CustomToast.show(this, getString(R.string.msg_no_internet))
    }

    override fun onRetryClicked(item: ChatMessage?) {
        if (AppUtils.isNetConnected(this)) uploadMedia(item!!.getMessageId()) else CustomToast.show(this, getString(R.string.msg_no_internet))
    }

    override fun onSenderItemClicked(item: ChatMessage?, position: Int) {
        handleSatrredMediaMessageClick(position)
    }

    override fun onReceiverItemClicked(item: ChatMessage?, position: Int) {
        handleSatrredMediaMessageClick(position)
    }

    /**
     * Handle media message item click and long press
     *
     * @param position Position of the item in view
     */
    private fun handleSatrredMediaMessageClick(position: Int) {
        if (clickedStarredMessages.isNotEmpty()) onItemClick(position) else if (position != -1) {
            /**
             * Compare elapsed time and clicked time to avoid double click action
             */
            if (SystemClock.elapsedRealtime() - lastClickTime > 1000) {
                itemPosition = position
                val clickedMessage = if (searchedStarredMessageList.isEmpty()) starredMessagesList[position] else searchedStarredMessageList[position]
                startActivity(Intent(this, ChatActivity::class.java)
                        .putExtra(LibConstants.JID, clickedMessage.getChatUserJid())
                        .putExtra(LibConstants.MESSAGE_ID, clickedMessage.getMessageId())
                        .putExtra(Constants.CHAT_TYPE, clickedMessage.getChatType())
                        .putExtra(Constants.IS_STARRED_MESSAGE, true))
            }
            lastClickTime = SystemClock.elapsedRealtime()
        }
    }

    override fun onSenderItemLongClick(item: ChatMessage?, position: Int) {
        onItemLongClick(position)
    }

    /**
     * Handle On item long click for the options having delete, forward and info. Add into the
     * selected list for the list showing and do the operations
     *
     * @param starredItemPosition Position of the clicked list item
     */
    private fun onItemLongClick(starredItemPosition: Int) {
        try {
            hideKeyboard()
            if (starredItemPosition != -1) {
                val clickedStarredMessage = if (searchedStarredMessageList.isEmpty()) starredMessagesList[starredItemPosition] else searchedStarredMessageList[starredItemPosition]
                /**
                 * Check already selected and choosing media is available locally.
                 */
                if (!clickedStarredMessages.contains(clickedStarredMessage.getMessageId())) onSelectItem(clickedStarredMessage)
            }
        } catch (e: java.lang.Exception) {
            LogMessage.e(Constants.TAG, e)
        }
    }
    override fun onReceiverItemLongClick(item: ChatMessage?, position: Int) {
        onItemClick(position)
    }

    /**
     * Handle On item click for the options having delete, forward and info. and remove that from
     * the selected list
     *
     * @param position Position of the item
     */
    private fun onItemClick(position: Int) {
        try {
            hideKeyboard()
            if (position != -1) {
                val clickedMessage = if (searchedStarredMessageList.isEmpty()) starredMessagesList[position] else searchedStarredMessageList[position]
                /**
                 * Remove the selected item if ta the single item.
                 */
                if (clickedStarredMessages.contains(clickedMessage.getMessageId())) {
                    clickedStarredMessages.remove(clickedMessage.getMessageId())
                    starredMessagesViewPresenter!!.refreshSelectedMessages()
                    starredMessagesViewPresenter!!.prepareActionMode()
                    selectedStarredMessagesList.remove(clickedMessage)
                } else {
                    /**
                     * Add the additional element to selected list.
                     */
                    onSelectItem(clickedMessage)
                }
            }
        } catch (e: java.lang.Exception) {
            LogMessage.e(TAG, e)
        }
    }

    /**
     * Hide the soft input keyboard from the startupActivityContext of the window that is currently accepting
     * input..
     */
    private fun hideKeyboard() {
        val view = getContext()!!.currentFocus
        Utils.hideSoftInput(getContext(), view)
    }

    /**
     * Call back for on item selection
     *
     * @param clickedMessage Selected message item
     */
    private fun onSelectItem(clickedMessage: ChatMessage) {
        try {
            /**
             * Check already selected and choosing media is available locally.
             */
            if (MessageType.NOTIFICATION != clickedMessage.getMessageType()) {
                if (getClickedStarredMessages().isEmpty()) actionMode =  starredMessageBinding.toolbar.startActionMode(this)
                clickedStarredMessages.add(clickedMessage.getMessageId())
                selectedStarredMessagesList.add(clickedMessage)
                starredMessagesViewPresenter!!.refreshSelectedMessages()
                starredMessagesViewPresenter!!.prepareActionMode()
            }
        } catch (e: java.lang.Exception) {
            LogMessage.e(TAG, e)
        }
    }

    override fun onReplyMessageClick(messageId: String) {
        //Do nthg
    }

    override fun onSenderMediaForward(item: ChatMessage, position: Int) {
        //Do nthg
    }

    override fun onContactClick(item: ChatMessage, position: Int, registeredJid: String?) {
        //Do nthg
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) starredMessagesInteractor!!.handleDialogResponse()
    }

    override fun listOptionSelected(position: Int) {
        //invite contact
        if (commonAlertDialog!!.dialogAction === CommonAlertDialog.DialogAction.INVITE && selectedContactMessage != null)
            InviteContactUtils().handleSelectedOptions(position, getContext(), null, selectedContactMessage!!.getContactPhoneNumbers()[0])
    }

    override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
        configureActionMode(actionMode, menu)
        return true
    }

    override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(p0: ActionMode?, menuItem: MenuItem?): Boolean {
        return onClickAction(menuItem!!.itemId)
    }

    /**
     * On click action boolean of the long press menu.
     *
     * @param itemId The menu item id
     * @return boolean True if click completed
     */
    private fun onClickAction(itemId: Int): Boolean {
        val clickDone: Boolean
        when (itemId) {
            R.id.action_delete -> clickDone = starredMessagesInteractor!!.deleteMessageAlert()
            R.id.action_copy -> {
                if (clickedStarredMessages.size == 1) ChatUtilsOperations().copyOrShareMsg(clickedStarredMessages, this)
                actionMode!!.finish()
                clickDone = true
            }
            R.id.action_forward -> clickDone = forwardStarredMessage()
            R.id.action_share -> {
                if (clickedStarredMessages.size == 1) MediaShareUtils().shareMediaExternal(clickedStarredMessages, this)
                actionMode!!.finish()
                clickDone = true
            }
            R.id.action_unfavourite -> {
                for (messages in selectedStarredMessagesList) {
                    updateFavouriteStatus(messages.getMessageId(), messages.getChatUserJid(), false, object : ChatActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            //Not Needed
                        }
                    })
                }
                actionMode!!.finish()
                updateAdapter()
                clickDone = true
            }
            else -> clickDone = false
        }
        return clickDone
    }

    /**
     * Validate user potions and forward the message
     *
     * @return boolean Return true if the message forwarded
     */
    private fun forwardStarredMessage(): Boolean {
        val isUserClicked: Boolean
        /*
          Check the user busy status while user forwarding the message
         */isUserClicked = if (!isBusyStatusEnabled()) {
            startActivity(Intent(this, ForwardMessageActivity::class.java)
                    .putStringArrayListExtra(com.contus.flycommons.Constants.CHAT_MESSAGE, clickedStarredMessages as java.util.ArrayList<String?>))
            true
        } else {
            showUserBusyAlert()
            false
        }
        return isUserClicked
    }

    /**
     * User alert while forwarding message if user enabled busy option in settings
     */
    private fun showUserBusyAlert() {
        commonAlertDialog!!.dialogAction = CommonAlertDialog.DialogAction.STATUS_BUSY
        commonAlertDialog!!.showAlertDialog(getString(R.string.msg_disable_busy_status), getString(R.string.action_yes),
                getString(R.string.action_no), CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false)
    }

    /**
     * Set the action menu for the long press menu
     *
     * @param mode Instance of the Alert dialog action mode
     * @param menu Instance of Menu
     */
    private fun configureActionMode(mode: ActionMode, menu: Menu) {
        this.menu = StarredMessagesUtils.configureStarredMenuActionMode(this, mode, menu)
        menu.findItem(R.id.action_reply).isVisible = false
    }

    override fun onDestroyActionMode(p0: ActionMode?) {
        clickedStarredMessages.clear()
        selectedStarredMessagesList.clear()
        starredMessagesViewPresenter!!.refreshSelectedMessages()
    }

    override fun getContext(): Activity? {
        return this
    }

    override fun getChatMessages(): MutableList<ChatMessage> {
        return starredMessagesList
    }

    override fun updateAdapter() {
        if (searchEnabled && searchedText.isNotEmpty()) {
            searchStarredMessage(searchedText)
        } else {
            searchedStarredMessageList.clear()
            starredMessagesList = getFavouriteMessages().toMutableList()
            starredMessagesAdapterAdapterData!!.setStarredMessages(starredMessagesList)
            starredMessagesAdapterAdapterData!!.notifyDataSetChanged()
        }
    }

    override fun setChatMessages(messages: MutableList<ChatMessage>) {
        this.starredMessagesList = messages
    }

    override fun getChatRecylerView(): CustomRecyclerView? {
        return listStarredMessages
    }

    override fun getChatAdapter(): StarredMessagesAdapter? {
        return starredMessagesAdapterAdapterData
    }

    override fun getActivity(): Context {
        return this
    }

    override fun getAlertDialog(): CommonAlertDialog? {
        return commonAlertDialog
    }

    override fun getActionMode(): ActionMode? {
        return actionMode
    }

    override fun getClickedStarredMessages(): MutableList<String> {
        return clickedStarredMessages
    }

    override fun getSelectedStarredMessages(): MutableList<ChatMessage> {
        return selectedStarredMessagesList
    }

    override fun getRootLayout(): RelativeLayout? {
        return rootLayout
    }

    override fun getMenu(): Menu? {
        return menu
    }

    override fun getLayoutManager(): LinearLayoutManager? {
        return mManager
    }

    override fun onAudioPlayed() {
        //No Implementation needed
    }

    override fun userBlockedMe(jid: String) {
        super.userBlockedMe(jid)
        updateAdapter()
    }

    override fun userUnBlockedMe(jid: String) {
        super.userUnBlockedMe(jid)
        updateAdapter()
    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        updateAdapter()
    }
}