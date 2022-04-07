package com.contusfly.call.calllog

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import com.contus.call.CallConstants.CALL_UI
import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG
import com.contus.flynetwork.ApiCalls
import com.contus.webrtc.api.CallLogManager
import com.contus.call.database.model.CallLog
import com.contusfly.dashboard.calllog.CallLogDiffCallback
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CallLogViewModel @Inject
constructor(private val repository: CallLogRepository, private val apiCalls: ApiCalls) : ViewModel() {

    val isUserBlockedUnblockedMe = MutableLiveData<Pair<String, Boolean>>()
    val profileUpdatedLiveData = MutableLiveData<String>()
    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        Log.e(TAG, "Coroutine Exception:  ${exception}")
    }

    /**
     * contact refreshing status
     */
    private var isRefreshing: Boolean = false

    val filteredCallLogsList = MutableLiveData<List<CallLog>>()

    // = = = = = = = = CallLogs Data = = = = = = = =
    private var callLogList: MutableList<CallLog> = ArrayList<CallLog>()
    val callLogAdapterList: ArrayList<CallLog> by lazy { ArrayList() }
    val selectedCallLogs: ArrayList<String> by lazy { ArrayList() }
    val callLogDiffResult = MutableLiveData<DiffUtil.DiffResult>()

    private val _showMessage = MutableLiveData<String>()
    val showMessage: LiveData<String>
        get() = _showMessage

    val callLog = MutableLiveData<CallLog>()
    fun getCallLog(roomId: String) {
        viewModelScope.launch {
            callLog.value = repository.getCallLog(roomId)
        }
    }

    private suspend fun getDiffUtilResult(diffUtilCallback: DiffUtil.Callback): DiffUtil.DiffResult = withContext(Dispatchers.IO) {
        DiffUtil.calculateDiff(diffUtilCallback)
    }


    fun getCallLogsList(isLoadDataOnMainThread: Boolean) {
        LogMessage.d(TAG, "$CALL_UI getCallLogsList loadOnMain: $isLoadDataOnMainThread")
        if (isLoadDataOnMainThread) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                callLogList = repository.getCallLogs()
                getCallLogDiffResult(false)
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                callLogList = repository.getCallLogs()
                getCallLogDiffResult(true)
            }
        }
    }

    private suspend fun getCallLogDiffResult(computeDiff: Boolean) {
        callLogAdapterList.clear()
        callLogAdapterList.addAll(callLogList)
        if (computeDiff) {
            val diffResult = getDiffUtilResult(CallLogDiffCallback(callLogAdapterList, callLogList))
            callLogDiffResult.postValue(diffResult) //we are on IO thread
        } else {
            callLogDiffResult.value = null  //we are on main thread
        }
    }

    fun uploadUnSyncedCallLogs() {
        if (AppUtils.isNetConnected(ChatManager.applicationContext)) {
            LogMessage.v(this@CallLogViewModel.TAG, "$CALL_UI uploadUnSyncedCallLogs working in thread: ${Thread.currentThread().name}")
            viewModelScope.launch(exceptionHandler) {
                CallLogManager.uploadUnSyncedCallLogs(apiCalls)
            }
        }
    }

    fun filterCallLogsList(searchKey: String) {
        viewModelScope.launch(exceptionHandler) { filteredCallLogsList.value = repository.filteredCallLogs(searchKey) }
    }
}