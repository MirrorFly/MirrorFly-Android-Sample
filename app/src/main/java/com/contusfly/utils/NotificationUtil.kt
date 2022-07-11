package com.contusfly.utils


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.contus.flycommons.PendingIntentHelper
import com.contus.flycommons.models.MessageType
import com.contusfly.R
import com.contusfly.BuildConfig
import com.contusfly.getAppName
import com.contusfly.views.SetDrawable
import com.contusflysdk.api.ChatManager.startActivity
import com.contusflysdk.api.FlyMessenger
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ContactManager.getProfileDetails
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.media.MediaUploadHelper
import kotlin.collections.ArrayList

/**
 * This class is used to create notification for unread messages to notify the events in app
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 2.0
 */
class NotificationUtil(private val context: Context) {
    /**
     * Instance of the NotificationManagerCompat
     */
    private val mNotificationManagerCompat: NotificationManagerCompat = NotificationManagerCompat.from(context)

    /**
     * to populate the list of notification Ids
     */
    private val notificationIds: MutableList<Int>

    /**
     * to populate the list of notificationInlineMessages
     */
    private val notificationInlineMessages: MutableList<String>

    /**
     * to populate the notifications
     */
    private val notifications: MutableList<Notification>
    private var bitmapUserProfile: Bitmap? = null

    //  A unique channel ID used to construct the NotificationChannel object.
    private var notificationChannelId: String? = null

    /**
     * to get the count of unread messages
     */
    private var unreadMessages = 0

    private val notificationMessage = " message): "
    private val notificationMessages = " messages): "

