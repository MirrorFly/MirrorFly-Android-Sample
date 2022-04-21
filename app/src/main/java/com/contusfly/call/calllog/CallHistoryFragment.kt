package com.contusfly.call.calllog

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.flycommons.*
import com.contus.flynetwork.ApiCalls
import com.contus.webrtc.CallMode
import com.contus.webrtc.CallState
import com.contus.webrtc.CallType
import com.contus.webrtc.Logger
import com.contus.webrtc.api.CallLogManager
import com.contus.webrtc.api.CallManager
import com.contus.call.database.CallLogUtils
import com.contus.call.database.model.CallLog
import com.contus.call.utils.CallConstants
import com.contus.call.utils.GroupCallUtils
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.R
import com.contusfly.activities.ChatActivity
import com.contusfly.activities.DashboardActivity
import com.contusfly.activities.NewContactsActivity
import com.contusfly.call.CallConfiguration
import com.contusfly.call.CallPermissionUtils
import com.contusfly.databinding.FragmentCallHistoryBinding
import com.contusfly.di.factory.AppViewModelFactory
import com.contusfly.setOnClickListener
import com.contusfly.setVisibile
import com.contusfly.utils.AppConstants
import com.contusfly.utils.MediaPermissions
import com.contusfly.viewmodels.DashboardViewModel
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.utils.ItemClickSupport
import com.contusflysdk.views.CustomToast
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class CallHistoryFragment : Fragment(), CoroutineScope, CommonAlertDialog.CommonDialogClosedListener, CallLogManager.CallLogActionListener {

    /**
     * if we came to this screen from notification load data in main thread to avoid no call
     * logs found ui which shows for a second
     */
    var isLoadDataOnMainThread: Boolean = false

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        println("Coroutine Exception ${TAG}:  ${exception.printStackTrace()}")
    }

    private var lastCallAction = ""

    @Inject
    lateinit var apiCalls: ApiCalls

    private lateinit var callHistoryBinding: FragmentCallHistoryBinding

    private var mSearchCallLogs = ArrayList<CallLog>()

    private lateinit var mAdapter: CallHistoryAdapter

    private val mSearchAdapter by lazy { CallHistorySearchAdapter(requireContext(), mSearchCallLogs, viewModel.selectedCallLogs, listener) }

    /**
     * The common alert dialog to display the alert dialogs in the alert view
     */
    private lateinit var commonAlertDialog: CommonAlertDialog

    private var mCallLogsType = CallLogsType.NORMAL

    /**
     * Boolean to verify whether the entire call log history has to be deleted.
     */
    private var isClearAll: Boolean = false

    private lateinit var callPermissionUtils: CallPermissionUtils

    @Inject
    lateinit var callLogsViewModelFactory: AppViewModelFactory

    private val viewModel: CallLogViewModel by viewModels { callLogsViewModelFactory }

    private val dashBoardViewModel: DashboardViewModel by viewModels(
        { requireActivity() },
        { callLogsViewModelFactory })

    private var listener = object : CallHistoryAdapter.OnItemClickListener {
        override fun onItemClick(view: ImageView, position: Int) {
            if (viewModel.selectedCallLogs.isEmpty())
                openChatView(view, position)
            else selectUnselectPayload(position)
        }
    }

    // Request multiple permissions contract
    private val requestCallPermissions: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Do something if some permissions granted or denied
            if (!permissions.containsValue(false)) {
                if (lastCallAction == CallType.AUDIO_CALL) {
                    callPermissionUtils.audioCall()
                } else {
                    callPermissionUtils.videoCall()
                }
            }
        }

    // General activity result contract
    private val openContactsActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedCallUsersList =
                    result.data?.getStringArrayListExtra(com.contusfly.utils.Constants.USERS_JID)!!
                makeCall(lastCallAction, "", false, selectedCallUsersList)
            }
        }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        callHistoryBinding = FragmentCallHistoryBinding.inflate(inflater, container, false)
        return callHistoryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        setObservers()
        setListeners()
        viewModel.getCallLogsList(isLoadDataOnMainThread)
        isLoadDataOnMainThread = false
    }

    private fun initView() {
        CallLogManager.setCallLogsListener(object : CallLogManager.CallLogsListener {
            override fun onCallLogsUpdated() {
                viewModel.getCallLogsList(false)
                Log.d(TAG, "Call Logs Updated")
                (activity as DashboardActivity).validateMissedCallsCount()
            }
        })
        callHistoryBinding.listCallHistory.apply {
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(20)
            itemAnimator = null
            setHasFixedSize(true)
            setEmptyView(callHistoryBinding.viewNoCallHistory.root)
        }
        callHistoryBinding.fabAddCall.setVisibile(CallConfiguration.isGroupCallEnabled())
        callHistoryBinding.fabAddCall.setOnClickListener {
            if (callHistoryBinding.fabMakeVoiceCall.isVisible) {
                callHistoryBinding.fabMakeVoiceCall.hide()
                callHistoryBinding.fabMakeVideoCall.hide()
            } else {
                callHistoryBinding.fabMakeVoiceCall.show()
                callHistoryBinding.fabMakeVideoCall.show()
            }
        }

        callHistoryBinding.fabMakeVoiceCall.setOnClickListener(1000) {
            val intent = Intent(context, NewContactsActivity::class.java).apply {
                putExtra(com.contusfly.utils.Constants.TITLE, getString(R.string.title_contacts))
                putExtra(com.contusfly.utils.Constants.MULTI_SELECTION, true)
                putExtra(com.contusfly.utils.Constants.IS_MAKE_CALL, true)
                putExtra(com.contusfly.utils.Constants.CALL_TYPE, CallType.AUDIO_CALL)
            }
            openContactsActivity.launch(intent)
            lastCallAction = CallType.AUDIO_CALL
            callHistoryBinding.fabMakeVoiceCall.hide()
            callHistoryBinding.fabMakeVideoCall.hide()
        }

        callHistoryBinding.fabMakeVideoCall.setOnClickListener(1000) {
            val intent = Intent(context, NewContactsActivity::class.java).apply {
                putExtra(com.contusfly.utils.Constants.TITLE, getString(R.string.title_contacts))
                putExtra(com.contusfly.utils.Constants.MULTI_SELECTION, true)
                putExtra(com.contusfly.utils.Constants.IS_MAKE_CALL, true)
                putExtra(com.contusfly.utils.Constants.CALL_TYPE, CallType.VIDEO_CALL)
            }
            openContactsActivity.launch(intent)
            lastCallAction = CallType.VIDEO_CALL
            callHistoryBinding.fabMakeVoiceCall.hide()
            callHistoryBinding.fabMakeVideoCall.hide()
        }
    }

    private fun setObservers() {
        viewModel.callLogDiffResult.observe(viewLifecycleOwner, Observer {
            initCallLogsListAdapter(it)
        })
        dashBoardViewModel.isUserBlockedUnblockedMe.observe(viewLifecycleOwner, Observer {
            val bundle = Bundle()
            bundle.putInt(AppConstants.NOTIFY_PROFILE_ICON, 3)
            getIndicesOfUserInCallLog(it.first).forEachIndexed { _, callLog ->
                if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter)) {
                    val finalIndex = viewModel.callLogAdapterList.indexOfFirst { cl -> cl.roomId == callLog.roomId }
                    mAdapter.notifyItemChanged(finalIndex, bundle)
                } else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter)) {
                    val finalIndex = mSearchCallLogs.indexOfFirst { cl -> cl.roomId == callLog.roomId }
                    mSearchAdapter.notifyItemChanged(finalIndex, bundle)
                }
            }
        })
        dashBoardViewModel.profileUpdatedLiveData.observe(viewLifecycleOwner, { userJid ->
            val bundle = Bundle()
            bundle.putInt(AppConstants.NOTIFY_PROFILE_ICON, 3)
            getIndicesOfUserInCallLog(userJid).forEachIndexed { _, callLog ->
                if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter)) {
                    mAdapter.notifyDataSetChanged()
                } else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter)) {
                    mAdapter.notifyDataSetChanged()
                }
            }
        })
        dashBoardViewModel.callsSearchKey.observe(viewLifecycleOwner, Observer { doSearch(it) })
        viewModel.filteredCallLogsList.observe(viewLifecycleOwner, Observer { observeFilteredCallLogs(it) })

    }

    private fun doSearch(searchKey: String) {
        if (!isVisible)
            return
        mSearchAdapter.setSearchKey(searchKey)
        if (searchKey.isEmpty()) {
            mCallLogsType = CallLogsType.NORMAL
            setAdapterBasedOnSearchType()
        } else {
            if (mCallLogsType == CallLogsType.NORMAL) {
                mCallLogsType = CallLogsType.SEARCH
                setAdapterBasedOnSearchType()
            }
            viewModel.filterCallLogsList(searchKey)
        }
    }

    private fun observeFilteredCallLogs(callLogs: List<CallLog>) {
        mSearchCallLogs.clear()
        mSearchCallLogs.addAll(callLogs)
        mSearchAdapter.notifyDataSetChanged()
    }

    private fun setAdapterBasedOnSearchType() {
        if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter))
            callHistoryBinding.listCallHistory.adapter = mAdapter
        else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter))
            callHistoryBinding.listCallHistory.adapter = mSearchAdapter
    }

    private fun getIndicesOfUserInCallLog(jid: String) = viewModel.callLogAdapterList.filter { callLog ->
        val endUserJid = if (callLog.callState == CallState.INCOMING_CALL
                || callLog.callState == CallState.MISSED_CALL)
            callLog.fromUser else callLog.toUser
        return@filter endUserJid?.trim() == jid && callLog.callMode == CallMode.ONE_TO_ONE
    }


    private fun setListeners() {
        commonAlertDialog = CommonAlertDialog(context)
        commonAlertDialog.setOnDialogCloseListener(this)

        val clickSupport = ItemClickSupport.addTo(callHistoryBinding.listCallHistory)

        clickSupport.setOnItemClickListener { _, position, v ->
            handleOnItemClicked(v, position)
        }

        clickSupport.setOnItemLongClickListener { _, position, _ ->
            handleOnItemLongClicked(position)
            true
        }
    }

    private fun selectUnselectPayload(position: Int) {
        val bundle = Bundle()
        bundle.putInt(AppConstants.NOTIFY_SELECTION, 4)
        val selectedCallLog: CallLog = if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter)) {
            viewModel.callLogAdapterList[position]
        } else {
            mSearchCallLogs[position]
        }
        if (viewModel.selectedCallLogs.contains(selectedCallLog.roomId)) {
            viewModel.selectedCallLogs.remove(selectedCallLog.roomId)
            bundle.putBoolean(AppConstants.NOTIFY_IS_SELECTED, false)
        } else {
            bundle.putBoolean(AppConstants.NOTIFY_IS_SELECTED, true)
            viewModel.selectedCallLogs.add(selectedCallLog.roomId!!)
        }
        if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter))
            mAdapter.notifyItemChanged(position, bundle)
        else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter))
            mSearchAdapter.notifyItemChanged(position, bundle)

    }

    private fun initCallLogsListAdapter(diffUtilResult: DiffUtil.DiffResult?) {
        if (!::mAdapter.isInitialized) {
            mAdapter = CallHistoryAdapter(
                requireContext(),
                viewModel.callLogAdapterList,
                viewModel.selectedCallLogs,
                listener
            )
            mAdapter.setHasStableIds(true)
            callHistoryBinding.listCallHistory.adapter = mAdapter
        } else {
            // Save Current Scroll state to retain scroll position after DiffUtils Applied
            val previousState =
                callHistoryBinding.listCallHistory.layoutManager?.onSaveInstanceState() as Parcelable
            diffUtilResult!!.dispatchUpdatesTo(mAdapter)
            callHistoryBinding.listCallHistory.layoutManager?.onRestoreInstanceState(previousState)
        }
    }

    private fun handleOnItemClicked(view: View, position: Int) {
        if (viewModel.selectedCallLogs.isEmpty())
            openChatView(view, position)
        else selectUnselectPayload(position)
        (activity as DashboardActivity).startActionModeForCallLogs(viewModel.selectedCallLogs.size, false)
    }

    private fun handleOnItemLongClicked(position: Int) {
        val selectedCallLog: CallLog = if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter)) {
            viewModel.callLogAdapterList[position]
        } else {
            mSearchCallLogs[position]
        }

        if (!viewModel.selectedCallLogs.contains(selectedCallLog.roomId)) {
            viewModel.selectedCallLogs.add(selectedCallLog.roomId!!)
            val bundle = Bundle()
            bundle.putInt(AppConstants.NOTIFY_SELECTION, 4)
            bundle.putBoolean(AppConstants.NOTIFY_IS_SELECTED, true)
            if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter))
                mAdapter.notifyItemChanged(position, bundle)
            else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter))
                mSearchAdapter.notifyItemChanged(position, bundle)
            (activity as DashboardActivity).startActionModeForCallLogs(viewModel.selectedCallLogs.size, true)
        }
    }

    private fun openChatView(view: View, position: Int) {
        val callLog: CallLog = if ((callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter))
            mSearchAdapter.getCallLogAtPosition(position)
        else
            mAdapter.getCallLogAtPosition(position)
        val toUser = if (callLog.callState == CallState.INCOMING_CALL
                || callLog.callState == CallState.MISSED_CALL)
            callLog.fromUser else callLog.toUser

        if (!callLog.groupId.isNullOrEmpty() || (!callLog.userList.isNullOrEmpty() && callLog.userList!!.filter { it != GroupCallUtils.getLocalUserJid() }.size > 1)) {
            openGroupChatView(callLog, view)
        } else {
            openDirectChatView(callLog, view, toUser)
        }
    }

    private fun openDirectChatView(callLog: CallLog, view: View, toUser: String?) {
        val profileDetails = ContactManager.getProfileDetails(toUser!!)
        val mTempUserListWithoutOwnJid = callLog.userList
        mTempUserListWithoutOwnJid?.remove(SharedPreferenceManager.instance.currentUserJid)
        if (!mTempUserListWithoutOwnJid.isNullOrEmpty() && mTempUserListWithoutOwnJid.size >= 2) {
            if (view.id == R.id.img_call_type)
                makeCall(
                    callLog.callType!!,
                    callLog.groupId,
                    profileDetails!!.isBlocked,
                    arrayListOf(profileDetails.jid)
                )
            else
                startActivity(
                    Intent(activity, CallHistoryDetailActivity::class.java)
                        .putExtra(CallConstants.ROOM_ID, callLog.roomId)
                )
        } else if (profileDetails != null) {
            if (view.id == R.id.img_call_type)
                makeCall(
                    callLog.callType!!,
                    callLog.groupId,
                    profileDetails.isBlocked,
                    arrayListOf(profileDetails.jid)
                )
            else {
                startActivity(
                    Intent(activity, ChatActivity::class.java)
                        .putExtra(LibConstants.JID, toUser)
                        .putExtra(Constants.CHAT_TYPE, ChatType.TYPE_CHAT)
                )
            }
        }
    }

    private fun openGroupChatView(callLog: CallLog, view: View) {
        if (view.id == R.id.img_call_type) {
            if(CallConfiguration.isGroupCallEnabled()) {
                makeCall(
                    callLog.callType!!,
                    callLog.groupId,
                    false,
                    GroupCallUtils.getConferenceUserList(
                        callLog.fromUser,
                        callLog.userList
                    ) as java.util.ArrayList<String>
                )
            } else {
                Toast.makeText(activity, getString(R.string.info_group_call_not_allowed), Toast.LENGTH_SHORT).show()
            }
        } else
            startActivity(Intent(activity, CallHistoryDetailActivity::class.java)
                    .putExtra(CallConstants.ROOM_ID, callLog.roomId))
    }

    /**
     * Starts the call activity based on the call icon click action.
     *
     * @param callLog the call log object in the adapter data set.
     */
    private fun makeCall(
        callType: String,
        groupId: String?,
        isBlocked: Boolean,
        jidList: java.util.ArrayList<String>
    ) {
        lastCallAction = callType
        if (callType == CallType.AUDIO_CALL) {
            GroupCallUtils.setIsCallStarted(CallType.AUDIO_CALL)
            callPermissionUtils = CallPermissionUtils(
                requireActivity(), isBlocked, jidList, groupId
                    ?: "", false
            )
            if (CallManager.isAudioCallPermissionsGranted()) {
                callPermissionUtils.audioCall()
            } else {
                MediaPermissions.requestAudioCallPermissions(
                    requireActivity(),
                    requestCallPermissions
                )
            }
        } else if (callType == CallType.VIDEO_CALL) {
            GroupCallUtils.setIsCallStarted(CallType.VIDEO_CALL)
            callPermissionUtils = CallPermissionUtils(
                requireActivity(), isBlocked, jidList, groupId
                    ?: "", false
            )
            if (CallManager.isVideoCallPermissionsGranted()) {
                callPermissionUtils.videoCall()
            } else {
                MediaPermissions.requestVideoCallPermissions(
                    requireActivity(),
                    requestCallPermissions
                )
            }
        }
        //if it is not a blocked contact , clear the value. so that we can avoid unknown calls
        //happening due to onBlockListCallBack() callback from CfBaseActivity.
        if (!isBlocked)
            GroupCallUtils.setIsCallStarted(null)
    }

    /**
     * Displays an alert dialog to get the confirmation from the user to clear
     * either the selected entry or the entire call log history.
     *
     * @param isClearAll true if the entire call log history has to be deleted.
     */
    fun showClearAlertDialog(isClearAll: Boolean) {
        this.isClearAll = isClearAll
        if (isClearAll && viewModel.callLogAdapterList.isNotEmpty()) {
            commonAlertDialog.showAlertDialog(resources.getString(R.string.action_clear_call_log_message),
                    resources.getString(R.string.action_Ok), resources.getString(R.string.action_cancel),
                    CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false)
        } else if (viewModel.selectedCallLogs.isNotEmpty()) {
            commonAlertDialog.showAlertDialog(
                resources.getQuantityString(
                    R.plurals.action_delete_call_log_message,
                    viewModel.selectedCallLogs.size
                ),
                resources.getString(R.string.action_Ok),
                resources.getString(R.string.action_cancel),
                CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL,
                false
            )
        } else CustomToast.show(activity, "No Call Log  ")
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            if (isClearAll) {
                launch(exceptionHandler) {
                    CallLogManager.clearCallLog(apiCalls, this@CallHistoryFragment)
                }
            } else if (viewModel.selectedCallLogs.isNotEmpty()) {
                launch(exceptionHandler) {
                    CallLogManager.deleteCallLog(apiCalls, viewModel.selectedCallLogs, this@CallHistoryFragment)
                }
            }
        }
    }

    override fun listOptionSelected(position: Int) {
        Logger.d(position.toString())
    }

    override fun onActionSuccess() {
        var readCount = SharedPreferenceManager.instance.getInt(SharedPreferenceManager.MISSED_CALL_COUNT)
        if (isClearAll) {
            readCount = 0
            viewModel.callLogAdapterList.clear()
            mSearchCallLogs.clear()
            mAdapter.notifyDataSetChanged()
            mSearchAdapter.notifyDataSetChanged()
            callHistoryBinding.listCallHistory.setEmptyView(callHistoryBinding.viewNoCallHistory.root)
        } else {
            readCount -= 0
            notifyAdapterOfDeletedCallLog()
        }
        (activity as DashboardActivity).startActionModeForCallLogs(-1, false)
        (activity as DashboardActivity).validateMissedCallsCount()
        SharedPreferenceManager.instance.storeInt(SharedPreferenceManager.MISSED_CALL_COUNT, readCount)
    }

    private fun notifyAdapterOfDeletedCallLog() {
        for (item in viewModel.selectedCallLogs) {
            val index = viewModel.callLogAdapterList.indexOfFirst { it.roomId == item }
            viewModel.callLogAdapterList.removeAt(index)
            mAdapter.notifyItemRemoved(index)
            if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter)) {
                val searchIndex = mSearchCallLogs.indexOfFirst { it.roomId == item }
                if (searchIndex >= 0) {
                    mSearchCallLogs.removeAt(searchIndex)
                    mSearchAdapter.notifyItemRemoved(searchIndex)
                }
            }
        }
        viewModel.selectedCallLogs.clear()
    }

    fun clearSelectedMessages() {
        lifecycleScope.launchWhenCreated {
            if (viewModel.selectedCallLogs.isNotEmpty())
                unSelectCallLogs()
                viewModel.selectedCallLogs.clear()
            }
    }

    private fun unSelectCallLogs() {
        val bundle = Bundle()
        bundle.putInt(AppConstants.NOTIFY_SELECTION, 4)
        bundle.putBoolean(AppConstants.NOTIFY_IS_SELECTED, false)
        for (item in viewModel.selectedCallLogs) {
            if (mCallLogsType == CallLogsType.NORMAL && (callHistoryBinding.listCallHistory.adapter is CallHistoryAdapter)) {
                val index = viewModel.callLogAdapterList.indexOfFirst { it.roomId == item }
                mAdapter.notifyItemChanged(index, bundle)
            } else if (mCallLogsType == CallLogsType.SEARCH && (callHistoryBinding.listCallHistory.adapter is CallHistorySearchAdapter)) {
                val index = mSearchCallLogs.indexOfFirst { it.roomId == item }
                mSearchAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CallLogManager.setCallLogsListener(null)
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + Job()

}