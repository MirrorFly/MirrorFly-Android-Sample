package com.contusfly.utils

import com.contus.flycommons.FlyCallback
import com.contusfly.interfaces.GetMessageStatusCallback
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.models.MessageStatusDetail

object GroupUtils {

    @JvmStatic
    fun getMessageStatus(messageId: String, getMessageStatusCallback: GetMessageStatusCallback) {
        var deliveredStatus: List<MessageStatusDetail>? = null
        var readStatus: List<MessageStatusDetail>? = null
        GroupManager.getGroupMessageDeliveredToList(messageId, FlyCallback { isSuccess, throwable, data ->
            if (isSuccess) {
                deliveredStatus = data[Constants.SDK_DATA] as (List<MessageStatusDetail>)
                if (readStatus != null)
                    getMessageStatusCallback.onGetMessageStatusLoaded(deliveredStatus!!, readStatus!!)
            } else
                deliveredStatus = listOf()
        })

        GroupManager.getGroupMessageReadByList(messageId, FlyCallback { isSuccess, throwable, data ->
            if (isSuccess) {
                readStatus = data[Constants.SDK_DATA] as (List<MessageStatusDetail>)
                if (deliveredStatus != null)
                    getMessageStatusCallback.onGetMessageStatusLoaded(deliveredStatus!!, readStatus!!)
            } else
                readStatus = listOf()
        })
    }
}