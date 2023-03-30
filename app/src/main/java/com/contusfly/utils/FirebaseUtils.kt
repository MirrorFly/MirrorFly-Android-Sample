package com.contusfly.utils

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.contusfly.constants.MobileApplication
import com.contusfly.notification.AppNotificationManager
import com.contusfly.notification.NotificationBuilder
import com.contusfly.notification.NotificationBuilderBelow24
import com.contusflysdk.api.ChatActionListener
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.api.notification.NotificationEventListener
import com.contusflysdk.api.notification.PushNotificationManager
import com.contusflysdk.media.MediaUploadHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class FirebaseUtils : CoroutineScope {

    /**
     * The notification data read from the Firebase.
     */
    private var notificationData: Map<String, String>? = null

    /**
     * Posts the refreshed token to the server to receive push notifications related to the
     * application package.
     */
    fun postRefreshedToken(s: String) {
        var firebaseToken = s
        if (firebaseToken.isEmpty()) {
            FirebaseInstallations.getInstance().getToken(true).addOnCompleteListener {
                firebaseToken = it.result!!.token
            }
        }
        SharedPreferenceManager.setString(Constants.FIRE_BASE_TOKEN, firebaseToken)
        PushNotificationManager.updateFcmToken(firebaseToken, object : ChatActionListener {
            override fun onResponse(isSuccess: Boolean, message: String) {
                if (isSuccess)
                    LogMessage.e(TAG, "Token updated successfully")
            }
        })
    }

    fun callImage(profileDetails: ProfileDetails?, context: Context) {
        var imgUrl = profileDetails?.image ?: ""
        imgUrl = Uri.parse(MediaUploadHelper.UPLOAD_ENDPOINT).buildUpon()
            .appendPath(Uri.parse(imgUrl).lastPathSegment).build().toString()
        NotificationBuilder.file = Glide.with(context).asFile().load(imgUrl).submit().get()
    }


    /**
     * Process the notification received via FCM.
     * @param context          Context of the service
     * @param firebaseData     The notification data read from the Firebase
     */
    fun handleReceivedMessage(context: Context, firebaseData: Map<String, String>?) {
        notificationData = firebaseData
        notificationData?.let {
            if (it.containsKey("push_from") && it["push_from"].equals("MirrorFly")) {
                PushNotificationManager.handleReceivedMessage(it, object : NotificationEventListener {
                    override fun onMessageReceived(chatMessage : ChatMessage) {
                        val userProfile: ProfileDetails? =
                            ProfileDetailsUtils.getProfileDetails(chatMessage.getSenderUserJid())
                        if (!userProfile?.image.isNullOrEmpty())
                            callImage(userProfile, context)
                        val messageType = Utils.returnEmptyStringIfNull(it[com.contus.flycommons.Constants.TYPE])
                        if ((it.containsKey("user_jid") && !ProfileDetailsUtils.getProfileDetails(it["user_jid"].toString())?.isMuted!!) ||
                            (messageType == com.contus.flycommons.Constants.RECALL)) {
                            AppNotificationManager.createNotification(MobileApplication.getContext(), chatMessage)
                        }
                    }

                    override fun onGroupNotification(groupJid: String, titleContent: String, chatMessage : ChatMessage) {
                        /* Create the notification for group creation with parameter values */
                        AppNotificationManager.createNotification(MobileApplication.getContext(), chatMessage)
                    }

                    @RequiresApi(Build.VERSION_CODES.M)
                    override fun onCancelNotification() {
                        AppNotificationManager.cancelNotifications(context)
                    }
                })
            }
        }
    }

    companion object {
        private val TAG = FirebaseUtils::class.java.simpleName

        /**
         * Set the analytics with the Unique id which will be shown in the Firebase console. We can
         * handle the multiple actions and views from the firebase instance.
         *
         * @param id          Id of the event
         * @param name        Name which will display in the console
         * @param contentType Firebase Analytic Type
         */
        @JvmStatic
        fun setAnalytics(id: String?, name: String?, contentType: String?) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id)
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, name)
            bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, contentType)
            FirebaseAnalytics.getInstance(MobileApplication.getContext()).logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + Job()

    fun onDestroy() {
        coroutineContext.cancel(CancellationException("$TAG Destroyed"))
    }
}