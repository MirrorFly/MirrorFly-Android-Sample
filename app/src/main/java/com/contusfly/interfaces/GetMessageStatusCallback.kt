package com.contusfly.interfaces

import com.contusflysdk.api.models.MessageStatusDetail

interface GetMessageStatusCallback {
    fun onGetMessageStatusLoaded(deliveredStatus: List<MessageStatusDetail>, readByStatus: List<MessageStatusDetail>)
}