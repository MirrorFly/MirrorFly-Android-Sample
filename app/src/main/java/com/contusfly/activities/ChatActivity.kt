package com.contusfly.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.*
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.contus.flycommons.ChatType
import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG
import com.contus.webrtc.CallType
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.CallManager.isOnTelephonyCall
import com.contus.call.utils.CallConstants
import com.contus.call.utils.GroupCallUtils
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.*
import com.contusfly.activities.parent.ChatParent
import com.contusfly.adapters.ChatAdapter
import com.contusfly.adapters.ReplySuggestionsAdapter
import com.contusfly.call.CallPermissionUtils
import com.contusfly.call.groupcall.UsersSelectionActivity
import com.contusfly.chat.RealPathUtil
import com.contusfly.chat.ReplyHashMap
import com.contusfly.chat.reply.MessageSwipeController
import com.contusfly.constants.MobileApplication
import com.contusfly.interfaces.OnChatItemClickListener
import com.contusfly.models.Chat
import com.contusfly.models.MediaPreviewModel
import com.contusfly.models.MessageObject
import com.contusfly.utils.*
import com.contusfly.views.*
import com.contusflysdk.api.ChatActionListener
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.utils.ConstantActions
import com.contusflysdk.utils.ItemClickSupport
import com.contusflysdk.utils.MediaShareUtils
import com.contusflysdk.views.CustomToast
import com.google.firebase.analytics.FirebaseAnalytics
import com.jakewharton.rxbinding3.recyclerview.scrollEvents
import dagger.android.AndroidInjection
import io.github.rockerhieu.emojicon.EmojiconGridFragment
import io.github.rockerhieu.emojicon.EmojiconsFragment
import io.github.rockerhieu.emojicon.emoji.Emojicon
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ChatActivity : ChatParent(), ActionMode.Callback, View.OnTouchListener, EmojiconsFragment.OnEmojiconBackspaceClickedListener,
        EmojiconGridFragment.OnEmojiconClickedListener, OnChatItemClickListener,
        CommonAlertDialog.CommonDialogClosedListener,
        CommonAlertDialog.CommonTripleDialogClosedListener, View.OnClickListener,
        ReplySuggestionsAdapter.SuggestionClickListener,
        AudioRecordView.RecordingListener {

    private val TAG = ChatActivity::class.java.simpleName

    private var keyboardHeightProvider: KeyboardHeightProvider? = null

    @Inject
    lateinit var mainApplication: Application

    private val uploadDownloadProgressObserver = PublishSubject.create<String>()

    /**
     * The receiver to handle the telephony call state.
     */
    private var telephoneCallReceiver: BroadcastReceiver? = null

    private var smartReply:String ?= null

    private var forwardClickedPostition:Int = -1

    private var isFromResumeState: Boolean = false

    private val downloadPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: ChatUtils.checkWritePermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if(writePermissionGranted) {
            handleDownloadProcess()
        }
    }

    override fun onMessageReceived(message: ChatMessage) {
        message.let {
            if (message.isMessageSentByMe())
                handleUnreadMessageSeparator(true)
            val localInstanceOfMessage = FlyMessenger.getMessageOfId(message.messageId)
            if (chat.toUser == message.chatUserJid && (!message.isMessageSentByMe() || message.isItCarbonMessage())
                && (localInstanceOfMessage != null && !localInstanceOfMessage.isMessageDeleted)) {
                removeUnReadMsgSeparatorOnMessageReceiver()
                addMessageAndScroll(message)
            }
            if (chatMessageEditText.text.toString().trim().isEmpty() && !isReplyTagged && !attachmentDialog.isShowing)
                addMessagesforSmartReply()
            if(FlyCore.isBusyStatusEnabled() && chat.isSingleChat()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateChatAdapterWithLatestData(true)
                }, 500)
            }
        }
    }

    private fun removeUnReadMsgSeparatorOnMessageReceiver() {
        try {
            if (mainList.count { it.messageId == unreadMessageTypeMessageId } >= 1) {
                val position = mainList.size - mainList.reversed().indexOfLast { it.messageId == unreadMessageTypeMessageId } - 1
                if (position != -1 && position < mainList.size) {
                    mainList.removeAt(position)
                    chatAdapter.notifyItemRemoved(position)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
        }
    }

    /**
     * Called when the profile update of the logged in user has been completed successfully.
     */
    override fun userUpdatedHisProfile(jid: String) {
        if (chat.toUser == jid) {
            viewModel.getProfileDetails()
        }
    }

    /**
     * Update the network state change while user chat with other user.
     */
    override fun updateNetworkStateChange(isConnected: Boolean) {
        viewModel.getProfileDetails()
        super.updateNetworkStateChange(isConnected)
    }
    override fun onLeftFromGroup(groupJid: String, leftUserJid: String) {
        super.onLeftFromGroup(groupJid, leftUserJid)
        parentViewModel.getParticipantsNameAsCsv(chat.toUser)
    }
    /**
     * Handle the upload/download progress changes
     */
    override fun onUploadDownloadProgressChanged(messageId: String, progressPercentage: Int) {
        Log.d(TAG, "onUploadDownloadProgressChanged progressPercentage: ${progressPercentage}")
        getMessagebyID(messageId)?.mediaChatMessage?.let {
            it.mediaProgressStatus = progressPercentage.toLong()
        }
        uploadDownloadProgressObserver.onNext(messageId)
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
                            chatAdapter.notifyItemChanged(item, bundle)
                       }
                    }
                }
        compositeDisposable.add(uploadDownloadProgressDisposable)
    }

    /**
     * Called when the upload has been completed from the chat view in the async task.
     *
     * @param message Updated message
     */
    override fun onMediaStatusUpdated(message: ChatMessage) {
        val messagePosition = getMessagePosition(message.messageId)
        if (messagePosition.isValidIndex()) {
            chatAdapter.refreshMessage(messagePosition, message)
            val bundle = Bundle()
            bundle.putInt(Constants.NOTIFY_MESSAGE_MEDIA_STATUS_CHANGED, 1)
            chatAdapter.notifyItemChanged(messagePosition, bundle)
        }
        invalidateActionMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        keyboardHeightProvider = KeyboardHeightProvider(this)
        keyboardHeightProvider?.addKeyboardListener(getKeyboardListener())
        mobileApplication = mainApplication as MobileApplication
        startObservingViewModel()
        handleMainIntent()
        initialization()
        viewModel.setUserJid(toUser)
        //Chat Parent
        setUserJid()
        initChatAdapter()
        if (isFromStarredMessages) viewModel.loadInitialData()
        setMessageCallBack()
        handleListItemClick()
        initClickListeners()
        initObservers()
        handleOnNewIntent(intent)
        registerTelephonyCallListener()
    }

    private fun setRecyclerViewScrollListener() {
        val recyclerScrollDisposable = listChats.scrollEvents().subscribe {
            firstCompletelyVisibleItemPosition = mManager.findFirstVisibleItemPosition()
            lastCompletelyVisibleItemPosition = mManager.findLastVisibleItemPosition()

            if (lastCompletelyVisibleItemPosition < (mainList.size - 1))
                showHideRedirectToLatest.onNext(true)
            else
                showHideRedirectToLatest.onNext(false)

            if (!allMessageLoaded && firstCompletelyVisibleItemPosition < 50 && !isLoadingMessageFromDB)
                loadOldMessages()
        }
        compositeDisposable.add(recyclerScrollDisposable)
    }

    private fun loadOldMessages() {
        isLoadingMessageFromDB = true
        Log.d("loadOldMessages", "Entered ")
    }

    private fun initClickListeners() {
        imgSend.setOnClickListener(this)
        attachment.setOnClickListener(this)
        back.setOnClickListener(this)
        closeReplyMessage.setOnClickListener(this)
        chatMessageEditText.setOnClickListener(this)
        emojiHandler.attachKeyboardListeners(chatMessageEditText)
        emojiHandler.setIconImageView(smiley)
        emojiHandler.setHandledFrom(TAG)
        smiley.setOnClickListener(this)
        chatMessageEditText.setOnLongClickListener {
            if(emojiHandler.isEmojiShowing) {
                emojiHandler.hideEmoji()
                chatMessageEditText.requestFocus()
                chatMessageEditText.showSoftKeyboard()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                showChatKeyboard = false
            }
            false
        }

        audioRecordView.initViews()
        audioRecordView.setRecordingListener(this)
    }

    override fun onStop() {
        super.onStop()
        setUnsentMessageForAnUser(chatMessageEditText.text.toString())
        if (selectedMessageIdForReply.isEmpty()) {
            SharedPreferenceManager.setString(Constants.REPLY_MESSAGE_ID, Constants.EMPTY_STRING)
            SharedPreferenceManager.setString(Constants.REPLY_MESSAGE_USER, Constants.EMPTY_STRING)
        } else ReplyHashMap.saveReplyId(chat.toUser, selectedMessageIdForReply)
        viewModel.deleteUnreadMessageSeparator(chat.toUser)
    }

    private fun initChatAdapter() {
        chatAdapter = ChatAdapter(mainList, clickedMessages, chat.chatType, this)
        chatAdapter.hasStableIds()
        listChats.adapter = chatAdapter
        chatAdapter.setOnDownloadClickListener(this)
        val messageSwipeController = MessageSwipeController(this, object : MessageSwipeController.SwipeControllerActions {
            override fun showSwipeInReplyUI(position: Int) {
                Handler(Looper.getMainLooper()).postDelayed({
                    isReplyTagged = true
                    replyMessageSlideActionClicked(position)
                    handleSearchToolbar(showSearch = false, hideKeyboard = false)
                    if (emojiHandler.isEmojiShowing) emojiHandler.hideEmoji()
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }, 150)
            }
        })
        messageSwipeController.getSwipeEscapeVelocity(0.25f)
        val itemTouchHelper = ItemTouchHelper(messageSwipeController)
        itemTouchHelper.attachToRecyclerView(listChats)
    }

    private fun startObservingViewModel() {
        startObserving()
        //Message Observer
        viewModel.initialMessageList.observe(this, { messagesList ->
            mainList.clear() //since these are intial messages, the list must be cleared
            mainList.addAll(messagesList)
            chatAdapter.notifyDataSetChanged()
            if (mainList.isNotEmpty() && messageId.isNotEmpty())
                highlightGivenMessageId(messageId)
        })

        //Roster Observer
        viewModel.userRoster.observe(this, { userRoster ->
            Log.d(TAG, "startObservingViewModel userRoster called")
            rosterObservable.onNext(userRoster)
        })

        parentViewModel.groupParticipantsName.observe(this, {
            setUserPresenceStatus(it)
        })
    }

    private fun startObserving() {
        val rosterObservable = rosterObservable.observeOn(AndroidSchedulers.mainThread()).subscribe({ profileDetails ->
            this.profileDetails = profileDetails
            setUserProfileAndStatus()
            setUserProfileImage()
            invalidateOptionsMenu()
            updateChatEditText()
        }, { LogMessage.e(TAG, it) })

        val dialogObservable = dialog.debounce(PROFILE_UPDATE_TIME_OUT, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe({
            if (doProgressDialog != null && doProgressDialog!!.isShowing) {
                doProgressDialog!!.dismiss()
                showToast(getString(R.string.msg_no_internet))
            }
        }, { LogMessage.e(TAG, it) })

        compositeDisposable.add(rosterObservable)
        compositeDisposable.add(dialogObservable)
    }

    /**
     * Handle the list item click and long click from the recycler view of the chat view.
     */
    private fun handleListItemClick() {
        ItemClickSupport.addTo(listChats).setOnItemLongClickListener { _: RecyclerView, position: Int, _: View ->
            onItemLongClick(position)
            Log.d(TAG, "setOnItemLongClickListener  $position")
            true
        }
        ItemClickSupport.addTo(listChats).setOnItemClickListener { _: RecyclerView, position: Int, _: View? ->
            Log.d(TAG, "setOnItemClickListener  $position")
            if (clickedMessages.isNotEmpty() && !dontAllowClick) onItemClick(position)
        }
    }

    /**
     * Handle On item long click for the options having delete, forward and info. Add into the
     * selected list for the list showing and do the operations
     *
     * @param position Position of the clicked list item
     */
    private fun onItemLongClick(position: Int) {
        try {
            if (searchEnabled) handleSearchToolbar(false, true)
            if (position != -1) {
                val clickedMessage: ChatMessage = mainList[position]
                if (!clickedMessages.contains(clickedMessage.messageId)) onSelectItem(clickedMessage, position)
            }
        } catch (e: Exception) {
            LogMessage.e(Constants.TAG, e)
        }
    }

    /**
     * Handle On item click for the options having delete, forward and info. and remove that from
     * the selected list
     *
     * @param position Position of the item
     */
    private fun onItemClick(position: Int) {
        try {
            if (position != -1) {
                val clickedMessage: ChatMessage = mainList[position]
                if (clickedMessages.contains(clickedMessage.messageId)) {
                    clickedMessages.remove(clickedMessage.messageId)
                    chatAdapter.notifyItemChanged(position)
                    invalidateActionMode()
                } else { // Add the additional element to selected list.
                    onSelectItem(clickedMessage, position)
                }
            }
        } catch (e: java.lang.Exception) {
            LogMessage.e(e)
        }
    }

    /**
     * Call back for on item selection
     *
     * @param clickedMessage Selected message item
     */
    private fun onSelectItem(clickedMessage: ChatMessage, position: Int) {
        try {
            if (!clickedMessage.isNotificationMessage() && !dontAllowClick) {
                if (clickedMessages.isEmpty())
                    actionMode = toolbar.startActionMode(this)
                clickedMessages.add(clickedMessage.messageId)
                chatAdapter.notifyItemChanged(position)
                invalidateActionMode()
            }
        } catch (e: Exception) {
            LogMessage.e(Constants.TAG, e)
        }
    }

    override fun onDeleteGroup(groupJid: String) {
        super.onDeleteGroup(groupJid)
        if (chat.toUser.equals(groupJid)){
            setResult(Activity.RESULT_FIRST_USER)
            startDashboardActivity()
            finish()
        }
    }
    private fun startDashboardActivity() {
        val dashboardIntent = Intent(applicationContext, DashboardActivity::class.java)
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(dashboardIntent)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionMenu = menu
        if (searchEnabled) {
            menuInflater.inflate(R.menu.menu_search_toolbar, menu)
            actionPrev = menu.get(R.id.action_prev)
        } else {
            menuInflater.inflate(R.menu.menu_chat_items, menu)

            if (chat.isSingleChat()) {
                menu.get(R.id.action_block).isVisible = !profileDetails.isBlocked
                menu.get(R.id.action_unblock).isVisible = profileDetails.isBlocked
            }

            setCallButtonVisibility()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_chat -> {
                onClearConversationMenuClicked()
            }
            R.id.action_email -> {
                onEmailConversationMenuClicked()
            }
            R.id.action_audio_call -> {
                checkInternetAndExecute {
                    callMenuClicked(CallType.AUDIO_CALL)
                }
            }
            R.id.action_video_call -> {
                checkInternetAndExecute {
                    callMenuClicked(CallType.VIDEO_CALL)
                }
            }
            R.id.action_block -> {
                isBlockUnblockCalled = true
                showBlockUserDialog(true)
            }
            R.id.action_unblock -> {
                isBlockUnblockCalled = true
                showBlockUserDialog(false)
            }
            R.id.action_add_chat_shortcuts -> {
                createAppChatShortcutForChat()
            }

            R.id.action_chat_search -> {
                handleSearchToolbar(true, true)
            }

            R.id.action_prev -> if (searchedText.isNotEmpty()) handlePrevNextClicked(true)
            R.id.action_next -> if (searchedText.isNotEmpty()) handlePrevNextClicked(false)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun callMenuClicked(@CallType callType: String) {
        hideKeyboard()
        profileDetails.let {
            optionMenu?.let {
                if (!profileDetails.isBlocked && !isOnTelephonyCall(this) && !isOnAnyCall()) {
                    it.get(R.id.action_audio_call).isEnabled = false
                    it.get(R.id.action_video_call).isEnabled = false
                }
            }
            if (profileDetails.getChatType() == ChatType.TYPE_CHAT) {
                makeAudioVideoCalls(callType)
            } else if (profileDetails.getChatType() == ChatType.TYPE_GROUP_CHAT) {
                when {
                    isOnTelephonyCall(this) -> {
                        showAlertDialog(this, com.contus.flycommons.getString(R.string.msg_telephony_call_alert))
                    }
                    isOnAnyCall() -> {
                        showAlertDialog(this, com.contus.flycommons.getString(R.string.msg_ongoing_call_alert_for_group_call))
                    }
                    else -> {
                        hideKeyboard()
                        GroupCallUtils.setIsCallStarted(callType)
                        startActivityForResult(Intent(this, UsersSelectionActivity::class.java)
                                .putExtra(com.contus.flycommons.Constants.GROUP_ID, it.jid)
                                .putExtra(com.contus.flycommons.Constants.CHAT_TYPE, it.getChatType())
                                .putExtra(CallConstants.CALL_TYPE, callType), com.contus.flycommons.Constants.ACTIVITY_REQ_CODE)
                    }
                }
            }
        }
    }

    private fun makeAudioVideoCalls(callType: String) {
        GroupCallUtils.setIsCallStarted(callType)
        if (callType == CallType.AUDIO_CALL) {
            if (CallManager.isAudioCallPermissionsGranted()) {
                CallPermissionUtils(
                    this,
                    profileDetails.isBlocked,
                    arrayListOf(profileDetails.jid),
                    "",
                    false
                ).audioCall()
            } else {
                MediaPermissions.requestAudioCallPermissions(
                    (activity as Activity),
                    RequestCode.AUDIO_CALL_PERMISSION_CODE
                )
            }
        } else {
            if (CallManager.isVideoCallPermissionsGranted()) {
                CallPermissionUtils(
                    this,
                    profileDetails.isBlocked,
                    arrayListOf(profileDetails.jid),
                    "",
                    false
                ).videoCall()
            } else {
                MediaPermissions.requestVideoCallPermissions(
                    (activity as Activity),
                    RequestCode.VIDEO_CALL_PERMISSION_CODE
                )
            }
        }
    }

    private fun initialization() {
        FirebaseUtils.setAnalytics(FirebaseAnalytics.Event.VIEW_ITEM, "Chat View", "")
        startStickyService()
        initViews()
        setUpListeners()
        setRecyclerViewScrollListener()
    }

    private fun setUpListeners() {
        commonAlertDialog.setOnDialogCloseListener(this)
        commonAlertDialog.setOnTripleDialogCloseListener(this)
    }

    private fun handleMainIntent() {
        val mainIntent = intent
        toUser = mainIntent.getStringExtra(LibConstants.JID) ?: Constants.EMPTY_STRING
        messageId = mainIntent.getStringExtra(LibConstants.MESSAGE_ID) ?: Constants.EMPTY_STRING
        chatType = mainIntent.getStringExtra(Constants.CHAT_TYPE) ?: Constants.EMPTY_STRING
        isFromStarredMessages = mainIntent.getBooleanExtra(Constants.IS_STARRED_MESSAGE, false)
        isFromQuickShare = mainIntent.getBooleanExtra(Constants.FROM_QUICK_SHARE, false)
        chat = Chat(chatType, toUser)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent?): Boolean {
        if (v.id == R.id.edit_chat_msg && MotionEvent.ACTION_UP == event!!.action && emojiHandler.isEmojiShowing)
            emojiHandler.hideEmoji()
        return false
    }

    override fun onEmojiconBackspaceClicked(v: View?) {
        EmojiconsFragment.backspace(chatMessageEditText)
    }

    override fun onEmojiconClicked(emojicon: Emojicon?) {
        EmojiconsFragment.input(chatMessageEditText, emojicon)
    }

    /**
     * Update reply message id and user id
     */
    private fun updateReplyMessageAction() {
        if (SharedPreferenceManager.getString(Constants.REPLY_MESSAGE_USER) == chat.toUser) {
            selectedMessageIdForReply = SharedPreferenceManager.getString(Constants.REPLY_MESSAGE_ID)
            if (selectedMessageIdForReply.isNotEmpty()) {
                clickedMessages.clear()
                replyMessageActionMenuClicked()
            } else {
                replyMessageSentView.gone()
                closeReplyMessage.gone()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.image_chat_send -> {
                sendChatMessage()
            }
            R.id.action_attachment -> {
                onAttachMenuClick()
                suggestionLayout.gone()
            }
            R.id.view_back, R.id.back -> {
                isBackPressed = true
                onBackPressed()
            }
            R.id.image_chat_smiley -> {
                if (attachmentDialog.isShowing)
                    closeControls()
                hideKeyboard()
                emojiHandler.setKeypad(chatMessageEditText)
            }
            R.id.close_reply -> if(mainList.last().isMessageSentByMe) resetReplyMessageView(false) else resetReplyMessageView()
        }
    }

    private fun sendChatMessage() {
        if (audioRecordView.isLocked) {
            audioRecordView.sendRecording()
        } else if (chatMessageEditText.text.toString().trim().isNotEmpty()) {
            if (attachmentDialog.isShowing)
                closeControls()
            else
                sendTextMessage()
        }
    }

    private fun validateAndSendMessage(messageObject: MessageObject) {
        if (isBlocked) {
            showBlockedAlert()
            messagesQueue.add(messageObject)
        } else if (FlyCore.isBusyStatusEnabled() && chat.isSingleChat()) {
            showBusyAlert()
            messagesQueue.add(messageObject)
        } else if (isUserCanSendMessage())
            sendMessage(messageObject)
    }

    private fun sendTextMessage() {
        isReplyTagged = false
        val textMessage = messagingClient.composeTextMessage(chat.toUser, chatMessageEditText.text.toString().trim(), selectedMessageIdForReply)
        validateAndSendMessage(textMessage)
        if(!FlyCore.isBusyStatusEnabled())
            chatMessageEditText.setText(Constants.EMPTY_STRING)
    }

    private fun sendImageMessage(imagePath: String) {
        val imageMessage = messagingClient.composeImageMessage(chat.toUser, imagePath, Constants.EMPTY_STRING, selectedMessageIdForReply)
        validateAndSendMessage(imageMessage)
    }

    private fun sendAudioMessage(audioPath: String) {
        val audioMessage = messagingClient.composeAudioMessage(chat.toUser,false, audioPath, selectedMessageIdForReply)
        if (!audioMessage.first)
            showAudioFileValidation()
        else if (!audioMessage.second)
            showUploadAlert(Constants.MAX_AUDIO_SIZE_LIMIT)
        else
            validateAndSendMessage(audioMessage.third!!)
    }

    private fun sendDocumentsMessage(filePathUri: Uri?) {
        filePathUri.let {
            val fileRealPath = RealPathUtil.getRealPath(this, filePathUri)
            if (fileRealPath == null)
                showDriveFileValidation()
            else if (fileRealPath.isNotEmpty()) {
                val fileMessage = messagingClient.composeDocumentsMessage(chat.toUser, fileRealPath, selectedMessageIdForReply)
                if (!fileMessage.first)
                    showFileValidation()
                else if (!fileMessage.second)
                    showUploadAlert(Constants.MAX_DOCUMENT_UPLOAD_SIZE)
                else
                    validateAndSendMessage(fileMessage.third!!)
            } else
                showFileValidation()
        }
    }

    private fun sendContactMessage(data: Intent) {
        val contactMessage = messagingClient.composeContactMessage(chat.toUser, data, selectedMessageIdForReply)
        validateAndSendMessage(contactMessage)
    }

    private fun sendLocationMessage(data: Intent) {
        val locationMessage = messagingClient.composeLocationMessage(chat.toUser, data, selectedMessageIdForReply)
        if (locationMessage.first)
            validateAndSendMessage(locationMessage.second!!)
        else
            commonAlertDialog.showAlertDialog(getString(R.string.error_location_selection), Constants.EMPTY_STRING, getString(R.string.action_Ok),
                    CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false)
    }

    private fun sendVoiceMessage() {
        handleVoiceRecording { mediaPath ->
            val audioMessage = messagingClient.composeAudioMessage(chat.toUser, true,mediaPath, selectedMessageIdForReply)
            if (!audioMessage.first)
                showAudioFileValidation()
            else if (!audioMessage.second) {
                showUploadAlert(Constants.MAX_AUDIO_SIZE_LIMIT)
            } else
                validateAndSendMessage(audioMessage.third!!)
        }
    }

    private fun sendMessage(messageObject: MessageObject) {
        sendTypingGone()
        messagingClient.sendMessage(messageObject, this@ChatActivity)
        chatMessageEditText.setText(Constants.EMPTY_STRING)
    }


    override fun onDestroy() {
        super.onDestroy()
        clear()
        contentResolver.unregisterContentObserver(mObserver)
        unRegisterTelephonyCallListener()
    }

    fun clear() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(chatMessageEditText.windowToken, 0)
        showChatKeyboard = false
        compositeDisposable.clear()
        coroutineContext.cancel(CancellationException("$TAG Destroyed"))
        dismissProgress()
        stopService(stickyService)
    }

    /**
     * Called to report a user click on an action button.
     *
     * @param mode The current ActionMode
     * @param menuItem   The item that was clicked
     * @return true if this callback handled the event, false if the standard MenuItem invocation
     * should continue.
     */
    override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
        menuItem.isEnabled = true
        return onActionMenuClick(menuItem.itemId)
    }

    override fun onMessageStatusUpdated(messageId: String) {
        val mid = messageId
        getMessageAndPosition(mid).let { messageAndPosition ->
            if (messageAndPosition.first != -1) {
                refreshMessageAndUpdateAdapter(mid, Constants.NOTIFY_MESSAGE_STATUS_CHANGED)
                invalidateActionMode()
                if (messageAndPosition.second != null && messageAndPosition.second!!.isMessageRecalled()) {
                    updateRecallMessageReplyView(messageAndPosition.second!!.messageId)
                    handleUnreadMessageSeparator(true)
                    addMessagesforSmartReply()
                }
            }
        }
    }

    override fun onUpdateUnStarAllMessages() {
        super.onUpdateUnStarAllMessages()
        updateAdapterUnStarAllMessages()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        actionModeMenu = configureMenuActionMode(mode, menu)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    private fun onActionMenuClick(itemId: Int): Boolean {
        val clickDone: Boolean
        when (itemId) {
            R.id.action_delete -> {
                deleteMessageActionMenuClicked()
                clickDone = true
            }
            R.id.action_info -> {
                messageInfoActionMenuClicked()
                actionMode?.finish()
                clickDone = true
            }
            R.id.action_copy -> {
                copyMessagesActionMenuClicked()
                actionMode?.finish()
                clickDone = true
            }
            R.id.action_forward -> {
                clickDone = forwardMessageActionMenuClicked(clickedMessages)
            }
            R.id.action_share -> {
                MediaShareUtils().shareMediaExternal(clickedMessages, this)
                actionMode?.finish()
                clickDone = true
            }
            R.id.action_favourite -> {
                starOrUnStarMessageActionMenuClicked(true)
                updateAdapter()
                actionMode?.finish()
                clickDone = true
            }
            R.id.action_unfavourite -> {
                starOrUnStarMessageActionMenuClicked(false)
                updateAdapter()
                actionMode?.finish()
                clickDone = true
            }
            R.id.action_reply -> {
                isReplyTagged = true
                replyMessageActionMenuClicked()
                chatMessageEditText.showSoftKeyboard()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                actionMode?.finish()
                clickDone = true
            }
            else -> clickDone = false
        }
        return clickDone
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        clearAndUpdateSelectedMessages(false)
        invalidateActionMode()
    }

    override fun onResume() {
        super.onResume()
        keyboardHeightProvider?.onResume()
        try {
            registerReceiver(dateChangedBroadcastReceiver, IntentFilter(Intent.ACTION_TIME_CHANGED))
        } catch (e: Exception) {
            LogMessage.e(e)
        }
        viewModel.getProfileDetails()
        addMessagesforSmartReply()
        handleOnResume()
        updateChatAdapterWithLatestData()
        if (SharedPreferenceManager.getBoolean(Constants.SHOW_LABEL))
            netConditionalCall(
                { chatXmppConnectionStatusLayout.gone() },
                { chatXmppConnectionStatusLayout.show() })
        getRecalledMessagesForThisUser()
        handleCursorAndKeyboardVisibility()
        clearNotification()
    }

    @SuppressLint("CheckResult")
    protected fun updateChatAdapterWithLatestData(fromForward: Boolean = false) {
        if (isFromStarredMessages) {
            unreadMessageCount = 0
            return
        }

        val lastMessage = mainList.lastOrNull()
        val newMessages = ArrayList<ChatMessage>()
        if (lastMessage != null) {
            val messages = parentViewModel.getMessagesAfterThisMessage(
                lastMessage.chatUserJid,
                lastMessage.messageSentTime,
                mainList
            )
            newMessages.addAll(messages)
        } else
            newMessages.addAll(viewModel.getAllMessages(chat.toUser))

        if (newMessages.isNotEmpty()) {
            mainList.addAll(newMessages)
            initSuggestion(mainList)
            removeUnReadMsgSeparator()
            chatAdapter.notifyItemRangeInserted(mainList.size, newMessages.size)
            if (!isFromForward)
                isFromForward = fromForward
            if (isFromForward)
                listChats.scrollToPosition(mainList.size - 1)
            else
                showHideRedirectToLatest.onNext(true)
        }
        isFromForward = false
        val (isUnreadSeparatorIsAvailable, separatorPosition) = findIndexOfUnreadMessageType()
        unreadMessageCount += if (isUnreadSeparatorIsAvailable) mainList.size - separatorPosition - 1 else newMessages.size
    }

    private fun removeUnReadMsgSeparator() {
        try {
            if (mainList.count { it.messageId == unreadMessageTypeMessageId } > 1) {
                val position = mainList.size - mainList.reversed()
                    .indexOfLast { it.messageId == unreadMessageTypeMessageId } - 1
                if (position != -1 && position < mainList.size) {
                    mainList.removeAt(position)
                    chatAdapter.notifyItemRemoved(position)
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            e.printStackTrace()
        }
    }

    private fun clearNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val barNotifications: Array<StatusBarNotification> =
                notificationManager.activeNotifications
            for (notification in barNotifications) {
                NotificationManagerCompat.from(applicationContext).cancel(notification.id)
            }
        } else
            NotificationManagerCompat.from(applicationContext).cancel(Constants.NOTIFICATION_ID)
    }

    private fun handleCursorAndKeyboardVisibility() {
        if (showChatKeyboard) {
            chatMessageEditText.requestFocus()
            chatMessageEditText.showSoftKeyboard()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            showChatKeyboard = false
        } else {
            chatMessageEditText.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }
    }


    override fun listOptionSelected(position: Int) {
        /**
         * Handle the Gallery or Camera alert list.
         */
        if (commonAlertDialog.dialogAction == CommonAlertDialog.DialogAction.GALLERY) {
            when (position) {
                0 -> selectImagesFromGallery()
                1 -> PickFileUtils.chooseVideoFromGallery(this)
            }
        }
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        val action: CommonAlertDialog.DialogAction? = commonAlertDialog.dialogAction
        action?.let {
            if (isSuccess) {
                handleDialogResp(it, CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, 0)
                if (action == CommonAlertDialog.DialogAction.SMART_REPLY_UNBLOCK) {
                    messagesQueue.add(messagingClient.composeTextMessage(chat.toUser,
                            chatMessageEditText.text.toString().trim(), selectedMessageIdForReply))
                    chatMessageEditText.setText(Constants.EMPTY_STRING)
                } else if(action == CommonAlertDialog.DialogAction.SMART_REPLY_BUSY) {
                    smartReply.let {
                        chatMessageEditText.setText(it)
                    }
                    sendSmartReply()
                } else if (action == CommonAlertDialog.DialogAction.STATUS_BUSY && isAttachMenuClick) {
                    onAttachMenuClick()
                    isAttachMenuClick = false
                }
            } else if (action == CommonAlertDialog.DialogAction.UNBLOCK) {
                messagesQueue.clear()
            } else if ((action == CommonAlertDialog.DialogAction.SMART_REPLY_UNBLOCK || action == CommonAlertDialog.DialogAction.SMART_REPLY_BUSY)) {
                chatMessageEditText.setText(Constants.EMPTY_STRING)
            }
        }
    }

    override fun onTripleOptionDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, position: Int) {
        handleDialogResp(commonAlertDialog.dialogAction, CommonAlertDialog.DIALOGTYPE.DIALOG_TRIPLE, position)
    }

    /**
     * here handling the dialog
     *
     * @param action Common Alert Action
     * @param dialogType Dialog type single or dual
     * @param position clicked position
     */
    private fun handleDialogResp(action: CommonAlertDialog.DialogAction?, dialogType: CommonAlertDialog.DIALOGTYPE, position: Int) {
        chatAdapter.pauseMediaPlayer()
        when (action) {
            CommonAlertDialog.DialogAction.CLEAR_CONVERSATION -> {
                when {
                    dialogType == CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL -> handleClearConversation(clearChatExceptStarredMessages = false)
                    position == 0 -> {
                        if (isReplyTagged)
                            closeReplyMessage.performClick()
                        handleClearConversation(clearChatExceptStarredMessages = false)
                    }
                    position == 2 -> {
                        handleClearConversation(clearChatExceptStarredMessages = true)
                        showSmartReplyAfterClearExceptStarred()
                    }
                }
            }
            CommonAlertDialog.DialogAction.DELETE_CHAT -> {
                var isRecalled = false
                if (dialogType == CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL || position == 1) {
                    sendDeleteMessageIQRequest()
                    handleCommonDeleteOperation(isRecalled)
                    smartReplyForPreviousMessage()
                } else if (position == 2) {
                    isRecalled = true
                    recallSelectedMessages()
                    handleCommonDeleteOperation(isRecalled)
                }
            }
            CommonAlertDialog.DialogAction.SMART_REPLY_BUSY -> {
                FlyCore.enableDisableBusyStatus(false)
            }
            CommonAlertDialog.DialogAction.FORWARD_STATUS_BUSY -> {
                FlyCore.enableDisableBusyStatus(false)
                if(clickedMessages.isNotEmpty())
                    forwardMessageActionMenuClicked(clickedMessages)
                else
                    senderMediaForward(forwardClickedPostition)
            }
            CommonAlertDialog.DialogAction.STATUS_BUSY -> {
                FlyCore.enableDisableBusyStatus(false)
                sendQueuedUpMessages()
            }
            CommonAlertDialog.DialogAction.STATUS_BUSY_EMOJI -> {
                if (attachmentDialog.isShowing)
                    closeControls()
                hideKeyboard()
                //   emojiHandler.setKeypad(chatMessageEditText)
                FlyCore.enableDisableBusyStatus(false)
            }

            CommonAlertDialog.DialogAction.STATUS_BUSY_KEYBOARD -> {
                chatMessageEditText.showSoftKeyboard()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                FlyCore.enableDisableBusyStatus(false)
            }

            CommonAlertDialog.DialogAction.BLOCK -> blockContact()

            CommonAlertDialog.DialogAction.UNBLOCK -> {
                isBlockUnblockCalled = true
                unblockContact()
            }
            else -> {
                /*No Implementation needed*/
            }
        }
    }

    /**
     * Called when the connection has been failed from the server
     *
     * @param message Connection failed message
     */
    override fun connectionFailed(message: String) {
        if (SharedPreferenceManager.getBoolean(Constants.SHOW_LABEL))
            chatXmppConnectionStatusLayout.show()
        else
            chatXmppConnectionStatusLayout.gone()
        netConditionalCall({ chatXmppConnectionText.text = resources.getString(R.string.connection_lost) }, {
            chatXmppConnectionText.text = resources.getString(R.string.msg_no_internet)
        })
    }


    /**
     * Block the Current user
     */
    private fun blockContact() {
        checkInternetAndExecute {
            doProgressDialog = DoProgressDialog(this)
            doProgressDialog!!.showProgress()
            hideKeyboard()
            if (selectedMessageIdForReply != Constants.EMPTY_STRING) makeViewsGone(replyMessageSentView, closeReplyMessage)
            FlyCore.blockUser(chat.toUser) { isSuccess, _, _ ->
                runOnUiThread {
                    onBlockUserResponse(!isSuccess, true)
                }
            }
            dialog.onNext(ConstantActions.BLOCK_USER)
        }
    }

    /**
     * Block the Current user
     */
    private fun unblockContact() {
        checkInternetAndExecute {
            doProgressDialog = DoProgressDialog(this)
            doProgressDialog!!.showProgress()
            if (selectedMessageIdForReply != Constants.EMPTY_STRING) {
                showViews(replyMessageSentView, closeReplyMessage)
                replyViewHandler.showReplyMessageToSend(selectedMessageIdForReply, parentViewModel, suggestionLayout, chat.toUser)
            }
            FlyCore.unblockUser(chat.toUser) { isSuccess, _, _ ->
                runOnUiThread {
                    onBlockUserResponse(!isSuccess, false)
                }
            }
            dialog.onNext(ConstantActions.UNBLOCK_USER)
        }
    }

    override fun usersIBlockedListFetched(jidList: List<String>) {
        super.usersIBlockedListFetched(jidList)
        onBlockUserResponse(isError = false, isBlockedStatus = false)
    }

    private fun onBlockUserResponse(isError: Boolean, isBlockedStatus: Boolean) {
        dismissProgress()
        if (isError) {
            showToast(Constants.ERROR_SERVER)
            return
        }
        if (isBlockUnblockCalled) {
            if (isBlockedStatus) {
                showToast(String.format(getString(R.string.msg_user_blocked), getUserNickname()))
                isBlocked = true
            } else {
                showToast(String.format(getString(R.string.msg_user_unblocked), getUserNickname()))
                isBlocked = false
                sendQueuedUpMessages()
            }
        }
        isBlockUnblockCalled = false
        viewModel.getProfileDetails()
        setUserProfileImage()
    }

    private fun handleCommonDeleteOperation(isRecalled: Boolean) {
        refreshDeleteOrRecalledMessagesInAdapter(isRecalled)
    }

    override fun onReplyMessageClick(messageId: String) {
        if (clickedMessages.isNotEmpty()) {
            val position = getMessagePosition(messageId)
            onItemClick(position)
        } else {
            val mainMessage = getMessagebyID(messageId)
            mainMessage?.replyParentChatMessage?.messageId?.let {
                val replyMessage = parentViewModel.getMessageForReply(mainMessage.replyParentChatMessage.messageId)
                replyMessage?.let {
                    highlightGivenMessageId(mainMessage.replyParentChatMessage.messageId)
                }
            }
        }
    }

    override fun onSenderMediaForward(item: ChatMessage, position: Int) {
        //if already a msg is selected then forward icon click must be handled like selection
        if (clickedMessages.isNotEmpty() && position >= 0) {
            if (!clickedMessages.contains(mainList[position].messageId))
                onItemLongClick(position)
            else
                onItemClick(position)
            return
        }else{
            forwardClickedPostition = position
        }

        if (SystemClock.elapsedRealtime() - lastClickTime > 1000) {
            try {
                hideKeyboard()
                senderMediaForward(position)
            } catch (e: Exception) {
                LogMessage.e(Constants.TAG, e)
            }
        }
        lastClickTime = SystemClock.elapsedRealtime()
    }

    private fun senderMediaForward(position: Int) {
        val forwardMediaMessageSelected: ChatMessage
        if (position.isValidIndex()) {
            forwardMediaMessageSelected = mainList[position]
            if (clickedMessages.contains(forwardMediaMessageSelected.messageId))
                return
            if (!forwardMediaMessageSelected.isNotificationMessage() && (!FlyCore.isBusyStatusEnabled() || chat.getChatType().name == ChatType.TYPE_GROUP_CHAT)) {
                hideKeyboard()
                launchActivity<ForwardMessageActivity> {
                    putExtra(Constants.CHAT_MESSAGE, arrayListOf(forwardMediaMessageSelected.messageId))
                }
            } else if (!forwardMediaMessageSelected.isNotificationMessage() && FlyCore.isBusyStatusEnabled()) {
                showForwardBusyAlert()
            }
        }
    }

    override fun onContactClick(item: ChatMessage, position: Int, registeredJid: String?) {

    }

    override fun onCancelDownloadClicked(messageItem: ChatMessage) {
        if (!messageItem.isMediaDownloaded())
            handleCancelClickedOnMediaMessage(messageItem)
        else {
            LogMessage.d(TAG, getString(R.string.media_downloaded))
            showToast(getString(R.string.media_downloaded))
        }
    }

    override fun onReceiverItemClicked(item: ChatMessage?, position: Int) {
        handleMediaMessageClick(position, item!!.messageId ?: "")
    }

    override fun clearActionMenu() {
        actionMode?.finish()
    }

    /**
     * Handle media message item click and long press
     *
     * @param position Position of the item in view
     */
    private fun handleMediaMessageClick(position: Int, messageId: String) {
        if (clickedMessages.isNotEmpty() && !dontAllowClick)
            onItemClick(position)
        else if (position != -1) {
            // Compare elapsed time and clicked time to avoid double click action
            if (SystemClock.elapsedRealtime() - lastClickTime > 1000) {
                val clickedPos = chatAdapter.validateAndRefreshMessagePosition(position, messageId, true)
                if (clickedPos != -1) {
                    val clickedMessage = mainList[clickedPos]
                    chatClickUtils.handleOnListClick(clickedMessage, activity)
                }
            }
            lastClickTime = SystemClock.elapsedRealtime()
        }
    }

    override fun onDownloadClicked(messageItem: ChatMessage) {
        msg = messageItem
        if (ChatUtils.checkWritePermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            netConditionalCall({
                val position = getMessagePosition(messageItem.messageId)
                FlyMessenger.downloadMedia(messageItem.messageId)
                val message = FlyMessenger.getMessageOfId(messageItem.messageId)
                message?.let { mainList[position] = it }
                chatAdapter.notifyItemChanged(position, messageItem)
            }, { showToast(getString(R.string.msg_no_internet)) })
        } else
            MediaPermissions.requestStorageAccess(this, downloadPermissionLauncher)
    }

    override fun onCancelUploadClicked(messageItem: ChatMessage) {
        if (!messageItem.isMediaUploaded())
            handleCancelClickedOnMediaMessage(messageItem)
        else {
            LogMessage.d(TAG, getString(R.string.media_downloaded))
            showToast(getString(R.string.media_downloaded))
        }
    }

    override fun onReceiverItemLongClick(item: ChatMessage?, position: Int) {
        onItemLongClick(position)
    }

    override fun onAudioPlayed() {
        pauseAudioRecording()
    }

    override fun onSenderItemClicked(item: ChatMessage?, position: Int) {
        handleMediaMessageClick(position, item!!.messageId ?: "")
    }

    override fun onRetryClicked(item: ChatMessage?) {
        netConditionalCall({
            item?.let {
                FlyMessenger.uploadMedia(it.getMessageId())
            }
        }, {
            CustomToast.show(this, getString(R.string.msg_no_internet))
        })
    }

    private fun handleCancelClickedOnMediaMessage(messageItem: ChatMessage) {
        checkInternetAndExecute {
            FlyMessenger.cancelMediaUploadOrDownload(messageItem.messageId)
            refreshMessageAndUpdateAdapter(messageItem.messageId, Constants.EMPTY_STRING)
        }
    }

    override fun onSenderItemLongClick(item: ChatMessage?, position: Int) {
        onItemLongClick(position)
    }

    /**
     * This is handle the clear the chat
     */
    private fun handleClearConversation(clearChatExceptStarredMessages: Boolean) {
        try {
            ChatManager.clearChat(chat.toUser, chat.getChatType(), clearChatExceptStarredMessages, object : ChatActionListener {
                override fun onResponse(isSuccess: Boolean, message: String) {
                    /*No Implementation needed*/
                }
            })
            unreadMessageCountView.gone()
            if (clearChatExceptStarredMessages) {
                val starredMessages = getStarredMessages()
                mainList.clear()
                mainList.addAll(starredMessages)
            } else {
                parentViewModel.removeMessages()
                mainList.clear()
            }
            chatAdapter.notifyDataSetChanged()
            if (mainList.isNotEmpty())
                listChats.scrollToPosition(mainList.size - 1)
        } catch (e: java.lang.Exception) {
            LogMessage.e(e)
        }
    }

    /**
     * Select Message Status
     *
     * @param isRecalled message is Recalled or Not.
     */
    private fun refreshDeleteOrRecalledMessagesInAdapter(isRecalled: Boolean) {
        if (isRecalled) {
            clearAndUpdateSelectedMessages(true)
        } else {
            addDateMessagesIfNeedToRemove()
        }
    }

    private fun sendDeleteMessageIQRequest() {
        val isMediaDelete = SharedPreferenceManager.getBoolean(Constants.DELETE_MEDIA_FROM_PHONE)
        ChatManager.deleteMessagesForMe(chat.toUser, clickedMessages, chat.getChatDeleteType(), isMediaDelete,
            object : ChatActionListener {
                override fun onResponse(isSuccess: Boolean, message: String) {
                    /*No Implementation Needed*/
                }
            })
    }

    private fun recallSelectedMessages() {
        val isMediaDelete = SharedPreferenceManager.getBoolean(Constants.DELETE_MEDIA_FROM_PHONE)
        ChatManager.deleteMessagesForEveryone(chat.toUser, clickedMessages, chat.getChatDeleteType(), isMediaDelete,
            object : ChatActionListener {
            override fun onResponse(isSuccess: Boolean, message: String) {
                /*No Implementation Needed*/
            }
        })
        updateRecalledMessage(clickedMessages)
    }

    private fun handleDownloadProcess() {
        if (msg != null)
            onDownloadClicked(msg!!)
        else {
            if (isEmailChatClicked) {
                isEmailChatClicked = false
                FlyCore.exportChatConversationToEmail(chat.toUser, emptyList())
            }
            LogMessage.d(TAG, "Storage Permission Code")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                RequestCode.TAKE_PHOTO -> {
                    handleTakePhotoFromCameraIntentResult()
                }
                RequestCode.SELECT_IMAGES -> {
                    data?.let { chatViewUtils.showSelectedImages(chat.toUser, this, data) }
                }
                RequestCode.FROM_GALLERY -> {
                    data?.let { handleAudioVideoIntentFromGalleryMenu(data) }
                }
                RequestCode.PICK_FILE -> {
                    data?.let { sendDocumentsMessage(data.data) }
                }
                RequestCode.SELECT_IMAGE_REQ_CODE -> {
                    data?.let {
                        val imagePath = RealPathUtil.getRealPath(this, it.data)
                        imagePath?.let {
                            sendImageMessage(imagePath)
                        }
                    }
                }
                RequestCode.TAKE_VIDEO -> {
                    validateAndPreviewVideo(videoFile!!.absolutePath.returnEmptyIfNull())
                }
                RequestCode.PICK_CONTACT_REQ_CODE -> {
                    data?.let { ContactUtils.handleContactSelection(this, data) }
                }
                RequestCode.SELECT_CONTACT_REQ_CODE -> {
                    data?.let { sendContactMessage(data) }
                }
                RequestCode.SELECT_MAP_REQ_CODE -> {
                    data?.let { sendLocationMessage(data) }
                }
                100 -> {
                    returnValue = data!!.getStringArrayListExtra(Constants.IMAGE_RESULTS)!!
                    mediaPath = returnValue[0]
                    val path = mediaPath
                    if (path?.contains(".mp4", true)!!) validateAndPreviewVideo(mediaPath)
                    else handleTakePhotoFromCameraIntentResult()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        toUser = intent.getStringExtra(LibConstants.JID) ?: Constants.EMPTY_STRING
        chatType = intent.getStringExtra(Constants.CHAT_TYPE) ?: Constants.EMPTY_STRING

        messageId = intent.getStringExtra(LibConstants.MESSAGE_ID) ?: Constants.EMPTY_STRING

        chat = Chat(chatType, toUser)
        viewModel.setUserJid(chat.toUser)

        val externalCall = intent.getBooleanExtra("externalCall", false)

        if (externalCall) {
            clickedMessages.clear()
            reloadUserChat()
        }

        updateReplyMessageAction()
        invalidateOptionsMenu()

        if (messageId.isNotEmpty()) {
            val messagePosition = getMessagePosition(messageId)
            if (messagePosition != -1)
                listChats.scrollToPosition(messagePosition)
        } else
            handleOnNewIntent(intent)
    }

    private fun reloadUserChat() {
        initChatAdapter()
        startObservingViewModel()
        mainList.clear()
        viewModel.clearChat()
        setUserJid()
        viewModel.loadInitialData()
        viewModel.deleteUnreadMessageSeparator(chat.toUser)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            when (requestCode) {
                RequestCode.LOCATION_PERMISSION_CODE -> {
                    attachLocation()
                }
                RequestCode.READ_CONTACTS_PERMISSION_CODE -> {
                    attachContact()
                }
                RequestCode.CAMERA_PERMISSION_CODE, RequestCode.ALL_PERMISSIONS_CODE, RequestCode.AUDIO_SELECTION_PERMISSIONS_CODE -> {
                    mediaClickEvent(selectedOptionName!!, requestCode)
                }
                RequestCode.STORAGE_PERMISSION_CODE -> {
                    handleDownloadProcess()
                }
                RequestCode.GALLERY_PERMISSION_CODE -> {
                    selectImagesFromGallery()
                }
                RequestCode.AUDIO_CALL_PERMISSION_CODE -> {
                    CallPermissionUtils(
                        this,
                        profileDetails.isBlocked,
                        arrayListOf(profileDetails.jid),
                        "",
                        false
                    ).audioCall()
                }
                RequestCode.VIDEO_CALL_PERMISSION_CODE -> {
                    CallPermissionUtils(
                        this,
                        profileDetails.isBlocked,
                        arrayListOf(profileDetails.jid),
                        "",
                        false
                    ).videoCall()
                }
                2000 -> {
//                    launch(exceptionHandler) {
//                        chatViewUtils.refreshContacts(this@ChatActivity, viewModel)
//                    }
                }
            }
        }
    }

    private fun handleOnNewIntent(intent: Intent) {
        when {
            intent.hasExtra(Constants.SELECTED_IMAGES) -> sendImageFromGallery(intent)
            intent.hasExtra(Constants.CHAT_MESSAGE) -> {
                suggestionLayout.gone()
                actionMode?.finish()
                reloadUserChat()
                isFromForward = true
                handleForwardMessages(intent)
            }
            intent.hasExtra(Constants.SELECTED_VIDEO) -> sendVideoFromGallery(intent.getStringExtra(Constants.SELECTED_VIDEO)!!,
                    intent.getStringExtra(Constants.SELECTED_VIDEO_CAPTION)!!)
        }
    }

    private fun sendVideoFromGallery(videoFilePath: String, videoCaption: String) {

        val videoDuration = Constants.VIDEO_DURATION_LIMIT//sharedPreferenceManager.getString(Constants.VIDEO_LIMIT).toLong()

        val videoMessage = messagingClient.composeVideoMessage(chat.toUser, videoFilePath, videoCaption, selectedMessageIdForReply)

        if (!videoMessage.first)
            showAudioVideoDurationValidationDialog(videoDuration.toString(), Constants.MSG_TYPE_VIDEO)
        else
            validateAndSendMessage(videoMessage.second!!)
    }

    private fun sendImageFromGallery(intent: Intent) {
        val selectedImages: List<MediaPreviewModel> = intent.getParcelableArrayListExtra(Constants.SELECTED_IMAGES)!!
        val replyMessageId = selectedMessageIdForReply
        for (item in selectedImages) {
            if (item.isImage) {
                val extension = item.path.substringAfterLast(".")
                if (supportedFormats.contains(extension)) {
                    val imageMessage = messagingClient.composeImageMessage(chat.toUser, item.path, item.caption, replyMessageId)
                    validateAndSendMessage(imageMessage)
                } else {
                    CustomToast.show(this, getString(R.string.file_not_supported))
                }
            } else sendVideoFromGallery(item.path, item.caption)
        }
    }

    private fun handleAudioVideoIntentFromGalleryMenu(intent: Intent) {
        val uri = intent.data
        if (uri != null) {
            val uriOfSelectedFile = intent.data!!
            val mimeType = contentResolver.getType(uriOfSelectedFile)
            val pathOfSelectedFile = RealPathUtil.getRealPath(this, uriOfSelectedFile) ?: return
            if (mimeType== null || mimeType.startsWith(Constants.MSG_TYPE_AUDIO))
                sendAudioMessage(pathOfSelectedFile)
            else if (mimeType.startsWith(Constants.MSG_TYPE_VIDEO)) {
                hideKeyboard()
                launchActivity<MediaPreviewActivity> {
                    putExtra(Constants.FILE_PATH, pathOfSelectedFile)
                    putExtra(Constants.USER_JID, toUser)
                    putExtra(Constants.IS_IMAGE, false)
                }
            }
        }
    }


    private fun handleForwardMessages(intent: Intent) {
        val messageIds: List<String> = intent.getStringArrayListExtra(Constants.CHAT_MESSAGE)!!
        ChatManager.forwardMessages(messageIds, chat.toUser, chat.getChatType(), object : ChatActionListener {
            override fun onResponse(isSuccess: Boolean, message: String) {
                if (isSuccess) {
                    updateChatAdapterWithLatestData(true)
                }
            }

        })
        chatAdapter.notifyDataSetChanged()
    }

    /**
     * adding the message
     *
     * @param tempMessage ChatMessage item
     */
    private fun addMessageAndScroll(tempMessage: ChatMessage) {
        if(tempMessage.isNotificationMessage() && tempMessage.messageTextContent.contains(resources.getString(R.string.msg_you_changed_icon)))
            return
        mainList.add(tempMessage)
        chatAdapter.notifyItemInserted(mainList.size - 1)
        if (!isViewingRecentMessage) {
            if (!tempMessage.isMessageSentByMe())
                unreadMessageCount++
            showHideRedirectToLatest.onNext(true)
        } else
            listChats.scrollToPosition(mainList.size - 1)
        handleUnreadMessageSeparator(false)
    }

    override fun setTypingStatus(singleOrGroupJid: String, userId: String, composing: String) {
        Log.d(TAG, "setTypingStatus $singleOrGroupJid  $userId $composing")
        if (chat.toUser == singleOrGroupJid) {
            if(!typingList.contains(userId))
                typingList.add(userId)
            if (composing.equals(Constants.COMPOSING, true)) {
                if (chat.isGroupChat()) {
                    val userName = getChatTypingUserName(Chat(toUser = typingList.get(typingList.size -1 )).getUsername())
                    chatViewUtils.setUserPresenceStatus(this,
                        "${userName} ${resources.getString(R.string.msg_typing)}"
                    )
                } else if (chat.isSingleChat())
                    chatViewUtils.setUserPresenceStatus(this, resources.getString(R.string.msg_typing))
            } else {
                if(typingList.isNotEmpty() && typingList.contains(userId))
                    typingList.remove(userId)
                setChatStatus()
            }
        }
    }


    private fun getChatTypingUserName(userName: String): String {
        val userNameArray = userName.split(" ")
        if (userNameArray.size == 1) {
            if (userNameArray[0].length > 10)
                return userNameArray[0].substring(0, 12) + "..."
            return userNameArray[0]
        } else if (userNameArray.size == 2) {
            if (userNameArray[0].length > 10)
                return userNameArray[0].substring(0, 12) + "..."
            else if (userNameArray[1].length > 10)
                return userNameArray[0]
            else
                return userName
        } else {
            if (userNameArray[0].length > 10)
                return userNameArray[0].substring(0, 12) + "..."
            else if (userNameArray[1].length > 10)
                return userNameArray[0]
            else if (userNameArray[2].length > 7)
                return userNameArray[0] + " " + userNameArray[1]
            else {
                if (userName.length > 20)
                    return userName.substring(0, 17) + "..."
                else
                    return userName
            }
        }
    }

    private fun mediaClickEvent(selectedOptionName: String, requestCode: Int) {
        when (selectedOptionName) {
            Constants.ATTACHMENT_CAMERA -> attachCamera()
            Constants.ATTACHMENT_DOCUMENT -> attachDocument()
            Constants.ATTACHMENT_GALLERY -> attachGallery()
            Constants.ATTACHMENT_AUDIO -> if (requestCode == RequestCode.AUDIO_SELECTION_PERMISSIONS_CODE) audioFileSelection()
        }
    }

    /**
     * handling the voice recorder
     */
    private fun handleVoiceRecording(sendVoiceMessage: (mediaPath: String) -> Unit) {
        /**
         * Check the media state to show record option in an audio recording
         */
        try {
            if (audioRecordView.mediaState != Constants.COUNT_ZERO && audioRecordView.mediaPath.returnEmptyIfNull().isNotEmpty()) {
                val retriever = MediaMetadataRetriever();retriever.setDataSource(audioRecordView.mediaPath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() / 1000
                if (duration > 0) {
                    audioRecordView.stopRecording()
                    sendVoiceMessage(audioRecordView.mediaPath!!)
                } else {
                    audioRecordView.stopRecording()
                    showToast(getString(R.string.audio_too_short))
                }
            }
        } catch (e: Exception) {
            LogMessage.e(Constants.TAG, e)
            showToast(getString(R.string.audio_too_short))
        }
    }


    /**
     * Error timeout response for chat connection
     */
    override fun chatConnectionErrorResponse() {
        if (doProgressDialog != null && doProgressDialog!!.isShowing) {
            doProgressDialog!!.dismiss()
            setUserPresenceStatus(Constants.EMPTY_STRING)
            CustomToast.show(this, getString(R.string.error_chat_connection))
        }
    }

    /**
     * Called when the logged in successfully completed for the user.
     */
    override fun handleLoggedIn() {
        viewModel.getProfileDetails()
    }

    override fun userBlockedMe(jid: String) {
        super.userBlockedMe(jid)
        if (chatType == ChatType.TYPE_CHAT && toUser == jid) {
            viewModel.getProfileDetails()
        }
    }

    override fun userUnBlockedMe(jid: String) {
        super.userUnBlockedMe(jid)
        if (chatType == ChatType.TYPE_CHAT && toUser == jid) {
            viewModel.getProfileDetails()
        }
    }

    /**
     * Handle the new user added in the group.
     *
     * @param groupJid Group jid
     * @param newMemberJid Jabber id of the User
     * @param addedByMemberJid Jid of user who adds the member
     */
    override fun onNewMemberAddedToGroup(groupJid: String, newMemberJid: String, addedByMemberJid: String) {
        super.onNewMemberAddedToGroup(groupJid, newMemberJid, addedByMemberJid)
        if (toUser == groupJid) {
            viewModel.getProfileDetails()
            updateChatEditText()
            chatAdapter.notifyDataSetChanged()
        }
    }

    /**
     * Handle the user removed from the group
     *
     * @param groupJid Group id
     * @param removedMemberJid Removed member Jid
     * @param removedByMemberJid Made remove member jid
     */
    override fun onMemberRemovedFromGroup(groupJid: String, removedMemberJid: String, removedByMemberJid: String) {
        super.onMemberRemovedFromGroup(groupJid, removedMemberJid, removedByMemberJid)
        if (toUser == groupJid) {
            viewModel.getProfileDetails()
            handleOnResume()
        }
    }

    /**
     * Called when the group notification message inserted
     *
     * @param message Notification message
     */
    override fun onGroupNotificationMessage(message: ChatMessage) {
        onMessageReceived(message)
        if(message.isNotificationMessage() && message.messageTextContent.contains("added you"))
            updateChatEditText()
    }

    /**
     * Called when the group is updated
     *
     * @param groupJid Group jid
     */
    override fun onGroupProfileUpdated(groupJid: String) {
        super.onGroupProfileUpdated(groupJid)
        /**
         * Update the group profile
         */
        if (toUser == groupJid) {
            viewModel.getProfileDetails()
            handleOnResume()
        }
    }

    /**
     * Called when the group admin changed the affiliation
     *
     * @param groupJid Group jid
     * @param newAdminMemberJid New admin jid
     * @param madeByMemberJid Made new admin jid
     */
    override fun onMemberMadeAsAdmin(groupJid: String, newAdminMemberJid: String, madeByMemberJid: String) {
        super.onMemberMadeAsAdmin(groupJid, newAdminMemberJid, madeByMemberJid)
        if (toUser == groupJid) {
            handleOnResume()
        }
    }


    /**
     * Called when rosters collected successfully for the user.
     */
    override fun onMessagesClearedOrDeleted(messageIds: ArrayList<String>, jid: String) {
        updateAdapter()
    }

    override fun onPause() {
        super.onPause()
        keyboardHeightProvider?.onPause()
        setUnsentMessageForAnUser(chatMessageEditText.text.toString())
        if (selectedMessageIdForReply.isEmpty()) {
            SharedPreferenceManager.setString(Constants.REPLY_MESSAGE_ID, Constants.EMPTY_STRING)
            SharedPreferenceManager.setString(Constants.REPLY_MESSAGE_USER, Constants.EMPTY_STRING)
        } else ReplyHashMap.saveReplyId(chat.toUser, selectedMessageIdForReply)
        sendTypingGone()
        viewModel.deleteUnreadMessageSeparator(chat.toUser)
        ChatManager.setOnGoingChatUser("")
        try {
            unregisterReceiver(dateChangedBroadcastReceiver)
        } catch (e: Exception) {
            LogMessage.e(e)
        }
        if (!isBlocked)
            chatAdapter.pauseMediaPlayer()
        if (isSoftKeyboardShown) showChatKeyboard = true
        sendAudioRecording()
    }

    /**
     * The method which registers the receiver to handle the telephony local broadcast
     */
    private fun registerTelephonyCallListener() {
        telephoneCallReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val callState = intent.getIntExtra(TelephonyServiceReceiver.CALL_STATE, -1)
                LogMessage.d(TAG, "handleCallState() callState -> $callState")
                when (callState) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        chatAdapter.pauseMediaPlayer()
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        LogMessage.d(TAG, "CALL_STATE_IDLE -> $callState")
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        LogMessage.d(TAG, "CALL_STATE_OFFHOOK -> $callState")
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(telephoneCallReceiver!!,
                IntentFilter(GroupCallUtils.ACTION_PHONE_CALL_STATE_CHANGED))
    }

    /**
     * The method which registers the receiver to handle the audio call disconnection.
     */
    private fun unRegisterTelephonyCallListener() {
        if (telephoneCallReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(telephoneCallReceiver!!)
        }
    }

    private fun sendAudioRecording() {
        audioRecordView.sendRecording()
    }

    private fun pauseAudioRecording() {
        audioRecordView.pauseOngoingRecording()
    }

    private fun sendQueuedUpMessages() {
        for (item in messagesQueue.distinct())
            validateAndSendMessage(item)
        messagesQueue.clear()
    }

    override fun onBackPressed() {
        if (emojiHandler.isEmojiShowing && !isBackPressed) {
            emojiHandler.hideEmoji()
        } else
            when {
                searchEnabled -> handleSearchToolbar(false, true)
                attachmentDialog.isShowing -> closeControls()
                else -> {
                    chatAdapter.stopMediaPlayer()
                    clear()
                    if (isFromQuickShare)
                        startActivity(Intent(this, DashboardActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
            }
    }

    private fun setMessageCallBack() {
        parentViewModel.messages.observe(this, Observer {
            if (mainList.isEmpty())
                suggestionLayout.gone()
        })
    }

    private fun initSuggestion(messageList: ArrayList<ChatMessage>) {
        replySuggestionAdapter = ReplySuggestionsAdapter(this, this)
        replySuggestionsRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        replySuggestionsRecycler.adapter = replySuggestionAdapter
        if (messageList.isNotEmpty())
            parentViewModel.addMessage(messageList.last(), chat.toUser)
        parentViewModel.clearSuggestions()
        smartReplySuggestionCallback()
    }

    private fun smartReplySuggestionCallback() {
        parentViewModel.getSuggestions().observe(this, {
            suggestionCount = it.size
            if ( it.isNotEmpty()) {
                suggestionLayout.show()
                replySuggestionAdapter.setSmartReplySuggestions(it)
            } else
                suggestionLayout.gone()
        })
    }

    override fun onSuggestionClick(text: String) {
        if (text.isNotEmpty()) {
            if(!FlyCore.isBusyStatusEnabled() || chatType == ChatType.TYPE_GROUP_CHAT)
                chatMessageEditText.setText(text)
            else
                smartReply = text
            sendSmartReplyMessage()
            updateAdapter()
        }
    }

    private fun sendSmartReplyMessage() {
        viewModel.deleteUnreadMessageSeparator(toUser)
        /*
         * Check if the user busy status is enabled or not
         */
        if (isBlocked)
            showSmartReplyUnblock()
        else
            sendSmartReply()
    }

    private fun sendSmartReply() {
        if (FlyCore.isBusyStatusEnabled() && chatType == ChatType.TYPE_CHAT) {
            showSmartReplyBusy()
        } else {
            isReplyTagged = false
            val msgData = messagingClient.composeTextMessage(chat.toUser, chatMessageEditText.text.toString().trim(), selectedMessageIdForReply)
            validateAndSendMessage(msgData)
            chatMessageEditText.setText(Constants.EMPTY_STRING)
        }
    }

    private val dateChangedBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            unregisterReceiver(this)
            mainList.clear()
            mainList.addAll(viewModel.getAllMessages(chat.toUser))
        }
    }

    override fun restoreCompleted() {
        viewModel.loadInitialData()
    }

    private fun getKeyboardListener() = object : KeyboardHeightProvider.KeyboardListener {
        override fun onHeightChanged(height: Int) {
            softKeyboardHeight = height
        }
    }


    override fun onRecordingStarted() {
        transparentView.gone()
        optionMenu?.get(R.id.action_audio_call)?.isEnabled = false
        optionMenu?.get(R.id.action_video_call)?.isEnabled = false
    }

    override fun onRecordingLocked() {
        transparentView.gone()
    }

    override fun onRecordingCompleted() {
        sendVoiceMessage()
        transparentView.gone()
        optionMenu?.get(R.id.action_audio_call)?.isEnabled = true
        optionMenu?.get(R.id.action_video_call)?.isEnabled = true
        showUnsentMessage(chatMessageEditText.text.toString())
    }

    override fun onRecordingCanceled() {
        transparentView.gone()
        optionMenu?.get(R.id.action_audio_call)?.isEnabled = true
        optionMenu?.get(R.id.action_video_call)?.isEnabled = true
        showUnsentMessage(chatMessageEditText.text.toString())
    }

}