package com.contusfly.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import androidx.core.content.ContextCompat
import com.contus.call.CallConstants
import com.contus.flycommons.LogMessage
import com.contus.webrtc.api.CallManager
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.activities.MediaPreviewActivity
import com.contusfly.call.CallPermissionUtils
import com.contusfly.call.groupcall.GroupCallActivity
import com.contusfly.call.groupcall.OnGoingCallPreviewActivity
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.AppUtils
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import java.io.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.channels.FileChannel

object ChatUtils {

    fun setSelectedChatItem(view: View, message: ChatMessage, selectedMessages: List<String>?, context: Context?) {
        if (Utils.isListExist(selectedMessages) && selectedMessages != null && selectedMessages.contains(message.getMessageId())) {
            view.setBackgroundColor(ContextCompat.getColor(context!!, R.color.color_selected_item))
        } else {
            view.setBackgroundColor(ContextCompat.getColor(context!!, android.R.color.transparent))
        }
    }

    fun setSelectedChatItem(view: View, isHighLighted:Boolean, context: Context?) {
        if (isHighLighted) {
            view.setBackgroundColor(ContextCompat.getColor(context!!, R.color.color_selected_item))
        } else {
            view.setBackgroundColor(ContextCompat.getColor(context!!, android.R.color.transparent))
        }
    }

    /**
     * Copies gif file from source to destination
     *
     * @param srcPath Source gif file
     * @param dst Destination file
     */
    fun copyGif(srcPath: String, dst: File?) {
        val bitmap: Bitmap = BitmapFactory.decodeFile(srcPath)
        val stream = FileOutputStream(dst?.absolutePath)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.close()
    }

    /**
     * Copies file from source to destination
     *
     * @param src Source file
     * @param dst Destination file
     */
    fun copy(src: File?, dst: File?) {
        var inStream: FileInputStream? = null
        var outStream: FileOutputStream? = null
        var inChannel: FileChannel? = null
        var outChannel: FileChannel? = null
        if (!dst!!.exists())
            dst.createNewFile()
        try {
            inStream = FileInputStream(src)
            inChannel = inStream.channel
            try {
                outStream = FileOutputStream(dst)
                outChannel = outStream.channel
                inChannel.transferTo(0, inChannel.size(), outChannel)
            } finally {
                outStream?.close()
            }
        } catch (e: Exception) {
            LogMessage.e(e)
        } finally {
            try {
                inStream?.close()
            } catch (e: IOException) {
                LogMessage.e(e)
            }
        }
    }

    fun checkMediaPermission(context: Context, permission: String): Boolean {
        return MediaPermissions.isPermissionAllowed(context, permission)
    }

    fun checkWritePermission(context: Context, permission: String): Boolean {
        val minSdk30 = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q
        return MediaPermissions.isPermissionAllowed(context, permission) || minSdk30
    }

    fun setPreviewActivity(previewClass: Class<MediaPreviewActivity>, toUser: String, chatType: String) {
        val mediaPreviewIntent = com.contusfly.mediapicker.helper.MediaPreviewIntent.instance
        mediaPreviewIntent?.let {
            it.mediaClass = previewClass
            it.toUser = toUser
            it.chatType = chatType
        }
    }

    fun setCameraPreviewActivity(chatClass: Class<MediaPreviewActivity>, toUser: String, chatType: String) {
        val mediaPreviewIntent = com.fxn.modals.MediaPreviewIntent.getInstance()
        mediaPreviewIntent.setMediaClass(chatClass)
        mediaPreviewIntent.toUser = toUser
        mediaPreviewIntent.chatType = chatType
    }


    fun getUserFromJid(jid: String): String {
        var user = ""
        val endIndex = jid.lastIndexOf(64.toChar())
        if (endIndex != -1) {
            user = jid.substring(0, endIndex)
        }
        return user
    }

