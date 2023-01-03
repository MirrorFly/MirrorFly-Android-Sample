package com.contusfly.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.contus.flycommons.ChatTypeEnum
import com.contus.flycommons.LogMessage
import com.contus.flycommons.models.MessageType
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.getAppName
import com.contusflysdk.api.ChatManager.startActivity
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ContactManager.getProfileDetails
import com.contusflysdk.api.models.ChatMessage

/**
 * This Class is used to handle the pushnotification related operation
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 1.0
 */
object NotificationUtils {
    private var mutecheckJid = Constants.EMPTY_STRING

    /**
     * Creates local notification when the app is in foreground for the incoming messages.
     *
     * @param context Instance of Context
     */
    fun createNotification(context: Context) {
        /**
         * if the user enables mute notif in settings, we should not show any notification
         */
        if (SharedPreferenceManager.getBoolean(Constants.MUTE_NOTIFICATION))
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val notificationUtil = NotificationUtil(context)
            notificationUtil.createNotification()
        } else {
            try {
                val notificationIntent: Intent
                if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
                    val notificationManager = context
                            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val notBuilder = NotificationCompat.Builder(context,
                            Constants.NOTIFICATION_CHANNEL_ID)
                    notBuilder.setSmallIcon(R.mipmap.ic_launcher)
                    notBuilder.color = Color.TRANSPARENT
                    notBuilder.setLargeIcon(BitmapFactory.decodeResource(context.resources,
                            R.mipmap.ic_launcher))
                    notBuilder.color = ContextCompat.getColor(context, R.color.colorPrimary)
                    notBuilder.setOnlyAlertOnce(true)
                    val jid: String = buildNotificationContent(context, notBuilder)
                    val isMute = getProfileMuteDetails(jid)
                    notBuilder.setAutoCancel(true)
                    NotifyRefererUtils.setNotificationSound(notBuilder)
                    notificationIntent = Intent(context, startActivity)
                    notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
                    if (!TextUtils.isEmpty(jid)) notificationIntent.putExtra(Constants.JID, jid)
                    val pendingIntent = PendingIntent.getActivity(context,
                            Constants.NOTIFICATION_ID, notificationIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT)
                    notBuilder.setContentIntent(pendingIntent)
                    NotifyRefererUtils.setNotificationSound(notBuilder)
                    if (!SharedPreferenceManager.getBoolean(Constants.MUTE_ALL_CONVERSATION) && !isMute) {
                        notificationManager.notify(Constants.NOTIFICATION_ID, notBuilder.build())
                    }
                }
            } catch (e: Exception) {
                LogMessage.e(e)
            }
        }
    }

    private fun buildNotificationContent(
        context: Context,
        notBuilder: NotificationCompat.Builder
    ): String {
        return if (BuildConfig.HIPAA_COMPLIANCE_ENABLED) addSecureNotification(context, notBuilder) else addUnseenMessagesToNotification(context, notBuilder)
    }

    private fun getProfileMuteDetails(jid: String): Boolean {
        if (!TextUtils.isEmpty(jid)) {
            return getProfileDetails(jid)!!.isMuted
        } else if (!TextUtils.isEmpty(mutecheckJid)) {
            return getProfileDetails(mutecheckJid)!!.isMuted
        }
        return false
    }

    private fun addSecureNotification(context: Context,
                                      notBuilder: NotificationCompat.Builder) : String {
        when (FlyMessenger.getLastUnreadMessage()) {
            null -> Constants.EMPTY_STRING
            else -> {
                val name = context.getAppName()
                notBuilder.setContentText("New Message")
                notBuilder.setDefaults(Notification.DEFAULT_SOUND)
                notBuilder.setContentTitle(NotificationUtil(context).getSummaryTitle(name, FlyMessenger.getUnreadMessageCountExceptMutedChat(), FlyMessenger.getUnreadMessagesCount()))
            }
        }
        return Constants.EMPTY_STRING
    }
    /**
     * Adds unseen messages to the notification
     *
     * @param context    Instance of Context
     * @param notBuilder Instance of Notification builder
     * @return String Jid of the user if single sender
     */
    private fun addUnseenMessagesToNotification(context: Context,
                                                notBuilder: NotificationCompat.Builder): String {
        val unseenMessage = FlyMessenger.getLastUnreadMessage()
        return when {
            unseenMessage == null -> Constants.EMPTY_STRING
            FlyMessenger.getUnreadMessagesCount() > 1 -> {
                mutecheckJid = unseenMessage.getChatUserJid()
                GetMsgNotificationUtils.getMessagesInboxNotification(context, notBuilder,
                    FlyMessenger.getLastNUnreadMessages(1000))
            }
            else -> {
                var messageContent = getMessageContent(unseenMessage)
                messageContent = getGroupUserAppendedText(unseenMessage, messageContent, ":")
                notBuilder.setContentText(messageContent)
                val toUser = unseenMessage.getChatUserJid()
                val profileDetails = getProfileDetails(toUser)
                if (profileDetails != null && !profileDetails.isMuted) notBuilder.setDefaults(Notification.DEFAULT_SOUND)
                notBuilder.setContentTitle(profileDetails?.name)
                unseenMessage.getChatUserJid()
            }
        }
    }

    /**
     * Returns the message summary
     *
     * @param message Instance of message
     * @return String Summary of the message
     */
    private fun getMessageContent(message: ChatMessage): String {
        return if (MessageType.TEXT == message.getMessageType()) message.getMessageTextContent() else message.getMessageType().name.toUpperCase()
    }

    /**
     * Returns group user appended text if the chat type is group.
     *
     * @param message        Unseen message
     * @param messageContent Notification line message content
     * @param delimiter      Delimiter to append in between
     * @return String Appended with group user
     */
    private fun getGroupUserAppendedText(message: ChatMessage, messageContent: String,
                                         delimiter: String): String {
        var appendedContent = messageContent
        if (ChatTypeEnum.groupchat == message.getMessageChatType()) {
            val groupUser = ContactManager.getProfileDetails(message.getChatUserJid())
            appendedContent = groupUser?.name + delimiter + messageContent
        }
        return appendedContent
    }

}