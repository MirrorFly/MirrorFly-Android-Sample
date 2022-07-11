package com.contusfly.call.groupcall

import android.app.Activity
import android.content.Context
import android.os.Build
import com.contus.webrtc.CallDirection
import com.contus.webrtc.CallStatus
import com.contus.webrtc.CallType
import com.contus.webrtc.api.CallManager
import com.contus.call.utils.GroupCallUtils
import com.contusfly.R
import com.contusfly.call.groupcall.utils.CallUtils
import com.contusfly.utils.Constants
import com.contusfly.views.CustomToast

fun GroupCallUtils.isOutgoingCall() = getCallDirection() == CallDirection.OUTGOING_CALL

fun GroupCallUtils.isInComingCall() = getCallDirection() == CallDirection.INCOMING_CALL

fun GroupCallUtils.isVideoCall() = getCallType() == CallType.VIDEO_CALL

fun GroupCallUtils.isOneToOneVideoCall() = isVideoCallUICanShow() && isOneToOneCall()

fun GroupCallUtils.isOneToOneRemoteVideoMuted() = isOneToOneCall() && CallManager.isRemoteVideoMuted(getEndCallerJid())

fun GroupCallUtils.isVideoCallUICanShow() = isVideoCall() && !CallManager.isRemoteVideoMuted(getEndCallerJid())

fun GroupCallUtils.isAudioCall() = getCallType() == CallType.AUDIO_CALL

fun GroupCallUtils.isCallNotConnected() = !isCallConnected() && !isCallAttended()

fun GroupCallUtils.isLocalTileCanResize() = isOneToOneCall() && (isAudioCall() || isCallConnected()) && !CallUtils.getIsGridViewEnabled()

fun GroupCallUtils.isPinnedUserLeft(userJid: String = Constants.EMPTY_STRING) = CallUtils.getPinnedUserJid() == userJid || (isOneToOneCall() && CallUtils.getPinnedUserJid() == getLocalUserJid())

fun GroupCallUtils.isUserAudioMuted(userJid: String): Boolean {
    return if (userJid == getLocalUserJid())
        isAudioMuted()
    else CallManager.isRemoteAudioMuted(userJid)
}

fun GroupCallUtils.isUserVideoMuted(userJid: String): Boolean {
    return if (userJid == getLocalUserJid())
        isVideoMuted()
    else CallManager.isRemoteVideoMuted(userJid)
}

//Get Call Status dependent functions
fun GroupCallUtils.getOnGoingCallStatus(context: Context): String {
    when {
        isCallConnected() -> return getCallConnectedStatus(context)
        isOutgoingCall() -> return getOutGoingCallStatus(context)
        isInComingCall() -> return getInComingCallStatus(context)
    }
    return Constants.EMPTY_STRING
}

fun GroupCallUtils.getCallConnectedStatus(context: Context): String {
    return if (isOneToOneCall()) {
        when (val localCallStatus = getCallStatus(getLocalUserJid())) {
            CallStatus.ON_HOLD -> localCallStatus
            CallStatus.RECONNECTING -> context.getString(R.string.reconnecting)
            else -> {
                when (val remoteCallStatus = getCallStatus(getEndCallerJid())) {
                    CallStatus.CALLING, CallStatus.RINGING, CallStatus.ON_HOLD -> remoteCallStatus
                    else -> Constants.EMPTY_STRING
                }
            }
        }
    } else
        CallStatus.CONNECTED
}

fun GroupCallUtils.getOutGoingCallStatus(context: Context): String {
    val localCallStatus = getCallStatus(getLocalUserJid())
    return when {
        isCallTryingToConnect(localCallStatus) -> context.getString(R.string.trying_to_connect)
        isCallTimeOut(localCallStatus) -> context.getString(R.string.call_try_again_info)
        isCallConnecting(localCallStatus) -> CallStatus.RINGING
        else -> localCallStatus
    }
}

fun isCallTryingToConnect(callStatus: String) = callStatus.isEmpty()
        || callStatus == CallStatus.DISCONNECTED

fun isCallConnecting(callStatus: String) = callStatus == CallStatus.CONNECTING || callStatus == CallStatus.CONNECTED

fun isCallTimeOut(callStatus: String) =
    callStatus.isNotBlank() && callStatus == CallStatus.OUTGOING_CALL_TIME_OUT

fun GroupCallUtils.getInComingCallStatus(context: Context): String {
    return if (isAudioCall())
        context.getString(R.string.incoming_audio_call)
    else
        context.getString(R.string.incoming_video_call)
}

fun Context.showCustomToast(text: String?) {
    text?.let {
        CustomToast.showCustomToast(this, text)
    }
}

fun Any?.isNull() : Boolean = this == null

fun Activity.isInPIPMode() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
