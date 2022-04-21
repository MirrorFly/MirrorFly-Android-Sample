package com.contusfly.utils

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.contus.flycommons.ChatTypeEnum
import com.contusfly.R
import com.contusfly.TAG
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ContactManager.getProfileDetails
import com.contusflysdk.api.models.ChatMessage
import java.util.*

/**
 * This Class contains all the common operations for creating and showing notification
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 2.0
 */
object NotifyRefererUtils {
    /**
     * To generate tone and vibration while receiving the message. The tone will played if user has
     * been selected ringtone for message where the option is available to select tone, vibration
     * while receive notification
     *
     * @param notificationCompatBuilder Instance of Notification builder
     */
    fun setNotificationSound(notificationCompatBuilder: NotificationCompat.Builder) {
        val notificationSoundUri = Uri.parse(SharedPreferenceManager.getString(Constants.NOTIFICATION_URI))

        if (!SharedPreferenceManager.getBoolean(Constants.NOTIFICATION_SOUND)) {
            notificationCompatBuilder.setSound(null)
        } else {
            if (notificationSoundUri != null) {
                notificationCompatBuilder.setSound(notificationSoundUri)
            } else {
                notificationCompatBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
        }

        if (SharedPreferenceManager.getBoolean(Constants.VIBRATION)) {
            notificationCompatBuilder.setVibrate(defaultVibrationPattern)
        } else {
            notificationCompatBuilder.setVibrate(null)
        }
    }

    /**
     * returns a default vibration pattern
     *
     * @return vibration pattern
     */
    val defaultVibrationPattern: LongArray
        get() {
            val vibrationTime = com.contus.flycommons.Constants.DEFAULT_VIBRATE
            return longArrayOf(vibrationTime, vibrationTime,
                vibrationTime, vibrationTime, vibrationTime)
        }
    /**
     * Returns the vibration pattern based on the user preferences.
     */
    /**
     * Returns group user appended text if the chat type is group.
     *
     * @param message        Unseen message
     * @param messageContent Notification line message content
     * @param delimiter      Delimiter to append in between
     * @return String Appended with group user
     */
    @JvmStatic
    fun getGroupUserAppendedText(message: ChatMessage, messageContent: String,
                                 delimiter: String): String {
        var appendedContent = messageContent
        if (ChatTypeEnum.groupchat == message.getMessageChatType()) {
            val groupUser = getProfileDetails(message.getChatUserJid())
            appendedContent = groupUser!!.name + delimiter + messageContent
        }
        return appendedContent
    }

    /**
     * Checks whether messages has single sender or not
     *
     * @param unseenMessages List of unread messages
     * @return boolean True: if single sender, false: if not
     */
    @JvmStatic
    fun hasMultipleSenders(unseenMessages: List<ChatMessage>): Boolean {
        val previousSender = unseenMessages[0].getChatUserJid()
        var i = 1
        val size = unseenMessages.size
        while (i < size) {
            if (previousSender != unseenMessages[i].getChatUserJid()) return true
            i++
        }
        return false
    }

    /**
     * Used to build notification channel for devices running on Oreo and above
     *
     * @param packageContext      Context
     * @param notificationManager instance of notification manager
     * @return
     */
    @TargetApi(Build.VERSION_CODES.O)
    fun buildNotificationChannel(packageContext: Context, notificationManager: NotificationManager?): String {
        if (SharedPreferenceManager.getBoolean(Constants.KEY_CHANGE_FLAG)) {
            SharedPreferenceManager.setBoolean(Constants.KEY_CHANGE_FLAG, false)
            deleteNotificationChannels(notificationManager)
        }
        val createdChannel: NotificationChannel
        val notificationSoundUri = Uri.parse(SharedPreferenceManager.getString(Constants.NOTIFICATION_URI))
        val isVibrate = SharedPreferenceManager.getBoolean(Constants.VIBRATION)
        val isRing = SharedPreferenceManager.getBoolean(Constants.NOTIFICATION_SOUND)
        val randomNumberGenerator = Random(System.currentTimeMillis())
        val channelName: CharSequence = packageContext.resources
                .getString(R.string.channel_name)
        val channelId = Integer.toString(randomNumberGenerator.nextInt())
        val cImportance = if (isVibrate) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
        val channelDescription = packageContext.resources.getString(R.string.channel_description)
        val channelImportance = if (isRing && !isLastMessageRecalled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
        deleteNotificationChannels(notificationManager)
        if (isRing) {
            val highPriorityChannel = NotificationChannel(channelId, channelName, channelImportance)
            highPriorityChannel.setShowBadge(true)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
            if (notificationSoundUri != null) {
                highPriorityChannel.setSound(notificationSoundUri, audioAttributes)
                highPriorityChannel.description = channelDescription
                highPriorityChannel.enableLights(true)
                highPriorityChannel.lightColor = Color.GREEN
                if (isVibrate) {
                    highPriorityChannel.vibrationPattern = defaultVibrationPattern
                } else {
                    highPriorityChannel.vibrationPattern = longArrayOf(0L, 0L, 0L, 0L, 0L)
                }
            } else {
                highPriorityChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes)
            }
            createdChannel = highPriorityChannel
        } else if (isVibrate) {
            val priorityChannel = NotificationChannel(channelId, channelName, cImportance)
            priorityChannel.setShowBadge(true)
            priorityChannel.description = channelDescription
            priorityChannel.enableLights(true)
            priorityChannel.lightColor = Color.GREEN
            priorityChannel.vibrationPattern = defaultVibrationPattern
            priorityChannel.shouldVibrate()
            priorityChannel.enableVibration(true)
            priorityChannel.setSound(null, null)
            createdChannel = priorityChannel
        } else {
            val lowPrioritychannel = NotificationChannel(channelId, channelName, channelImportance)
            lowPrioritychannel.description = channelDescription
            lowPrioritychannel.enableLights(true)
            lowPrioritychannel.lightColor = Color.GREEN
            createdChannel = lowPrioritychannel
        }
        if (notificationManager != null)
            notificationManager?.createNotificationChannel(createdChannel)
        SharedPreferenceManager.setString(Constants.KEY_CHANNEL_SINGLE_ID, createdChannel.id)
        return createdChannel.id
    }

    /**
     * Generates a list of notification channels associated with a notification manager
     *
     * @param mNotificationManager Instance of notification manager
     * @return list of notification channels
     */
    @TargetApi(Build.VERSION_CODES.O)
    fun deleteNotificationChannels(mNotificationManager: NotificationManager?) {
        try {
            val notificationChannelList: List<NotificationChannel>
            if (mNotificationManager != null) {
                notificationChannelList = mNotificationManager.notificationChannels
                for (notificationChannel in notificationChannelList)
                    if(!notificationChannel.name.equals("com.contusflysdk.MediaDownload_NotificationChannel")
                        && !notificationChannel.name.equals("Email Contacts operations")
                        && !notificationChannel.name.equals("Contact operations")
                        && !notificationChannel.id.equals("calling")
                        && !notificationChannel.id.equals("Mark read"))
                        mNotificationManager.deleteNotificationChannel(notificationChannel.id)
            }
        } catch (e: Exception) {
            LogMessage.e(TAG, e.message)
        }
    }

    private val isLastMessageRecalled: Boolean
        get() {
            return FlyMessenger.getLastUnreadMessage()?.isMessageRecalled() ?: false
        }
}