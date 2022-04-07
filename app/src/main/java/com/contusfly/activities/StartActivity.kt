package com.contusfly.activities

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.an.biometric.BiometricCallback
import com.an.biometric.BiometricManager
import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG
import com.contus.webrtc.utils.GroupCallUtils
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.AppLifecycleListener
import com.contusfly.R
import com.contusfly.getChatType
import com.contusfly.utils.*
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.FlyCore
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import com.bumptech.glide.Glide


class StartActivity : BaseActivity(), CoroutineScope, BiometricCallback {

    /**
     * instance of BiometricManager class
     */
    private var mBiometricManager: BiometricManager? = null

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userView()
    }

    private fun userView() {
        clearNotification()
        SharedPreferenceManager.setBoolean(Constants.PIN_SCREEN,false)
        if (intent.hasExtra(GroupCallUtils.IS_CALL_NOTIFICATION)) {
            val showCallsTab = intent.getBooleanExtra(GroupCallUtils.IS_CALL_NOTIFICATION, false)
            GroupCallUtils.setCallsTabToBeShown(showCallsTab)
        }
        if (SharedPreferenceManager.getBoolean(Constants.IS_LOGGED_IN)) {
            if (SharedPreferenceManager.getBoolean(Constants.IS_PROFILE_LOGGED)) {
                checkNotificationIntent(intent)
            } else {
                startActivity(Intent(this, ProfileStartActivity::class.java).putExtra(Constants.IS_FIRST_LOGIN, true)
                        .putExtra(Constants.FROM_SPLASH, true))
            }
        } else {
            startActivity(Intent(this, OtpActivity::class.java))
        }
        GlobalScope.launch {
            AppShortCuts.dynamicAppShortcuts(this@StartActivity)
        }
        finish()
    }

    private fun checkEnableSafeChat() {
        if (intent.getBooleanExtra(Constants.IS_FOR_SAFE_CHAT, false)){
            SafeChatUtils.changeSafeChatUpdateValue()
        }
        AppShortCuts.dynamicAppShortcuts(this)
    }

    /**
     * Checks the notification intent and loads ChatViewActivity if need.
     *
     * @param intent Notification intent
     */
    private fun checkNotificationIntent(intent: Intent) {
        checkEnableSafeChat()
        if (intent.hasExtra(LibConstants.JID)) {
            clearNotification()
            val jid = intent.getStringExtra(LibConstants.JID)
            if (!jid.isNullOrBlank()) {
                val profileDetail = FlyCore.getUserProfile(jid)
                if (profileDetail != null) {
                    val chatType = profileDetail.getChatType()
                    goToChatView(jid, chatType)
                } else goToDashboard()
            } else goToDashboard()
        }else if (intent.hasExtra(Constants.IS_FOR_SAFE_CHAT)){
            Log.d(TAG, getString(R.string.is_from_chat_shortcut))
            startActivities(
                TaskStackBuilder.create(this)
                    .addNextIntent(
                        Intent(this,DashboardActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(Constants.IS_FOR_SAFE_CHAT, true)
                    ).intents
            )
        }  else goToDashboard()
    }

    private fun goToDashboard() {
        if (AppLifecycleListener.backPressedSP) startActivity(Intent(this, DashboardActivity::class.java)) else {
            if (AppLifecycleListener.fromOnCreate && AppLifecycleListener.isPinEnabled) pinForDashBoard() else if (!AppLifecycleListener.isForeground && shouldShowPinOrNot()) {
                if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
                    SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
                    val intent = Intent(this@StartActivity, BiometricActivity::class.java)
                    intent.putExtra(GOTO, "DASHBOARD")
                    startActivity(intent)
                } else {
                    SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
                    pinForDashBoard()
                }
            } else if (AppLifecycleListener.pinActivityShowing)
                pinForDashBoard()
            else if (AppLifecycleListener.isForeground && AppLifecycleListener.isPinEnabled) pinForDashBoard() else startActivity(Intent(this, DashboardActivity::class.java)
                .putExtra("fromDashboard", true).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    private fun pinForDashBoard() {
        if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
            val intent = Intent(this@StartActivity, BiometricActivity::class.java)
            intent.putExtra(GOTO, "DASHBOARD")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ChatManager.applicationContext.startActivity(intent)
        } else {
            val intent = Intent(ChatManager.applicationContext, ChatManager.pinActivity)
            intent.putExtra(GOTO, "DASHBOARD")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ChatManager.applicationContext.startActivity(intent)
        }
    }

    private fun goToChatView(jid: String, chatType: String) {
        if (AppLifecycleListener.fromOnCreate && AppLifecycleListener.isPinEnabled)
            pinForChatOrGroup(jid, chatType)
        else if (!AppLifecycleListener.isForeground && shouldShowPinOrNot()) {
            if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
                SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
                val intent = Intent(this@StartActivity, BiometricActivity::class.java)
                    intent.putExtra(GOTO, "CHATVIEW")
                    intent.putExtra(LibConstants.JID, jid)
                    intent.putExtra(com.contus.flycommons.Constants.CHAT_TYPE, chatType)
                    startActivity(intent)
            } else{
                SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
                pinForChatOrGroup(jid, chatType)
            }
        } else if (AppLifecycleListener.pinActivityShowing) {
            SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
            pinForChatOrGroup(jid, chatType)
        }else if (intent.hasExtra(Constants.IS_FOR_SAFE_CHAT)){
            Log.d(TAG, getString(R.string.is_from_chat_shortcut))
            startActivities(
                TaskStackBuilder.create(this)
                    .addNextIntent(
                        Intent(this,DashboardActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(Constants.IS_FOR_SAFE_CHAT, true)
                    ).intents
            )
        }else {
            if (intent.hasExtra(Constants.IS_FROM_CHAT_SHORTCUT)) {
                Log.d(TAG, getString(R.string.is_from_chat_shortcut))
                startActivities(
                    TaskStackBuilder.create(this)
                        .addNextIntent(
                            Intent(this,DashboardActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .putExtra(Constants.IS_FROM_CHAT_SHORTCUT, chatType)
                        ).addNextIntent(
                            Intent(this, ChatActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .putExtra(LibConstants.JID, jid)
                                .putExtra(com.contus.flycommons.Constants.CHAT_TYPE, chatType)
                        )
                        .intents
                )
            } else {
                startActivities(
                    TaskStackBuilder.create(this)
                        .addNextIntent(
                            Intent(this, DashboardActivity::class.java).setAction(Intent.ACTION_VIEW)
                        )
                        .addNextIntent(
                            Intent(this, ChatActivity::class.java).setAction(Intent.ACTION_VIEW)
                                .putExtra(LibConstants.JID, jid)
                                .putExtra(com.contus.flycommons.Constants.CHAT_TYPE, chatType)
                        )
                        .intents
                )
            }
        }
    }

    private fun pinForChatOrGroup(jid: String?, chatType: String?) {
        if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
            val intent = Intent(this@StartActivity, BiometricActivity::class.java)
            intent.putExtra(GOTO, "CHATVIEW")
            intent.putExtra(LibConstants.JID, jid)
            intent.putExtra(com.contus.flycommons.Constants.CHAT_TYPE, chatType)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ChatManager.applicationContext.startActivity(intent)
        } else {
            val intent = Intent(ChatManager.applicationContext, ChatManager.pinActivity)
            intent.putExtra(GOTO, "CHATVIEW")
            intent.putExtra(LibConstants.JID, jid)
            intent.putExtra(com.contus.flycommons.Constants.CHAT_TYPE, chatType)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ChatManager.applicationContext.startActivity(intent)
        }
    }

    private fun shouldShowPinOrNot(): Boolean {
        LogMessage.d("StartActivity", AppLifecycleListener.shouldShowPinActivity().toString() + " shouldShowPinOrNot " + AppLifecycleListener.isPinEnabled)
        return AppLifecycleListener.shouldShowPinActivity() && AppLifecycleListener.isPinEnabled
    }

    private fun clearNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val barNotifications: Array<StatusBarNotification> = notificationManager.activeNotifications
            for (notification in barNotifications) {
                NotificationManagerCompat.from(applicationContext).cancel(notification.id)
            }
        } else
            NotificationManagerCompat.from(applicationContext).cancel(Constants.NOTIFICATION_ID)
    }

    override fun onSdkVersionNotSupported() {
        Toast.makeText(applicationContext, getString(R.string.biometric_error_sdk_not_supported), Toast.LENGTH_LONG).show()
    }

    override fun onBiometricAuthenticationNotSupported() {
        Toast.makeText(applicationContext, getString(R.string.biometric_error_hardware_not_supported), Toast.LENGTH_LONG).show()
    }

    override fun onBiometricAuthenticationPermissionNotGranted() {
        Toast.makeText(applicationContext, getString(R.string.biometric_error_permission_not_granted), Toast.LENGTH_LONG).show()
    }

    override fun onBiometricAuthenticationNotAvailable() {
        SharedPreferenceManager.setBoolean(Constants.BIOMETRIC,false)
        Toast.makeText(applicationContext, getString(R.string.biometric_error_fingerprint_not_available), Toast.LENGTH_LONG).show()
    }

    override fun onAuthenticationFailed() {
        Toast.makeText(applicationContext, getString(R.string.biometric_failure), Toast.LENGTH_LONG).show()
    }

    override fun onBiometricAuthenticationInternalError(error: String) {
        Toast.makeText(applicationContext, error, Toast.LENGTH_LONG).show()
    }

    override fun onAuthenticationCancelled() {
        Toast.makeText(applicationContext, getString(R.string.biometric_cancelled), Toast.LENGTH_LONG).show()
        mBiometricManager!!.cancelAuthentication()
    }

    override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence) {
        Toast.makeText(applicationContext, helpString, Toast.LENGTH_LONG).show()
    }

    override fun onAuthenticationSuccessful() {
        Toast.makeText(applicationContext, getString(R.string.biometric_success), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, DashboardActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        Toast.makeText(applicationContext, errString, Toast.LENGTH_LONG).show()
    }

    companion object {
        private val TAG = StartActivity::class.java.simpleName
        private const val GOTO = Constants.GO_TO
    }
}
