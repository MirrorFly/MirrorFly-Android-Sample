/*
 * @category ContusFly
 * @copyright Copyright (C) 2018 Contus. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.contusfly.call

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.contus.call.CallConstants.CALL_UI
import com.contus.flycommons.*
import com.contus.webrtc.CallType
import com.contus.webrtc.api.CallActionListener
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.CallManager.isOnTelephonyCall
import com.contus.webrtc.api.CallManager.makeGroupVideoCall
import com.contus.webrtc.api.CallManager.makeGroupVoiceCall
import com.contus.webrtc.api.CallManager.makeVideoCall
import com.contus.webrtc.api.CallManager.makeVoiceCall
import com.contus.call.utils.GroupCallUtils
import com.contus.call.utils.GroupCallUtils.isOnGoingAudioCall
import com.contus.call.utils.GroupCallUtils.isOnGoingVideoCall
import com.contusfly.R
import com.contusfly.showToast
import com.contusfly.utils.MediaPermissions
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CommonAlertDialog.CommonDialogClosedListener
import com.contusfly.views.CommonAlertDialog.DIALOGTYPE
import com.contusfly.views.DoProgressDialog
import com.contusflysdk.AppUtils
import com.contusflysdk.api.FlyCore.unblockUser
import com.contusflysdk.views.CustomToast
import java.util.*

/**
 * This class is used to start the video call audio call activity. Here checking the audio and video
 * permissions are checked before make a call.And also check whether the user is blocked or not.
 */
class CallPermissionUtils(activity: Activity, isBlocked: Boolean, isAdminBlocked: Boolean, jidList: ArrayList<String>, groupId: String?, isCloseScreen: Boolean) : CommonDialogClosedListener {
    /**
     * Instance for the activity
     */
    private val activity: Context

    /**
     * Instance of isBlocked
     */
    private var isBlocked: Boolean

    /**
     * Instance of isAdminBlocked
     */
    private var isAdminBlocked: Boolean

    /**
     * JID
     */
    private val jidList: ArrayList<String>

    /**
     * Group ID
     */
    private val groupId: String?

    /**
     * Call making screen has to be closed or not
     */
    private val isCloseScreen: Boolean

    /**
     * The progress dialog to display the progress bar When the background operations has been doing
     */
    private var doProgressDialog: DoProgressDialog? = null

    /**
     * Check the jid has blocked or not. If JID has blocked then show the alert and call after unblock
     * the jid
     */
    fun audioCall() {
        if (!AppUtils.isNetConnected(activity)) {
            activity.showToast(getString(R.string.msg_no_internet))
        } else if (isBlocked) {
            showBlockedAlertAudioCall()
        } else if (isOnTelephonyCall(activity)) {
            showTelephonyCallAlert(activity)
        } else if ( /*LiveStreamUtils.isOnGoingLiveStream() ||*/isOnGoingAudioCall() || isOnGoingVideoCall()) {
            showOngoingCallAlert(activity)
        } else if (!isAdminBlocked) {
            makeVoiceCall()
        }
    }

    /**
     * Alert dialog whether to unblock or cancel to continue sending messages...
     */
    private fun showBlockedAlertAudioCall() {
        // The common alert dialog to display the alert dialogs in the alert view.
        val commonAlertDialog = CommonAlertDialog(activity)
        commonAlertDialog.setOnDialogCloseListener(this)
        commonAlertDialog.dialogAction = CommonAlertDialog.DialogAction.UNBLOCK
        commonAlertDialog.showAlertDialog(
            activity.getString(R.string.msg_unblockAudioCall),
            activity.getString(R.string.action_unblock), activity.getString(R.string.action_cancel),
            DIALOGTYPE.DIALOG_DUAL, false
        )
    }

    /**
     * Check the jid has blocked or not. If JID has blocked then show the alert and call after unblock
     * the jid
     */
    fun videoCall() {
        if (!AppUtils.isNetConnected(activity)) {
            activity.showToast(getString(R.string.msg_no_internet))
        } else if (isBlocked) {
            showBlockedAlertVideoCall()
        } else if (isOnTelephonyCall(activity)) {
            showTelephonyCallAlert(activity)
        } else if ( /*LiveStreamUtils.isOnGoingLiveStream() ||*/isOnGoingAudioCall() || isOnGoingVideoCall()) {
            showOngoingCallAlert(activity)
        } else if (!isAdminBlocked) {
            makeVideoCall()
        }
    }

    /**
     * Alert dialog whether to unblock or cancel to continue sending messages...
     */
    private fun showBlockedAlertVideoCall() {
        // The common alert dialog to display the alert dialogs in the alert view.
        val commonAlertDialog = CommonAlertDialog(activity)
        commonAlertDialog.setOnDialogCloseListener(this)
        commonAlertDialog.dialogAction = CommonAlertDialog.DialogAction.UNBLOCK
        commonAlertDialog.showAlertDialog(
            activity.getString(R.string.msg_unblockVideoCall),
            activity.getString(R.string.action_unblock),
            activity.getString(R.string.action_cancel),
            DIALOGTYPE.DIALOG_DUAL,
            false
        )
    }


