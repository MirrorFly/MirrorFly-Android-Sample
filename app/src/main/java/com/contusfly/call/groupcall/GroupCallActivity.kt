package com.contusfly.call.groupcall

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.*
import android.util.DisplayMetrics
import android.util.Rational
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.contus.call.CallActions
import com.contus.call.RippleBackgroundView
import com.contus.flycommons.LogMessage
import com.contus.webrtc.*
import com.contus.webrtc.WebRtcCallService.Companion.setupVideoCapture
import com.contus.webrtc.api.CallActionListener
import com.contus.webrtc.api.CallEventsListener
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.CallManager.isCallConversionRequestAvailable
import com.contus.webrtc.api.CallManager.isRemoteAudioMuted
import com.contus.webrtc.api.CallManager.isRemoteVideoMuted
import com.contus.webrtc.api.CallManager.isRemoteVideoPaused
import com.contus.webrtc.utils.GroupCallUtils
import com.contus.webrtc.utils.GroupCallUtils.getAvailableCallUsersList
import com.contus.webrtc.utils.GroupCallUtils.getCallDirection
import com.contus.webrtc.utils.GroupCallUtils.getCallStatus
import com.contus.webrtc.utils.GroupCallUtils.getCallType
import com.contus.webrtc.utils.GroupCallUtils.getEndCallerJid
import com.contus.webrtc.utils.GroupCallUtils.getIsCallAgain
import com.contus.webrtc.utils.GroupCallUtils.getLocalUserJid
import com.contus.webrtc.utils.GroupCallUtils.getUserAvailableForReconnection
import com.contus.webrtc.utils.GroupCallUtils.getUserConnectedInCall
import com.contus.webrtc.utils.GroupCallUtils.isActivityDestroyed
import com.contus.webrtc.utils.GroupCallUtils.isAddUsersToTheCall
import com.contus.webrtc.utils.GroupCallUtils.isAudioMuted
import com.contus.webrtc.utils.GroupCallUtils.isCallAttended
import com.contus.webrtc.utils.GroupCallUtils.isCallConnected
import com.contus.webrtc.utils.GroupCallUtils.isOnVideoCall
import com.contus.webrtc.utils.GroupCallUtils.isOneToOneAudioCall
import com.contus.webrtc.utils.GroupCallUtils.isOneToOneCall
import com.contus.webrtc.utils.GroupCallUtils.isReconnecting
import com.contus.webrtc.utils.GroupCallUtils.removeTimeoutUser
import com.contus.webrtc.utils.GroupCallUtils.setIsOnVideoCall
import com.contus.webrtc.utils.GroupCallUtils.setPIPMode
import com.contusfly.*
import com.contusfly.R
import com.contusfly.activities.BaseActivity
import com.contusfly.activities.DashboardActivity
import com.contusfly.call.CallConfiguration
import com.contusfly.call.groupcall.utils.AnimationsHelper
import com.contusfly.call.groupcall.utils.CustomStaggeredGridLayoutManager
import com.contusfly.utils.CommonUtils
import com.contusfly.utils.MediaPermissions
import com.contusfly.views.CircularImageView
import com.contusflysdk.AppUtils
import com.contusflysdk.FlyDatabaseManager
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.utils.ChatTimeFormatter
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This call activity is handle the incoming and outgoing group calls for both audio and video
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 2.0
 */
class GroupCallActivity : BaseActivity(), View.OnClickListener {

    private lateinit var addParticipantFragment: AddParticipantFragment

    //region -  Declarations
    private val TOGGLE_VIDEO_MUTE_CODE: Int = 2021

    private val ACCEPT_VIDEO_CALL_SWITCH: Int = 2022

    /**
     * This flag indicates whether [.onDestroy] method is called or not
     */
    private val isDisconnectCalled = AtomicBoolean(false)

    /**
     * This flag indicates whether [.answer]  method is called or not
     */
    private val isAnswerCalled = AtomicBoolean(false)

    /**
     * Instance for call user view
     */
    private lateinit var callUsersRecyclerView: RecyclerView

    private lateinit var callUsersAdapter: GroupCallAdapter

    /**
     * indicates back stack lost status
     */
    private var mBackStackLost = false

    /**
     * Local video view height
     */
    private var heightEnd = 0

    /**
     * Local video view width
     */
    private var widthEnd = 0

    /**
     * Actual screen height in dp
     */
    private var actualScreenHeight = 0

    /**
     * Actual screen width in dp
     */
    private var actualScreenWidth = 0

    /**
     * Time in milli seconds
     */
    var timeInMilliseconds = 0L

    /**
     * Creating call duration
     */
    var callDuration: String? = null

    /**
     * This image for caller profile  picture
     */
    private var imgCallerPicture: CircularImageView? = null

    /**
     * This is for audio call is incoming or outgoing and duration details
     */
    var txtAudioCallDetails: TextView? = null

    /**
     * This image view for video mute overlay
     */
    private var mutedVideoImage: ImageView? = null

    /**
     * This image view for Audio mute overlay
     */
    private var mutedAudioImage: ImageView? = null

    /**
     * mute status overlay view
     */
    private var muteLayout: View? = null

    private var txtCallMute: TextView? = null

    /**
     * Instance for surface view render local view
     */
    private var videoLocalView: SurfaceViewRenderer? = null

    /**
     * This image for minimize current screen
     */
    private lateinit var imgMinimizeCall: ImageButton

    /**
     * This is for call is incoming or outgoing and duration details
     */
    var txtCallDetails: TextView? = null
    var txtCallAttendStatus: TextView? = null
    private var startCallTime: Long = 0

    /**
     * boolean to check outGoing request
     */
    private var outGoingRequest = false

    /**
     * boolean to check inComing request
     */
    private var inComingRequest = false

    /**
     * This image view for  swap camera
     */
    private lateinit var switchCameraImage: ImageView

    /**
     * This image view for  audio mute
     */
    private lateinit var muteAudioImage: ImageView

    /**
     * This image view for video mute
     */
    private lateinit var muteVideoImage: ImageView

    /**
     * This image view for audio devices switching
     */
    private lateinit var switchAudioImage: ImageView

    // True if local view is in the fullscreen renderer.
    private var isSwappedFeeds = false

    private var txtCallStatus: TextView? = null
    private var txtCallerName: TextView? = null
    private var txtGroupName: TextView? = null
    private var txtAttendUser: TextView? = null
    private var callDirection: String? = null
    private var callIncomingLayout: RelativeLayout? = null
    private var callOptionsLayout: LinearLayout? = null
    private var callDetailsLayout: RelativeLayout? = null

    private var callRetryLayout: RelativeLayout? = null
    private var callRetryText: TextView? = null

    /**
     * This indicates whether the call status updated to server or not
     */
    private var isBackCamera = false
    private var overlayView: View? = null
    private var callInfoLayout: RelativeLayout? = null

    /**
     * PIP mode layouts
     */
    private var layoutPipMode: RelativeLayout? = null
    private var txtCallType: TextView? = null
    private var imgCallStatus: ImageView? = null
    private var txtPipCallStatus: TextView? = null
    private var txtPipGroupName: TextView? = null

    // A reference to the service used to communicate with the service.
    private var mService: WebRtcCallService? = null

    // localView animation ended
    private var minimizeAnimationDone = false

    /**
     * jid of the group call initiated
     */
    private var groupId: String? = null
    private var callUsers: ArrayList<String?>? = null

    /**
     * Call attend/reject swiping button
     */
    private var callSwipeButton: ImageView? = null

    /**
     * incoming call view up arrow
     */
    private var imageCallUpArrow1: ImageView? = null
    private var imageCallUpArrow2: ImageView? = null

    /**
     * incoming call view down arrow
     */
    private var imageCallDownArrow1: ImageView? = null

    // Monitors the state of the connection to the service.
    private var imageCallDownArrow2: ImageView? = null

    /**
     * add users in call image view
     */
    private lateinit var imgAddUser: ImageView

    /**
     * call accept image view
     */
    private lateinit var imgCallAnswer: ImageView

    private lateinit var callAgainText: TextView

    /**
     * call reject image view
     */
    private lateinit var imgCallReject: ImageView

    /**
     * call end image view
     */
    private lateinit var imageEndCall: ImageView

    /**
     * This flag indicates whether the call button motion started or not
     */
    private var begin = false
    private var answerY = 0f
    private var oldMove = 0f
    private var rippleBackground: RippleBackgroundView? = null
    private lateinit var imageCallOptionsUpArrow: ImageView

    /**
     * boolean value indicates whether the video views initialized or not .
     */
    private var isVideoViewsInitialized: AtomicBoolean? = null
    private var callImage: ImageView? = null

    /**
     * Audio call start time
     */
    private var startTime = 0L

    /**
     * Instance for duration handler
     */
    private var durationHandler: Handler = Handler()

    /**
     * The arguments to be used for Picture-in-Picture mode.
     */
    private var mPictureInPictureParamsBuilder: PictureInPictureParams.Builder? = null

    /**
     * root layout view
     */
    private lateinit var rootLayout: View

    /**
     * Alert to confirm call switch
     */
    private var callSwitchConfirmationAlert: AlertDialog? = null

    /**
     * Alert to show incoming call switch request
     */
    private var callSwitchAlert: AlertDialog? = null

    /**
     * Alert to show requesting call switch
     */
    private var requestingDialog: AlertDialog? = null

    /**
     * call events listener
     */
    private val customCallEventsListener = CustomCallEventsListener()

    //endregion

