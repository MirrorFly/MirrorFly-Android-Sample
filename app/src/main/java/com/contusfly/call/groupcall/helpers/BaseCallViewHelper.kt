package com.contusfly.call.groupcall.helpers

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Rational
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import com.contus.call.CallActions
import com.contus.call.CallConstants.CALL_UI
import com.contus.flycommons.LogMessage
import com.contus.webrtc.*
import com.contus.call.utils.GroupCallUtils
import com.contusfly.*
import com.contusfly.R
import com.contusfly.call.groupcall.*
import com.contusfly.call.groupcall.listeners.ActivityOnClickListener
import com.contusfly.call.groupcall.listeners.BaseViewOnClickListener
import com.contusfly.call.groupcall.utils.AnimationsHelper
import com.contusfly.call.groupcall.utils.CallUtils
import com.contusfly.databinding.ActivityGroupCallBinding
import com.contusfly.utils.CommonUtils
import com.contusfly.utils.Constants
import com.contusflysdk.api.utils.ChatTimeFormatter
import kotlin.math.roundToInt

class BaseCallViewHelper(
    private val activity: AppCompatActivity,
    private val binding: ActivityGroupCallBinding,
    private val callUsersListAdapter: GroupCallListAdapter,
    private val callUserGridAdapter: GroupCallGridAdapter,
    private val durationHandler: Handler,
    private val activityOnClickListener: ActivityOnClickListener
) : BaseViewOnClickListener {

    private val callNotConnectedViewHelper by lazy { CallNotConnectedViewHelper(activity, binding.layoutCallNotConnected) }
    private val callConnectedViewHelper by lazy { CallConnectedViewHelper(binding.layoutCallConnected, activity, callUsersListAdapter, callUserGridAdapter, activityOnClickListener, this) }
    private val pipViewHelper by lazy { PIPViewHelper(activity, binding.layoutPipMode) }
    private val callOptionsViewHelper by lazy { CallOptionsViewHelper(activity, binding.layoutCallOptions, activityOnClickListener, this) }
    private val incomingCallViewHelper by lazy { IncomingCallViewHelper(activity, binding.layoutIncomingCall, activityOnClickListener) }
    private val retryCallViewHelper by lazy { RetryCallViewHelper(binding.layoutCallRetry, activityOnClickListener) }

    /**
     * Actual screen height in dp
     */
    private var actualScreenHeight = 0

    /**
     * Actual screen width in dp
     */
    private var actualScreenWidth = 0

    /**
     * The arguments to be used for Picture-in-Picture mode.
     */
    private val mPictureInPictureParamsBuilder : PictureInPictureParams.Builder? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PictureInPictureParams.Builder()
        } else {
            null
        }
    }

    init {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = activity.display
            display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(displayMetrics)
        }
        actualScreenHeight = displayMetrics.heightPixels
        actualScreenWidth = displayMetrics.widthPixels
        callUsersListAdapter.setScreenHeight(actualScreenHeight)
        callUsersListAdapter.setScreenWidth(actualScreenWidth)
        callUserGridAdapter.setScreenHeight(actualScreenHeight)
        callUserGridAdapter.setScreenWidth(actualScreenWidth)
    }

    /**
     * Time in milli seconds
     */
    var timeInMilliseconds = 0L

    /**
     * Call start time
     */
    private var startTime = 0L

    var callDuration = Constants.EMPTY_STRING

    /**
     * The Update Timer thread to run continuously when call is going on.
     */
    private val updateTimerThread: Runnable = object : Runnable {
        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime
            callDuration = ChatTimeFormatter.getFormattedCallDurationTime(timeInMilliseconds)
            if (callDuration.isBlank())
                callDuration = activity.getString(R.string.start_timer)
            callConnectedViewHelper.updateCallDuration(callDuration)
            durationHandler.postDelayed(this, 1000)
        }
    }

    private fun startCallTimer() {
        startTime = GroupCallUtils.getCallStartTime()
        durationHandler.postDelayed(updateTimerThread, 0)
    }

    fun setUpCallUI() {
        LogMessage.d(TAG, "$CALL_UI setUpCallUI()")
        setOverlayBackground()
        callNotConnectedViewHelper.setUpCallUI()
        callConnectedViewHelper.setUpCallUI()
        callOptionsViewHelper.setUpCallUI()
        retryCallViewHelper.setUpCallUI()
        incomingCallViewHelper.setUpCallUI()

        hidePIPLayout()
        resizeLocalTile()

        if (GroupCallUtils.isCallAttended()) callDuration = activity.getString(R.string.start_timer)
        if (GroupCallUtils.isCallConnected()) {
            startCallTimer()
            enableCallOptionAnimation()
        }
    }

    /**
     * Show/Hide local small video view in adapter while mute/un mute the local video
     */
    override fun ownVideoMuteStatusUpdated() {
        LogMessage.d(TAG, "$CALL_UI ownVideoMuteStatusUpdated")
        if (CallUtils.getIsGridViewEnabled()) {
            val gridIndex = callUserGridAdapter.gridCallUserList.indexOf(GroupCallUtils.getLocalUserJid())
            if (gridIndex.isValidIndex()) { // if local user available in grid view then refresh grid view
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED, 1)
                callUserGridAdapter.notifyItemChanged(gridIndex, bundle)
            }
        } else {
            val index = callUsersListAdapter.callUserList.indexOf(GroupCallUtils.getLocalUserJid())
            if (index.isValidIndex()) { // if local user available in list view then refresh list view
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED, 1)
                callUsersListAdapter.notifyItemChanged(index, bundle)
            } else {
                if (GroupCallUtils.isOneToOneCall())
                    callConnectedViewHelper.checkAndShowLocalVideoView()
                else
                    callConnectedViewHelper.updatePinnedUserVideoMuteStatus()
            }
        }
    }

    fun setUpProfileDetails(callUsers: ArrayList<String>) {
        if (GroupCallUtils.isCallConnected())
            callConnectedViewHelper.updateCallMemberDetails(callUsers)
        else
            callNotConnectedViewHelper.updateCallMemberDetails(callUsers)
    }

    fun updateCallStatus() {
        if (GroupCallUtils.isCallNotConnected())
            callNotConnectedViewHelper.updateCallStatus()
        else {
            callConnectedViewHelper.updateCallStatus()
            disableCallOptionAnimation()
        }
    }

    fun updateStatusAdapter(userJid: String?) {
        LogMessage.d(TAG, "$CALL_UI updateStatusAdapter userJid: $userJid")
        if (userJid != null) {
            val bundle = Bundle()
            bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
            if (CallUtils.getIsGridViewEnabled()) {
                val gridIndex = callUserGridAdapter.gridCallUserList.indexOf(userJid)
                if (gridIndex.isValidIndex()) {
                    callUserGridAdapter.notifyItemChanged(gridIndex, bundle)
                }
            } else {
                val index = callUsersListAdapter.callUserList.indexOf(userJid)
                if (index.isValidIndex()) {
                    callUsersListAdapter.notifyItemChanged(index, bundle)
                }
            }
        }
    }

    private fun setOverlayBackground() {
        LogMessage.d(TAG, "$CALL_UI setOverlayBackground()")
        if (activity.isInPIPMode())
            binding.viewOverlay.background = null
        else if (GroupCallUtils.isCallNotConnected()) {
            if (GroupCallUtils.isVideoCall()) {
                binding.viewOverlay.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_black_transparent))
            } else {
                binding.viewOverlay.background = ContextCompat.getDrawable(activity, R.drawable.ic_audio_call_bg)
            }
        } else {
            if (!GroupCallUtils.isOneToOneVideoCall())
                binding.viewOverlay.setBackgroundColor(ContextCompat.getColor(activity, R.color.audio_caller_background))
            else
                binding.viewOverlay.background = null
        }
    }

    /**
     * sets icon for the audio device image view based on the selected audio device
     */
    fun setSelectedAudioDeviceIcon() {
        callOptionsViewHelper.setAudioDeviceIcon(CallAudioManager.getInstance(activity).selectedAudioDevice)
    }

    private fun hideCallOptions() {
        callOptionsViewHelper.hideCallOptions()
    }

    private fun showCallOptions() {
        callOptionsViewHelper.showCallOptions()
    }

    fun checkAndUpdateCameraView() {
        callOptionsViewHelper.checkAndUpdateCameraView()
    }

    /**
     * This method animates the call options layout with given animation
     *
     * @param animation             animation id
     * @param callOptionsVisibility visibility to be changed for callOptions view
     * @param arrowVisibility       visibility to be changed for arrow view
     */
    override fun animateCallOptions(animation: Int, callOptionsVisibility: Int, arrowVisibility: Int) {
        callOptionsViewHelper.animateCallOptions(animation, callOptionsVisibility, arrowVisibility)
    }

    fun toggleVideoMute() {
        callOptionsViewHelper.toggleVideoMute()
    }

    /**
     * shows buttons to call again or cancel the action.̥
     */
    fun showCallAgainView() {
        LogMessage.d(TAG, "$CALL_UI showCallAgainView")
        hideCallOptions()
        binding.imageMinimizeCall.gone()
        callNotConnectedViewHelper.showRetryLayout()
        retryCallViewHelper.showRetryLayout()
    }

    /**
     * hides the call again view
     */
    fun hideCallAgainView() {
        LogMessage.d(TAG, "$CALL_UI hideCallAgainView()")
        showCallOptions()
        binding.imageMinimizeCall.show()
        callNotConnectedViewHelper.hideRetryLayout()
        retryCallViewHelper.hideRetryLayout()
    }

    fun hidePIPLayout() {
        pipViewHelper.hidePIPLayout()
    }

    fun gotoPIPMode() {
        LogMessage.d(
            TAG,
            "$CALL_UI gotoPIPMode(): ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && GroupCallUtils.isCallConnected() && !GroupCallUtils.isAddUsersToTheCall()}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && GroupCallUtils.isCallConnected() && !GroupCallUtils.isAddUsersToTheCall()) {
            if (CommonUtils.isPipModeAllowed(activity)) {
                // Calculate the aspect ratio of the PiP screen.
                val aspectRatio = Rational(binding.rootLayout.width, binding.rootLayout.height)
                mPictureInPictureParamsBuilder!!.setAspectRatio(aspectRatio).build()
                val isSuccess = activity.enterPictureInPictureMode(mPictureInPictureParamsBuilder!!.build())
                if (isSuccess) {
                    showPIPLayout()
                }
            } else {
                CommonUtils.openPipModeSettings(activity)
            }
        }
    }

    fun showPIPLayout() {
        // Hide the call controls UI while going to picture-in-picture mode.
        hideCallOptions()
        binding.callOptionsUpArrow.gone()
        binding.imageMinimizeCall.gone()
        callConnectedViewHelper.hideConnectedView()
        pipViewHelper.showPIPLayout()
    }

    private val hideOptionsRunnable = Runnable {
        LogMessage.d(TAG, "$CALL_UI hideOptionsRunnable")
        animateCallOptionsView()
    }

    override fun enableCallOptionAnimation() {
        when {
            CallUtils.getIsGridViewEnabled() -> {
                durationHandler.removeCallbacks(hideOptionsRunnable)
                showGridTitle()
            }
            GroupCallUtils.isOneToOneVideoCall() -> durationHandler.postDelayed(hideOptionsRunnable, 3000)
            else -> durationHandler.removeCallbacks(hideOptionsRunnable)
        }
    }

    private fun disableCallOptionAnimation() {
        durationHandler.removeCallbacks(hideOptionsRunnable)
    }

    override fun checkOptionArrowVisibility(visibility: Int) {
        binding.callOptionsUpArrow.visibility = visibility
    }

    /**
     * animates the local video view to move down to bottom of the screen
     */
    override fun onCallOptionsHidden() {
        LogMessage.d(TAG, "$CALL_UI onCallOptionsHidden()")
        val bottomMarginStart =
            binding.layoutCallOptions.layoutCallOptions.height // margin start value
        if (GroupCallUtils.isOneToOneCall()) {
            val bottomMarginTo = CommonUtils.convertDpToPixel(activity, 20) // where to animate to
            val params =
                binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams as RelativeLayout.LayoutParams
            if (binding.layoutCallConnected.layoutOneToOneAudioCall.visibility == View.VISIBLE) {
                AnimationsHelper.animateViewWithValues(
                    binding.layoutCallConnected.layoutOneToOneAudioCall, bottomMarginStart,
                    bottomMarginTo, 500
                ) { updatedValue: Int ->
                    params.setMargins(
                        0,
                        0,
                        CommonUtils.convertDpToPixel(activity, 20),
                        updatedValue
                    )
                    binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
                }
            } else {
                params.setMargins(
                    0,
                    0,
                    CommonUtils.convertDpToPixel(activity, 20),
                    bottomMarginStart
                )
                binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
            }
        } else {
            showListViewAtBottom()
        }
    }

    /**
     * animates the local video view to move up above [.callOptionsLayout]
     */
    override fun onCallOptionsVisible() {
        LogMessage.d(TAG, "$CALL_UI onCallOptionsVisible()")
        val bottomMarginTo = binding.layoutCallOptions.layoutCallOptions.height // where to animate to
        if (GroupCallUtils.isOneToOneCall()) {
            val bottomMarginStart = CommonUtils.convertDpToPixel(activity, 20) // margin start value
            val params =
                binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams as RelativeLayout.LayoutParams
            if (binding.layoutCallConnected.layoutOneToOneAudioCall.visibility == View.VISIBLE) {
                AnimationsHelper.animateViewWithValues(
                    binding.layoutCallConnected.layoutOneToOneAudioCall,
                    bottomMarginStart,
                    bottomMarginTo,
                    500
                ) { updatedValue: Int ->
                    params.setMargins(
                        0,
                        0,
                        CommonUtils.convertDpToPixel(activity, 20),
                        updatedValue
                    )
                    binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
                }
            } else {
                params.setMargins(
                    0,
                    0,
                    CommonUtils.convertDpToPixel(activity, 20),
                    bottomMarginTo
                )
                binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
            }
        } else {
            showListViewAboveCallOptions()
        }
    }

    private fun showListViewAtBottom() {
        LogMessage.d(TAG, "$CALL_UI showListViewAtBottom()")
        val bottomMarginEnd = CommonUtils.convertDpToPixel(activity, 20) // margin start value
        val layoutMargin = CommonUtils.convertDpToPixel(activity, 10) // margin value
        val bottomMarginStart = binding.layoutCallOptions.layoutCallOptions.height // margin start value
        val params = binding.layoutCallConnected.callUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
        params.width = RelativeLayout.LayoutParams.MATCH_PARENT
        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT
        AnimationsHelper.animateViewWithValues(
            binding.layoutCallConnected.callUsersRecyclerview, bottomMarginStart,
            bottomMarginEnd, 500
        ) { updatedValue: Int ->
            params.setMargins(
                layoutMargin,
                layoutMargin,
                layoutMargin,
                updatedValue
            )
            binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
        }
    }

    private fun showListViewAboveCallOptions() {
        LogMessage.d(TAG, "$CALL_UI showListViewAboveCallOptions()")
        val bottomMarginTo = binding.layoutCallOptions.layoutCallOptions.height // where to animate to
        val layoutMargin = CommonUtils.convertDpToPixel(activity, 10) // margin value
        val params = binding.layoutCallConnected.callUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
        params.width = RelativeLayout.LayoutParams.MATCH_PARENT
        params.height = RelativeLayout.LayoutParams.WRAP_CONTENT
        AnimationsHelper.animateViewWithValues(
            binding.layoutCallConnected.callUsersRecyclerview,
            0,
            bottomMarginTo,
            500
        ) { updatedValue: Int ->
            params.setMargins(
                layoutMargin,
                layoutMargin,
                layoutMargin,
                updatedValue
            )
            binding.layoutCallConnected.callUsersRecyclerview.layoutParams = params
        }
    }

    /**
     * animates the call options layout with respect to it's visibility
     */
    override fun animateCallOptionsView() {
        LogMessage.d(TAG, "$CALL_UI animateCallOptionsView()")
        if (!GroupCallUtils.isCallConnected() || GroupCallUtils.isAddUsersToTheCall()
            || !CallUtils.getIsGridViewEnabled() && (GroupCallUtils.isOneToOneAudioCall()
                    || GroupCallUtils.isOneToOneRemoteVideoMuted()
                    || GroupCallUtils.isReconnecting())
        )
            return

        if (CallUtils.getIsGridViewEnabled())
            animateGridView()
        else
            animateListView()
    }

    private fun animateGridView() {
        durationHandler.removeCallbacks(hideOptionsRunnable)
        if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE && binding.layoutCallConnected.layoutTitle.visibility == View.VISIBLE) {
            animateCallOptions(R.anim.slide_down, View.GONE, View.GONE)
            animateGridCallDetails(R.anim.slide_out_up, View.GONE)
        } else {
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
            animateGridCallDetails(R.anim.slide_out_down, View.VISIBLE)
        }
    }

    private fun animateGridCallDetails(animation: Int, callDetailsVisibility: Int) {
        val slideUpAnimation = AnimationUtils.loadAnimation(activity, animation)
        binding.layoutCallConnected.layoutTitle.startAnimation(slideUpAnimation)
        slideUpAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                /* not needed */
            }

            override fun onAnimationEnd(animation: Animation) {
                binding.layoutCallConnected.layoutTitle.visibility = callDetailsVisibility
            }

            override fun onAnimationRepeat(animation: Animation) {
                /* not needed */
            }
        })
        if (callDetailsVisibility == View.VISIBLE) onCallTitleVisible() else onCallTitleHidden()
    }

    private fun onCallTitleHidden() {
        LogMessage.d(TAG, "$CALL_UI onCallTitleHidden()")
        val bottomMarginStart = binding.layoutCallConnected.layoutTitle.height // margin start value
        val bottomMarginTo = CommonUtils.convertDpToPixel(activity, 8) // where to animate to
        val params = binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams

        AnimationsHelper.animateViewWithValues(
            binding.layoutCallConnected.callGridUsersRecyclerview, bottomMarginStart,
            bottomMarginTo, 500
        ) { updatedValue: Int ->
            params.setMargins(
                bottomMarginTo,
                updatedValue + bottomMarginTo,
                bottomMarginTo,
                bottomMarginTo
            )
            binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams = params
        }
    }

    private fun onCallTitleVisible() {
        LogMessage.d(TAG, "$CALL_UI onCallTitleVisible()")
        val bottomMarginStart = CommonUtils.convertDpToPixel(activity, 8) // margin start value
        val bottomMarginTo = binding.layoutCallConnected.layoutTitle.height // where to animate to
        val params = binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
        AnimationsHelper.animateViewWithValues(
            binding.layoutCallConnected.callGridUsersRecyclerview,
            bottomMarginStart,
            bottomMarginTo,
            500
        ) { updatedValue: Int ->
            params.setMargins(
                bottomMarginStart,
                updatedValue + bottomMarginStart,
                bottomMarginStart,
                bottomMarginStart
            )
            binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams = params
        }
    }

    private fun showGridTitle() {
        LogMessage.d(TAG, "$CALL_UI showGridTitle()")
        val bottomMarginStart = CommonUtils.convertDpToPixel(activity, 8) // margin start value
        binding.layoutCallConnected.backgroundView.gone()
        binding.layoutCallConnected.backgroundView.post {
            val bottomMarginTo = binding.layoutCallConnected.layoutTitle.height // where to animate to
            val params = binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
            params.setMargins(
                bottomMarginStart,
                bottomMarginTo + bottomMarginStart,
                bottomMarginStart,
                bottomMarginStart
            )
            binding.layoutCallConnected.callGridUsersRecyclerview.layoutParams = params
        }
    }

    private fun animateListView() {
        LogMessage.d(TAG, "$CALL_UI animateListView()")
        if (GroupCallUtils.isOneToOneCall()) {
            animateOneToOneCallOption()
        } else {
            animateGroupListView()
        }
    }

    private fun animateOneToOneCallOption() {
        LogMessage.d(TAG, "$CALL_UI animateOneToOneCallOption()")
        if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE) {
            animateCallOptions(R.anim.slide_down, View.GONE, View.VISIBLE)
            animateCallDetails(R.anim.slide_out_up, View.GONE)
            durationHandler.removeCallbacks(hideOptionsRunnable)
        } else {
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
            animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
            if (GroupCallUtils.isOneToOneVideoCall())
                durationHandler.postDelayed(hideOptionsRunnable, 3000)
        }
    }

    /**
     * This method animates the call options layout with List View
     */
    private fun animateGroupListView() {
        LogMessage.d(TAG, "$CALL_UI animateGroupListView()")
        if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE) {
            animateCallOptions(R.anim.slide_down, View.GONE, View.GONE)
            durationHandler.removeCallbacks(hideOptionsRunnable)
        } else {
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
        }
        if (binding.layoutCallConnected.layoutTitle.visibility != View.VISIBLE)
            animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
    }

    /**
     * This method animates the call options layout with given animation
     *
     * @param animation             animation id
     * @param callDetailsVisibility visibility to be changed for callDetails view
     */
    private fun animateCallDetails(animation: Int, callDetailsVisibility: Int) {
        val slideUpAnimation = AnimationUtils.loadAnimation(activity, animation)
        binding.layoutCallConnected.layoutTitle.startAnimation(slideUpAnimation)
        slideUpAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                /* not needed */
            }

            override fun onAnimationEnd(animation: Animation) {
                binding.layoutCallConnected.layoutTitle.visibility = callDetailsVisibility
            }

            override fun onAnimationRepeat(animation: Animation) {
                /* not needed */
            }
        })
    }

    fun disconnectCall() {
        durationHandler.removeCallbacks(hideOptionsRunnable)
        durationHandler.removeCallbacks(updateTimerThread)
        releaseSurfaceViews()
    }

    /**
     * Release video view surface while disconnect the call
     */
    private fun releaseSurfaceViews() {
        LogMessage.d(TAG, "$CALL_UI #surface releaseSurfaceViews: remoteView")
        binding.layoutCallConnected.viewVideoLocal.release()
        binding.layoutCallConnected.viewVideoPinned.release()
        val bundle = Bundle()
        bundle.putInt(CallActions.NOTIFY_REMOTE_VIEW_RELEASE, 1)
        if (CallUtils.getIsGridViewEnabled())
            callUserGridAdapter.notifyItemRangeChanged(0, callUserGridAdapter.gridCallUserList.size, bundle)
        else
            callUsersListAdapter.notifyItemRangeChanged(0, callUsersListAdapter.callUserList.size, bundle)
    }

    fun updateDisconnectedStatus(callStatus: String) {
        if (isCallUIVisible()) {
            val animation = AnimationUtils.loadAnimation(activity, R.anim.blink)
            if (callDuration.isNotBlank() || GroupCallUtils.isCallConnected()) {
                binding.layoutCallConnected.textCallDuration.text = callStatus
                if (binding.layoutCallConnected.layoutTitle.visibility == View.GONE) {
                    if (CallUtils.getIsGridViewEnabled())
                        animateGridView()
                    else {
                        animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
                        animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
                    }
                }
                binding.layoutCallConnected.textCallDuration.startAnimation(animation)
            } else {
                binding.layoutCallNotConnected.textCallStatus.text = callStatus
                binding.layoutCallNotConnected.textCallStatus.startAnimation(animation)
            }
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    /* not needed */
                }

                override fun onAnimationEnd(animation: Animation) {
                    activity.finish()
                }

                override fun onAnimationRepeat(animation: Animation) {
                    /* not needed */
                }
            })
        } else
            activity.finish()
    }

    private fun isCallUIVisible(): Boolean {
        return !(activity.isInPIPMode()) && AppLifecycleListener.isForeground && !GroupCallUtils.isAddUsersToTheCall()
    }

    fun updateRemoteAudioMuteStatus(userJid: String) {
        CallUtils.clearPeakSpeakingUser(userJid)
        callConnectedViewHelper.updateRemoteAudioMuteStatus()
        callConnectedViewHelper.onUserStoppedSpeaking(userJid)
        if (CallUtils.getIsGridViewEnabled()) {
            val gridIndex = callUserGridAdapter.gridCallUserList.indexOf(userJid)
            if (gridIndex.isValidIndex()) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_MUTE_UPDATED, 1)
                callUserGridAdapter.notifyItemChanged(gridIndex, bundle)
            }
        } else {
            val index = callUsersListAdapter.callUserList.indexOf(userJid)
            if (index.isValidIndex()) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_MUTE_UPDATED, 1)
                callUsersListAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    /**
     * After the video call is connected the video view will be placed near call options view
     */
    fun onVideoTrackAdded(userJid: String) {
        LogMessage.d(TAG, "$CALL_UI onVideoTrackAdded():$userJid")
        if (!GroupCallUtils.isActivityDestroyed(activity)) {
            if (activity.isInPIPMode()) {
                pipViewHelper.onVideoTrackAdded(userJid)
            } else {
                callConnectedViewHelper.onVideoTrackAdded(userJid)
                if (GroupCallUtils.isOneToOneCall())
                    resizeLocalTile()
            }
        } else LogMessage.d(TAG, "$CALL_UI onVideoTrackAdded Activity Destroyed")
    }

    private fun resizeLocalTile() {
        if (GroupCallUtils.isLocalTileCanResize()) {
            LogMessage.d(TAG, "$CALL_UI resizeLocalTile isLocalTileCanResize")
            val params = binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams as RelativeLayout.LayoutParams
            val rightMargin = CommonUtils.convertDpToPixel(activity, 20)
            /* align video view bottom in right-center of call options layout */
            if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE) {
                // once view measured, get height
                binding.layoutCallOptions.layoutCallOptions.post {
                    params.height = actualScreenHeight / 5
                    params.width = (actualScreenWidth / 3.5).roundToInt()
                    val callOptionsHeight = binding.layoutCallOptions.layoutCallOptions.height
                    params.setMargins(0, 0, rightMargin, callOptionsHeight)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                    params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
                    LogMessage.i(TAG, "$CALL_UI Set video layout params on view post")
                }
            } else {
                params.height = actualScreenHeight / 5
                params.width = (actualScreenWidth / 3.5).roundToInt()
                params.setMargins(0, 0, rightMargin, rightMargin)
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
                LogMessage.i(TAG, "$CALL_UI Set video layout params")
            }
        } else {
            LogMessage.i(TAG, "$CALL_UI resizeLocalTile skip one to one call tile update")
            if (!CallUtils.getIsListViewAnimated())
                animateListViewWithCallOptions()
        }
    }

    override fun animateListViewWithCallOptions() {
        LogMessage.i(TAG, "$CALL_UI animateListViewWithCallOptions")
        if (!CallUtils.getIsGridViewEnabled() && GroupCallUtils.isCallConnected())
            binding.layoutCallOptions.layoutCallOptions.post {
                CallUtils.setIsListViewAnimated(true)
                if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE) {
                    val layoutMargin = CommonUtils.convertDpToPixel(activity, 20)
                    if (layoutMargin >= binding.layoutCallConnected.callUsersRecyclerview.marginBottom)
                        showListViewAboveCallOptions()
                } else
                    showListViewAtBottom()
            }
    }

    /*
    * Update List view alignment while 1-1 call converted into 1-Many call
    */
    fun showListView() {
        LogMessage.i(TAG, "$CALL_UI showListView()")
        if (CallUtils.getIsGridViewEnabled())
            return
        if (binding.layoutCallOptions.layoutCallOptions.visibility == View.VISIBLE) {
            val layoutMargin = CommonUtils.convertDpToPixel(activity, 20)
            if (layoutMargin >= binding.layoutCallConnected.callUsersRecyclerview.marginBottom) {
                val bottomMarginTo = binding.layoutCallOptions.layoutCallOptions.height // where to animate to
                val margin = CommonUtils.convertDpToPixel(activity, 10) // margin value
                val params = binding.layoutCallConnected.callUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
                params.width = RelativeLayout.LayoutParams.MATCH_PARENT
                params.height = RelativeLayout.LayoutParams.WRAP_CONTENT
                params.setMargins(margin, margin, margin, bottomMarginTo)
                binding.layoutCallConnected.callUsersRecyclerview.layoutParams = params
            }
        } else {
            val bottomMarginEnd = CommonUtils.convertDpToPixel(activity, 20) // margin start value
            val layoutMargin = CommonUtils.convertDpToPixel(activity, 10) // margin value
            val params = binding.layoutCallConnected.callUsersRecyclerview.layoutParams as RelativeLayout.LayoutParams
            params.width = RelativeLayout.LayoutParams.MATCH_PARENT
            params.height = RelativeLayout.LayoutParams.WRAP_CONTENT
            params.setMargins(layoutMargin, layoutMargin, layoutMargin, bottomMarginEnd)
            binding.layoutCallConnected.layoutOneToOneAudioCall.layoutParams = params
        }
    }

    fun setMirrorLocalView() {
        callConnectedViewHelper.setMirrorLocalView()
    }

    override fun onSwapVideo() {
        callConnectedViewHelper.onSwapVideo()
    }

    fun updatePinnedUserVideoMuteStatus() {
        callConnectedViewHelper.updatePinnedUserVideoMuteStatus()
        callConnectedViewHelper.updateRemoteAudioMuteStatus() // Update audio mute icon position based on video mute
    }

    override fun pinnedUserRemoved() {
        CallUtils.setIsUserTilePinned(false)
        callConnectedViewHelper.updatePinnedUserIcon()
    }

    fun pinnedUserChanged(userJid: String) {
        CallUtils.setIsUserTilePinned(true)
        callConnectedViewHelper.pinnedUserChanged(userJid)
        callConnectedViewHelper.updatePinnedUserIcon(userJid)
    }

    fun pinnedUserLeft() {
        CallUtils.setIsUserTilePinned(false)
        callConnectedViewHelper.pinnedUserLeft()
        callConnectedViewHelper.updatePinnedUserIcon()
    }

    override fun ownAudioMuteStatusUpdated() {
        callConnectedViewHelper.ownAudioMuteStatusUpdated()
    }

    fun onUserStoppedSpeaking(userJid: String) {
        if (GroupCallUtils.isCallConnected()) {
            if (activity.isInPIPMode())
                pipViewHelper.onUserStoppedSpeaking(userJid)
            else
                callConnectedViewHelper.onUserStoppedSpeaking(userJid)
            CallUtils.onUserStoppedSpeaking(userJid)
        }
    }

    fun onUserSpeaking(userJid: String, audioLevel: Int) {
        if (GroupCallUtils.isCallConnected()) {
            CallUtils.onUserSpeaking(userJid, audioLevel)
            if (activity.isInPIPMode())
                pipViewHelper.onUserSpeaking(userJid, audioLevel)
            else
                callConnectedViewHelper.onUserSpeaking(userJid, audioLevel)
        }
    }
}