    /**
     * This unblock method is start the chat service with the action of unblock
     */
    private fun unBlock() {
        if (AppUtils.isNetConnected(activity)) {
            doProgressDialog = DoProgressDialog(activity)
            doProgressDialog?.showProgress()
            unblockUser(jidList[0],
                FlyCallback { isSuccess: Boolean, _: Throwable?, _: HashMap<String?, Any?>? ->
                    if (isSuccess) {
                        isBlocked = false
                        if (GroupCallUtils.getIsCallStarted().equals(CallType.AUDIO_CALL)) {
                            audioCall()
                            GroupCallUtils.setIsCallStarted(null)
                        } else if (GroupCallUtils.getIsCallStarted().equals(CallType.VIDEO_CALL)) {
                            videoCall()
                            GroupCallUtils.setIsCallStarted(null)
                        }
                    }
                })
        } else CustomToast.show(activity, activity.getString(R.string.msg_no_internet))
    }

    /**
     * This method is start the audio call activity
     */
    @SuppressLint("MissingPermission")
    private fun makeVoiceCall() {
        if (!jidList.contains(SharedPreferenceManager.instance.currentUserJid)) {
            if (CallManager.isAudioCallPermissionsGranted()) {
                if (groupId != null && groupId.isNotEmpty()) {
                    makeGroupVoiceCall(jidList, groupId, object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeVoiceCall: $message")
                        }
                    })
                    closeScreen()
                } else if (jidList.size > 1) {
                    makeGroupVoiceCall(jidList, "", object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeGroupVoiceCall: $message")
                        }
                    })
                    closeScreen()
                } else {
                    makeVoiceCall(jidList[0], object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeVoiceCall: $message")
                        }
                    })
                }
            } else MediaPermissions.requestAudioCallPermissions(
                (activity as Activity),
                (activity as ComponentActivity).registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                    if (!permissions.containsValue(false)) {
                        makeVoiceCall()
                    }
                }
            )
        }
    }

    /**
     * This method is start the video call activity
     */
    @SuppressLint("MissingPermission")
    private fun makeVideoCall() {
        if (!jidList.contains(SharedPreferenceManager.instance.currentUserJid)) {
            if (CallManager.isVideoCallPermissionsGranted()) {
                if (groupId != null && groupId.isNotEmpty()) {
                    makeGroupVideoCall(jidList, groupId, object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeVideoCall: $message")
                        }
                    })
                    closeScreen()
                } else if (jidList.size > 1) {
                    makeGroupVideoCall(jidList, "", object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeVideoCall: $message")
                        }
                    })
                    closeScreen()
                } else {
                    makeVideoCall(jidList[0], object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            LogMessage.i(TAG, "$CALL_UI makeVideoCall: $message")
                        }
                    })
                }
            } else MediaPermissions.requestVideoCallPermissions(
                (activity as Activity),
                (activity as ComponentActivity).registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                    if (!permissions.containsValue(false)) {
                        makeVideoCall()
                    }
                }
            )
        }
    }

    private fun closeScreen() {
        if (isCloseScreen) (activity as Activity).finish()
    }

    /**
     * Calling this method to check the permission status
     *
     * @param context    Context of the activity
     * @param permission Permission to ask
     * @return boolean True if grand the permission
     */
    fun isPermissionAllowed(context: Context?, permission: String?): Boolean {
        val result = ContextCompat.checkSelfPermission(context!!, permission!!)
        return result == PackageManager.PERMISSION_GRANTED
    }

    /**
     * OnD Dialog closed
     *
     * @param dialogType the dialog type
     * @param isSuccess  the is success
     */
    override fun onDialogClosed(dialogType: DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            SharedPreferenceManager.instance.storeString("CALL_JID", jidList[0])
            unBlock()
            LogMessage.v("onDialogClosed", " $CALL_UI success")
            if (doProgressDialog != null) doProgressDialog!!.dismiss()
        } else {
            LogMessage.v("onDialogClosed", " $CALL_UI success")
        }
    }

    /**
     * Push all the Custom HyperLog values to server
     *
     * @param position the position
     */
    override fun listOptionSelected(position: Int) {
        // Implementation goes here.
    }

    companion object {
        private val TAG = CallPermissionUtils::class.java.simpleName
        /**
         * Alert dialog for user when he tries to make a call, while on another telephony call.
         */
        fun showTelephonyCallAlert(activity: Context) {
            // The common alert dialog to display the alert dialogs in the alert view.
            val commonAlertDialog = CommonAlertDialog(activity)
            commonAlertDialog.showAlertDialog(
                activity.getString(R.string.msg_telephony_call_alert),
                activity.getString(R.string.action_Ok),
                activity.getString(R.string.action_cancel), DIALOGTYPE.DIALOG_SINGLE, false
            )
        }

        /**
         * Alert dialog for user when he tries to make a call, while on another call.
         */
        fun showOngoingCallAlert(activity: Context) {
            // The common alert dialog to display the alert dialogs in the alert view.
            val commonAlertDialog = CommonAlertDialog(activity)
            commonAlertDialog.showAlertDialog(
                activity.getString(R.string.msg_ongoing_call_alert),
                activity.getString(R.string.action_Ok),
                activity.getString(R.string.action_cancel), DIALOGTYPE.DIALOG_SINGLE, false
            )
        }
    }

    init {
        this.activity = activity
        this.isBlocked = isBlocked
        this.isAdminBlocked = isAdminBlocked
        this.jidList = jidList
        this.groupId = groupId
        this.isCloseScreen = isCloseScreen
    }
}