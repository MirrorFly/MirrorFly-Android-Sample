package com.contusfly

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import com.contus.flycommons.*
import com.contus.webrtc.api.CallManager
import com.contusfly.chat.AndroidUtils
import com.contusfly.models.Chat
import com.contusfly.utils.DebounceOnClickListener
import com.contusfly.utils.MediaPermissions
import com.contusfly.utils.MediaUtils
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.utils.SharedPreferenceManager.getCurrentUserJid
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CustomDrawable
import com.contusfly.views.SetDrawable
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.DeleteChatType
import com.contusflysdk.api.MessageStatus
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.api.models.RecentChat
import com.contusflysdk.api.models.ReplyParentChatMessage
import com.contusflysdk.views.CustomToast
import kotlinx.android.synthetic.main.profile_toolbar.view.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.ceil
import com.contusfly.utils.Constants


/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */

fun View.show() {
    let { visibility = View.VISIBLE }
}

fun View.hide() {
    let { visibility = View.INVISIBLE }
}

fun View.gone() {
    let { visibility = View.GONE }
}

fun LinearLayout.setWidthAndHeight(height: Int, width:Int) {
    layoutParams.width = ceil((AndroidUtils.getDensity(context) * width).toDouble()).toInt()
    layoutParams.height = ceil((AndroidUtils.getDensity(context) * height).toDouble()).toInt()
}

fun RelativeLayout.setWidthAndHeight(height: Int, width:Int) {
    layoutParams.width = ceil((AndroidUtils.getDensity(context) * width).toDouble()).toInt()
    layoutParams.height = ceil((AndroidUtils.getDensity(context) * height).toDouble()).toInt()
}

fun Int.isValidIndex() = this >= 0

fun Boolean?.isTrue(): Boolean = this != null && this

fun <ViewT : View> AppCompatActivity.bindView(@IdRes idRes: Int): Lazy<ViewT> {
    return lazy(LazyThreadSafetyMode.NONE) {
        findViewById(idRes)
    }
}

inline fun Context.checkInternetAndExecute(showToast: Boolean = true, function: () -> Unit) {
    if (AppUtils.isNetConnected(this))
        function()
    else if (showToast)
        showToast(getString(R.string.msg_no_internet))
}

fun Menu.get(menuId: Int): MenuItem = findItem(menuId)
fun MenuItem.action(menuItemAction: Int) = this.setShowAsAction(menuItemAction)

fun MenuItem.hide() {
    isVisible = false
}

fun MenuItem.show() {
    isVisible = true
}

fun hideMenu(vararg menuItems: MenuItem) {
    menuItems.map { it.hide() }
}

fun showMenu(vararg menuItems: MenuItem) {
    menuItems.map { it.show() }
}

fun showViews(vararg views: View) {
    views.map { it.show() }
}

fun hideViews(vararg views: View) {
    views.map { it.hide() }
}

fun makeViewsGone(vararg views: View) {
    views.map { it.gone() }
}

fun Context.showToast(text: String?) {
    text?.let {
        CustomToast.show(this, text)
    }
}

fun Context.isPermissionsAllowed(vararg permissions: String): Boolean {
    var isPermissionsAllowed = true
    for (item in permissions) {
        if (!MediaPermissions.isPermissionAllowed(this, item)) {
            isPermissionsAllowed = false
            break
        }
    }
    return isPermissionsAllowed
}

fun View.showSoftKeyboard(){
    if (this.requestFocus()) {
        val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }
}

fun ChatMessage.getDeleteChatType(): DeleteChatType {
    return when (messageChatType) {
        ChatTypeEnum.groupchat  -> DeleteChatType.groupchat
        else -> DeleteChatType.chat
    }
}

fun ChatMessage.getChatType(): String {
    return when (messageChatType) {
        ChatTypeEnum.groupchat  -> ChatType.TYPE_GROUP_CHAT
        ChatTypeEnum.broadcast -> ChatType.TYPE_BROADCAST_CHAT
        else -> ChatType.TYPE_CHAT
    }
}

fun RecentChat.getChatType(): String {
    return when {
        isGroup -> ChatType.TYPE_GROUP_CHAT
        isBroadCast -> ChatType.TYPE_BROADCAST_CHAT
        else -> ChatType.TYPE_CHAT
    }
}

fun Boolean.ifElse(functionOne: () -> Unit, functionTwo: () -> Unit) {
    if (this)
        functionOne()
    else
        functionTwo()
}

