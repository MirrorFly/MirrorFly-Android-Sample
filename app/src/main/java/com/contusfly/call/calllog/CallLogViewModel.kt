package com.contusfly.call.calllog

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contus.call.CallConstants.CALL_UI
import com.contus.flynetwork.ApiCalls
import com.contus.webrtc.api.CallLogManager
import com.contus.call.database.model.CallLog
import com.contus.flycommons.*
import com.contus.webrtc.api.CallManager
import com.contus.flycommons.Features
import com.contusfly.dashboard.calllog.CallLogDiffCallback
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallLogViewModel @Inject
constructor(private val repository: CallLogRepository, private val apiCalls: ApiCalls) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine Exception:  $exception")
    }

    val updatedFeaturesLiveData = MutableLiveData<Features>()

    val filteredCallLogsList = MutableLiveData<List<CallLog>>()

    private var isFetching = false
    private var currentPage = 0
    private var totalPages = 1
    private var fromCallLogScreen = false

    val addLoader = MutableLiveData<Boolean>()
    val removeLoader = MutableLiveData<Boolean>()
    val callList = MutableLiveData<List<CallLog>>()
    val fetchingError = MutableLiveData<Boolean>()

    // = = = = = = = = CallLogs Data = = = = = = = =
    private var callLogList: MutableList<CallLog> = ArrayList<CallLog>()
    val callLogAdapterList: ArrayList<CallLog> by lazy { ArrayList() }
    val selectedCallLogs: ArrayList<String> by lazy { ArrayList() }

    val callLog = MutableLiveData<CallLog>()
    fun getCallLog(roomId: String) {
        viewModelScope.launch {
            callLog.value = repository.getCallLog(roomId)
        }
    }

    private fun resetPagination() {
        isFetching = false
        currentPage = 0
        totalPages = 1
    }

    fun getCallLogsList(isLoadDataOnMainThread: Boolean) {
        LogMessage.d(TAG, "$CALL_UI getCallLogsList loadOnMain: $isLoadDataOnMainThread")
        if (lastPageFetched())
            return
        updateLoaderStatus()
        fetchingError.value = false
        viewModelScope.launch(if(isLoadDataOnMainThread) Dispatchers.Main else Dispatchers.IO) {
            currentPage += 1
            setUserListFetching(true)
            CallManager.getCallLogs(currentPage) { isSuccess, throwable, data ->
                if (isSuccess) {
                    val callLogDBList = data.getData() as MutableList<CallLog>
                    totalPages = data.getParams("total_pages") as Int
                    removeLoader.postValue(true)
                    callList.postValue(callLogDBList)
                    callLogList.addAll(callLogDBList)
                    LogMessage.d(TAG, "$CALL_UI getCallLogs pageNumber: $currentPage, $totalPages")
                    updateLoaderStatus()
                } else {
                    currentPage -= 1
                    viewModelScope.launch(Dispatchers.Main) {
                        removeLoader.postValue(true)
                        fetchingError.value = true
                    }
                    LogMessage.d(TAG, "$CALL_UI getCallLogs failed throwable: $throwable")
                }
                setUserListFetching(false)
            }
        }
    }

    private fun updateLoaderStatus() {
        if (lastPageFetched()) {
            removeLoader.postValue(true)
        } else
            addLoader.postValue(true)
    }

    fun addLoaderToTheList(fromCallLog : Boolean = false) {
        resetPagination()
        fromCallLogScreen = fromCallLog
        addLoader.postValue(true)
    }

    fun lastPageFetched() = currentPage >= totalPages

    fun isCallLogScreenInitiated() = fromCallLogScreen

    private fun setUserListFetching(isFetching: Boolean) {
        this.isFetching = isFetching
    }

    fun getUserListFetching(): Boolean {
        return isFetching
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

    fun updateFeatureActions(updateFeatures: Features) {
        updatedFeaturesLiveData.postValue(updateFeatures)
    }
}