    /**
     * The Update Timer thread to run continuously when call is going on.
     */
    private val updateTimerThread: Runnable = object : Runnable {
        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime
            callDuration = ChatTimeFormatter.getFormattedCallDurationTime(timeInMilliseconds)
            if (startTime == 0L || callDuration.isNullOrEmpty()) {
                txtCallDetails!!.text = getString(R.string.start_timer)
                txtPipCallStatus!!.text = getString(R.string.start_timer)
                txtAudioCallDetails!!.text = getString(R.string.start_timer)
            } else {
                txtCallDetails!!.text = callDuration
                txtPipCallStatus!!.text = callDuration
                txtAudioCallDetails!!.text = callDuration
            }
            durationHandler.postDelayed(this, 1000)
        }
    }
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            LogMessage.i(LOG_TAG, "service connected")
            val binder = service as WebRtcCallService.LocalBinder
            mService = binder.service
            initVideoViews()
            if (isCallConnected()) {
                LogMessage.d(LOG_TAG, "service call connected")
                if (isOneToOneCall()) {
                    val status =
                        if (getCallStatus(getLocalUserJid()) == CallStatus.ON_HOLD) CallStatus.ON_HOLD else getCallStatus(
                            getEndCallerJid()
                        )
                    updateStatus(status)
                } else {
                    updateStatus(CallStatus.CONNECTED)
                }
                updateViews()
                startTimer()
                if (getCallType() == CallType.VIDEO_CALL) {
                    updateVideoViews(getLocalUserJid())
                }
            } else {
                if (callDirection == CallDirection.OUTGOING_CALL) {
                    LogMessage.d(LOG_TAG, "service call not connected outgoing call")
                    val callStatus = getOutGoingCallStatus()
                    updateStatus(callStatus)
                } else {
                    LogMessage.d(LOG_TAG, "service call not connected incoming call")
                    callRetryLayout!!.visibility = View.GONE
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            LogMessage.i(LOG_TAG, "service disconnected")
            mService = null
        }
    }

    private fun getOutGoingCallStatus(): String {
        return if (getCallStatus(getLocalUserJid()).isEmpty()
            || getCallStatus(getLocalUserJid()) == CallStatus.DISCONNECTED
            || getCallStatus(getLocalUserJid()) == CallStatus.CONNECTING)
                getString(R.string.trying_to_connect)
        else
            getCallStatus(getLocalUserJid())
    }

    /**
     * When user doesn't respond to video call request for 20 seconds
     * local toast is shown
     */
    private val outgoingRequestRunnable = Runnable {
        LogMessage.d(LOG_TAG, "outgoingRequestRunnable no response fron end user")
        outGoingRequest = false
        requestingDialog!!.dismiss()
        muteVideoImage.isActivated = false
        convertTileViewToMinimizedView(true)
        CallManager.muteVideo(true)
        Toast.makeText(
            context,
            "No response from " + ContactManager.getDisplayName(getEndCallerJid()),
            Toast.LENGTH_LONG
        ).show()
    }

    private val hideOptionsRunnable = Runnable {
        LogMessage.d(LOG_TAG, "hideOptionsRunnable")
        animateCallOptionsView()
    }

    private val resizeRunnable = Runnable {
        LogMessage.d(LOG_TAG, "resizeRunnable")
        resizeView()
    }

    /**
     * shows buttons to call again or cancel the action.̥
     */
    fun showCallAgainView() {
        LogMessage.d(LOG_TAG, "showCallAgainView")
        callRetryLayout!!.visibility = View.VISIBLE
        callOptionsLayout!!.visibility = View.GONE
        imgMinimizeCall.visibility = View.GONE
        if (isOneToOneCall()) {
            callRetryText!!.visibility = View.GONE
        } else {
            callRetryText!!.visibility = View.VISIBLE
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogMessage.d(LOG_TAG, "onCreate")
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_group_call)
        CallManager.configureCallActivity(this)
        if (intent.extras != null) {
            callDirection = intent.getStringExtra(WebRtcCallService.EXTRA_CALL_DIRECTION)
            groupId = intent.getStringExtra(WebRtcCallService.EXTRA_GROUP_ID)
            callUsers = intent.getStringArrayListExtra(WebRtcCallService.EXTRA_USERS)
            if (callUsers != null)
                callUsers!!.remove(getLocalUserJid())
        }
        initViews()
        setUpCallSwipeButton()
        setUpCallUI()
        showCallSwitchAlert()
        showAlert()
        showRequestingAlert()
        if (getCallType() == CallType.AUDIO_CALL) {
            updateVideoViews(getLocalUserJid())
        }
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.


        /* check permissions */
        if (getCallType() == CallType.VIDEO_CALL && !(MediaPermissions.isPermissionAllowed(this, Manifest.permission.RECORD_AUDIO)
                        && MediaPermissions.isPermissionAllowed(this, Manifest.permission.CAMERA))) {
                MediaPermissions.requestVideoCallPermissions(this, com.contus.flycommons.Constants.CAMERA_PERMISSION_CODE)
        }
        if (getCallType() == CallType.AUDIO_CALL) {
            imgCallerPicture!!.visibility = View.VISIBLE
            callAgainText.setCompoundDrawablesWithIntrinsicBounds(
                0,
                R.drawable.ic_group_call_again,
                0,
                0
            )
        } else {
            callAgainText.setCompoundDrawablesWithIntrinsicBounds(
                0,
                R.drawable.ic_group_video_call_again,
                0,
                0
            )
        }
        getProfile(GroupCallUtils.getCallUsersList())
        //register for call events
        CallManager.setCallEventsListener(customCallEventsListener)

        heightEnd = CommonUtils.convertDpToPixel(this, 150)
        widthEnd = CommonUtils.convertDpToPixel(this, 120)
        //  Configuration configuration = getResources().getConfiguration();
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        actualScreenHeight = displayMetrics.heightPixels
        actualScreenWidth = displayMetrics.widthPixels

        callUsersAdapter.setScreenHeight(actualScreenHeight)
        callUsersAdapter.setScreenWidth(actualScreenWidth)
        if (isCallConversionRequestAvailable()) {
            handleIncomingRequest()
        }
        //check active internet connection
        lifecycleScope.launch(Dispatchers.IO) {
            if (!hasActiveInternet()) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.fly_error_msg_no_internet))
                }
            }
        }
    }

    /**
     * Alert for call switch
     */
    private fun showCallSwitchAlert() {
        LogMessage.d(LOG_TAG, "showCallSwitchAlert")
        val mBuilder = AlertDialog.Builder(this, R.style.AlertDialogStyle)
        if(getEndCallerJid().contains("@") && getEndCallerJid().length > 0) {
            mBuilder.setMessage(ContactManager.getDisplayName(getEndCallerJid()) + " requesting to switch to video call")
        }else{
            mBuilder.setMessage("requesting to switch to video call")
        }
        mBuilder.setPositiveButton(
            context!!.getString(R.string.fly_info_call_notification_accept)
        ) { _: DialogInterface?, _: Int ->
            if (MediaPermissions.isPermissionAllowed(context, Manifest.permission.CAMERA)) {
                acceptVideoCallSwitch()
            } else {
                MediaPermissions.requestCameraPermission(this, ACCEPT_VIDEO_CALL_SWITCH)
            }
        }
        mBuilder.setNegativeButton(
            context!!.getString(R.string.fly_info_call_notification_decline)
        ) { _: DialogInterface?, _: Int ->
            LogMessage.d(LOG_TAG, "showCallSwitchAlert Decline")
            switchCameraImage.isEnabled = false
            sendCallResponse(false)
            callSwitchAlert!!.dismiss()
        }
        callSwitchAlert = mBuilder.create()
        if (callSwitchAlert?.window != null) {
            callSwitchAlert?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        callSwitchAlert!!.setCancelable(false)
    }

    private fun acceptVideoCallSwitch() {
        val isRequestAvailable = isCallConversionRequestAvailable()
        LogMessage.d(LOG_TAG, "showCallSwitchAlert Accept: $isRequestAvailable")
        if (isCallConversionRequestAvailable()) {
            callSwitchAlert!!.dismiss()
            muteVideoImage.isActivated = true
            animateCallOptionsView()
            convertTileViewToMinimizedView(false)
            txtCallAttendStatus!!.gone()
            callInfoLayout!!.visibility = View.GONE
            txtCallStatus!!.visibility = View.GONE
            txtCallerName!!.visibility = View.GONE
            txtGroupName!!.visibility = View.GONE
            switchCameraImage.visibility = View.VISIBLE
            switchCameraImage.isEnabled = true
            sendCallResponse(true)
            updateCallUIByType()
            updateVideoViews(getEndCallerJid())
        }
    }

    /**
     * Video Call switch request accepted
     */
    private fun switchToVideoCall() {
        LogMessage.d(LOG_TAG, "switchToVideoCall")
        requestingDialog!!.dismiss()
        muteVideoImage.isActivated = true
        callUsersRecyclerView.visibility = View.VISIBLE
        txtCallAttendStatus!!.gone()
        callInfoLayout!!.visibility = View.GONE
        txtCallStatus!!.visibility = View.GONE
        txtCallerName!!.visibility = View.GONE
        txtGroupName!!.visibility = View.GONE
        switchCameraImage.visibility = View.VISIBLE
        switchCameraImage.isEnabled = true
        sendCallResponse(true)
        CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
        durationHandler.removeCallbacks(outgoingRequestRunnable)
        callSwitchAlert!!.dismiss()
        if (callSwitchConfirmationAlert != null && callSwitchConfirmationAlert!!.isShowing) {
            callSwitchConfirmationAlert!!.dismiss()
        }
        inComingRequest = false
        outGoingRequest = false
        // while switching to video call simultaneously for the second time, we need to update the
        // views from here
        updateViews()
        updateVideoViews(getEndCallerJid())
    }

    /**
     * show Call Switch Confirmation Alert
     */
    private fun showAlert() {
        LogMessage.d(LOG_TAG, "showAlert")
        val mBuilder = AlertDialog.Builder(this, R.style.AlertDialogStyle)
        mBuilder.setMessage("Are you sure you want to switch to Video call?")
        mBuilder.setPositiveButton(
            context!!.getString(R.string.action_switch)
        ) { _: DialogInterface?, _: Int ->
            outGoingRequest = true
            inComingRequest = isCallConversionRequestAvailable()
            callSwitchConfirmationAlert!!.dismiss()
            requestingDialog!!.show()
            animateCallOptionsView()
            callUsersAdapter.removeUser(getLocalUserJid())
            resizeView()
            durationHandler.postDelayed(outgoingRequestRunnable, 20000)
            muteVideoImage.isActivated = true
            CallManager.requestVideoCallSwitch()
        }
        mBuilder.setNegativeButton(
            context!!.getString(R.string.action_cancel)
        ) { _: DialogInterface?, _: Int ->
            outGoingRequest = false
            callSwitchConfirmationAlert!!.dismiss()
        }
        callSwitchConfirmationAlert = mBuilder.create()
        if (callSwitchConfirmationAlert?.window != null) {
            callSwitchConfirmationAlert?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        callSwitchConfirmationAlert!!.setCancelable(false)
    }

    /**
     * Update mute status to the adapter to show or hide video view
     */
    private fun showOrHideSurfaceViews() {
        LogMessage.d(LOG_TAG, "showOrHideSurfaceViews: ${callUsersAdapter.callUserList != null}")
        if (callUsersAdapter.callUserList != null) {
            val bundle = Bundle()
            bundle.putInt(CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED, 1)
            bundle.putInt(CallActions.NOTIFY_VIEW_MUTE_UPDATED, 1)
            callUsersAdapter.notifyItemRangeChanged(0, callUsersAdapter.callUserList!!.size, bundle)
        }
    }

    /**
     * Release video view surface while disconnect the call
     */
    private fun releaseSurfaceViews() {
        LogMessage.d(
            LOG_TAG,
            "#surface releaseSurfaceViews: localview:${videoLocalView != null} remoteView: ${callUsersAdapter.callUserList != null}"
        )
        if (videoLocalView != null) {
            videoLocalView!!.release()
        }
        if (callUsersAdapter.callUserList != null) {
            val bundle = Bundle()
            bundle.putInt(CallActions.NOTIFY_REMOTE_VIEW_RELEASE, 1)
            callUsersAdapter.notifyItemRangeChanged(0, callUsersAdapter.callUserList!!.size, bundle)
        }
    }

    /**
     * Set mirror view to the adapter In case local video swapped with remote video view
     */
    private fun setMirrorLocalView(isMirror: Boolean) {
        LogMessage.d(LOG_TAG, "setMirrorLocalView: $isMirror")
        if (isOneToOneCall()) {
            LogMessage.d(
                LOG_TAG,
                "setMirrorLocalView: isOneToOneCall: ${isOneToOneCall()} isSwappedFeeds:${isSwappedFeeds}"
            )
            setMirrorViewForOneToOneCall(isMirror)
        } else {
            LogMessage.d(
                LOG_TAG,
                "setMirrorLocalView: remote view: ${callUsersAdapter.callUserList != null}"
            )
            setMirrorViewForGroupCall(isMirror)
        }
    }

    private fun setMirrorViewForGroupCall(isMirror: Boolean) {
        if (callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(getLocalUserJid())
            if (index > -1) {
                updateMirrorViewInAdapter(index, isMirror)
            }
        }
    }

    private fun setMirrorViewForOneToOneCall(isMirror: Boolean) {
        if (isSwappedFeeds) {
            if (callUsersAdapter.callUserList != null) {
                updateMirrorViewInAdapter(0, isMirror)
            }
        } else {
            videoLocalView!!.setMirror(isMirror)
        }
    }

    private fun updateMirrorViewInAdapter(index: Int, isMirror: Boolean) {
        val bundle = Bundle()
        if (isMirror)
            bundle.putInt(CallActions.NOTIFY_LOCAL_VIEW_MIRROR, 1)
        else
            bundle.putInt(CallActions.NOTIFY_LOCAL_VIEW_NOT_MIRROR, 1)
        callUsersAdapter.notifyItemChanged(index, bundle)
    }

    /**
     * Hide local small video view in adapter while mute the local video
     */
    private fun hideLocalVideoView() {
        LogMessage.d(LOG_TAG, "hideLocalVideoView: ${callUsersAdapter.callUserList != null}")
        if (callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(getLocalUserJid())
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_REMOTE_VIEW_HIDE, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    /**
     * Show local small video view in adapter while unmute the local video
     */
    private fun showLocalVideoView() {
        LogMessage.d(LOG_TAG, "showLocalVideoView: ${callUsersAdapter.callUserList != null}")
        if (callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(getLocalUserJid())
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_REMOTE_VIEW_SHOW, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    /**
     * When 3rd user added/removed in Video call, local small video view will appear or disappear in UI.
     */
    private fun convertTileViewToMinimizedView(isTileView: Boolean) {
        LogMessage.d(LOG_TAG, "convertTileViewToMinimizedView: $isTileView")
        if (isTileView) {
            videoLocalView!!.visibility = View.GONE
            muteLayout!!.visibility = View.GONE
            callUsersAdapter.addUser(getLocalUserJid())
            durationHandler.removeCallbacks(resizeRunnable)
            durationHandler.postDelayed(resizeRunnable, 500)
            unSwapFeeds()
        } else {
            callUsersAdapter.removeUser(getLocalUserJid())
            resizeView()
            videoLocalView!!.visibility = View.VISIBLE
            CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
            setMuteStatusText()
        }
    }

    /**
     * Swap local video view back to small view
     */
    private fun unSwapFeeds() {
        LogMessage.d(LOG_TAG, "unSwapFeeds")
        val bundle = Bundle()
        bundle.putInt(CallActions.NOTIFY_UN_SWAP_VIDEO_SINK, 1)
        callUsersAdapter.notifyItemChanged(0, bundle)
    }

    /**
     * Alert dialog shown to user once call switch conformation is done
     */
    private fun showRequestingAlert() {
        LogMessage.d(LOG_TAG, "showRequestingAlert")
        val mBuilder = AlertDialog.Builder(this, R.style.AlertDialogStyle)
        mBuilder.setMessage("Requesting to switch to video call.")
        mBuilder.setPositiveButton(
            context!!.getString(R.string.action_cancel)
        ) { _: DialogInterface?, _: Int ->
            outGoingRequest = false
            requestingDialog!!.dismiss()
            durationHandler.removeCallbacks(outgoingRequestRunnable)
            muteVideoImage.isActivated = false
            convertTileViewToMinimizedView(true)
            CallManager.cancelVideoCallSwitchRequest()
        }
        requestingDialog = mBuilder.create()
        if (requestingDialog?.window != null) {
            requestingDialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        requestingDialog!!.setCancelable(false)
    }

    /**
     * send call request responce
     */
    private fun sendCallResponse(callAccepted: Boolean) {
        LogMessage.d(LOG_TAG, "sendCallResponse $callAccepted")
        if (callAccepted) {
            videoLocalView!!.visibility = View.VISIBLE
            CallManager.acceptVideoCallSwitchRequest()
        } else {
            videoLocalView!!.visibility = View.GONE
            CallManager.declineVideoCallSwitchRequest()
        }
        showOrHideSurfaceViews()
    }

    /**
     * Update call UI based call type and the connected users
     */
    private fun updateCallUIByType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            return
        if (isOneToOneCall()) {
            updateCallUIOneToOneCall()
        } else {
            updateCallUIGroupCall()
        }
    }

    private fun updateCallUIGroupCall() {
        LogMessage.d(LOG_TAG, "updateCallUI to GroupCall")
        setOverlayBackground(!isCallConnected())
        callImage!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.logo_group_call))
        callRetryLayout!!.setBackgroundColor(
            ContextCompat.getColor(
                context!!,
                R.color.caller_background
            )
        )
        if (isCallConnected()) {
            txtCallerName!!.visibility = View.VISIBLE
            callOptionsLayout!!.visibility = View.VISIBLE
            muteVideoImage.isActivated = !GroupCallUtils.isVideoMuted()
            if (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL) {
                muteLayout!!.visibility = View.VISIBLE
                setMuteStatusText()
                if (!getUserConnectedInCall(getEndCallerJid()) || getCallStatus(getLocalUserJid()) == CallStatus.ON_HOLD) {
                    txtCallStatus!!.text =
                        if (getCallStatus(getLocalUserJid()) == CallStatus.ON_HOLD) CallStatus.ON_HOLD else getCallStatus(
                            getEndCallerJid()
                        )
                    callDetailsLayout!!.visibility = View.GONE
                    callInfoLayout!!.visibility = View.VISIBLE
                } else {
                    callInfoLayout!!.visibility = View.GONE
                    callDetailsLayout!!.visibility = View.VISIBLE
                }
            } else {
                callInfoLayout!!.visibility = View.GONE
                callDetailsLayout!!.visibility = View.VISIBLE
                muteLayout!!.visibility = View.GONE
            }
        }
    }

    private fun updateCallUIOneToOneCall() {
        callRetryLayout!!.setBackgroundColor(Color.TRANSPARENT)
        if (getCallType() == CallType.AUDIO_CALL) {
            updateCallUIOneToOneAudioCall()
        } else if (getCallType() == CallType.VIDEO_CALL) {
            updateCallUIOneToOneVideoCall()
        }
    }

    private fun updateCallUIOneToOneVideoCall() {
        LogMessage.d(LOG_TAG, "updateCallUI to OneToOneVideoCall")
        setOverlayBackground(!isCallConnected())
        callImage!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.logo_video_call))

        if (isCallConnected()) {
            if (getUserConnectedInCall(getEndCallerJid()) && getCallStatus(getLocalUserJid()) != CallStatus.ON_HOLD) {
                setMuteStatusText()
                txtCallStatus!!.text =
                    if (getCallStatus(getLocalUserJid()) == CallStatus.ON_HOLD) CallStatus.ON_HOLD else getCallStatus(
                        getEndCallerJid()
                    )
                muteLayout!!.visibility = View.VISIBLE
                callDetailsLayout!!.visibility = View.GONE
                callInfoLayout!!.visibility = View.GONE
                txtCallerName!!.visibility = View.GONE
                callOptionsLayout!!.visibility = View.VISIBLE
            } else {
                txtCallStatus!!.text =
                    if (getCallStatus(getLocalUserJid()) == CallStatus.ON_HOLD) CallStatus.ON_HOLD else getCallStatus(
                        getEndCallerJid()
                    )
                muteLayout!!.visibility = View.GONE
                callDetailsLayout!!.visibility = View.GONE
                callInfoLayout!!.visibility = View.VISIBLE
                txtCallerName!!.visibility = View.VISIBLE
                callOptionsLayout!!.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCallUIOneToOneAudioCall() {
        LogMessage.d(LOG_TAG, "updateCallUI to OneToOneAudioCall")
        overlayView!!.background = ContextCompat.getDrawable(this, R.drawable.bg_audio_call)
        callImage!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.logo_call))

        if (isCallConnected()) {
            setMuteStatusText()
            if (getCallStatus(getEndCallerJid()) == CallStatus.CALLING ||
                getCallStatus(getEndCallerJid()) == CallStatus.RINGING
            ) {
                updateStatus(getCallStatus(getEndCallerJid()))
            }
            muteLayout!!.visibility = View.VISIBLE
            callDetailsLayout!!.visibility = View.GONE
            callInfoLayout!!.visibility = View.VISIBLE
            txtCallerName!!.visibility = if(groupId.isNullOrEmpty()) View.GONE else View.VISIBLE
            durationHandler.removeCallbacks(hideOptionsRunnable)
            callOptionsLayout!!.visibility = View.VISIBLE
        }
    }

    /**
     * This method is getting the caller name and profile picture
     *
     * @param callUsers listof Users in Call
     */
    private fun getProfile(callUsers: ArrayList<String>) {
        LogMessage.d(LOG_TAG, "getProfile $callUsers")
        var name = StringBuilder("")
        if (isOneToOneCall()) {
            val profileDetails = ContactManager.getProfileDetails(getEndCallerJid())
            if (profileDetails != null) {
                imgCallerPicture!!.loadUserProfileImage(context!!, profileDetails)
                name = StringBuilder(profileDetails.name)
            }
            LogMessage.d(LOG_TAG, "getProfile name: $name")
            txtGroupName!!.text = name.toString()
            txtCallerName!!.text = name.toString()
            txtAttendUser!!.text = name.toString()
            txtCallerName!!.visibility = if(groupId.isNullOrEmpty()) View.GONE else View.VISIBLE
        } else {
            name = GroupCallUtils.getCallUsersName(callUsers)
            txtCallerName!!.visibility = View.VISIBLE
            txtCallerName!!.text = name.toString()
            txtAttendUser!!.text = name.toString()
        }

        setGroupInfo(groupId)
    }

    private fun setGroupInfo(groupId: String?) {
        LogMessage.d(LOG_TAG, "setGroupInfo groupId: $groupId")
        if (groupId == null || groupId.isNullOrEmpty()) {
            return
        }
        val profileDetails = ContactManager.getProfileDetails(groupId)
        //   Checks the group information is not null
        if (profileDetails != null) {
            txtGroupName!!.text = Utils.returnEmptyStringIfNull(profileDetails.nickName)
            txtAttendUser!!.text = Utils.returnEmptyStringIfNull(profileDetails.nickName)
            txtPipGroupName!!.text = Utils.returnEmptyStringIfNull(profileDetails.nickName)
            imgCallerPicture!!.loadUserProfileImage(context!!, profileDetails)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LogMessage.i(LOG_TAG, "onNewIntent()")
        if (intent.extras != null) {
            callDirection = getIntent().getStringExtra(WebRtcCallService.EXTRA_CALL_DIRECTION)
            groupId = getIntent().getStringExtra(WebRtcCallService.EXTRA_GROUP_ID)
            callUsers = getIntent().getStringArrayListExtra(WebRtcCallService.EXTRA_USERS)
            LogMessage.d(
                LOG_TAG,
                "onNewIntent() GroupId: ${getIntent().getStringExtra(WebRtcCallService.EXTRA_GROUP_ID)}"
            )
            if (callUsers != null)
                callUsers!!.remove(getLocalUserJid())
        }
        setUpCallSwipeButton()
        setUpCallUI()
        getProfile(GroupCallUtils.getCallUsersList())
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        if (getCallType() == CallType.VIDEO_CALL) {
            bindService(
                Intent(this, WebRtcCallService::class.java), mServiceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
        if (isCallConversionRequestAvailable()) {
            handleIncomingRequest()
        }
    }

    private fun handleIncomingRequest() {
        LogMessage.d(LOG_TAG, "handleIncomingRequest()")
        callSwitchAlert!!.show()
        CallAudioManager.getInstance(this).playIncomingRequestTone()
        updateCallUIByType()
        if (callSwitchConfirmationAlert != null && callSwitchConfirmationAlert!!.isShowing) {
            callSwitchConfirmationAlert!!.dismiss()
        }
        inComingRequest = isCallConversionRequestAvailable()
        if (inComingRequest && outGoingRequest) {
            switchToVideoCall()
        }
    }

    override fun onStart() {
        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        LogMessage.d(LOG_TAG, "onStart()")
        bindService(
            Intent(this, WebRtcCallService::class.java), mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
        super.onStart()
        if (getCallType() == CallType.AUDIO_CALL &&
            !MediaPermissions.isPermissionAllowed(this, Manifest.permission.RECORD_AUDIO)
        ) {
            /* check permissions */
            MediaPermissions.requestAudioCallPermissions(
                this,
                com.contus.flycommons.Constants.RECORD_AUDIO_CODE
            )
        }
    }

    public override fun onUserLeaveHint() {
        LogMessage.d(
            LOG_TAG,
            "onUserLeaveHint() isOneToOneAudioCall:${isOneToOneAudioCall()} getIsCallAgain:${getIsCallAgain()}"
        )
        if (!isOneToOneAudioCall()) {
            gotoPIPMode()
        }
        if (getIsCallAgain()) {
            cancelCallAgain()
        } else if (!isCallConnected() && callDirection == CallDirection.OUTGOING_CALL) {
            finish()
        }
    }

    override fun onBackPressed() {
        LogMessage.d(LOG_TAG, "onBackPressed()")
        if (getIsCallAgain()) {
            cancelCallAgain()
        }
        super.onBackPressed()
    }

    private fun gotoPIPMode() {
        LogMessage.d(
            LOG_TAG,
            "gotoPIPMode(): ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isCallConnected() && !isAddUsersToTheCall()}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isCallConnected() && !isAddUsersToTheCall()) {
            if (CommonUtils.isPipModeAllowed(this)) {
                if (mPictureInPictureParamsBuilder == null) {
                    mPictureInPictureParamsBuilder = PictureInPictureParams.Builder()
                }
                // Calculate the aspect ratio of the PiP screen.
                val aspectRatio = Rational(rootLayout.width, rootLayout.height)
                mPictureInPictureParamsBuilder!!.setAspectRatio(aspectRatio).build()
                val isSucces = enterPictureInPictureMode(mPictureInPictureParamsBuilder!!.build())
                if (isSucces) {
                    // Hide the call controls UI while going to picture-in-picture mode.
                    callOptionsLayout!!.visibility = View.GONE
                    imageCallOptionsUpArrow.visibility = View.GONE
                    imgMinimizeCall.visibility = View.GONE
                    callDetailsLayout!!.visibility = View.GONE
                    checkAddParticipantsAvailable()
                    setPIPModeByCallType()
                }
            } else {
                CommonUtils.openPipModeSettings(this)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        setPIPMode(isInPictureInPictureMode)
        LogMessage.d(
            LOG_TAG,
            "onPictureInPictureModeChanged() isInPictureInPictureMode $isInPictureInPictureMode"
        )
       if(!isInPictureInPictureMode) {
            mBackStackLost = true
            // Restore the full-screen UI.
            callOptionsLayout!!.visibility = View.VISIBLE
            callUsersRecyclerView.visibility = View.VISIBLE
            callUsersRecyclerView.setPadding(0, 0, 0, 0)
            imgMinimizeCall.visibility = View.VISIBLE
            setMuteStatusText()
            checkAddParticipantsAvailable()
            callUsersAdapter.setScreenHeight(actualScreenHeight)
            callUsersAdapter.setScreenWidth(actualScreenWidth)
            layoutPipMode!!.visibility = View.GONE

            resetPIPModeByCallType()
        }
    }

    private fun resetPIPModeByCallType() {
        if (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL) {
            LogMessage.d(LOG_TAG, "onPictureInPictureModeChanged() isVideoCall: true")
            val params = videoLocalView!!.layoutParams as RelativeLayout.LayoutParams
            params.height = heightEnd
            params.width = widthEnd
            /* align video view bottom in right-center of call options layout */
            val bottomMargin = callOptionsLayout!!.height
            params.setMargins(
                0,
                0,
                CommonUtils.convertDpToPixel(context!!, 20),
                bottomMargin
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
        } else {
            LogMessage.d(LOG_TAG, "onPictureInPictureModeChanged() isGroupCall: true")
            imgMinimizeCall.visibility = View.VISIBLE
            if (isCallConnected())
                updateCallUIByType()
        }
    }

    private fun setPIPModeByCallType() {
        if (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL) {
            LogMessage.d(LOG_TAG, "onPictureInPictureModeChanged() isVideoCall: true")
            callUsersRecyclerView.visibility = View.VISIBLE
            layoutPipMode!!.visibility = View.GONE
            val params = videoLocalView!!.layoutParams as RelativeLayout.LayoutParams
            params.height = heightEnd / 3
            params.width = widthEnd / 3
            /* align video view bottom in right-center of call options layout */
            params.setMargins(
                0, 0, CommonUtils.convertDpToPixel(context!!, 6),
                CommonUtils.convertDpToPixel(context!!, 6)
            )
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            videoLocalView!!.layoutParams = params
            callUsersAdapter.setScreenHeight(actualScreenHeight / 3)
            callUsersAdapter.setScreenWidth(actualScreenWidth / 3)
            resizeView()
            setMuteStatusText()
        } else {
            setPIPModeForGroupCall()
        }
    }

    private fun setPIPModeForGroupCall() {
        LogMessage.d(LOG_TAG, "onPictureInPictureModeChanged() isGroupCall: true")
        layoutPipMode!!.visibility = View.VISIBLE
        muteLayout!!.visibility = View.GONE
        if (getCallType() == CallType.AUDIO_CALL) {
            txtCallType!!.text = getString(R.string.audio_call)
            imgCallStatus!!.visibility = View.INVISIBLE
        } else {
            txtCallType!!.text = getString(R.string.video_call)
            imgCallStatus!!.visibility = View.INVISIBLE
        }
        if (isCallConnected()) {
            txtPipCallStatus!!.visibility = View.VISIBLE
        } else {
            txtPipCallStatus!!.visibility = View.GONE
        }
        callUsersRecyclerView.setPadding(20, 20, 20, 20)
        callUsersRecyclerView.visibility = View.GONE
    }

    override fun finish() {
        LogMessage.d(LOG_TAG, "finish()")
        if (!isCallConnected()) {
            if (mBackStackLost) {
                LogMessage.i(LOG_TAG, "Back stack has been lost ")
                // durationHandler!!.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) finishAndRemoveTask()
                startActivity(
                    Intent.makeRestartActivityTask(
                        ComponentName(
                            this,
                            DashboardActivity::class.java
                        )
                    )
                )
                //}, 500)
            } else super.finish()
        } else {
            if (!isOneToOneAudioCall() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                gotoPIPMode()
            } else {
                super.finish()
            }
            LogMessage.i(LOG_TAG, "Call is connected, so entering into pip mode ")
        }
    }

    /**
     * Here handling the video swap
     */
    private fun setSwappedFeeds(isSwappedFeeds: Boolean) {
        LogMessage.d(LOG_TAG, "setSwappedFeeds() isSwappedFeeds -> $isSwappedFeeds")
        if (isOneToOneCall() && minimizeAnimationDone) {
            this.isSwappedFeeds = isSwappedFeeds
            val bundle = Bundle()
            if (isSwappedFeeds) {
                if (!callUsersAdapter.callUserList.isNullOrEmpty() && CallManager.getRemoteProxyVideoSink(
                        callUsersAdapter.callUserList!![0]
                    ) != null
                ) {
                    CallManager.getRemoteProxyVideoSink(callUsersAdapter.callUserList!![0])!!
                        .setTarget(null)
                    CallManager.getRemoteProxyVideoSink(callUsersAdapter.callUserList!![0])!!
                        .setTarget(videoLocalView)
                }
                bundle.putInt(CallActions.NOTIFY_SWAP_VIDEO_SINK, 1)
                if (!isBackCamera) {
                    bundle.putInt(CallActions.NOTIFY_LOCAL_VIEW_MIRROR, 1)
                } else {
                    bundle.putInt(CallActions.NOTIFY_LOCAL_VIEW_NOT_MIRROR, 1)
                }
            } else {
                CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
                bundle.putInt(CallActions.NOTIFY_UN_SWAP_VIDEO_SINK, 1)
                bundle.putInt(CallActions.NOTIFY_LOCAL_VIEW_NOT_MIRROR, 1)
            }
            callUsersAdapter.notifyItemChanged(0, bundle)
            /* we don't need mirror view for back camera */
            videoLocalView!!.setMirror(!isBackCamera)
        }
    }

    /**
     * Here all the video views are initializing
     */
    private fun initVideoViews() {
        // re-initiating video views will causes {@link IllegalStateException},
        //so this flag is checked before initializing video views
        callUsersAdapter.setCallService(mService)
        if (isVideoViewsInitialized!!.compareAndSet(false, true)) {
            LogMessage.d(LOG_TAG, "initVideoViews()")
            videoLocalView?.let {
                if (getCallType() == CallType.AUDIO_CALL || !isOneToOneCall()) {
                    videoLocalView!!.visibility = View.GONE
                    updateUserAdded(getLocalUserJid())
                } else
                    videoLocalView!!.visibility = View.VISIBLE
                if (CallManager.getRootEglBase() != null) {
                    videoLocalView!!.init(CallManager.getRootEglBase()!!.eglBaseContext, null)
                    videoLocalView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    videoLocalView!!.setZOrderMediaOverlay(true)
                    /* setting Target SurfaceViews to VideoSinks  */
                    CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
                    videoLocalView!!.setMirror(true)
                } else {
                    finish()
                }
            }
        }
    }

    /**
     * initialize views here
     */
    private fun initViews() {
        LogMessage.d(LOG_TAG, "initViews()")
        layoutPipMode = findViewById(R.id.layout_pip_mode)
        txtCallType = findViewById(R.id.text_call_type)
        imgCallStatus = findViewById(R.id.img_call_status)
        txtPipCallStatus = findViewById(R.id.text_pip_call_status)
        txtPipGroupName = findViewById(R.id.text_pip_group_name)
        callSwipeButton = findViewById(R.id.btn_call_swipe)
        imageCallUpArrow1 = findViewById(R.id.image_call_up_arrow1)
        imageCallDownArrow1 = findViewById(R.id.image_call_down_arrow1)
        imageCallUpArrow2 = findViewById(R.id.image_call_up_arrow2)
        imageCallDownArrow2 = findViewById(R.id.image_call_down_arrow2)
        rippleBackground = findViewById(R.id.ripple_bg)
        imageCallOptionsUpArrow = findViewById(R.id.call_options_up_arrow)
        imageCallOptionsUpArrow.setOnClickListener(this)
        rootLayout = findViewById(R.id.root_layout)
        rootLayout.setOnClickListener(this)
        callUsersRecyclerView = findViewById(R.id.call_users_recyclerview)
        callUsersRecyclerView.setOnClickListener(this)
        overlayView = findViewById(R.id.view_overlay)
        overlayView!!.setOnClickListener(this)
        callInfoLayout = findViewById(R.id.layout_call_info)
        txtCallStatus = findViewById(R.id.text_call_status)
        txtGroupName = findViewById(R.id.text_group_name)
        txtAttendUser = findViewById(R.id.text_attend_user)
        txtCallerName = findViewById(R.id.text_caller_name)
        txtCallDetails = findViewById(R.id.text_call_details)
        txtCallAttendStatus = findViewById(R.id.text_call_attend_status)
        callOptionsLayout = findViewById(R.id.layout_call_options)
        callDetailsLayout = findViewById(R.id.layout_call_details)
        muteLayout = findViewById(R.id.layout_mute)
        mutedVideoImage = findViewById(R.id.image_video_muted)
        mutedAudioImage = findViewById(R.id.image_audio_muted)

        txtCallMute = findViewById(R.id.text_call_mute)
        muteAudioImage = findViewById(R.id.image_mute_audio)
        muteAudioImage.setOnClickListener(this)
        switchCameraImage = findViewById(R.id.image_switch_camera)
        switchCameraImage.setOnClickListener(this)
        muteVideoImage = findViewById(R.id.image_mute_video)
        muteVideoImage.setOnClickListener(this)
        switchAudioImage = findViewById(R.id.img_speaker)
        switchAudioImage.setOnClickListener(this)
        imageEndCall = findViewById(R.id.image_end_call)
        imageEndCall.setOnClickListener(this)
        callImage = findViewById(R.id.text_app_info)
        callIncomingLayout = findViewById(R.id.layout_call_incoming)
        imgCallReject = findViewById(R.id.image_call_reject)
        imgCallReject.setOnClickListener(this)
        imgCallAnswer = findViewById(R.id.image_call_answer)
        imgCallAnswer.setOnClickListener(this)
        imgAddUser = findViewById(R.id.image_add_user)
        imgAddUser.setOnClickListener(this)
        callRetryLayout = findViewById(R.id.layout_call_retry)
        callRetryText = findViewById(R.id.text_call_retry)
        val cancelText = findViewById<TextView>(R.id.text_cancel)
        cancelText.setOnClickListener(this)
        callAgainText = findViewById(R.id.text_call_again)
        callAgainText.setOnClickListener(this)
        imgCallerPicture = findViewById(R.id.img_profile_image)
        txtAudioCallDetails = findViewById(R.id.text_audio_call_details)
        imgMinimizeCall = findViewById(R.id.image_minimize_call)
        imgMinimizeCall.setOnClickListener(this)
        videoLocalView = findViewById(R.id.view_video_local)
        videoLocalView!!.setOnClickListener(this)
        startCallTime = System.currentTimeMillis() * 1000
        isVideoViewsInitialized = AtomicBoolean(false)

        callUsersRecyclerView.setHasFixedSize(true)

        callUsersAdapter = GroupCallAdapter(this)

        val gridLayoutManager =
            CustomStaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        // Set the layout manager
        callUsersRecyclerView.layoutManager = gridLayoutManager

        callUsersRecyclerView.adapter = callUsersAdapter

        if (isOneToOneCall()) {
            rootLayout.background = ContextCompat.getDrawable(this, R.drawable.bg_audio_call)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpCallSwipeButton() {
        LogMessage.d(LOG_TAG, "setUpCallSwipeButton()")
        rippleBackground!!.startRippleAnimation()
        /* start animating arrows */
        AnimationsHelper.animateArrows(
            imageCallUpArrow1,
            imageCallUpArrow2,
            imageCallDownArrow1,
            imageCallDownArrow2
        )
        rippleBackground!!.setOnTouchListener { view: View, motionEvent: MotionEvent ->
            val curY: Float
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    imageCallUpArrow1!!.visibility = View.GONE
                    imageCallUpArrow2!!.visibility = View.GONE
                    imageCallDownArrow1!!.visibility = View.GONE
                    imageCallDownArrow2!!.visibility = View.GONE
                    answerY = motionEvent.y
                    begin = true
                    oldMove = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    curY = motionEvent.y
                    view.scrollBy(view.scrollX, (answerY - curY).toInt())
                    oldMove -= answerY - curY
                    answerY = curY
                    if (oldMove < -100 || oldMove > 100) begin = false
                    if (curY.toInt() - imgCallAnswer.bottom < 100) {
                        callSwipeButton!!.drawable.setColorFilter(
                            Color.GREEN,
                            PorterDuff.Mode.SRC_ATOP
                        )
                    } else if ((imgCallReject.top - curY.toInt()) < 100) {
                        callSwipeButton!!.drawable.setColorFilter(
                            Color.RED,
                            PorterDuff.Mode.SRC_ATOP
                        )
                    }
                    actionClick(curY)
                }
                MotionEvent.ACTION_UP -> {
                    callSwipeButton!!.drawable.clearColorFilter()
                    view.scrollTo(view.scrollX, 0)
                    imageCallUpArrow1!!.visibility = View.VISIBLE
                    imageCallUpArrow2!!.visibility = View.VISIBLE
                    imageCallDownArrow1!!.visibility = View.VISIBLE
                    imageCallDownArrow2!!.visibility = View.VISIBLE
                }
            }
            true
        }
    }

    /**
     * This function is used to perform the click operation on [.imgCallAnswer] or
     * [.imgCallReject] when the [.callSwipeButton] is near those buttons.
     *
     * @param curY call swipe button [.callSwipeButton] y position
     */
    private fun actionClick(curY: Float) {
        LogMessage.d(LOG_TAG, "actionClick() curY:${curY}")
        if (curY <= imgCallAnswer.bottom && !begin) {
            /* we shouldn't perform click operation when view is disabled */
            if (imgCallAnswer.isEnabled) {
                imgCallAnswer.performClick()
            }
        } else if (curY >= imgCallReject.top && !begin) {
            /* we shouldn't perform click operation when view is disabled */
            if (imgCallReject.isEnabled) imgCallReject.performClick() else LogMessage.i(
                LOG_TAG,
                "Hangup button disabled,so skipping performClick"
            )
        }
    }

    /**
     * Setting the call UI
     */
    private fun setUpCallUI() {
        LogMessage.d(LOG_TAG, "setUpCallUI()")
        setMuteStatusText()
        if (getCallType() == CallType.VIDEO_CALL) {
            setVideoCallUI()
        } else {
            setAudioCallUI()
            switchCameraImage.visibility = View.GONE
        }
    }

    private fun setVideoCallUI() {
        LogMessage.d(LOG_TAG, "setVideoCallUI() callDirection: ${callDirection}")
        muteVideoImage.isActivated = true
        switchCameraImage.visibility = View.VISIBLE
        if (!isCallConnected() && !isCallAttended()) {
            if (callDirection == CallDirection.INCOMING_CALL) {
                LogMessage.d(LOG_TAG, "incoming videoCallUI: 1")
                showIncomingView()
            } else if (callDirection == CallDirection.OUTGOING_CALL) {
                showOutgoingView()
            }
        }
        callRetryLayout!!.visibility = View.GONE
        setAudioDeviceIcon(
            switchAudioImage, CallAudioManager.getInstance(context)
                .selectedAudioDevice
        )
    }

    /**
     * sets icon for the audio device image view based on the selected audio device
     *
     * @param audioDeviceIcon audio device image view
     * @param device          selected audio device
     * @param callType        call type
     */
    fun setAudioDeviceIcon(audioDeviceIcon: ImageView, @AudioDevice device: String?) {
        when (device) {
            AudioDevice.BLUETOOTH -> audioDeviceIcon.setImageResource(R.drawable.ic_device_bluetooth)
            AudioDevice.EARPIECE -> {
                audioDeviceIcon.setImageResource(R.drawable.ic_speaker_inactive)
                audioDeviceIcon.isActivated = false
            }
            AudioDevice.SPEAKER_PHONE -> {
                audioDeviceIcon.setImageResource(R.drawable.ic_speaker_active)
                audioDeviceIcon.isActivated = true
            }
            AudioDevice.WIRED_HEADSET -> audioDeviceIcon.setImageResource(R.drawable.ic_device_headset)
        }
    }

    private fun setAudioCallUI() {
        LogMessage.d(LOG_TAG, "setAudioCallUI()")
        if (!outGoingRequest) {
            muteVideoImage.isActivated = false
        }
        if (!isCallConnected()) {
            muteVideoImage.isEnabled = false
        }
        switchCameraImage.isEnabled = false
        if (!isCallConnected()) {
            callInfoLayout!!.visibility = View.VISIBLE
            updateCallUIByType()
        }
        if (callDirection == CallDirection.INCOMING_CALL) {
            if (!isCallConnected() && !isCallAttended()) {
                callIncomingLayout!!.visibility = View.VISIBLE
                txtCallStatus!!.text =
                    if (isOneToOneCall()) getString(R.string.incoming_audio_call) else getString(R.string.incoming_group_audio_call)
            } else {
                txtCallStatus!!.text = getCallStatus(getLocalUserJid())
                callOptionsLayout!!.visibility = View.VISIBLE
                if (isOneToOneCall())
                    txtAudioCallDetails!!.text = callDuration ?: "00:00"
                txtCallDetails!!.text = callDuration ?: "00:00"
                txtCallDetails!!.visibility = View.VISIBLE
            }
            callRetryLayout!!.visibility = View.GONE
        } else if (callDirection == CallDirection.OUTGOING_CALL) {
            callOptionsLayout!!.visibility = View.VISIBLE
            txtCallStatus!!.text = getCallStatus(getLocalUserJid())
            txtCallDetails!!.text = callDuration
            txtCallDetails!!.visibility = View.VISIBLE
        }
        setAudioDeviceIcon(
            switchAudioImage, CallAudioManager.getInstance(context)
                .selectedAudioDevice
        )
        overlayView!!.visibility = View.VISIBLE
    }

    /**
     * set mute status text based on remote user mute actions
     * @param userJid id of muted user
     */
    private fun setMuteStatus(userJid: String?) {
        if (!isCallConnected() || userJid == null) {
            LogMessage.i(LOG_TAG, "Skipping mute UI update, since call is not connected")
            return
        }
        LogMessage.d(LOG_TAG, "setMuteStatus() userJid:${userJid}")

        if (callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(userJid)
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_MUTE_UPDATED, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    /**
     * set vide mute status text based on remote user video mute actions
     * @param userJid id of video muted user
     */
    private fun setVideoMuteStatus(userJid: String?) {
        if (!isCallConnected() || userJid == null) {
            LogMessage.i(LOG_TAG, "Skipping video mute UI update, since call is not connected")
            return
        }

        LogMessage.d(LOG_TAG, "setVideoMuteStatus() userJid:${userJid}")
        if (callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(userJid)
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    /**
     * set mute status text based on remote user mute actions
     */
    private fun setMuteStatusText() {
        if (isSkipMuteStatus()) {
            LogMessage.i(
                LOG_TAG,
                "Skipping mute UI update, since call is not connected or not one to one call"
            )
            return
        }
        LogMessage.d(LOG_TAG, "setMuteStatusText()")
        setMuteStatusVisibility()
        if (isRemoteAudioMuted(getEndCallerJid()) && isRemoteVideoMuted(getEndCallerJid()) && getCallType() == CallType.VIDEO_CALL) {
            mutedAudioImage!!.visibility = View.VISIBLE
            mutedVideoImage!!.visibility = View.VISIBLE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_audio_and_video_mute),
                getProfileNameForMute(getEndCallerJid())
            )
        } else if (isRemoteAudioMuted(getEndCallerJid()) && isRemoteVideoPaused(getEndCallerJid()) && getCallType() == CallType.VIDEO_CALL) {
            mutedAudioImage!!.visibility = View.VISIBLE
            mutedVideoImage!!.visibility = View.VISIBLE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_audio_mute_and_video_pause),
                getProfileNameForMute(getEndCallerJid())
            )
        } else if (isRemoteAudioMuted(getEndCallerJid())) {
            mutedAudioImage!!.visibility = View.VISIBLE
            mutedVideoImage!!.visibility = View.GONE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_audio_mute),
                getProfileNameForMute(getEndCallerJid())
            )
        } else if (isRemoteVideoMuted(getEndCallerJid()) && getCallType() == CallType.VIDEO_CALL) {
            muteLayout!!.visibility = View.VISIBLE
            txtCallMute!!.visibility = View.VISIBLE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_video_mute),
                getProfileNameForMute(getEndCallerJid())
            )
            mutedVideoImage!!.visibility = View.VISIBLE
            mutedAudioImage!!.visibility = View.GONE
        } else if (isRemoteVideoPaused(getEndCallerJid()) && getCallType() == CallType.VIDEO_CALL) {
            muteLayout!!.visibility = View.VISIBLE
            txtCallMute!!.visibility = View.VISIBLE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_video_pause),
                getProfileNameForMute(getEndCallerJid())
            )
            mutedVideoImage!!.visibility = View.VISIBLE
            mutedAudioImage!!.visibility = View.GONE
        }
    }

    /**
     * This method obtains the user information from the jid.
     *
     * @param toUser the user jid.
     * @return the formatted name of the caller.
     */

    fun getProfileNameForMute(toUser: String): String? {
        val name = ContactManager.getDisplayName(toUser)
        return if (name.length < 35)
            name
        else {
            name.substring(0, 30) + "..."
        }
    }

    private fun isSkipMuteStatus(): Boolean {
        return !isCallConnected() || !isOneToOneCall()
    }

    private fun setMuteStatusVisibility() {
        if (!isRemoteAudioMuted(getEndCallerJid()) && !(isRemoteVideoMuted(getEndCallerJid()) ||
                    isRemoteVideoPaused(getEndCallerJid())) && getCallType() == CallType.VIDEO_CALL
        ) {
            muteLayout!!.visibility = View.GONE
            mutedVideoImage!!.visibility = View.GONE
            mutedAudioImage!!.visibility = View.GONE
            txtCallMute!!.text = ""
            return
        } else if (!isRemoteAudioMuted(getEndCallerJid())) {
            muteLayout!!.visibility = View.GONE
            mutedVideoImage!!.visibility = View.GONE
            mutedAudioImage!!.visibility = View.GONE
        } else {
            muteLayout!!.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                txtCallMute!!.visibility = View.GONE
            } else {
                txtCallMute!!.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Shows the out going call UI
     */
    private fun showOutgoingView() {
        LogMessage.d(LOG_TAG, "showOutgoingView()")
        showLocalVideoView()

        mutedVideoImage!!.visibility = View.GONE
        mutedAudioImage!!.visibility = View.GONE
        muteLayout!!.visibility = View.GONE
        overlayView!!.visibility = View.VISIBLE
        callInfoLayout!!.visibility = View.VISIBLE
        txtCallStatus!!.visibility = View.VISIBLE
        txtCallStatus!!.text = getString(R.string.trying_to_connect)
        callIncomingLayout!!.visibility = View.GONE
        callRetryLayout!!.visibility = View.GONE
        callOptionsLayout!!.visibility = View.VISIBLE
        getProfile(GroupCallUtils.getCallUsersList())
        updateCallUIByType()
    }

    /**
     * Shows the incoming call UI
     */
    private fun showIncomingView() {
        LogMessage.d(LOG_TAG, "showIncomingView()")
        showLocalVideoView()
        mutedVideoImage!!.visibility = View.GONE
        mutedAudioImage!!.visibility = View.GONE
        muteLayout!!.visibility = View.GONE
        overlayView!!.visibility = View.VISIBLE
        callInfoLayout!!.visibility = View.VISIBLE
        txtCallStatus!!.text =
            if (isOneToOneCall()) getString(R.string.incoming_video_call) else getString(R.string.incoming_group_video_call)
        callOptionsLayout!!.visibility = View.GONE
        callRetryLayout!!.visibility = View.GONE
        callIncomingLayout!!.visibility = View.VISIBLE
        callSwipeButton!!.setImageDrawable(
            ContextCompat.getDrawable(
                this,
                R.drawable.ic_group_call_swipe_button
            )
        )
        updateCallUIByType()
        getProfile(GroupCallUtils.getCallUsersList())
    }

    private fun setOverlayBackground(isEnable: Boolean) {
        LogMessage.d(LOG_TAG, "setOverlayBackground() isEnable:${isEnable}")
        if (isEnable) {
            if (getCallType() == CallType.VIDEO_CALL) {
                overlayView!!.setBackgroundColor(
                    ContextCompat.getColor(
                        context!!,
                        R.color.color_black_transparent
                    )
                )
            } else {
                overlayView!!.background =
                    ContextCompat.getDrawable(this, R.drawable.ic_bg_group_audio_call)
            }
        } else {
            overlayView!!.background = null
        }
    }

    /**
     * Show the connected UI
     */
    private fun showConnectedView() {
        LogMessage.d(LOG_TAG, "showConnectedView()")
        durationHandler.postDelayed(hideOptionsRunnable, 3000)
        callRetryLayout!!.visibility = View.GONE
        updateCallUIByType()
        checkAddParticipantsAvailable()
        txtCallAttendStatus!!.gone()
        if (getCallType() == CallType.AUDIO_CALL && !isOnVideoCall()) {
            muteVideoImage.isEnabled = true
            startTimer()
        }
        callUsersRecyclerView.visibility = View.VISIBLE
        callIncomingLayout!!.visibility = View.GONE
        callOptionsLayout!!.visibility = View.VISIBLE
        muteAudioImage.isActivated = isAudioMuted()

        checkAndAddLocalView()

        val userList = ArrayList<String>()
        for (userJid in getAvailableCallUsersList()) {
            if (callUsersAdapter.callUserList == null || !callUsersAdapter.callUserList!!.contains(
                    userJid
                )
            ) {
                userList.add(userJid)
            }
        }
        if (userList.isNotEmpty()) {
            callUsersAdapter.addUsers(userList)
        }
    }

    /**
     * Show the connected UI for PIP mode
     */
    private fun showPIP() {
        LogMessage.d(LOG_TAG, "showPIP()")
        layoutPipMode!!.visibility = View.VISIBLE
        txtPipCallStatus!!.visibility = View.VISIBLE
        callUsersRecyclerView.visibility = View.GONE
        imgMinimizeCall.visibility = View.GONE
        mutedVideoImage!!.visibility = View.GONE
        mutedAudioImage!!.visibility = View.GONE
        muteLayout!!.visibility = View.GONE
        setOverlayBackground(false)
        txtCallAttendStatus!!.visibility = View.GONE
        callInfoLayout!!.visibility = View.GONE
        callIncomingLayout!!.visibility = View.GONE
        callRetryLayout!!.visibility = View.GONE
        callOptionsLayout!!.visibility = View.GONE
    }

    /**
     * This method is used to set the Audio mute icons and also send a mute message
     */
    private fun toggleMic() {
        LogMessage.d(LOG_TAG, "toggleMic()")
        muteAudioImage.isActivated = !muteAudioImage.isActivated
        CallManager.muteAudio(muteAudioImage.isActivated)
        setMuteStatus(getLocalUserJid())
    }

    /**
     * This method is used to set the video mute icons and also send a mute message
     */
    private fun toggleVideoMute() {
        LogMessage.d(LOG_TAG, "toggleVideoMute()")
        if (isOneToOneCall()) {
            if (isCallConnected() && getCallType() == CallType.AUDIO_CALL) {
                if (isOnVideoCall()) {
                    muteVideoImage.isActivated = !muteVideoImage.isActivated
                    CallManager.muteVideo(!muteVideoImage.isActivated)
                } else {
                    hangVideoCall()
                }
            } else {
                muteVideoImage.isActivated = !muteVideoImage.isActivated
                switchCameraImage.isEnabled = muteVideoImage.isActivated
                CallManager.muteVideo(!muteVideoImage.isActivated)
            }
        } else {
            muteVideoImage.isActivated = !muteVideoImage.isActivated
            CallManager.muteVideo(!muteVideoImage.isActivated, object : CallActionListener {
                override fun onResponse(isSuccess: Boolean, message: String) {
                    LogMessage.d(LOG_TAG, "muteVideo onResponse()")
                    if (muteVideoImage.isActivated) {
                        switchCameraImage.show()
                        switchCameraImage.isEnabled = true
                        CallAudioManager.getInstance(context)
                            .setDefaultAudioDevice(AudioDevice.SPEAKER_PHONE)
                        showLocalVideoView()
                    } else {
                        switchCameraImage.gone()
                        switchCameraImage.isEnabled = false
                        CallAudioManager.getInstance(context)
                            .setDefaultAudioDevice(AudioDevice.EARPIECE)
                        hideLocalVideoView()
                    }
                }
            })
        }
    }

    private fun hangVideoCall() {
        LogMessage.d(LOG_TAG, "hangVideoCall()")
        if (!muteVideoImage.isActivated) {
            callSwitchConfirmationAlert!!.show()
            if (inComingRequest && outGoingRequest) {
                CallManager.muteVideo(!muteVideoImage.isActivated)
                callSwitchConfirmationAlert!!.dismiss()
            }
        } else {
            if (inComingRequest && outGoingRequest) {
                callSwitchConfirmationAlert!!.dismiss()
            } else {
                muteVideoImage.isActivated = !muteVideoImage.isActivated
                CallManager.muteVideo(!muteVideoImage.isActivated)
            }
        }
    }

    /**
     * This method shows the audio device selection UI
     */
    private fun showSelection() {
        LogMessage.d(LOG_TAG, "toggleSpeaker()")
        val audioDevices = CallManager.getAudioDevices()
        LogMessage.i(LOG_TAG, "handleSpeaker#audioDevices:$audioDevices")
        val devices = audioDevices.toTypedArray()
        if (devices.size <= 2) {
            if (devices.size <= 1) {
                return
            }
            val selectedDevice =
                if (CallAudioManager.getInstance(context).selectedAudioDevice == devices[0]) devices[1] else devices[0]
            setAudioDeviceIcon(switchAudioImage, selectedDevice)
            LogMessage.d(LOG_TAG, "handleSpeaker#choosendevice:$selectedDevice")
            CallAudioManager.getInstance(context).selectAudioDevice(selectedDevice)
            return
        }

        val builder = AlertDialog.Builder(this, R.style.AudioDevicesDialogStyle)
        builder.setItems(devices) { _: DialogInterface?, which: Int ->
            @AudioDevice val device = devices[which]
            setAudioDeviceIcon(switchAudioImage, device)
            LogMessage.d(LOG_TAG, "handleSpeaker#choosendevice:$device")
            CallManager.setAudioDevice(device)
        }
        val audioDevicesDialog = builder.create()
        if (audioDevicesDialog.window != null) audioDevicesDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        audioDevicesDialog.show()
    }

    /**
     * call answer method
     */
    @SuppressLint("MissingPermission")
    private fun answer() {
        LogMessage.d(LOG_TAG, "answer()")
        if (isAnswerCalled.compareAndSet(false, true)) {
            CallManager.answerCall(object : CallActionListener {
                override fun onResponse(isSuccess: Boolean, message: String) {
                    LogMessage.d(LOG_TAG, "answer() success: $isSuccess")
                }
            })
            showConnectedView()
        }
    }

    /**
     * update call connected view
     */
    private fun updateViews() {
        LogMessage.d(LOG_TAG, "updateViews()")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            if (!isOneToOneAudioCall()) {
                showPIP()
            }
        } else {
            showConnectedView()
        }

        setMuteStatusText()

        setAudioDeviceIcon(
            switchAudioImage, CallAudioManager.getInstance(context)
                .selectedAudioDevice
        )
    }

    /**
     * Update the call status details in UI
     *
     * @param callStatus message
     */
    fun updateStatus(callStatus: String) {
        LogMessage.d(LOG_TAG, "updateStatus(): $callStatus")
        txtCallStatus!!.visibility = View.VISIBLE
        txtCallStatus!!.text = callStatus
        if (getCallType() == CallType.VIDEO_CALL) {
            switchCameraImage.visibility = View.VISIBLE
        }
    }

    /**
     * After the video call is connected the video view will be placed near call options view
     */
    private fun updateVideoViews(userJid: String?) {
        LogMessage.d(LOG_TAG, "updateVideoViews():$userJid")
        if (!isActivityDestroyed(this)) {
            getProfile(getAvailableCallUsersList())
            callUsersRecyclerView.post { updateRemoteSink(userJid) }
            videoLocalView?.let {
                if (GroupCallUtils.getCallConnectedUsersList().size < 2) {
                    durationHandler.removeCallbacks(hideOptionsRunnable)
                    val params = videoLocalView!!.layoutParams as RelativeLayout.LayoutParams
                    params.height = heightEnd
                    params.width = widthEnd
                    // TODO: 07/07/21  try layout above in using rules
                    val rightMargin = CommonUtils.convertDpToPixel(context!!, 20)
                    /* align video view bottom in right-center of call options layout */
                    if (callOptionsLayout!!.visibility == View.VISIBLE) {
                        // once view measured, get height
                        callOptionsLayout!!.post {
                            val callOptionsHeight = callOptionsLayout!!.height
                            params.setMargins(0, 0, rightMargin, callOptionsHeight)
                            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                            videoLocalView!!.layoutParams = params
                            LogMessage.i(LOG_TAG, "Set video layout params on view post")
                        }
                    } else {
                        params.setMargins(0, 0, rightMargin, rightMargin)
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                        videoLocalView!!.layoutParams = params
                        LogMessage.i(LOG_TAG, "Set video layout params")
                    }
                } else {
                    LogMessage.i(LOG_TAG, "updateVideoViews skip one to one video layout update")
                }
            } ?: run {
                LogMessage.i(LOG_TAG, "updateVideoViews Skipping since Local View is NULL")
            }
        } else LogMessage.d(LOG_TAG, "updateVideoViews Activity Destroyed")
    }

    private fun updateRemoteSink(userJid: String?) {
        LogMessage.d(LOG_TAG, "updateRemoteSink for jid: $userJid")
        if (userJid != null && callUsersAdapter.callUserList != null) {
            val bundle = Bundle()
            if (userJid == getLocalUserJid()) {
                bundle.putString(CallActions.NOTIFY_CONNECT_TO_SINK, "")
                bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
                if (callUsersAdapter.callUserList!!.contains(userJid))
                    callUsersAdapter.notifyItemChanged(
                        callUsersAdapter.callUserList!!.indexOf(
                            userJid
                        ), bundle
                    )
            } else if (CallManager.getRemoteProxyVideoSink(userJid) != null) {
                bundle.putString(CallActions.NOTIFY_CONNECT_TO_SINK, userJid)
                bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
                if (callUsersAdapter.callUserList!!.contains(userJid))
                    callUsersAdapter.notifyItemChanged(
                        callUsersAdapter.callUserList!!.indexOf(
                            userJid
                        ), bundle
                    )
            }
        }
    }

    /**
     * animates the local video view to move down to bottom of the screen
     */
    private fun onCallOptionsHidden() {
        LogMessage.d(LOG_TAG, "onCallOptionsHidden()")
        val bottomMarginStart = callOptionsLayout!!.height // margin start value
        val bottomMarginTo = CommonUtils.convertDpToPixel(this, 20) // where to animate to
        val params = videoLocalView!!.layoutParams as RelativeLayout.LayoutParams
        if (videoLocalView!!.visibility == View.VISIBLE) {
            AnimationsHelper.animateViewWithValues(
                videoLocalView, bottomMarginStart,
                bottomMarginTo, 500
            ) { updatedValue: Int ->
                if (videoLocalView != null) {
                    params.setMargins(
                        0,
                        0,
                        CommonUtils.convertDpToPixel(this, 20),
                        updatedValue
                    )
                    videoLocalView!!.layoutParams = params
                } else {
                    LogMessage.d(
                        LOG_TAG,
                        "video view is null so skipping onCallOptionsHidden() animation"
                    )
                }
            }
        } else {
            params.setMargins(
                0,
                0,
                CommonUtils.convertDpToPixel(this, 20),
                bottomMarginStart
            )
            videoLocalView!!.layoutParams = params
        }
    }

    /**
     * animates the local video view to move up above [.callOptionsLayout]
     */
    private fun onCallOptionsVisible() {
        LogMessage.d(LOG_TAG, "onCallOptionsVisible()")
        val bottomMarginStart = CommonUtils.convertDpToPixel(this, 20) // margin start value
        val bottomMarginTo = callOptionsLayout!!.height // where to animate to
        videoLocalView?.let {
            val params = videoLocalView!!.layoutParams as RelativeLayout.LayoutParams
            if (videoLocalView!!.visibility == View.VISIBLE) {
                AnimationsHelper.animateViewWithValues(
                    videoLocalView, bottomMarginStart, bottomMarginTo,
                    500
                ) { updatedValue: Int ->
                    params.setMargins(
                        0,
                        0,
                        CommonUtils.convertDpToPixel(this, 20),
                        updatedValue
                    )
                    videoLocalView!!.layoutParams = params
                }
            } else {
                params.setMargins(
                    0,
                    0,
                    CommonUtils.convertDpToPixel(this, 20),
                    bottomMarginTo
                )
                videoLocalView!!.layoutParams = params
            }
        }

    }

    private fun disconnectCall(updateStatus: Boolean) {
        LogMessage.d(LOG_TAG, "disconnectCall()")
        if (isDisconnectCalled.compareAndSet(false, true)) {
            // The below code execution is guaranteed to be called only once
            shutVideoViews()
            inComingRequest = false
            outGoingRequest = false
            if (getCallType() == CallType.AUDIO_CALL) {
                setResult(AUDIO_CALL_REQUEST_CODE)
            } else {
                setResult(VIDEO_CALL_REQUEST_CODE)
            }
            durationHandler.removeCallbacks(updateTimerThread)
            durationHandler.removeCallbacks(hideOptionsRunnable)
            updateDisconnectedStatus(updateStatus)
        }
    }

    private fun updateDisconnectedStatus(updateStatus: Boolean) {
        if (isCallUIVisible(updateStatus)) {
            val animation = AnimationUtils.loadAnimation(context, R.anim.blink)
            if (isOneToOneCallVisible()) {
                callInfoLayout!!.visibility = View.VISIBLE
                txtCallStatus!!.visibility = View.VISIBLE
                callDetailsLayout!!.visibility = View.GONE
                txtCallStatus!!.startAnimation(animation)
            } else {
                animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
                callDetailsLayout!!.visibility = View.VISIBLE
                txtCallAttendStatus!!.visibility = View.VISIBLE
                callInfoLayout!!.visibility = View.GONE
                txtCallAttendStatus!!.startAnimation(animation)
            }
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    /* not needed */
                }

                override fun onAnimationEnd(animation: Animation) {
                    finish()
                }

                override fun onAnimationRepeat(animation: Animation) {
                    /* not needed */
                }
            })
        } else {
            finish()
        }
    }

    private fun isOneToOneCallVisible(): Boolean {
        return callDuration.isNullOrBlank() || (callUsersAdapter.callUserList == null || callUsersAdapter.callUserList!!.size < 3)
    }

    private fun isCallUIVisible(updateStatus: Boolean): Boolean {
        return updateStatus && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) && AppLifecycleListener.isForeground
    }

    private fun shutVideoViews() {
        LogMessage.d(LOG_TAG, "shutVideoViews()")
        releaseSurfaceViews()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (callDirection == CallDirection.INCOMING_CALL) CallAudioManager.getInstance(context)
                .stopRingTone()
            false
        } else super.onKeyDown(keyCode, event)
    }

    override fun onClick(v: View) {
        if (isDisconnectCalled.get()) {
            LogMessage.i(LOG_TAG, "Skipping onclick events")
            return
        }
        when (v.id) {
            R.id.view_video_local ->
                setSwappedFeeds(!isSwappedFeeds)
            R.id.root_layout, R.id.call_options_up_arrow, R.id.view_overlay -> if (isCallConnected()) {
                animateCallOptionsView()
            }
            R.id.image_mute_audio -> toggleMic()
            R.id.image_switch_camera -> {
                v.startAnimation(AnimationUtils.loadAnimation(applicationContext, R.anim.alpha))
                swapCamera()
            }
            R.id.image_mute_video -> {
                if (MediaPermissions.isPermissionAllowed(this, Manifest.permission.CAMERA)) {
                    toggleVideoMute()
                } else {
                    MediaPermissions.requestVideoCallPermissions(this, TOGGLE_VIDEO_MUTE_CODE)
                }
            }
            R.id.img_speaker -> showSelection()
            R.id.image_end_call -> {
                LogMessage.d(LOG_TAG, "toggleEnd()")
                imageEndCall.isEnabled = false
                CallManager.disconnectCall()
            }
            R.id.image_call_reject -> {
                LogMessage.d(LOG_TAG, "toggleReject()")
                imgCallReject.isEnabled = false
                CallManager.declineCall()
            }
            R.id.image_call_answer -> {
                LogMessage.d(LOG_TAG, "toggleAnswer()")
                imgCallAnswer.isEnabled = false
                imgCallReject.isEnabled = false
                /* check permissions */
                if (getCallType() == CallType.AUDIO_CALL && CallManager.isAudioCallPermissionsGranted() || getCallType() == CallType.VIDEO_CALL && CallManager.isVideoCallPermissionsGranted()) answer() else {
                    CallManager.declineCall()
                    Toast.makeText(
                        this,
                        getString(R.string.call_permission_denied),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            R.id.text_cancel -> cancelCallAgain()
            R.id.text_call_again -> makeCallAgain()
            R.id.image_minimize_call -> onBackPressed()
            R.id.image_add_user -> addUsersInCall()
        }
    }

    private fun addUsersInCall() {
        LogMessage.d(LOG_TAG, "addUsersInCall()")
        checkInternetAndExecute(true) {
            if (callUsersAdapter.callUserList != null && callUsersAdapter.callUserList!!.size < CallManager.getMaxCallUsersCount()) {
                GroupCallUtils.setIsAddUsersToTheCall(true)
                addParticipantFragment = AddParticipantFragment.newInstance(
                    groupId,
                    groupId.isNullOrEmpty(),
                    getAvailableCallUsersList()
                )
                val fragmentTransaction: FragmentTransaction =
                    supportFragmentManager.beginTransaction()
                fragmentTransaction.replace(
                    R.id.view_container,
                    addParticipantFragment,
                    addParticipantFragment.javaClass.name
                )
                fragmentTransaction.addToBackStack(null)
                fragmentTransaction.commit()
            }
        }
    }

    private fun checkAddParticipantsAvailable() {
        LogMessage.d(LOG_TAG, "checkAddParticipantsAvailable()")
        if (isCallConnected() && (getAvailableCallUsersList().size + 1) < CallManager.getMaxCallUsersCount()
            && isAllUsersInCallForGroup()
            && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            && CallConfiguration.isGroupCallEnabled()
        )
            imgAddUser.visibility = View.VISIBLE
        else
            imgAddUser.visibility = View.GONE
    }

    private fun isAllUsersInCallForGroup(): Boolean {
        return when {
            isOneToOneCall() -> true
            GroupCallUtils.getGroupId().isBlank() -> true
            else -> {
                val rostersList =
                    FlyDatabaseManager.ROSTER.getGroupUsersWithoutOwnRoster(GroupCallUtils.getGroupId())
                rostersList == null || rostersList.size > getAvailableCallUsersList().size
            }
        }
    }

    override fun onStop() {
        LogMessage.d(LOG_TAG, "onStop()")
        // Unbind from the service. This signals to the service that this activity is no longer
        // in the foreground, and the service can respond by promoting itself to a foreground
        // service.
        unbindService(mServiceConnection)
        super.onStop()
    }

    override fun onResume() {
        LogMessage.d(LOG_TAG, "onResume()")
        super.onResume()
        setupVideoCapture(this, false)
    }

    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        if (::addParticipantFragment.isInitialized && isAddUsersToTheCall()) {
             addParticipantFragment.refreshUsersList()
        }
    }

    /**
     * animates the call options layout with respect to it's visibility
     */
    private fun animateCallOptionsView() {
        LogMessage.d(LOG_TAG, "animateCallOptionsView()")
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            || isOneToOneAudioCall() || !isCallConnected() || isAddUsersToTheCall() || isReconnecting()
        )
            return

        if (isOneToOneCall()) {
            animateOneToOneCallOption()
        } else {
            animateGroupCallOption()
        }
    }

    private fun animateOneToOneCallOption() {
        LogMessage.d(LOG_TAG, "animateOneToOneCallOption()")
        if (callOptionsLayout!!.visibility == View.VISIBLE) {
            animateCallOptions(R.anim.slide_down, View.GONE, View.VISIBLE)
            animateCallDetails(R.anim.slide_out_up, View.GONE)
            durationHandler.removeCallbacks(hideOptionsRunnable)
        } else {
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
            animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
            durationHandler.postDelayed(hideOptionsRunnable, 3000)
        }
    }

    private fun animateGroupCallOption() {
        if (callOptionsLayout!!.visibility == View.VISIBLE && callDetailsLayout!!.visibility == View.VISIBLE) {
            animateCallOptions(R.anim.slide_down, View.GONE, View.GONE)
            animateCallDetails(R.anim.slide_out_up, View.GONE)
            durationHandler.removeCallbacks(hideOptionsRunnable)
        } else {
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
            animateCallDetails(R.anim.slide_out_down, View.VISIBLE)
            durationHandler.postDelayed(hideOptionsRunnable, 3000)
        }
    }

    /**
     * This method animates the call options layout with given animation
     *
     * @param animation             animation id
     * @param callOptionsVisibility visibility to be changed for callOptions view
     * @param arrowVisibility       visibility to be changed for arrow view
     */
    private fun animateCallOptions(
        animation: Int,
        callOptionsVisibility: Int,
        arrowVisibility: Int
    ) {
        LogMessage.d(LOG_TAG, "animateCallOptions callOptionsVisibility: $callOptionsVisibility")
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            || !isCallConnected() || isAddUsersToTheCall()
        )
            return
        val slideDownAnimation = AnimationUtils.loadAnimation(this, animation)
        callOptionsLayout!!.startAnimation(slideDownAnimation)
        slideDownAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                if (arrowVisibility == View.GONE) imageCallOptionsUpArrow.visibility =
                    arrowVisibility
            }

            override fun onAnimationEnd(animation: Animation) {
                if (!isOneToOneCall() || getCallType() == CallType.VIDEO_CALL) {
                    callOptionsLayout!!.visibility = callOptionsVisibility
                    GroupCallUtils.setIsCallOptionsVisible(callOptionsVisibility == View.VISIBLE)
                    imageCallOptionsUpArrow.visibility = arrowVisibility
                }
            }

            override fun onAnimationRepeat(animation: Animation) {
                /* not needed */
            }
        })
        if (callOptionsVisibility == View.VISIBLE) onCallOptionsVisible() else onCallOptionsHidden()
    }

    /**
     * This method animates the call options layout with given animation
     *
     * @param animation             animation id
     * @param callDetailsVisibility visibility to be changed for callDetails view
     */
    private fun animateCallDetails(animation: Int, callDetailsVisibility: Int) {
        val slideUpAnimation = AnimationUtils.loadAnimation(this, animation)
        callDetailsLayout!!.startAnimation(slideUpAnimation)
        slideUpAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                /* not needed */
            }

            override fun onAnimationEnd(animation: Animation) {
                callDetailsLayout!!.visibility = callDetailsVisibility
            }

            override fun onAnimationRepeat(animation: Animation) {
                /* not needed */
            }
        })
    }

    /**
     * Check the internet connectivity and send the call message
     */
    private fun makeCallAgain() {
        if (AppUtils.isNetConnected(this)) {
            LogMessage.d(LOG_TAG, "makeCallAgain()")
            hideCallAgainView()
            CallManager.makeCallAgain()
        } else {
            CustomToast.show(applicationContext, getString(R.string.fly_error_msg_no_internet))
            cancelCallAgain()
        }
    }

    private fun cancelCallAgain() {
        LogMessage.d(LOG_TAG, "cancelCallAgain()")
        CallManager.cancelCallAgain()
        disconnectCall(false)
    }

    /**
     * hides the call again view
     */
    private fun hideCallAgainView() {
        LogMessage.d(LOG_TAG, "hideCallAgainView()")
        updateStatus(getString(R.string.trying_to_connect))
        imgMinimizeCall.visibility = View.VISIBLE
        callRetryLayout!!.visibility = View.GONE
        callOptionsLayout!!.visibility = View.VISIBLE
    }

    /**
     * handles the swap camera functionality and animations
     *
     */
    private fun swapCamera() {
        LogMessage.d(LOG_TAG, "swapCamera()")
        CallManager.switchCamera()
        isBackCamera = !isBackCamera
        switchCameraImage.isActivated = isBackCamera
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == com.contus.flycommons.Constants.CAMERA_PERMISSION_CODE) {
            handleCameraPermissionResult(permissions, grantResults)
        } else if (requestCode == com.contus.flycommons.Constants.RECORD_AUDIO_CODE) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]
                if (permission == Manifest.permission.RECORD_AUDIO && grantResult != PackageManager.PERMISSION_GRANTED)
                    CallManager.sendCallPermissionDenied()
            }
        } else if (requestCode == TOGGLE_VIDEO_MUTE_CODE && !grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            toggleVideoMute()
        } else if (requestCode == ACCEPT_VIDEO_CALL_SWITCH && !grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            acceptVideoCallSwitch()
        }
    }

    private fun handleCameraPermissionResult(permissions: Array<String>, grantResults: IntArray) {
        for (i in permissions.indices) {
            val permission = permissions[i]
            val grantResult = grantResults[i]
            if ((permission == Manifest.permission.RECORD_AUDIO || permission == Manifest.permission.CAMERA)
                && grantResult != PackageManager.PERMISSION_GRANTED)
                CallManager.sendCallPermissionDenied()
        }
    }

    public override fun onDestroy() {
        LogMessage.d(LOG_TAG, "onDestroy  called()")
        CallManager.removeCallEventsListener(customCallEventsListener)
        super.onDestroy()
    }

    private fun startTimer() {
        /* Handled null exception */
        if (mService == null) return
        startTime = mService!!.callStartTime
        durationHandler.postDelayed(updateTimerThread, 0)
    }

    private fun updateMuteUI() {
        LogMessage.d(LOG_TAG, " updateMuteUI()")
        if (callUsersAdapter.callUserList!!.size < 2 && isRemoteAudioMuted(getEndCallerJid())) {
            muteLayout!!.visibility = View.VISIBLE
            mutedAudioImage!!.visibility = View.VISIBLE
            mutedVideoImage!!.visibility = View.GONE
            txtCallMute!!.visibility = View.VISIBLE
            txtCallMute!!.text = String.format(
                getString(R.string.action_remote_audio_mute),
                ContactManager.getDisplayName(getEndCallerJid())
            )
        }
        if (switchCameraImage.isActivated) {
            swapCamera()
        }
        if (getCallType() == CallType.AUDIO_CALL) {
            switchCameraImage.visibility = View.GONE
        } else if (getCallType() == CallType.VIDEO_CALL) {
            switchCameraImage.visibility = View.VISIBLE
        }
    }

    private fun updateStatusAdapter(userJid: String?) {
        LogMessage.d(LOG_TAG, " updateStatusAdapter userJid: $userJid")
        if (userJid != null && callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(userJid)
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
            }
        }
    }

    private fun updateStatusAndRemove(userJid: String?) {
        LogMessage.d(LOG_TAG, " updateStatusAndRemove userJid: $userJid")
        if (userJid != null && callUsersAdapter.callUserList != null) {
            val index = callUsersAdapter.callUserList!!.indexOf(userJid)
            if (index > -1) {
                val bundle = Bundle()
                bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
                callUsersAdapter.notifyItemChanged(index, bundle)
                durationHandler.postDelayed({
                    updateUserLeft(userJid)
                }, 500)
            }
        }
    }

    private fun handleCallVideoMessages(callAction: String, userJid: String) {
        when (callAction) {
            CallAction.ACTION_REMOTE_VIDEO_STATUS -> {
                setMuteStatusText()
                when {
                    !isOneToOneCall() -> setVideoMuteStatus(userJid)
                }
            }
            else -> handleCallConversionMessages(callAction)
        }
    }

    private fun handleCallConversionMessages(callAction: String) {
        when (callAction) {
            CallAction.ACTION_VIDEO_CALL_CONVERSION_ACCEPTED -> if (outGoingRequest) {
                videoLocalView!!.visibility = View.VISIBLE
                CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
                showOrHideSurfaceViews()
                callInfoLayout!!.visibility = View.GONE
                txtCallStatus!!.visibility = View.GONE
                txtCallerName!!.visibility = View.GONE
                txtGroupName!!.visibility = View.GONE
                switchCameraImage.visibility = View.VISIBLE
                switchCameraImage.isEnabled = true
                durationHandler.removeCallbacks(outgoingRequestRunnable)
                requestingDialog!!.dismiss()
                if (callSwitchAlert != null && callSwitchAlert!!.isShowing) {
                    callSwitchAlert!!.dismiss()
                }
                setMuteStatusText()
                updateCallUIByType()
                updateVideoViews(getEndCallerJid())
            }
            CallAction.ACTION_VIDEO_CALL_CONVERSION_REJECTED -> {
                animateCallOptionsView()
                convertTileViewToMinimizedView(true)
                muteVideoImage.isActivated = false
                switchCameraImage.isEnabled = false
                requestingDialog!!.dismiss()
                durationHandler.removeCallbacks(outgoingRequestRunnable)
                outGoingRequest = false
                setMuteStatusText()
                Toast.makeText(this, "Request declined", Toast.LENGTH_SHORT).show()
            }
            CallAction.ACTION_VIDEO_CALL_CANCEL_CONVERSION -> {
                inComingRequest = isCallConversionRequestAvailable()
                if (!inComingRequest && !outGoingRequest) {
                    CallAudioManager.getInstance(context).stopIncomingRequestTone()
                    callSwitchAlert!!.dismiss()
                    convertTileViewToMinimizedView(true)
                    switchCameraImage.isEnabled = false
                    txtCallStatus!!.visibility = View.VISIBLE
                    txtGroupName!!.visibility = View.VISIBLE
                    updateMuteUI()
                    setIsOnVideoCall(false)
                    CallAudioManager.getInstance(context).setDefaultAudioDevice(AudioDevice.EARPIECE)
                    inComingRequest = false
                    outGoingRequest = false
                }
            }
        }
    }

    private inner class CustomCallEventsListener : CallEventsListener {
        override fun onCallStatusUpdated(callStatus: String, userJid: String) {
            handleCallStatusMessages(callStatus, userJid)
        }

        override fun onCallAction(callAction: String, userJid: String) {
            handleCallActionMessages(callAction, userJid)
        }

        override fun onVideoTrackAdded(userJid: String) {
            LogMessage.d(LOG_TAG, "onVideoTrackAdded : $userJid")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                LogMessage.d(LOG_TAG, "updateVideoViews() skipped due to pip mode")
            } else {
                if(!isOneToOneCall()) {
                    updateViews()
                }
                updateVideoViews(userJid)
            }
        }

        override fun onMuteStatusUpdated(@MuteEvent muteEvent: String, userJid: String) {
            handleMuteEvents(muteEvent, userJid)
        }

        private fun handleMuteEvents(muteEvent: String, userJid: String) {
            LogMessage.d(LOG_TAG, "handleMuteEvents event: $muteEvent user: $userJid")
            when (muteEvent) {
                MuteEvent.ACTION_REMOTE_VIDEO_MUTE -> {
                    setMuteStatusText()
                    if (!isOneToOneCall()) {
                        setVideoMuteStatus(userJid)
                    }
                }
                MuteEvent.ACTION_REMOTE_VIDEO_UN_MUTE -> {
                    setVideoMuteStatus(userJid)
                    setMuteStatusText()
                }
                MuteEvent.ACTION_REMOTE_AUDIO_MUTE, MuteEvent.ACTION_REMOTE_AUDIO_UN_MUTE -> {
                    setMuteStatus(userJid)
                    setMuteStatusText()
                }
                else -> {
                    LogMessage.e(LOG_TAG, "unknown mute event")
                }
            }
        }

        private fun handleCallStatusMessages(@CallStatus callEvent: String, userJid: String) {
            LogMessage.d(LOG_TAG, " received call status: $callEvent")
            when (callEvent) {
                CallStatus.CONNECTED -> {
                    handleCallStatusConnected(callEvent, userJid)
                }
                CallStatus.DISCONNECTED -> {
                    disconnectCall(true)
                    updateStatus(callEvent)
                    updateStatusInTimer(callEvent)
                }
                CallStatus.CONNECTING -> {
                    updateStatusAdapter(userJid)
                }
                CallStatus.OUTGOING_CALL_TIME_OUT -> {
                    if (isCallConnected()) {
                        checkAndUpdateTimeoutUsers()
                    } else {
                        updateStatus(getString(R.string.call_try_again_info))
                        showCallAgainView()
                    }
                }
                CallStatus.INVITE_CALL_TIME_OUT -> {
                    checkAndUpdateTimeoutInviteUsers()
                }

                CallStatus.INCOMING_CALL_TIME_OUT -> {
                    disconnectCall(true)
                    updateStatus(CallStatus.DISCONNECTED)
                    updateStatusInTimer(CallStatus.DISCONNECTED)
                }
                else -> handleOtherCallStatusMessages(callEvent, userJid)
            }
        }

        private fun handleOtherCallStatusMessages(@CallStatus callEvent: String, userJid: String) {
            when (callEvent) {
                CallStatus.ON_RESUME -> {
                    handleCallStatusResume(userJid)
                }
                CallStatus.RECONNECTING -> {
                    if (isOneToOneCall() || (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL)) {
                        callInfoLayout!!.visibility = View.VISIBLE
                        callDetailsLayout!!.visibility = View.GONE
                        updateStatus(callEvent)
                        updateStatusInTimer(callEvent)
                        animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
                    } else {
                        checkAndUpdateReconnectingUsers()
                    }
                }
                CallStatus.ON_HOLD -> {
                    handleCallStatusOnHold(userJid, callEvent)
                }
                CallStatus.RINGING, CallStatus.CALLING_AFTER_10S -> {
                    handleCallStatus(callEvent, userJid)
                }
                CallStatus.RECONNECTED -> {
                    LogMessage.d(LOG_TAG, "getReconnectingUsersList Reconnected Called:")
                    handleCallStatusReconnected(userJid)
                }
                CallStatus.USER_JOINED -> {
                    updateUserJoined(userJid)
                }
                CallStatus.USER_LEFT -> {
                    updateUserLeft(userJid)
                }
            }
        }

        private fun handleCallActionMessages(callAction: String, userJid: String) {
            LogMessage.d(LOG_TAG, " received callAction: $callAction")
            when (callAction) {
                CallAction.ACTION_REMOTE_OTHER_BUSY -> {
                    getProfile(getAvailableCallUsersList())
                    updateStatusAndRemove(userJid)
                }
                CallAction.ACTION_REMOTE_HANGUP, CallAction.ACTION_PERMISSION_DENIED, CallAction.ACTION_DENY_CALL, CallAction.ACTION_LOCAL_HANGUP -> {
                    updateStatus(CallStatus.DISCONNECTED)
                    updateStatusInTimer(CallStatus.DISCONNECTED)
                    disconnectCall(true)
                }
                CallAction.ACTION_REMOTE_BUSY -> {
                    updateStatus("User Busy")
                    disconnectCall(true)
                }
                CallAction.ACTION_REMOTE_ENGAGED -> {
                    updateStatus("Call Engaged")
                    disconnectCall(true)
                }
                CallAction.ACTION_AUDIO_DEVICE_CHANGED -> {
                    setAudioDeviceIcon(
                        switchAudioImage, CallAudioManager.getInstance(context)
                            .selectedAudioDevice
                    )
                }
                CallAction.CHANGE_TO_AUDIO_CALL -> {
                    convertTileViewToMinimizedView(true)
                    txtCallStatus!!.visibility = View.VISIBLE
                    txtGroupName!!.visibility = View.VISIBLE
                    muteLayout!!.visibility = View.GONE
                    txtCallMute!!.visibility = View.GONE
                    muteVideoImage.isActivated = false
                    getProfile(getAvailableCallUsersList())
                    updateMuteUI()
                    switchCameraImage.isEnabled = false
                    inComingRequest = false
                    outGoingRequest = false
                    switchCameraImage.visibility = View.GONE
                    updateCallUIByType()
                    animateCallOptionsView()
                }
                CallAction.ACTION_INVITE_USERS -> {
                    for (inviteUserJid in GroupCallUtils.getInvitedUsersList()) {
                        if (getCallStatus(inviteUserJid) != CallStatus.DISCONNECTED)
                            callUsersAdapter.addUser(inviteUserJid)
                    }
                    GroupCallUtils.setIsAddUsersToTheCall(false)
                    getProfile(getAvailableCallUsersList())
                    checkAddParticipantsAvailable()
                    checkAndAddLocalView()
                    resizeView()
                    updateCallUIByType()
                }
                CallAction.ACTION_CAMERA_SWITCH_SUCCESS -> {
                    setMirrorLocalView(!isBackCamera)
                }
                CallAction.ACTION_CAMERA_SWITCH_FAILURE -> {
                    LogMessage.e(LOG_TAG, "Camera switch error occurred")
                }
                else -> handleCallVideoMessages(callAction, userJid)
            }
        }
    }


    private fun handleCallStatus(callStatus: String, userJid: String?) {
        if (isOneToOneCall() || (!isCallConnected() && getCallDirection() == CallDirection.OUTGOING_CALL)) {
            updateStatus(callStatus)
            updateStatusInTimer(callStatus)
            if (callStatus.equals(CallStatus.ON_HOLD, ignoreCase = true)) {
                animateCallOptionsView()
            }
        } else {
            updateStatusAdapter(userJid)
        }
        if (!callStatus.equals(CallStatus.RINGING, ignoreCase = true)) txtCallAttendStatus!!.show()
    }

    private fun handleCallStatusReconnected(userJid: String?) {
        updateStatus(CallStatus.CONNECTED)
        updateStatusAdapter(userJid)
        if (isOneToOneCall()) {
            if (userJid == getLocalUserJid()) {
                updateStatusInTimer(CallStatus.CONNECTED)
                animateCallOptionsView()
            } else {
                LogMessage.i(LOG_TAG, "skip reconnected ui update")
            }
        } else {
            callDetailsLayout!!.visibility = View.VISIBLE
            if (userJid != null && callUsersAdapter.callUserList != null) {
                val index = callUsersAdapter.callUserList!!.indexOf(userJid)
                if (index > -1) {
                    updateStatusAdapter(userJid)
                } else {
                    callUsersAdapter.addUser(userJid)
                    checkAndAddLocalView()
                    durationHandler.removeCallbacks(resizeRunnable)
                    durationHandler.postDelayed(resizeRunnable, 500)
                    checkAddParticipantsAvailable()
                    updateCallUIByType()
                }
            }
        }
        txtCallAttendStatus!!.gone()
        if (getCallType() == CallType.VIDEO_CALL) {
            callInfoLayout!!.visibility = View.GONE
            switchCameraImage.visibility = View.VISIBLE
        }
    }

    private fun handleCallStatusResume(userJid: String?) {
        if (getCallType() == CallType.VIDEO_CALL) {
            switchCameraImage.visibility = View.VISIBLE
        }
        txtCallAttendStatus!!.gone()
        if (!isOneToOneCall() || (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL)) {
            callInfoLayout!!.visibility = View.GONE
            txtCallStatus!!.visibility = View.GONE
        }
        updateStatusAdapter(userJid)
        updateStatus(CallStatus.CONNECTED)
    }

    private fun handleCallStatusOnHold(userJid: String?, callStatus: String) {
        if (isOneToOneCall() || (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL)) {
            callInfoLayout!!.visibility = View.VISIBLE
            callDetailsLayout!!.visibility = View.GONE
            updateStatus(callStatus)
            updateStatusInTimer(callStatus)
            durationHandler.removeCallbacks(hideOptionsRunnable)
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
        } else {
            updateStatusAdapter(userJid)
        }
    }

    private fun handleCallStatusConnected(callStatus: String, userJid: String?) {
        startTimer()
        updateStatus(callStatus)
        checkAndRemoveLocalView()
        resizeView()
        updateViews()
        updateStatusAdapter(userJid)
        if (getCallType() == CallType.VIDEO_CALL) {
            switchCameraImage.visibility = View.VISIBLE
        } else {
            switchCameraImage.visibility = View.GONE
        }
    }

    private fun checkAndUpdateTimeoutUsers() {
        LogMessage.d(LOG_TAG, " checkAndUpdateTimeoutUsers()")
        for (userJid in GroupCallUtils.getTimeOutUsersList()) {
            callUsersAdapter.removeUser(userJid)
            removeTimeoutUser(userJid)
        }
        checkAddParticipantsAvailable()
        checkAndRemoveLocalView()
        resizeView()
        getProfile(getAvailableCallUsersList())
        setMuteStatusText()
        updateCallUIByType()
    }

    private fun checkAndUpdateTimeoutInviteUsers() {
        LogMessage.d(LOG_TAG, " checkAndUpdateTimeoutInviteUsers()")
        for (userJid in GroupCallUtils.getInviteTimeOutUsersList()) {
            callUsersAdapter.removeUser(userJid)
            removeTimeoutUser(userJid)
        }
        checkAddParticipantsAvailable()
        checkAndRemoveLocalView()
        updateCallUIByType()
        resizeView()
        getProfile(getAvailableCallUsersList())
    }

    private fun checkAndUpdateReconnectingUsers() {
        LogMessage.d(LOG_TAG, " checkAndUpdateReconnectingUsers()")
        for (userJid in GroupCallUtils.getReconnectingUsersList()) {
            updateStatusAdapter(userJid)
        }
    }

    private fun updateStatusInTimer(callStatus: String) {
        txtCallAttendStatus!!.text = callStatus
    }

    private fun updateUserAdded(userJid: String?) {
        LogMessage.d(LOG_TAG, "updateUserAdded: $userJid")
        if (userJid != null) {
            if (!getAvailableCallUsersList().contains(userJid) && (callUsersAdapter.callUserList == null || !callUsersAdapter.callUserList!!.contains(
                    userJid
                ))
            ) {
                callUsersAdapter.addUser(userJid)
                checkAndAddLocalView()
                durationHandler.removeCallbacks(resizeRunnable)
                durationHandler.postDelayed(resizeRunnable, 500)
            }
            checkAddParticipantsAvailable()
            updateCallUIByType()
        }
    }

    private fun updateUserJoined(userJid: String?) {
        LogMessage.d(LOG_TAG, " updateUserJoined: $userJid")
        if (userJid != null) {
            checkAndAddLocalView()
            checkAddParticipantsAvailable()
            updateCallUIByType()
            showOrHideSurfaceViews()
        }
    }

    private fun updateUserLeft(userJid: String?) {
        LogMessage.d(LOG_TAG, "  updateUserLeft: $userJid")
        if (userJid != null && !isDisconnectCalled.get()) {
            callUsersAdapter.removeUser(userJid)
            checkAndRemoveLocalView()
            resizeView()
            setMuteStatusText()
            getProfile(getAvailableCallUsersList())
            checkAddParticipantsAvailable()
            updateCallUIByType()
        }
    }

    private fun resizeView() {
        LogMessage.d(LOG_TAG, "resizeView()")
        if (callUsersAdapter.callUserList != null) {
            LogMessage.d(LOG_TAG, " resizeView() inside:")
            val bundle = Bundle()
            bundle.putInt(CallActions.NOTIFY_VIEW_SIZE_UPDATED, 1)
            bundle.putInt(CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED, 1)
            bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
            callUsersAdapter.notifyItemRangeChanged(0, callUsersAdapter.callUserList!!.size, bundle)
        }
    }

    private fun checkAndAddLocalView() {
        LogMessage.d(LOG_TAG, "  checkAndAddLocalView()")
        if (videoLocalView!!.visibility == View.VISIBLE && callUsersAdapter.callUserList != null && callUsersAdapter.callUserList!!.size > 1) {
            callUsersAdapter.addUser(getLocalUserJid())
            videoLocalView!!.visibility = View.GONE
        }
    }

    private fun checkAndRemoveLocalView() {
        LogMessage.d(LOG_TAG, "  checkAndRemoveLocalView()")
        if (videoLocalView!!.visibility == View.GONE && getCallType() == CallType.VIDEO_CALL && callUsersAdapter.callUserList!!.size < 3) {
            callUsersAdapter.removeUser(getLocalUserJid())
            videoLocalView!!.visibility = View.VISIBLE
            if (isCallConnected())
                updateVideoViews(null)
            CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView!!)
            animateCallOptions(R.anim.slide_up, View.VISIBLE, View.GONE)
            if (!getUserAvailableForReconnection(getEndCallerJid())) {
                callInfoLayout!!.visibility = View.VISIBLE
            } else {
                durationHandler.postDelayed(hideOptionsRunnable, 3000)
            }
        }
    }

    companion object {
        /**
         * log tag
         */
        const val LOG_TAG = "GroupCallActivity"

        /**
         * Video call request code
         */
        const val VIDEO_CALL_REQUEST_CODE = 1111

        /**
         * Audio call request code
         */
        const val AUDIO_CALL_REQUEST_CODE = 2222
    }
}