fun RecentChat.isDeletedContact() = contactType == ContactType.DELETED_CONTACT
fun RecentChat.isSingleChat() = !isGroup && !isBroadCast

fun String?.returnEmptyIfNull() = this ?: Constants.EMPTY_STRING

fun RecentChat.getChatTypeEnum(): ChatTypeEnum {
    return when {
        isGroup -> ChatTypeEnum.groupchat
        isBroadCast -> ChatTypeEnum.broadcast
        else -> ChatTypeEnum.chat
    }
}

fun ProfileDetails.getChatTypeEnum(): ChatTypeEnum {
    return when {
        isGroupProfile -> ChatTypeEnum.groupchat
        else -> ChatTypeEnum.chat
    }
}

/**
 * extension function to provide TAG value
 */
val Any.TAG: String
    get() {
        return if (!javaClass.isAnonymousClass) {
            val name = javaClass.simpleName
            if (name.length <= 23) name else name.substring(0, 23)// first 23 chars
        } else {
            val name = javaClass.name
            if (name.length <= 23) name else name.substring(name.length - 23, name.length)// last 23 chars
        }
    }

fun AppCompatImageView.loadUserProfileImage(context: Context, recentChat: RecentChat) {
    val drawable: Drawable?
    var imageUrl = if (!recentChat.profileThumbImage.isNullOrEmpty()) {
        recentChat.profileThumbImage
    } else recentChat.profileImage ?: Constants.EMPTY_STRING
    if (recentChat.isBlockedMe || recentChat.isAdminBlocked) {
        imageUrl = Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(recentChat)
    } else if (recentChat.isDeletedContact()) {
        imageUrl = recentChat.profileImage ?: Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(recentChat)
    } else if (TextUtils.isEmpty(imageUrl) || this.drawable == null)
        drawable = CustomDrawable(context).getDefaultDrawable(recentChat)
    else
        drawable = CustomDrawable(context).getDefaultDrawable(recentChat)
    if (imageUrl.startsWith(Constants.STORAGE))
        MediaUtils.loadImageWithGlide(context, imageUrl, this, drawable)
    else
        MediaUtils.loadImage(context, imageUrl, this, drawable)
}

fun ImageView.loadUserProfileImage(context: Context, userProfileDetails: ProfileDetails) {
    val drawable: Drawable?
    var imageUrl = if (!userProfileDetails.thumbImage.isNullOrEmpty()) {
        userProfileDetails.thumbImage
    } else userProfileDetails.image ?: Constants.EMPTY_STRING
    if (userProfileDetails.isBlockedMe || userProfileDetails.isAdminBlocked) {
        imageUrl = Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(userProfileDetails)
    } else if (userProfileDetails.isDeletedContact()) {
        imageUrl = userProfileDetails.image ?: Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(userProfileDetails)
    } else if (TextUtils.isEmpty(imageUrl) || this.drawable == null)
        drawable = CustomDrawable(context).getDefaultDrawable(userProfileDetails)
    else
        drawable = CustomDrawable(context).getDefaultDrawable(userProfileDetails)
    if (imageUrl.startsWith(Constants.STORAGE))
        MediaUtils.loadImageWithGlide(context, imageUrl, this, drawable)
    else
        MediaUtils.loadImage(context, imageUrl, this, drawable)
}

fun ImageView.loadUserInfoProfileImage(context: Context, profileDetails: ProfileDetails) {
    val drawable: Drawable?
    var imageUrl = profileDetails.image ?: Constants.EMPTY_STRING
    if (profileDetails.isBlockedMe || profileDetails.isAdminBlocked) {
        imageUrl = Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(profileDetails)
    } else if (profileDetails.isDeletedContact()) {
        imageUrl = profileDetails.image ?: Constants.EMPTY_STRING
        drawable = CustomDrawable(context).getDefaultDrawable(profileDetails)
    } else if (TextUtils.isEmpty(imageUrl) || this.drawable == null)
        drawable = CustomDrawable(context).getDefaultDrawable(profileDetails)
    else
        drawable = CustomDrawable(context).getDefaultDrawable(profileDetails)
    if (imageUrl.startsWith(Constants.STORAGE))
        MediaUtils.loadImageWithGlide(context, imageUrl, this, drawable)
    else {
        if (!profileDetails.thumbImage.isNullOrEmpty()) {
            MediaUtils.loadThumbImage(context, profileDetails.image, profileDetails.thumbImage, this, drawable)
        } else {
            MediaUtils.loadImage(context, imageUrl, this, drawable)
        }
    }
}

