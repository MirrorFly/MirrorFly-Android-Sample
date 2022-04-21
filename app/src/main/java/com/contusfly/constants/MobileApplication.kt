package com.contusfly.constants

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.provider.FontRequest
import androidx.emoji.text.EmojiCompat
import androidx.emoji.text.FontRequestEmojiCompatConfig
import androidx.lifecycle.ProcessLifecycleOwner
import com.contus.flycommons.SharedPreferenceManager
import com.contus.webrtc.CallType
import com.contus.webrtc.GroupCallDetails
import com.contus.webrtc.Logger
import com.contus.webrtc.WebRtcCallService
import com.contus.webrtc.api.CallHelper
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.MissedCallListener
import com.contus.call.utils.GroupCallUtils
import com.contusfly.AppLifecycleListener
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.activities.BiometricActivity
import com.contusfly.activities.PinActivity
import com.contusfly.activities.StartActivity
import com.contusfly.call.CallConfiguration
import com.contusfly.call.CallNotificationUtils
import com.contusfly.call.WebRtcUtils
import com.contusfly.call.groupcall.GroupCallActivity
import com.contusfly.di.components.DaggerAppComponent
import com.contusfly.utils.*
import com.contusflysdk.ChatSDK
import com.contusflysdk.GroupConfig
import com.contusflysdk.api.CallMessenger
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.MediaNotificationHelper
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.utils.NameHelper
import com.facebook.stetho.Stetho
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class MobileApplication : Application(), HasAndroidInjector {

    @Inject
    lateinit var activityInjector: DispatchingAndroidInjector<Any>

    init {
        instance = this
    }

    companion object {
        private var instance: MobileApplication? = null

        fun getContext(): Context {
            return instance!!.applicationContext
        }
    }
    private var defaultUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        // For chat log
        LogMessage.enableDebugLogging(BuildConfig.DEBUG)
        DaggerAppComponent.builder()
                .application(this)
                .sdkComponent(ChatManager.sdkComponent)
                .build().inject(this)
        FirebaseApp.initializeApp(this)
        Stetho.initializeWithDefaults(this)

        //before setting our own exception handler, taking a backup of default handler so
        // that we don't break any exception handlers/loggers like crashlytics
        defaultUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Logger.e(e.stackTraceToString())
            defaultUncaughtHandler?.uncaughtException(t, e)
        }


        val groupConfiguration = GroupConfig.Builder()
                .enableGroupCreation(true)
                .setMaximumMembersInAGroup(250)
                .onlyAdminCanAddOrRemoveMembers(true)
                .build()

        ChatSDK.Builder()
            .useProfileName(true)
            .setIsTrialLicenceKey(BuildConfig.IS_TRIAL_LICENSE)
            .setDomainBaseUrl(BuildConfig.SDK_BASE_URL)
            .enableMobileNumberLogin(true)
            .setGroupConfiguration(groupConfiguration)
            .setMediaFolderName(Constants.LOCAL_PATH)
            .setLicenseKey(BuildConfig.LICENSE)
            .setIVKey(BuildConfig.IV_KEY)
            .build()

        //activity to open when use clicked from notification
        //activity to open when a user logout from the app.
        ChatManager.startActivity = StartActivity::class.java

        ChatManager.pinActivity = PinActivity::class.java

        ChatManager.biometricActivty = BiometricActivity::class.java

        ChatManager.setMediaNotificationHelper(object : MediaNotificationHelper {
            override fun setMediaNotificationIntentAction(
                notificationCompatBuilder: NotificationCompat.Builder, jidList: List<String>) {
                notificationCompatBuilder.setContentIntent(getPendingIntent(jidList))
            }
        })

        //register observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener())

        initEmojiCompat()

        //Set Name based on the Profile data
        GroupManager.setNameHelper(object  : NameHelper {
            override fun getDisplayName(jid: String): String {
                return if (ContactManager.getProfileDetails(jid) != null) ContactManager.getProfileDetails(jid)!!.name else Constants.EMPTY_STRING
            }
        })

        //initialize call sdk
        initialsizeCallSdk()
        Logger.enableDebugLogging(true)
        setupFirebaseRemoteConfig()
    }

    private fun setupFirebaseRemoteConfig() {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 14400
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    LogMessage.d(TAG, "Config params updated: $updated")
                } else {
                    LogMessage.d(TAG, "Config params Fetch failed")
                }
                CallConfiguration.setIsGroupCallEnabled(remoteConfig.getBoolean("is_group_call_enabled"))
            }
    }

    private fun initialsizeCallSdk(){
        CallManager.init(this)
        CallManager.setCurrentUserId(SharedPreferenceManager.instance.currentUserJid)
        CallManager.setSignalServerUrl(BuildConfig.SIGNAL_SERVER)
        CallManager.setJanusWebSocketServerUrl(BuildConfig.JANUS_WEB_SOCKET_SERVER)
        CallManager.setCallActivityClass(GroupCallActivity::class.java)
        CallManager.setIceServers(WebRtcUtils.getTempIceServers())
        CallManager.setMissedCallListener(object : MissedCallListener {
            override fun onMissedCall(
                isOneToOneCall: Boolean, userJid: String, groupId: String?, callType: String,
                userList: ArrayList<String>
            ) {
                //show missed call notification
                com.contus.flycommons.LogMessage.d(TAG, "onMissedCall")
                val missedCallMessage = StringBuilder()
                missedCallMessage.append(resources.getString(R.string.you_missed_call))
                val messageContent: String
                if (isOneToOneCall && groupId.isNullOrEmpty()) {
                    if (callType == CallType.AUDIO_CALL) {
                        missedCallMessage.append("an ")
                    } else {
                        missedCallMessage.append("a ")
                    }
                    missedCallMessage.append(callType).append(" call")
                    messageContent = ContactManager.getProfileDetails(userJid)?.name!!
                } else {
                    missedCallMessage.append("a group ").append(callType).append(" call")
                    messageContent = if (!groupId.isNullOrBlank()) {
                        ContactManager.getProfileDetails(groupId)?.name!!
                    } else {
                        GroupCallUtils.getCallUsersName(userList).toString()
                    }
                }

                CallNotificationUtils.createNotification(
                    getContext(),
                    missedCallMessage.toString(),
                    messageContent
                )
            }
        })

        CallManager.setCallHelper(object : CallHelper {
            override fun getDisplayName(jid: String): String {
                return if (ContactManager.getProfileDetails(jid) != null) ContactManager.getProfileDetails(jid)!!.name else Constants.EMPTY_STRING
            }

            override fun sendCallMessage(details: GroupCallDetails, users: List<String>, invitedUsers: List<String>) {
                CallMessenger.sendCallMessage(details, users, invitedUsers)
            }
        })
        ChatManager.callService = WebRtcCallService::class.java
    }

    private fun getPendingIntent(toUsers: List<String>): PendingIntent? {
        val notificationIntent = Intent(this, ChatManager.startActivity)
        notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
        notificationIntent.putExtra(Constants.JID, if (toUsers.count() == 1) toUsers.elementAt(0) else Constants.EMPTY_STRING)
        val requestID = System.currentTimeMillis().toInt()
        return PendingIntent.getActivity(this, requestID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun androidInjector(): AndroidInjector<Any> {
        return activityInjector
    }

    private fun initEmojiCompat() {
        val config: EmojiCompat.Config

        // Use a downloadable font for EmojiCompat
        val fontRequest = FontRequest(
                "com.google.android.gms.fonts",
                "com.google.android.gms",
                "Noto Color Emoji Compat", R.array.com_google_android_gms_fonts_certs)
        config = FontRequestEmojiCompatConfig(applicationContext, fontRequest)
        config.setReplaceAll(true)
                .registerInitCallback(object : EmojiCompat.InitCallback() {
                    override fun onInitialized() {
                        Log.i(TAG, "EmojiCompat initialized")
                    }

                    override fun onFailed(throwable: Throwable?) {
                        Log.e(TAG, "EmojiCompat initialization failed", throwable)
                    }
                })
        EmojiCompat.init(config)
    }
}