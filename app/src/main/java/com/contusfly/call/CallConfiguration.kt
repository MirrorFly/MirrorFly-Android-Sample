package com.contusfly.call

import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG

object CallConfiguration {
    /**
     * flag indicates whether group call feature is enabled or not
     *
     * setting this flag to false will hide call buttons from group, call logs
     * add group call button , add participants button in one to one call
     * and also the call button of group call logs in call log screen.
     */
    private var isGroupCallEnabled = false

    fun isGroupCallEnabled(): Boolean {
        LogMessage.d(TAG, "isGroupCallEnabled: $isGroupCallEnabled")
        return isGroupCallEnabled
    }

    fun setIsGroupCallEnabled(enabled: Boolean) {
        LogMessage.d(TAG, "setIsGroupCallEnabled: $enabled")
        isGroupCallEnabled = enabled
    }
}