@SuppressLint("DefaultLocale")
fun CustomDrawable.getDefaultDrawable(recentChat: RecentChat): Drawable {
    return when {
        recentChat.isGroup -> this.context.getDefaultDrawable(ChatType.TYPE_GROUP_CHAT)
        else -> {
            val profileDetails:ProfileDetails? = ProfileDetailsUtils.getProfileDetails(recentChat.jid)
            if(profileDetails?.isBlockedMe!! || profileDetails.isAdminBlocked || profileDetails.isDeletedContact()){
                this.context.getDefaultDrawable(profileDetails.getChatType())
            }else{
                SetDrawable (context, profileDetails).setDrawable(profileDetails.name)!!
            }
        }
    }
}

@SuppressLint("DefaultLocale")
fun CustomDrawable.getDefaultDrawable(profileDetails: ProfileDetails): Drawable {
    return when {
        profileDetails.isGroupProfile -> this.context.getDefaultDrawable(ChatType.TYPE_GROUP_CHAT)
        else -> {
            if(!profileDetails.isBlockedMe && !profileDetails.isAdminBlocked && !profileDetails.isDeletedContact())
                SetDrawable (context, profileDetails).setDrawable(profileDetails.name)!!
            else
                this.context.getDefaultDrawable(profileDetails.getChatType())
        }

    }
}

fun Context.drawable(drawable: Int): Drawable = ContextCompat.getDrawable(this, drawable)!!

fun Context.getDefaultDrawable(chatType: String): Drawable {
    return when (chatType) {
        ChatType.TYPE_CHAT -> drawable(R.drawable.ic_sng_bg)
        ChatType.TYPE_GROUP_CHAT -> drawable(R.drawable.ic_grp_bg)
        ChatType.TYPE_BROADCAST_CHAT -> drawable(R.drawable.ic_broadcast)
        else -> drawable(R.drawable.ic_profile)
    }
}

inline fun <reified T : Any> Context.launchActivity(options: Bundle? = null, noinline init: Intent.() -> Unit = {}) {
    val intent = newIntent<T>(this)
    intent.init()
    startActivity(intent, options)
}

inline fun <reified T : Any> Activity.launchActivityForResult(requestCode: Int, noinline init: Intent.() -> Unit = {}) {
    val intent = newIntent<T>(this)
    intent.init()
    startActivityForResult(intent, requestCode)
}

inline fun <reified T : Any> newIntent(context: Context): Intent = Intent(context, T::class.java)

fun emptyString() = Constants.EMPTY_STRING

fun ChatMessage.isMessageSent() = messageStatus == MessageStatus.SENT
fun ChatMessage.isMessageAcknowledged() = messageStatus == MessageStatus.ACKNOWLEDGED
fun ChatMessage.isMessageDelivered() = messageStatus == MessageStatus.DELIVERED
fun ChatMessage.isMessageSeen() = messageStatus == MessageStatus.SEEN

fun ChatMessage.isGroupMessage() = messageChatType == ChatTypeEnum.groupchat
fun ChatMessage.getSenderJid(): String = if (isGroupMessage()) getChatUserJid() else getSenderUserJid()
fun ChatMessage.isTextMessage() = messageType == com.contus.flycommons.models.MessageType.TEXT
fun ChatMessage.isAudioMessage() = messageType == com.contus.flycommons.models.MessageType.AUDIO
fun ChatMessage.isImageMessage() = messageType == com.contus.flycommons.models.MessageType.IMAGE
fun ChatMessage.isVideoMessage() = messageType == com.contus.flycommons.models.MessageType.VIDEO
fun ChatMessage.isFileMessage() = messageType == com.contus.flycommons.models.MessageType.DOCUMENT
fun ChatMessage.isNotificationMessage() = messageType == com.contus.flycommons.models.MessageType.NOTIFICATION
fun ChatMessage.isMediaMessage() = (isAudioMessage() || isVideoMessage() || isImageMessage() || isFileMessage())
fun ChatMessage.isMediaUploaded(): Boolean {
    return isMediaMessage() && (mediaChatMessage.mediaUploadStatus == MediaUploadStatus.MEDIA_UPLOADED)
}
fun ChatMessage.isMediaDownloaded(): Boolean {
    return isMediaMessage() && (mediaChatMessage.mediaDownloadStatus == MediaDownloadStatus.MEDIA_DOWNLOADED)
}
fun ChatMessage.isMediaUploadOrDownload(): Boolean {
    return isMediaMessage() && (mediaChatMessage.mediaUploadStatus == MediaUploadStatus.MEDIA_UPLOADING || mediaChatMessage.mediaDownloadStatus == MediaDownloadStatus.MEDIA_DOWNLOADING)
}
fun ChatMessage.getSenderName(): String {
    return if (isMessageSentByMe())
        Constants.YOU
    else
        getSenderUserName()
}

