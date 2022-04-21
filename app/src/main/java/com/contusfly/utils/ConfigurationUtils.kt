package com.contusfly.utils

import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import com.contus.flycommons.FlyCallback
import com.contusfly.R
import com.contusfly.utils.NotifyRefererUtils.buildNotificationChannel
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.FlyCore.getBusyStatusList
import com.contusflysdk.api.FlyCore.getMyBusyStatus
import com.contusflysdk.api.FlyCore.getProfileStatusList
import com.contusflysdk.api.FlyCore.insertMyBusyStatus
import com.contusflysdk.api.FlyCore.setMyBusyStatus
import com.contusflysdk.api.FlyCore.setMyProfileStatus
import com.contusflysdk.api.models.ProfileStatus
import java.util.*

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class ConfigurationUtils {

    companion object {

        /**
         * Set Default values in Mobile Application
         */
        fun setDefaultValues(context: Context) {
            if (!context.getSharedPreferences(context.resources.getString(R.string.title_app_name), Context.MODE_PRIVATE)
                    .contains(Constants.NOTIFICATION_SOUND)) {
                SharedPreferenceManager.setBoolean(Constants.NOTIFICATION_SOUND, true)
                if (!context.getSharedPreferences(context.resources.getString(R.string.title_app_name), Context.MODE_PRIVATE)
                        .contains(Constants.VIBRATION)) {
                    SharedPreferenceManager.setBoolean(Constants.VIBRATION, false)
                    SharedPreferenceManager.setBoolean(Constants.KEY_CHANGE_FLAG, false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    buildNotificationChannel(context, notificationManager)
                }
            }
        }

        /**
         * Insert the default status for the logged in user. Also initiate the message tone and vibration alert and conversation settings data.
         *
         * @param context Instance of application
         */
        fun insertDefaultStatus(context: Context, status: String?) {
            if (getProfileStatusList().isEmpty()) {
                val defaultStatus = context.resources.getStringArray(R.array.default_status_values)
                for (statusValue in defaultStatus) {
                    setMyProfileStatus(statusValue!!, FlyCallback { _: Boolean, _: Throwable?, _: HashMap<String?, Any?>? -> })
                }
                setMyProfileStatus(status!!, FlyCallback { _: Boolean, _: Throwable?, _: HashMap<String?, Any?>? -> })
                SharedPreferenceManager.setString(Constants.VIBRATION_TYPE, 0.toString())
                SharedPreferenceManager.setString(Constants.NOTIFICATION_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
                SharedPreferenceManager.setBoolean(Constants.CONVERSATION_SOUND, true)
                SharedPreferenceManager.setBoolean(Constants.MUTE_ALL_CONVERSATION, false)
            }else if(SharedPreferenceManager.getString(Constants.NOTIFICATION_URI).isEmpty()){
                SharedPreferenceManager.setString(Constants.NOTIFICATION_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
            }
        }

        /**
         * Insert default busy status for the logged in user.
         *
         * @param context Instance of application
         */
        fun insertDefaultBusyStatus(context: Context) {
            if (getBusyStatusList().isEmpty()) {
                val defaultStatus = context.resources.getStringArray(R.array.default_busy_status_values)
                for (statusValue in defaultStatus) {
                    insertMyBusyStatus(statusValue!!)
                }
                if (getMyBusyStatus() == null || getMyBusyStatus()!!.status.isEmpty()) {
                    setMyBusyStatus(context.getString(R.string.default_busy_status))
                }
            }
        }

        /**
         * Insert the default status for the logged in user. Also initiate the message tone and vibration alert and conversation settings data.
         *
         * @param context Instance of application
         */
        fun insertDefaultStatusToUser(context: Context, status: String?) {
            val profileStatus: List<ProfileStatus> = getProfileStatusList()
            if (profileStatus.isNotEmpty()) {
                val defaultStatus = context.resources.getStringArray(R.array.default_status_values)
                for (statusValue in defaultStatus) {
                    var isStatusNotExist = true
                    for (flyStatus in profileStatus) {
                        if (flyStatus.equals(statusValue))
                            isStatusNotExist = false
                    }
                    if (isStatusNotExist)
                        FlyCore.insertDefaultStatus(statusValue)
                }
            }
        }

    }
}