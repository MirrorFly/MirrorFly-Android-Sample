package com.contusfly.activities

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.MenuItem
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.contus.call.utils.GroupCallUtils
import com.contus.flycommons.LogMessage
import com.contus.webrtc.api.CallActionListener
import com.contus.webrtc.api.CallManager
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.call.groupcall.isCallNotConnected
import com.contusfly.call.groupcall.isInComingCall
import com.contusfly.call.groupcall.isInPIPMode
import com.contusfly.chat.AndroidUtils
import com.contusfly.checkInternetAndExecute
import com.contusfly.constants.MobileApplication
import com.contusfly.showToast
import com.contusfly.utils.*
import com.contusflysdk.activities.FlyBaseActivity
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import java.util.*

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
open class BaseActivity : FlyBaseActivity() {

    /**
     * The broadcast receiver. To handle the actions from the service/activity
     */
    private lateinit var broadcastReceiver: BroadcastReceiver

    private var handler: Handler? = null

    private var otherUserHandler: Handler? = null

    private var adminBlockStatus = false

    private var adminBlockedOtherUserStatus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidUtils.calculateAndStoreDeviceWidth(this)
        handler = Handler(Looper.getMainLooper())
        otherUserHandler = Handler(Looper.getMainLooper())
        if (com.contusfly.AppLifecycleListener.isPinEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleBroadcastActions(intent)
            }
        }

        registerBroadcast()
    }

    override fun myProfileUpdated(isSuccess: Boolean) {
        super.myProfileUpdated(isSuccess)
        if (isSuccess) {
            ContactManager.getUserProfile(SharedPreferenceManager.getString(Constants.SENDER_USER_JID),
                fetchFromServer = false,
                saveAsFriend = false) { success, _, data ->
                if (success && data.isNotEmpty()) {
                    val profileDetails = data["data"] as ProfileDetails
                    SharedPreferenceManager.setString(Constants.USER_PROFILE_NAME, profileDetails.name)
                    SharedPreferenceManager.setString(Constants.USER_PROFILE_IMAGE, profileDetails.image)
                    var status = profileDetails.status
                    if (status.isNullOrEmpty()) status = getString(R.string.default_status)
                    SharedPreferenceManager.setString(Constants.USER_STATUS, status)
                }
            }
        }
    }

    /**
     * register broadcast to handle the chat services
     */
    private fun registerBroadcast() {
        val intentFilter = IntentFilter(Constants.FORWARDED_MESSAGE_ITEM)
        /*
         * Register receiver
         */
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * Here handle the following callbacks from the local broadcast receivers.
     *
     * FORWARDED_MESSAGE_ITEM.
     *
     * @param intent Intent from the broadcast actions
     */
    private fun handleBroadcastActions(intent: Intent) {
        try {
            val action = intent.action
            if (action != null) {
                when (action) {
                    Constants.FORWARDED_MESSAGE_ITEM -> clearActionMenu()
                    Constants.QUICK_SHARE_UPLOAD_RESPONSE -> notifyShareUploadStatus(intent)
                }
            }
        } catch (e: Exception) {
            LogMessage.e(e)
        }
    }

    /**
     * Update the upload status of a file
     *
     * @param intent intent that contains message and status of the uploaded file
     */
    override fun notifyShareUploadStatus(intent: Intent) {
        /*Implementation will be added in extended activity*/
    }

    override fun onDestroy() {
        super.onDestroy()
        /*
         * Unregister receiver
         */
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    /**
     * Callback for clearing of action Menu
     */
    protected open fun clearActionMenu() {
        /*Implementation will be added in extended activity*/
    }

    /**
     * Callback for update user details in Recent Chat
     */
    protected open fun userDetailsUpdated() {
        /*Implementation will be added in extended activity*/
    }

    override fun onBackPressed() {
        finish()
        super.onBackPressed()
    }

    override fun showOrUpdateOrCancelNotification(jid: String) {
        super.showOrUpdateOrCancelNotification(jid)
        if (FlyMessenger.getUnreadMessagesCount() <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val barNotifications: Array<StatusBarNotification> = notificationManager.activeNotifications
                for (notification in barNotifications) {
                    NotificationManagerCompat.from(applicationContext).cancel(notification.id)
                }
            } else
                NotificationManagerCompat.from(applicationContext).cancel(Constants.NOTIFICATION_ID)
        } else {
            if (ContactManager.getProfileDetails(jid)?.isMuted != true)
                NotificationUtils.createNotification(MobileApplication.getContext())
        }
    }

    override fun updateGroupReplyNotificationForArchivedSettingsEnabled() {
        super.updateGroupReplyNotificationForArchivedSettingsEnabled()
        NotificationUtils.createNotification(MobileApplication.getContext())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onLoggedOut() {
        SharedPreferenceManager.clearAllPreference()
        super.onLoggedOut()
    }

    override fun onConnected() {
        super.onConnected()
        checkInternetAndExecute(false) {
            FlyCore.getFriendsList(true) { isSuccess, _, _ ->
                if (isSuccess)
                    userDetailsUpdated()
            }
        }
    }

    override fun onAdminBlockedUser(jid: String, status: Boolean) {
        adminBlockStatus = status
        LogMessage.d(TAG, "#onAdminBlockedStatus == $adminBlockStatus")
        //To avoid multiple callbacks
        handler?.postDelayed({
            if (adminBlockStatus) {
                if (checkIsUserInCall()) {
                    CallManager.disconnectCall(object : CallActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) {
                            startShowStopperActivity()
                        }
                    })
                } else startShowStopperActivity()
            }
        }, 800)
    }

    private fun checkIsUserInCall(): Boolean {
        return (GroupCallUtils.isOnGoingAudioCall() || GroupCallUtils.isOnGoingVideoCall()) ||
                (GroupCallUtils.isCallNotConnected() && GroupCallUtils.isInComingCall()) || isInPIPMode()
    }

    private fun startShowStopperActivity() {
        val intent = Intent(this@BaseActivity, AdminBlockedActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("EXIT", true)
        startActivity(intent)
        finish()
    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        adminBlockedOtherUserStatus = status
        //To avoid multiple callbacks
        otherUserHandler?.postDelayed({
            if (GroupCallUtils.getGroupId() == jid && adminBlockedOtherUserStatus && checkIsUserInCall()) {
                CallManager.disconnectCall(object : CallActionListener {
                    override fun onResponse(isSuccess: Boolean, message: String) {
                        startDashboardActivity()
                    }
                })
            }
        }, 800)
    }

    /**
     * Launches a new Dashboard activity. If the activity being launched is already running in the current task,
     * then instead of launching a new instance of that activity, all of the other activities on top of it will be closed
     * and this Intent will be delivered to the (now on top) old activity as a new Intent.
     */
    private fun startDashboardActivity() {
        showToast(getString(R.string.group_block_message_label))
        val dashboardIntent = Intent(this@BaseActivity, DashboardActivity::class.java)
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        dashboardIntent.putExtra("EXIT", true)
        startActivity(dashboardIntent)
        finish()
    }
}