fun Chat.getMyJid(): String = getCurrentUserJid()
fun Chat.isSingleChat() = chatType == ChatType.TYPE_CHAT
fun Chat.isGroupChat() = chatType == ChatType.TYPE_GROUP_CHAT
fun Chat.getChatType():ChatTypeEnum {
    return when (chatType) {
        ChatType.TYPE_CHAT -> ChatTypeEnum.chat
        ChatType.TYPE_GROUP_CHAT -> ChatTypeEnum.groupchat
        else -> ChatTypeEnum.broadcast
    }
}

fun Chat.getChatDeleteType(): DeleteChatType {
    return when (chatType) {
        ChatType.TYPE_CHAT -> DeleteChatType.chat
        ChatType.TYPE_GROUP_CHAT -> DeleteChatType.groupchat
        else -> DeleteChatType.chat
    }
}

fun AppCompatActivity.netConditionalCall(functionOne: () -> Unit, functionTwo: () -> Unit) {
    if (AppUtils.isNetConnected(this)) functionOne() else functionTwo()
}

fun String?.getColourCode(): Int {
    if (this != null && this == Constants.YOU)
        return ContextCompat.getColor(ChatManager.applicationContext, R.color.color_black)

    val colorsArray = ChatManager.applicationContext.resources.getIntArray(R.array.colour_code)
    val hashcode = this.hashCode()
    val rand =  hashcode % colorsArray.size
    return colorsArray[abs(rand)]
}

fun String.startsWithTextInWords(text: String): Boolean {
    return when {
        this.indexOf(text, ignoreCase = true) <= -1 -> false
        else -> return this.checkIndexes(text) > -1
    }
}

fun Context.getAppName(): String = applicationInfo.loadLabel(packageManager).toString()

fun String.checkIndexes(searchedKey: String): Int {
    var i = -1
    while (this.indexOf(searchedKey, i + 1, ignoreCase = true).also { i = it } != -1) {
        if (i == 0 || (i > 0 && (this.split("")[i].matches(regex = "[^A-Za-z0-9 ]".toRegex())
                    || this.split("")[i] == " ")))
            return i
        i++
    }
    return -1
}

fun Context.color(res: Int): Int = ContextCompat.getColor(this, res)


fun ReplyParentChatMessage.caption(context: Context): String {
    return if (mediaChatMessage.mediaCaptionText.isEmpty())
        if (isImageMessage()) context.getString(R.string.title_image) else context.getString(R.string.title_video)
    else
        mediaChatMessage.mediaCaptionText
}

fun ReplyParentChatMessage.getMediaFileName(context: Context): String {
    return if (getMediaChatMessage().getMediaFileName().isEmpty())
        context.getString(R.string.title_file)
    else
        getMediaChatMessage().getMediaFileName()
}

fun ReplyParentChatMessage.isImageMessage() = messageType == com.contus.flycommons.models.MessageType.IMAGE

fun ReplyParentChatMessage.getSenderName(): String {
    return if (isMessageSentByMe)
        Constants.YOU
    else
        getSenderUserName()
}

fun Chat.getUsername(): String {
    val profileDetails = ProfileDetailsUtils.getProfileDetails(toUser)
    return if (profileDetails == null) {
        toUser
    } else {
        profileDetails.name
    }
}

fun ProfileDetails.isUnknownContact() = !isGroupProfile && !isLiveContact() && !isDeletedContact() && mobileNumber.isNotNumber()
fun ProfileDetails.isDeletedContact() = contactType == ContactType.DELETED_CONTACT
fun ProfileDetails.isLiveContact() = contactType == ContactType.LIVE_CONTACT
fun ProfileDetails.getChatType(): String {
    return when {
        isGroupProfile -> ChatType.TYPE_GROUP_CHAT
        else -> ChatType.TYPE_CHAT
    }
}
fun String.isNotNumber() : Boolean {
    return try {
        this.toDouble()
        false
    } catch (e: NumberFormatException) {
        true
    }
}

