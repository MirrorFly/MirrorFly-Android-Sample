package com.contusfly.call.groupcall.helpers

import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.contus.call.CallConstants.CALL_UI
import com.contus.flycommons.LogMessage
import com.contus.webrtc.api.CallManager
import com.contus.call.utils.GroupCallUtils
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.call.groupcall.listeners.ActivityOnClickListener
import com.contusflysdk.api.contacts.ContactManager

/**
 * This call dialog view helper is handle the incoming and outgoing video call switch requests.
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 2.0
 */
class DialogViewHelper(
    private val context: Context,
    private val durationHandler: Handler,
    private val activityOnClickListener: ActivityOnClickListener
) {

    /**
     * boolean to check outGoing request
     */
    private var outGoingRequest = false

    /**
     * boolean to check inComing request
     */
    private var inComingRequest = false

    /**
     * When user doesn't respond to video call request for 20 seconds
     * local toast is shown
     */
    private val outgoingRequestRunnable by lazy {
        Runnable {
            LogMessage.d(TAG, "$CALL_UI outgoingRequestRunnable no response from end user")
            outGoingRequest = false
            dismissVideoCallRequestingDialog()
            CallManager.muteVideo(true)
            activityOnClickListener.onRequestingVideoCallDialog()
            Toast.makeText(context, "No response from " + ContactManager.getDisplayName(GroupCallUtils.getEndCallerJid()), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Alert dialog to confirm call switch
     */
    private val callSwitchConfirmationDialog: AlertDialog by lazy {
        val mBuilder = AlertDialog.Builder(context, R.style.AlertDialogStyle)
        mBuilder.setMessage("Are you sure you want to switch to Video call?")
        mBuilder.setPositiveButton(context.getString(R.string.action_switch)) { _: DialogInterface?, _: Int ->
            outGoingRequest = true
            inComingRequest = CallManager.isCallConversionRequestAvailable()
            callSwitchConfirmationDialog.dismiss()
            showVideoCallRequestingDialog()
            durationHandler.postDelayed(outgoingRequestRunnable, 20000)
            activityOnClickListener.onCallSwitchConfirmationDialog()
            CallManager.requestVideoCallSwitch()
        }
        mBuilder.setNegativeButton(context.getString(R.string.action_cancel)) { _: DialogInterface?, _: Int ->
            outGoingRequest = false
            callSwitchConfirmationDialog.dismiss()
        }
        mBuilder.create()
    }

    /**
     * Show call switch confirmation dialog
     */
    fun showCallSwitchConfirmationDialog() {
        if (callSwitchConfirmationDialog.window != null) {
            callSwitchConfirmationDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        callSwitchConfirmationDialog.setCancelable(false)
        callSwitchConfirmationDialog.show()
    }

    /**
     * Dismiss call switch confirmation dialog
     */
    fun dismissCallSwitchConfirmationDialog() {
        if (callSwitchConfirmationDialog.isShowing) {
            callSwitchConfirmationDialog.dismiss()
        }
    }

    /**
     * Alert dialog to show requesting call switch
     */
    private val requestingVideoCallDialog: AlertDialog by lazy {
        val mBuilder = AlertDialog.Builder(context, R.style.AlertDialogStyle)
        mBuilder.setMessage("Requesting to switch to video call.")
        mBuilder.setPositiveButton(context.getString(R.string.action_cancel)) { _: DialogInterface?, _: Int ->
            outGoingRequest = false
            requestingVideoCallDialog.dismiss()
            durationHandler.removeCallbacks(outgoingRequestRunnable)
            CallManager.cancelVideoCallSwitchRequest()
            activityOnClickListener.onRequestingVideoCallDialog()
        }
        mBuilder.create()
    }

    /**
     * Show video call requesting dialog to user once call switch conformation is done
     */
    private fun showVideoCallRequestingDialog() {
        LogMessage.d(TAG, "$CALL_UI showRequestingAlert")
        if (requestingVideoCallDialog.window != null) {
            requestingVideoCallDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        requestingVideoCallDialog.setCancelable(false)
        requestingVideoCallDialog.show()
    }

    /**
     * Dismiss video call requesting dialog
     */
    fun dismissVideoCallRequestingDialog() {
        if (requestingVideoCallDialog.isShowing) {
            requestingVideoCallDialog.dismiss()
        }
        outGoingRequest = false
        durationHandler.removeCallbacks(outgoingRequestRunnable)
    }

    /**
     * Alert dialog to show incoming call switch request
     */
    private val callSwitchDialog: AlertDialog by lazy {
        val mBuilder = AlertDialog.Builder(context, R.style.AlertDialogStyle)
        if (GroupCallUtils.getEndCallerJid().contains("@") && GroupCallUtils.getEndCallerJid().isNotEmpty()) {
            mBuilder.setMessage(ContactManager.getDisplayName(GroupCallUtils.getEndCallerJid()) + " requesting to switch to video call")
        } else {
            mBuilder.setMessage("requesting to switch to video call")
        }
        mBuilder.setPositiveButton(context.getString(R.string.fly_info_call_notification_accept)) { _: DialogInterface?, _: Int ->
            activityOnClickListener.onCallSwitchDialog(true)
        }
        mBuilder.setNegativeButton(context.getString(R.string.fly_info_call_notification_decline)) { _: DialogInterface?, _: Int ->
            LogMessage.d(TAG, "$CALL_UI showCallSwitchAlert Decline")
            activityOnClickListener.onCallSwitchDialog(false)
            callSwitchDialog.dismiss()
        }
        mBuilder.create()
    }

    /**
     * Show dialog for call switch
     */
    fun showCallSwitchDialog() {
        LogMessage.d(TAG, "$CALL_UI showCallSwitchAlert")
        inComingRequest = CallManager.isCallConversionRequestAvailable()
        if (callSwitchDialog.window != null) {
            callSwitchDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        callSwitchDialog.setCancelable(false)
        callSwitchDialog.show()
    }

    /**
     * Dismiss call switch dialog
     */
    fun dismissCallSwitchDialog() {
        if (callSwitchDialog.isShowing) {
            callSwitchDialog.dismiss()
        }
    }

    /**
     * Accepted video call switch request
     */
    fun videoCallSwitchRequestAccepted() {
        if (outGoingRequest && !inComingRequest)
            CallManager.muteVideo(true)
        dismissCallSwitchDialog()
        dismissVideoCallRequestingDialog()
        dismissCallSwitchConfirmationDialog()
        inComingRequest = false
        outGoingRequest = false
    }

    /**
     * Canceled video call switch request
     */
    fun videoCallSwitchRequestCancelled() {
        inComingRequest = false
        outGoingRequest = false
    }

    fun disconnectCall() {
        videoCallSwitchRequestAccepted()
    }

    fun isVideoCallSwitchRequestedFromBothSides() = inComingRequest && outGoingRequest

    fun isVideoCallSwitchRequested() = outGoingRequest

    fun isIncomingRequestAvailable() {
        inComingRequest = CallManager.isCallConversionRequestAvailable()
    }
}