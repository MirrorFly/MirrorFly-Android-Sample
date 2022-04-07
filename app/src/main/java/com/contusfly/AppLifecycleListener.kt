package com.contusfly

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.contus.flycommons.Prefs
import com.contus.call.utils.GroupCallUtils
import com.contusfly.utils.Constants
import com.contusfly.utils.LogMessage
import com.contusfly.utils.SharedPreferenceManager
import com.contusflysdk.api.ChatManager


class AppLifecycleListener : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() {
        isForeground = false
        // app moved to background
        Log.d(TAG, "App moved to background")
        SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun appLifeCycleOnCreate() {
        registerBroadcastReceiver()
        SharedPreferenceManager.setBoolean(Constants.BACK_PRESS, false)
        SharedPreferenceManager.setString(Constants.APP_SESSION, System.currentTimeMillis().toString())
        fromOnCreate = true
        Log.d(TAG, "OnCreate")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        isForeground = true
        // app moved to foreground
        deviceContactCount = 0
        val deviceTimeFormat = DateFormat.is24HourFormat(ChatManager.applicationContext)
        val devicePreviousTimeFormat = SharedPreferenceManager.getBoolean(Constants.IS_24_FORMAT)
        SharedPreferenceManager.setBoolean(Constants.IS_TIME_FORMAT_CHANGED, deviceTimeFormat != devicePreviousTimeFormat)
        SharedPreferenceManager.setBoolean(Constants.IS_24_FORMAT, deviceTimeFormat)

        if (isPinEnabled && !(GroupCallUtils.isOnGoingAudioCall() || GroupCallUtils.isOnGoingVideoCall())) {
            fromOnCreate = false
            Log.d(TAG, " show pin $isOnCall$backPressedSP")
            showPinActivity("onMoveToForeground")
            SharedPreferenceManager.setBoolean(Constants.BACK_PRESS, false)
        } else Log.d(TAG, "Else dont show pin")
        Log.d(TAG, "App moved to Foreground " + isPinEnabled + " " + sessionTimeDifference + shouldShowPinActivity())
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResumeCallback() {
        Log.d(TAG, "App OnResume $deviceLock $isForeground")
        if (deviceLock && isForeground) {
            presentPinActivity("onResumeCallback")
            deviceLock = false
        }
    }

    private fun registerBroadcastReceiver() {
        val theFilter = IntentFilter()
        /** System Defined Broadcast  */
        theFilter.addAction(Intent.ACTION_SCREEN_ON)
        theFilter.addAction(Intent.ACTION_SCREEN_OFF)
        theFilter.addAction(Intent.ACTION_USER_PRESENT)
        val screenOnOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val strAction = intent.action
                val myKM = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (strAction == Intent.ACTION_USER_PRESENT || strAction == Intent.ACTION_SCREEN_OFF || strAction == Intent.ACTION_SCREEN_ON) if (myKM.inKeyguardRestrictedInputMode()) {
                    Log.d(TAG, "Screen_off LOCKED")
                } else {
                    Log.d(TAG, "Screen_off UNLOCKED")
                    deviceLock = true
                    if (isForeground) {
                        presentPinActivity("receiver ")
                        deviceLock = false
                    }
                }
            }
        }
        ChatManager.applicationContext.registerReceiver(screenOnOffReceiver, theFilter)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onAppDestroyed() {
        Log.d(TAG, "app destroyed")
    }

    companion object {
        private val TAG = AppLifecycleListener::class.java.simpleName
        private val SESSION_TIME = (32 * 1000).toLong()
        var isOnCall = false

        @JvmField
        var isForeground = false

        @JvmField
        var fromOnCreate = false
        var deviceLock = false

        @JvmField
        var pinActivityShowing = false

        @JvmField
        var deviceContactCount = 0

        @JvmField
        var isFromQuickShareForBioMetric = false

        @JvmField
        var isFromQuickShareForPin = false
        val backPressedSP: Boolean
            get() = SharedPreferenceManager.getBoolean(Constants.BACK_PRESS) && !shouldShowPinActivity()

        fun showPinActivity(from: String?) {
            if (shouldShowPinActivity()) {
                presentPinActivity(from)
            }
        }

        fun shouldShowPinActivity(): Boolean =
            if(SharedPreferenceManager.getBoolean(Constants.IS_SAFE_CHAT_ENABLED)){ true }
            else sessionTimeDifference >= SESSION_TIME

        private val sessionTimeDifference: Long
            get() {
                val currentTimeInMillis = System.currentTimeMillis()
                val spValue = SharedPreferenceManager.getString(Constants.APP_SESSION)
                return if (spValue.isEmpty()) {
                    0.toLong()
                } else {
                    when {
                        java.lang.Long.valueOf(spValue) == 0L -> {
                            0.toLong()
                        }
                        java.lang.Long.valueOf(spValue) >= 0L -> {
                            val timeSinceLastUse = java.lang.Long.valueOf(spValue)
                            currentTimeInMillis - timeSinceLastUse
                        }
                        else -> {
                            0L
                        }
                    }
                }
            }

        val isPinEnabled: Boolean
            get() = SharedPreferenceManager.getBoolean(Constants.SHOW_PIN)

        private val isQuickShare: Boolean
            get() {
                if (SharedPreferenceManager.getBoolean(Constants.QUICK_SHARE)) {
                    SharedPreferenceManager.setBoolean(Constants.QUICK_SHARE, false)
                    return true
                }
                return false
            }

        fun presentPinActivity(from: String?) {
            isForeground = false
            if (isPinEnabled && !isQuickShare &&!isOnCall) {
                if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
                    val intent = Intent(ChatManager.applicationContext, ChatManager.biometricActivty)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    ChatManager.applicationContext.startActivity(intent)
                } else if (!isOnCall && !pinActivityShowing) {
                    val intent = Intent(ChatManager.applicationContext, ChatManager.pinActivity)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    ChatManager.applicationContext.startActivity(intent)
                }
            }
        }
    }
}