    /**
     * to get the count of unreadChats
     */
    private var unreadChats = 0
    private var profileDetails: ProfileDetails? = null
    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val bitmap: Bitmap = if (drawable!!.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // Single color bitmap will be created of 1x1 pixel
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Creates a notification for the unread messages to notify the events in your app while it's not in use..
     */
    fun createNotification() {
        if (BuildConfig.HIPAA_COMPLIANCE_ENABLED){
            unreadMessages = FlyMessenger.getUnreadMessageCountExceptMutedChat()
            unreadChats = FlyMessenger.getUnreadMessagesCount()
            notificationIds.clear()
            createSecuredNotification(FlyMessenger.getLastUnreadMessage())
        } else {
            val notificationMessages = FlyMessenger.getNUnreadMessagesOfEachUsers(100)
            unreadChats = notificationMessages.size
            unreadMessages = 0
            notificationIds.clear()
            for (jid in notificationMessages.keys) {
                profileDetails = getProfileDetails(jid)
                if (profileDetails != null) {
                    if (!profileDetails!!.isMuted)
                        createMessagingStyleNotification(jid, notificationMessages[jid]!!)
                } else createInboxStyleNotification(jid, notificationMessages[jid]!!)
            }
        }
        if (notificationIds.isNotEmpty()) displaySummaryNotification()
    }

    /**
     * Creates a INBOX_STYLE Notification for the devices starting on API level 16, otherwise, displays a basic notification.
     *
     * @param chatJid The jabber id of the corresponding chat for which the notification message is received.
     */
    private fun createInboxStyleNotification(chatJid: String, messages: List<ChatMessage>) {
        notificationIds.add(chatJid.hashCode().toLong().toInt())
        val profileDetails = ContactManager.getProfileDetails(chatJid)
        var title = profileDetails?.name
        unreadMessages += messages.size
        val inboxStyle = NotificationCompat.InboxStyle()
        if (messages.size == 1) inboxStyle.setBigContentTitle(title).setSummaryText("1 new message") else {
            val appendMessagesLabel = " " + context.getString(R.string.messages_label) + ")"
            val titleBuilder = StringBuilder()
            titleBuilder.append(title).append(" (").append(messages.size).append(appendMessagesLabel)
            inboxStyle.setBigContentTitle(titleBuilder)
            title = titleBuilder.toString()
        }
        val lastMessage = generateNotificationBasedOnMessageType(messages, inboxStyle)
        val notificationIntent = Intent(context, startActivity)
        notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
        if (!TextUtils.isEmpty(chatJid)) notificationIntent.putExtra(Constants.JID, chatJid)

        // Gets a PendingIntent containing the entire back stack.
        val mainPendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        // Because we want this to be a new notification (not updating a previous notification), we create a new Builder.
        // However, we don't need to update this notification later,
        // so we will not need to set a global builder for access to the notification later.
        val notificationCompatBuilder = NotificationCompat.Builder(context, notificationChannelId!!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationChannelId != null) notificationCompatBuilder.setChannelId(notificationChannelId!!)
        } else NotifyRefererUtils.setNotificationSound(notificationCompatBuilder)
        notificationCompatBuilder
                .setStyle(inboxStyle)
                .setContentTitle(title)
                .setContentText(lastMessage)
                .setAutoCancel(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.common_full_open_on_phone))
                .setContentIntent(mainPendingIntent)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary)) // Adds the notification to the group, sharing the specified key.
                .setGroup(GROUP_KEY_MESSAGE)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        notificationCompatBuilder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val inlineMessageBuilder = title + " (" + messages.size + (if (messages.size == 1) notificationMessage else notificationMessages) + lastMessage
        notificationInlineMessages.add(inlineMessageBuilder)
        notifications.add(notificationCompatBuilder.build())
    }

    /**
     * Used to generate notification
     *
     * @param messages   Unseen messages
     * @param inboxStyle Notification style
     * @return last message
     */
    private fun generateNotificationBasedOnMessageType(messages: List<ChatMessage>, inboxStyle: NotificationCompat.InboxStyle): String? {
        var contentBuilder: StringBuilder? = null
        for (i in (if (messages.size < 7) 0 else messages.size - 6) until messages.size) {
            val message = messages[i]
            contentBuilder = StringBuilder()
            when (message.messageType) {
                MessageType.TEXT -> {
                    contentBuilder.append(message.getMessageTextContent())
                    inboxStyle.addLine(contentBuilder)
                }
                MessageType.IMAGE -> {
                    contentBuilder.append(if (message.getMediaChatMessage().getMediaCaptionText() != null
                            && message.getMediaChatMessage().getMediaCaptionText().isNotEmpty()) message.getMediaChatMessage().getMediaCaptionText() else "Image")
                    inboxStyle.addLine(contentBuilder)
                }
                else -> {
                    /*No Implementation Needed*/
                }
            }
        }
        return contentBuilder?.toString()
    }

    /**
     * Creates a MESSAGING_STYLE Notification for the devices starting on API level 24, otherwise, displays a basic BIG_TEXT_STYLE notification.
     *
     * @param chatJid The jabber id of the corresponding chat for which the notification message is received.
     */
    private fun createMessagingStyleNotification(chatJid: String, messages: List<ChatMessage>) {
        profileDetails = getProfileDetails(chatJid)
        var title = profileDetails?.name
        notificationIds.add(chatJid.hashCode().toLong().toInt())
        unreadMessages += messages.size
        var lastMessage: String? = null
        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder()
                .setName("Me").build())
        var name: String
        var lastMessageTime: Long = 0
        for (message in messages.asReversed().takeLast(10)) {
            val contentBuilder = StringBuilder()
            val userProfile: ProfileDetails? = getProfileDetails(message.getSenderUserJid())
            name = userProfile?.name ?: Constants.EMPTY_STRING
            contentBuilder.append(GetMsgNotificationUtils.getMessageSummary(context, message))
            notificationMessageStyle(profileDetails, messagingStyle, contentBuilder, message, name)
            lastMessage = contentBuilder.toString()
            lastMessageTime = if (message.getMessageSentTime().toString().length > 13) message.getMessageSentTime() / 1000 else message.getMessageSentTime()
        }
        setGroupConversationMessagingStyle(messages, title, messagingStyle)
        val notificationIntent = Intent(context, startActivity)
        notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
        if (!TextUtils.isEmpty(chatJid)) notificationIntent.putExtra(Constants.JID, chatJid)
        val requestID = System.currentTimeMillis().toInt()
        val mainPendingIntent = PendingIntent.getActivity(context, requestID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationCompatBuilder = NotificationCompat.Builder(context, notificationChannelId!!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationChannelId != null) notificationCompatBuilder.setChannelId(notificationChannelId!!)
        } else NotifyRefererUtils.setNotificationSound(notificationCompatBuilder)
        notificationCompatBuilder
                .setStyle(messagingStyle)
                .setContentTitle(title)
                .setContentText(lastMessage ?: "")
                .setAutoCancel(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(mainPendingIntent) // Adds the notification to the group sharing the specified key.
                .setGroup(GROUP_KEY_MESSAGE)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY) //.setSubText(String.valueOf("Subtext"))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (lastMessageTime > 0) notificationCompatBuilder.setWhen(lastMessageTime)

        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
        notificationCompatBuilder.setLargeIcon(getUserProfileImage(profileDetails))
        if (SharedPreferenceManager.getBoolean(Constants.VIBRATION)) {
            notificationCompatBuilder.setVibrate(NotifyRefererUtils.defaultVibrationPattern)
        } else {
            notificationCompatBuilder.setVibrate(null)
        }
        val inlineMessageBuilder = title + " (" + messages.size + (if (messages.size == 1) notificationMessage else notificationMessages) + lastMessage
        notificationInlineMessages.add(inlineMessageBuilder)
        notifications.add(notificationCompatBuilder.build())
    }

    private fun notificationMessageStyle(
        profileDetails: ProfileDetails?,
        messagingStyle: NotificationCompat.MessagingStyle,
        contentBuilder: StringBuilder,
        message: ChatMessage,
        name: String
    ) {
        val userProfileImage = getUserProfileImage(profileDetails)
        if (profileDetails != null && userProfileImage != null) {
            messagingStyle.addMessage(contentBuilder, message.getMessageSentTime(),
                Person.Builder().setName(name)
                    .setIcon(IconCompat.createWithBitmap(userProfileImage)).build())
        } else {
            messagingStyle.addMessage(contentBuilder, message.getMessageSentTime(),
                Person.Builder().setName(name)
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher)).build())
        }
    }

    private fun setGroupConversationMessagingStyle(messages: List<ChatMessage>, title: String?, messagingStyle: NotificationCompat.MessagingStyle) {
        if (messages.size > 1) {
            val appendMessagesLabel = " " + context.getString(R.string.messages_label) + ")"
            val modifiedTitle: String
            if (profileDetails!!.isGroupProfile) {
                modifiedTitle = title + " (" + messages.size + appendMessagesLabel
                messagingStyle.setGroupConversation(true).conversationTitle = modifiedTitle
            } else if (unreadChats <= 1) {
                modifiedTitle = " (" + messages.size + appendMessagesLabel
                messagingStyle.conversationTitle = modifiedTitle
            }
        } else if (profileDetails!!.isGroupProfile) messagingStyle.setGroupConversation(true).conversationTitle = title
    }

    /**
     * To create a group of notification
     */
    private fun displaySummaryNotification() {
        val appTitle = context.resources.getString(R.string.title_app_name)
        val summaryBuilder = NotificationCompat.Builder(context, notificationChannelId!!)
                .setContentTitle(appTitle)
                .setContentText(notificationInlineMessages[notificationInlineMessages.size - 1])
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(summaryPendingIntent)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                .setAutoCancel(true)
        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(appTitle)
        val summaryText = StringBuilder()
        summaryText.append(unreadMessages).append(" messages from ").append(unreadChats).append(" chats")
        inboxStyle.setSummaryText(summaryText)
        for (notificationInlineMessage in notificationInlineMessages) inboxStyle.addLine("notificationInlineMessage")
        summaryBuilder.setStyle(inboxStyle)
                .setGroup(GROUP_KEY_MESSAGE)
                .setGroupSummary(true)
                .build()
        for (i in notificationIds.indices) {
            mNotificationManagerCompat.notify(notificationIds[i], notifications[i])
        }
        if (unreadChats > 0)
            mNotificationManagerCompat.notify(SUMMARY_ID, summaryBuilder.build())
    }

    private val summaryPendingIntent: PendingIntent
        get() {
            val notificationIntent = Intent(context, startActivity)
            notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
            notificationIntent.putExtra(Constants.JID, "")
            val requestID = System.currentTimeMillis().toInt()
            return PendingIntent.getActivity(context, requestID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /**
     * Creates a MESSAGING_STYLE Notification for the devices starting on API level 24, otherwise, displays a basic BIG_TEXT_STYLE notification.
     *
     * @param message The most recent received message
     */
    private fun createSecuredNotification(message: ChatMessage?) {
        if (message == null)
            return
        val name = context.getAppName()
        notificationIds.add(message.chatUserJid.hashCode().toLong().toInt())
        val lastMessage: String
        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder()
            .setName("Me").build())
        var lastMessageTime: Long = 0
        val contentBuilder = StringBuilder()
        contentBuilder.append("New Message")
        secureNotificationMessageStyle(messagingStyle, contentBuilder, message, getSummaryTitle(name, unreadMessages, unreadChats))
        lastMessage = contentBuilder.toString()
        lastMessageTime = if (message.getMessageSentTime().toString().length > 13) message.getMessageSentTime() / 1000 else message.getMessageSentTime()
        val notificationIntent = Intent(context, startActivity)
        notificationIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TASK
                or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notificationIntent.putExtra(Constants.IS_FROM_NOTIFICATION, true)
        notificationIntent.putExtra(Constants.JID, "")
        val requestID = System.currentTimeMillis().toInt()
        val mainPendingIntent = PendingIntentHelper.getActivity(context, requestID, notificationIntent)
        val notificationCompatBuilder = NotificationCompat.Builder(context, notificationChannelId!!)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationChannelId != null) notificationCompatBuilder.setChannelId(notificationChannelId!!)
        } else NotifyRefererUtils.setNotificationSound(notificationCompatBuilder)
        notificationCompatBuilder
            .setStyle(messagingStyle)
            .setContentTitle(name)
            .setContentText(lastMessage)
            .setAutoCancel(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent) // Adds the notification to the group sharing the specified key.
            .setGroup(GROUP_KEY_MESSAGE)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY) //.setSubText(String.valueOf("Subtext"))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (lastMessageTime > 0) notificationCompatBuilder.setWhen(lastMessageTime)

        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
        if (SharedPreferenceManager.getBoolean(Constants.VIBRATION)) {
            notificationCompatBuilder.setVibrate(NotifyRefererUtils.defaultVibrationPattern)
        } else {
            notificationCompatBuilder.setVibrate(null)
        }
        val inlineMessageBuilder = name + " (" + unreadMessages + (if (unreadMessages == 1) notificationMessage else notificationMessages) + lastMessage
        notificationInlineMessages.add(inlineMessageBuilder)
        notifications.add(notificationCompatBuilder.build())
    }

    fun getSummaryTitle(name : String, unreadMessageCount: Int, unreadChatCount: Int): String {
        var summary = name
        summary = if (unreadMessageCount == 1)
            "$summary ($unreadMessageCount message"
        else
            "$summary ($unreadMessageCount messages"
        summary = if (unreadChatCount == 1)
            "$summary)"
        else
            "$summary from $unreadChatCount chats)"
        return summary
    }

    private fun secureNotificationMessageStyle(
        messagingStyle: NotificationCompat.MessagingStyle,
        contentBuilder: StringBuilder,
        message: ChatMessage,
        name: String
    ) {
        messagingStyle.addMessage(contentBuilder, message.getMessageSentTime(),
            Person.Builder()
                .setName(name)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .build())
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getUserProfileImage(profileDetails: ProfileDetails?): Bitmap? {
        var imgUrl = profileDetails?.image ?: ""
        try {
            if (imgUrl.isNotEmpty()) {
                imgUrl = Uri.parse(MediaUploadHelper.UPLOAD_ENDPOINT).buildUpon().appendPath(Uri.parse(imgUrl).lastPathSegment).build().toString()
                val file = Glide.with(context).asFile().load(imgUrl).submit().get()
                bitmapUserProfile = getCircularBitmap(BitmapFactory.decodeFile(file.absolutePath))
            } else {
                // Load Default icon based on name
                bitmapUserProfile = if (profileDetails != null && profileDetails.isGroupProfile) {
                    drawableToBitmap(context.getDrawable(R.drawable.ic_group_avatar))
                } else {
                    getCircularBitmap(drawableToBitmap(setDrawable(profileDetails)))
                }
            }
        } catch (e: Exception) {
            Log.d("qwerty", "Inside getProfileImage Exception ===> $e")
        }
        return bitmapUserProfile
    }

    private fun getCircularBitmap(bitmap: Bitmap?): Bitmap {
        val output: Bitmap = if (bitmap!!.width > bitmap.height) {
            Bitmap.createBitmap(bitmap.height, bitmap.height, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val r: Int = if (bitmap.width > bitmap.height) {
            bitmap.height / 2
        } else {
            bitmap.width / 2
        }
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawCircle(r.toFloat(), r.toFloat(), r.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    @Synchronized
    fun setDrawable(profileDetails: ProfileDetails?): Drawable? {
        if (profileDetails != null && !profileDetails.isGroupProfile) {
            return SetDrawable (context, profileDetails).setDrawable(profileDetails.name)!!
        }
        return ContextCompat.getDrawable(context, R.drawable.ic_profile)
    }

    companion object {
        // A unique identifier string for creating the group notification.
        private val GROUP_KEY_MESSAGE: String = BuildConfig.APPLICATION_ID + ".MESSAGE"
        private const val SUMMARY_ID = 0
    }

    /**
     * The constructor to be invoked when creating the instance of this class object.
     *
     */
    init {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotifyRefererUtils.buildNotificationChannel(context, notificationManager)
        } else
            NotifyRefererUtils.getNotificationId()
        notificationIds = ArrayList()
        notificationInlineMessages = ArrayList()
        notifications = ArrayList()
    }
}