    /**
     * Prepares the file size text to be displayed from the actual size represented in bytes.
     *
     * @param fileSizeInBytes The actual file size represented in bytes.
     * @return The file size represented in the byte convention format.
     */
    fun getFileSizeText(fileSizeInBytes: String): String {
        val fileSizeBuilder = StringBuilder()
        val fileSize = fileSizeInBytes.toLong().toDouble()
        if (fileSize > 1073741824) {
            fileSizeBuilder.append(getRoundedFileSize(fileSize / 1073741824))
                .append(" ").append("GB")
        } else if (fileSize > 1048576) {
            fileSizeBuilder.append(getRoundedFileSize(fileSize / 1048576))
                .append(" ").append("MB")
        } else if (fileSize > 1024) {
            fileSizeBuilder.append(getRoundedFileSize(fileSize / 1024))
                .append(" ").append("KB")
        } else {
            fileSizeBuilder.append(fileSizeInBytes).append(" ").append("bytes")
        }
        return fileSizeBuilder.toString()
    }

    /**
     * Returns a new double value with the specified scale.
     *
     * @param unscaledValue Value to be converted to a [Double].
     * @return [Double] instance with the value `unscaledVal`.
     */
    private fun getRoundedFileSize(unscaledValue: Double): Double {
        return BigDecimal.valueOf(unscaledValue).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    fun getJidFromPhoneNumber(phoneNumberUtil: PhoneNumberUtil, mobileNumber: String, countryCode: String): String? {
        return if (mobileNumber.startsWith("*")) {
            LogMessage.d(TAG, "Invalid PhoneNumber:$mobileNumber")
            return null
        } else {
            try {
                val phoneNumber = phoneNumberUtil.parse(mobileNumber.replace("^0+".toRegex(), ""), countryCode)
                val unformattedPhoneNumber = phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "")
                unformattedPhoneNumber + "@" + com.contus.flycommons.Constants.getDomain()
            } catch (var6: NumberParseException) {
                LogMessage.e(TAG, var6)
                null
            }
        }
    }

    fun navigateToOnGoingCallPreviewScreen(context: Context, userJid: String, url: String): Boolean {
        val callLink = url.replace(BuildConfig.WEB_CHAT_LOGIN, "")
        if (AppUtils.isNetConnected(context)) {
            return if (CallManager.isOnGoingCall()) {
                val onGngCallLink = CallManager.getCallLink()
                if (onGngCallLink == callLink) {
                    context.startActivity(Intent(context, GroupCallActivity::class.java))
                    true
                } else {
                    askCallSwitchPopup(context, callLink)
                    false
                }
            } else if (CallManager.isOnTelephonyCall(context)) {
                CallPermissionUtils.showTelephonyCallAlert(context)
                false
            } else {
                context.startActivity(
                    Intent(context, OnGoingCallPreviewActivity::class.java).putExtra(CallConstants.CALL_LINK, callLink)
                        .putExtra(Constants.USER_JID, userJid))
                true
            }
        } else {
            CustomToast.show(context, context.getString(R.string.error_check_internet))
            return true
        }
    }

    private fun askCallSwitchPopup(context: Context, url: String) {
        val commonAlertDialog = CommonAlertDialog(context)
        commonAlertDialog.showCallSwitchDialog(url,
            context.getString(R.string.action_ok),
            context.getString(R.string.action_cancel),
            CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL)
    }

    /**
     * Converts message to a valid spanned text
     *
     * @param message message date which is sent/received
     */
    fun getSpannedText(context: Context, message: String?): Spanned {
        val htmlText: Spanned
        val chatMessage = getHtmlChatMessageText(context, message!!).replace("\n", "<br>").replace("  ", "&nbsp;&nbsp;")
        htmlText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(getHtmlChatMessageText(context, chatMessage), Html.FROM_HTML_MODE_LEGACY)
        else
            Html.fromHtml(getHtmlChatMessageText(context, chatMessage))

        return if (htmlText.isEmpty() && chatMessage != "")
            SpannableStringBuilder(getHtmlChatMessageText(context, chatMessage))
        else
            htmlText
    }


    /**
     * Returns Spanned string by adding HTML empty text to avoid overlap with time view in
     * FrameLayout
     *
     * @param message Message content
     * @return Spanned Result spanned text with space
     */
    private fun getHtmlChatMessageText(context: Context, message: String): String {
        val text = context.getString(R.string.chat_text)
        return message + text
    }
}