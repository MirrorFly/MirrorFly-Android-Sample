package com.contusfly.utils

object AppConstants {

    const val SEND_TO = "send_to"
    const val MAX_MEDIA_SELECTION_RESTRICTION = "max_media_selection_restriction"
    const val NOTIFY_PROFILE_ICON = "notify_profile_icon"
    const val NOTIFY_ADMIN_BLOCK = "notify_admin_block"
    const val NOTIFY_SELECTION = "notify_selection"
    const val NOTIFY_IS_SELECTED = "notify_is_selected"
    const val MY_JID = "my_jid"
    const val USER_PROFILE = "user_profile"
    const val GROUP_JID = "group_jid"
    const val GROUP_PROFILE = "group_profile"
    const val PROFILE_DATA = "profile_data"
    const val YOU = "You"
    const val DB_NAME = "UIKit"

    val supportedFormats = arrayOf("pdf", "txt", "rtf", "xls", "ppt", "pptx", "zip", "xlsx", "doc", "docx", "wav", "mp3", "mp4", "aac", "jpg", "jpeg", "png", "webp", "gif", "pptx", "csv")

    /**
     * Send the network state change to update chat user online status
     */
    const val NETWORK_STATE_CHANGE = "com.contus.connection.network_change"

    /**
     * Send the network status to update chat user online status
     */
    const val NETWORK_STATE_STATUS = "com.contus.connection.network_status"
}