fun sortProfileList(profilesList: List<ProfileDetails>?): List<ProfileDetails> {
    profilesList?.let {
        return it.sortedBy { profileDetails -> profileDetails.name }
    }
    return listOf()
}


fun Drawable.applyMultiplyColorFilter(@ColorInt color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        colorFilter = BlendModeColorFilter(color, BlendMode.MULTIPLY)
    } else {
        setColorFilter(color, PorterDuff.Mode.MULTIPLY)
    }
}

fun Drawable.applySrcInColorFilter(@ColorInt color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        colorFilter = BlendModeColorFilter(color, BlendMode.SRC_IN)
    } else {
        setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }
}

fun Drawable.applyDarkenColorFilter(@ColorInt color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        colorFilter = BlendModeColorFilter(color, BlendMode.DARKEN)
    } else {
        setColorFilter(color, PorterDuff.Mode.DARKEN)
    }
}

fun Drawable.applySourceColorFilter(@ColorInt color: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        colorFilter = BlendModeColorFilter(color, BlendMode.SRC_ATOP)
    } else {
        setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }
}

@SuppressLint("DefaultLocale")
fun CustomDrawable.getMoreUsersDrawable(name: String): Drawable {
    setText(name)
    setTransparentDrawableColour(R.color.color_dark_gray_transparent)
    return this
}

fun showAlertDialog(activity: Activity, title: String) {
    // The common alert dialog to display the alert dialogs in the alert view.
    val commonAlertDialog = CommonAlertDialog(activity)
    commonAlertDialog.showAlertDialog(title, activity.getString(R.string.action_Ok),
            activity.getString(R.string.fly_action_cancel), CommonAlertDialog.DIALOGTYPE.DIALOG_SINGLE, false)
}

fun isOnAnyCall(): Boolean {
    return CallManager.isOnGoingCall()
}

fun View.setOnClickListener(debounceInterval: Long, listenerBlock: (View) -> Unit) =
        setOnClickListener(DebounceOnClickListener(debounceInterval, listenerBlock))

fun profileNameCharValidation(name: String): Boolean {
    return name.length >= 3
}

fun View.setVisible(isVisible: Boolean) {
    visibility = if (isVisible) {
        View.VISIBLE
    } else {
        View.GONE
    }
}

fun TextView.makeLinks(vararg links: Pair<String, View.OnClickListener>) {
    val spannableString = SpannableString(this.text)
    var startIndexOfLink = -1
    for (link in links) {
        val clickableSpan = object : ClickableSpan() {
            override fun updateDrawState(textPaint: TextPaint) {
                textPaint.color = textPaint.linkColor
                textPaint.isUnderlineText = true
            }

            override fun onClick(view: View) {
                Selection.setSelection((view as TextView).text as Spannable, 0)
                view.invalidate()
                link.second.onClick(view)
            }
        }
        startIndexOfLink = this.text.toString().indexOf(link.first, startIndexOfLink + 1)
        spannableString.setSpan(
            clickableSpan, startIndexOfLink, startIndexOfLink + link.first.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    this.movementMethod = LinkMovementMethod.getInstance()
    this.setText(spannableString, TextView.BufferType.SPANNABLE)
}

suspend fun hasActiveInternet(): Boolean {
    return try {
        val timeoutMs = 1500
        val sock = Socket()
        val sockaddr: SocketAddress = InetSocketAddress("8.8.8.8", 53)
        sock.connect(sockaddr, timeoutMs)
        sock.close()
        true
    } catch (e: IOException) {
        false
    }
}

fun getFileSizeInStringFormat(size: Long): String? {
    val df = DecimalFormat("0.00")
    val sizeKb = 1024.0f
    val sizeMb = sizeKb * sizeKb
    val sizeGb = sizeMb * sizeKb
    val sizeTerra = sizeGb * sizeKb
    return when {
        size < sizeMb -> df.format(size / sizeKb.toDouble()) + " KB"
        size < sizeGb -> df.format(size / sizeMb.toDouble()) + " MB"
        size < sizeTerra -> df.format(size / sizeGb.toDouble()) + " GB"
        else -> ""
    }
}