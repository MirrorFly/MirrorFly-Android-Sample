package com.contusfly.call.groupcall

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.lifecycle.observe
import com.contus.call.CallConstants
import com.contus.call.CallConstants.CALL_UI
import com.contus.call.joincall.JoinCallListener
import com.contus.flycommons.Error
import com.contus.flycommons.LogMessage
import com.contus.webrtc.TextureViewRenderer
import com.contus.webrtc.api.CallActionListener
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.JoinCallActionListener
import com.contusfly.*
import com.contusfly.activities.BaseActivity
import com.contusfly.activities.DashboardActivity
import com.contusfly.call.groupcall.utils.CallUtils
import com.contusfly.databinding.ActivityOnGoingCallPreviewScreenBinding
import com.contusfly.network.NetworkConnection
import com.contusfly.utils.*
import com.contusfly.views.*
import com.contusflysdk.AppUtils
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import kotlin.collections.ArrayList

class OnGoingCallPreviewActivity : BaseActivity(), View.OnClickListener, CommonAlertDialog.CommonDialogClosedListener {

    private lateinit var onGoingCallPreviewScreenBinding: ActivityOnGoingCallPreviewScreenBinding

    /**
     * Instance for surface view render local view
     */
    private var videoLocalView: TextureViewRenderer? = null

    private var progressDialog: DoProgressDialog? = null

    private var userProfilePicView: LinearLayout? = null

    private var callEndedView: LinearLayout? = null

    private var joinCallView: RelativeLayout? = null

    private var callEndedText: CustomTextView? = null

    private var checkInternetConnection: CustomTextView? = null

    private var callEndedTextMessage: CustomTextView? = null

    private var imgProfilePicture: CircularImageView? = null

    private var setDrawable: SetDrawable? = null

    private var callLink: String = Constants.EMPTY_STRING

    private var userJid: String = Constants.EMPTY_STRING

    /**
     * The common alert dialog to display the alert dialogs in the alert view
     */
    private var commonAlertDialog: CommonAlertDialog? = null

    private val videoCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (!permissions.containsValue(false)) {
            setAudioMuteUnMuteStatus()
            CallManager.startVideoCapture()
            showLocalVideoView(true)
            setViewMuteAndUnMuteStatus(muteVideoImage, false)
            toggleVideoMute()
        }
    }

    private val audioCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (!permissions.containsValue(false)) {
            setViewMuteAndUnMuteStatus(muteAudioImage, false)
            toggleMic()
        }
    }

    private val permissionAlertDialog : PermissionAlertDialog by lazy { PermissionAlertDialog(this) }

    /**
     * This image view for  audio mute
     */
    private lateinit var muteAudioImage: ImageView

    private var isAudioMuteClicked = false

    private var isFromSplashScreen = false

    private var isFromOnCreate = false

    private var userName: String = Constants.EMPTY_STRING

    /**
     * This image view for video mute
     */
    private lateinit var muteVideoImage: ImageView

    private var groupUsersList: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppUtils.isNetConnected(this)) {
            CustomToast.show(context, getString(R.string.error_check_internet))
            finish()
            return
        }
        isFromOnCreate = true
        onGoingCallPreviewScreenBinding = ActivityOnGoingCallPreviewScreenBinding.inflate(layoutInflater)
        setContentView(onGoingCallPreviewScreenBinding.root)
        onGoingCallPreviewScreenBinding.viewUsersProfileImage.layoutMembersImage.visibility = View.INVISIBLE
        userName = Utils.returnEmptyStringIfNull(SharedPreferenceManager.getString(Constants.USER_PROFILE_NAME))
        progressDialog = DoProgressDialog(this)
        progressDialog!!.showProgress()
        callEndedView = onGoingCallPreviewScreenBinding.layoutCallEndedView
        joinCallView = onGoingCallPreviewScreenBinding.layoutOnGngCallView
        callEndedText = onGoingCallPreviewScreenBinding.callEndedText
        callEndedTextMessage = onGoingCallPreviewScreenBinding.callEndedTextMessage
        checkInternetConnection = onGoingCallPreviewScreenBinding.internetConnection
        commonAlertDialog = CommonAlertDialog(this)
        commonAlertDialog!!.setOnDialogCloseListener(this)

        //Initialise the join call setup in the call SDK
        callLink = intent.extras!!.getString(CallConstants.CALL_LINK, Constants.EMPTY_STRING)
        isFromSplashScreen = intent.extras!!.getBoolean(Constants.FROM_SPLASH_SCREEN, false)
        userJid = intent.extras!!.getString(Constants.USER_JID, Constants.EMPTY_STRING)

        if (callLink.isNotEmpty()) {
            LogMessage.d(TAG, "#OnGngCall Preview Started")
            validateAndStartJoinCallSetup(callLink)
        } else {
            CustomToast.show(this, "Call link can't be empty")
            finish()
            return
        }
    }

    private fun validateAndStartJoinCallSetup(callLink: String) {
        val onGngCallLink = CallManager.getCallLink()
        if (CallManager.isOnGoingCall() && (onGngCallLink != callLink)) {
            hideProgressDialog()
            askCallSwitchPopup(callLink)
        } else if (CallManager.isOnTelephonyCall(this)) {
            hideProgressDialog()
            commonAlertDialog!!.showAlertDialog(
                this.getString(R.string.msg_telephony_call_alert),
                this.getString(R.string.action_Ok),
                this.getString(R.string.action_cancel), CommonAlertDialog.DIALOGTYPE.DIALOG_SINGLE, false
            )
        } else {
            joinCallPreviewInitialization()
        }
    }

    private fun askCallSwitchPopup(url: String) {
        commonAlertDialog!!.showCallSwitchAlertDialog(url,
            this.getString(R.string.action_ok),
            this.getString(R.string.action_cancel),
            CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL)
    }

    private fun joinCallPreviewInitialization() {
        CallManager.setupJoinCallViaLink()
        CallManager.subscribeCallEvents(callLink, userName, object : JoinCallActionListener {
            override fun onFailure(error: Error) {
                LogMessage.d(TAG, "Subscribe call failure")
                handleOnFailure(error)
            }

            override fun onSuccess() {
                LogMessage.d(TAG, "Subscribe call success")
                checkCallPermissions()
            }
        })

        initViews()
        initVideoLocalView()
        observingCallEvents()
        setLocalViewProfilePic()
        observeNetworkListener()
    }

    private fun handleOnFailure(error: Error) {
        LogMessage.d(TAG, "$CALL_UI ${error.code}")
        var callEnded: String = Constants.EMPTY_STRING
        var callEndedMessage: String = Constants.EMPTY_STRING
        var isInvalidLink = false
        when (error) {
            Error.INVALID_CALL_LINK -> {
                callEnded = getString(R.string.invalid_link)
                callEndedMessage = getString(R.string.return_to_chat)
                isInvalidLink = true
            }
            Error.CALL_ENDED_ALREADY -> {
                callEnded = getString(R.string.call_ended_text)
                callEndedMessage = getString(R.string.call_ended_text_message)
                isInvalidLink = false
            }
            Error.INTERNAL_SERVER_ERROR -> {
                callEnded = getString(R.string.call_ended_text)
                callEndedMessage = getString(R.string.something_went_wrong)
                isInvalidLink = false
            }
            Error.MAX_USERS_REACHED -> {
                showToast(String.format(getString(R.string.max_members_in_call), CallManager.getMaxCallUsersCount()))
                finish()
                return
            }
        }
        showErrorResponse(callEnded, callEndedMessage, isInvalidLink)
    }

    private fun setAudioVideoMuteStatus() {
        muteAudioImage.isActivated = false
        muteVideoImage.isActivated = false
    }

    private fun setViewMuteAndUnMuteStatus(muteImage: ImageView, status: Boolean) {
        muteImage.isActivated = status
    }

    private fun initViews() {
        setDrawable = SetDrawable(this)
        setUpToolbar()

        userProfilePicView = onGoingCallPreviewScreenBinding.viewProfilePic
        imgProfilePicture = onGoingCallPreviewScreenBinding.imgProfileImage
        videoLocalView = onGoingCallPreviewScreenBinding.viewVideoLocal
        muteAudioImage = onGoingCallPreviewScreenBinding.imageMuteAudio
        muteVideoImage = onGoingCallPreviewScreenBinding.imageMuteVideo
        muteAudioImage.setOnClickListener(this)
        muteVideoImage.setOnClickListener(this)
        onGoingCallPreviewScreenBinding.returnToChat.setOnClickListener(this)
        onGoingCallPreviewScreenBinding.textJoin.setOnClickListener(this)
        setAudioVideoMuteStatus()
        handleJoinNowButton(false)
    }

    private fun checkCallPermissions() {
        /* Video Permission */
        if (CallManager.isVideoCallPermissionsGranted(skipBlueToothPermission = false)) {
            CallManager.muteVideo(false)
            muteVideoImage.isActivated = true
            showLocalVideoView(true)
        } else {
            CallManager.muteVideo(true)
            muteVideoImage.isActivated = false
            showLocalVideoView(false)
            MediaPermissions.requestVideoCallPermissions(this, permissionAlertDialog, videoCallPermissionLauncher)
        }

        /* Audio Permission */
        if (CallManager.isAudioCallPermissionsGranted(skipBlueToothPermission = false)) {
            CallManager.muteAudio(false)
            muteAudioImage.isActivated = true
        } else {
            CallManager.muteAudio(true)
            muteAudioImage.isActivated = false
            MediaPermissions.requestAudioCallPermissions(this, permissionAlertDialog, audioCallPermissionLauncher)
        }
    }

    private fun setUpToolbar() {
        setSupportActionBar(onGoingCallPreviewScreenBinding.toolbar.root)
        supportActionBar?.title = Constants.EMPTY_STRING
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        UserInterfaceUtils.initializeCustomToolbar(this, onGoingCallPreviewScreenBinding.toolbar.root)
        onGoingCallPreviewScreenBinding.toolbar.root.navigationIcon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(ContextCompat.getColor(this, R.color.color_white), BlendModeCompat.SRC_ATOP)
    }

    private fun initVideoLocalView() {
        videoLocalView!!.init(CallManager.getRootEglBase()!!.eglBaseContext, null)
        videoLocalView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        videoLocalView!!.setMirror(true)
        /* setting Target SurfaceViews to VideoSinks  */
        CallManager.getLocalProxyVideoSink()?.setTarget(videoLocalView)
    }

    override fun onPause() {
        super.onPause()
        LogMessage.d(TAG, "OnPause Subscribe call: username $userName - callLink $callLink")
        groupUsersList.clear()
        isFromOnCreate = false
    }

    private fun observeNetworkListener() {
        val networkConnection = NetworkConnection(applicationContext)
        networkConnection.observe(this) { isConnected ->
            checkUserCallSubscribeDetails(isConnected)
        }
    }

    private fun checkUserCallSubscribeDetails(subscribeStatus: Boolean) {
        if (subscribeStatus) {
            if (!isFromOnCreate) {
                CallManager.subscribeCallEvents(callLink, userName, object : JoinCallActionListener {
                    override fun onFailure(error: Error) {
                        LogMessage.d(TAG, "Subscribe call failure")
                        handleOnFailure(error)
                    }

                    override fun onSuccess() {
                        LogMessage.d(TAG, "Subscribe call success")
                        checkInternetConnection!!.visibility = View.GONE
                        handleJoinNowButton(true)
                    }
                })
            }
        } else {
            handleJoinNowButton(false)
            groupUsersList.clear()
            isFromOnCreate = false
            checkInternetConnection!!.visibility = View.VISIBLE
        }
    }

    private fun observingCallEvents() {
        CallManager.setJoinCallEventsListener(object : JoinCallListener {
            override fun onError(error: Error) {
                handleOnFailure(error)
            }

            override fun onLocalTrack(videoTrack: VideoTrack?) {
                videoTrack?.addSink(CallManager.getLocalProxyVideoSink())
            }

            override fun onSubscribeSuccess() {
                handleJoinNowButton(true)
            }

            override fun onUsersUpdated(usersList: List<String>) {
                hideProgressDialog()
                showJoinCallOrCallEndedView(usersList)
            }
        })
        CallManager.startVideoCapture()
    }

    private fun hideProgressDialog() {
        if (progressDialog?.isShowing!!)
            progressDialog?.dismiss()
    }

    private fun showErrorResponse(
        callEnded: String,
        callEndedMessage: String,
        isInvalidLink: Boolean
    ) {
        hideProgressDialog()
        if (isInvalidLink) {
            onGoingCallPreviewScreenBinding.callEndedIcon.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_invalid_link))
            callEndedTextMessage!!.visibility = View.GONE
            onGoingCallPreviewScreenBinding.returnToChat.visibility = if (isFromSplashScreen) View.GONE else View.VISIBLE
        } else {
            onGoingCallPreviewScreenBinding.callEndedIcon.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_join_call_ended))
            callEndedTextMessage!!.visibility = View.VISIBLE
            onGoingCallPreviewScreenBinding.returnToChat.visibility = View.GONE
        }
        onGoingCallPreviewScreenBinding.toolbar.toolbarTitle.text = Constants.EMPTY_STRING
        callEndedText!!.text = callEnded
        callEndedTextMessage!!.text = callEndedMessage
        callEndedView!!.visibility = View.VISIBLE
        joinCallView!!.visibility = View.GONE
    }

    private fun showJoinCallOrCallEndedView(usersList: List<String>) {
        if (usersList.isEmpty() || usersList.size == 1) {
            onGoingCallPreviewScreenBinding.toolbar.toolbarTitle.text = Constants.EMPTY_STRING
            callEndedView!!.visibility = View.VISIBLE
            joinCallView!!.visibility = View.GONE
            return
        } else {
            joinCallView!!.visibility = View.VISIBLE
            callEndedView!!.visibility = View.GONE
        }
        groupUsersList = usersList as java.util.ArrayList<String>
        val usersNotExceeded = groupUsersList.size < CallManager.getMaxCallUsersCount()
        if (!usersNotExceeded) CustomToast.show(this@OnGoingCallPreviewActivity, String.format(getString(R.string.max_members_in_call), CallManager.getMaxCallUsersCount()))
        handleJoinNowButton(usersNotExceeded)
        updateGroupMemberDetails(groupUsersList)
    }

    private fun handleJoinNowButton(enable: Boolean) {
        onGoingCallPreviewScreenBinding.textJoin.isEnabled = enable
    }

    private fun setAudioMuteUnMuteStatus() {
        if (CallManager.isAudioCallPermissionsGranted(skipBlueToothPermission = false) && !isAudioMuteClicked) {
            setViewMuteAndUnMuteStatus(muteAudioImage, false)
            toggleMic()
        }
    }

    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        if (groupUsersList.isNotEmpty()) updateGroupMemberDetails(groupUsersList)
    }

    private fun showUserProfilePic() {
        videoLocalView!!.visibility = View.GONE
        userProfilePicView!!.visibility = View.VISIBLE
    }

    private fun setLocalViewProfilePic() {
        val userImgUrl = Utils.returnEmptyStringIfNull(SharedPreferenceManager.getString(Constants.USER_PROFILE_IMAGE))
        if (userImgUrl.isNotEmpty())
            MediaUtils.loadImage(this, userImgUrl, imgProfilePicture!!, AppCompatResources.getDrawable(applicationContext, R.drawable.profile_img))
        else imgProfilePicture?.setImageDrawable(setDrawable!!.setDrawableForProfile(userName))
    }

    private fun showLocalVideoView(status: Boolean) {
        if (status) {
            userProfilePicView!!.visibility = View.GONE
            videoLocalView!!.visibility = View.VISIBLE
        } else {
            userProfilePicView!!.visibility = View.VISIBLE
            videoLocalView!!.visibility = View.GONE
        }
    }

    override fun onClick(v: View) {
        when(v.id) {
            R.id.image_mute_audio -> {
                isAudioMuteClicked = true
                if (CallManager.isAudioCallPermissionsGranted(skipBlueToothPermission = false)) toggleMic()
                else MediaPermissions.requestAudioCallPermissions(this, permissionAlertDialog, audioCallPermissionLauncher)
            }
            R.id.image_mute_video -> {
                if (CallManager.isVideoCallPermissionsGranted(skipBlueToothPermission = false)) toggleVideoMute()
                else MediaPermissions.requestVideoCallPermissions(this, permissionAlertDialog, videoCallPermissionLauncher)
            }
            R.id.text_join -> checkAndAllowToOnGngCall()
            R.id.return_to_chat -> finish()
        }
    }

    private fun checkAndAllowToOnGngCall() {
        if (AppUtils.isNetConnected(this)) {
            if (!CallManager.isAudioCallPermissionsGranted(skipBlueToothPermission = false)) MediaPermissions.requestAudioCallPermissions(
                this,
                permissionAlertDialog, audioCallPermissionLauncher)
            else if (muteVideoImage.isActivated && !CallManager.isVideoCallPermissionsGranted(skipBlueToothPermission = false)) MediaPermissions.requestVideoCallPermissions(
                this,
                permissionAlertDialog, videoCallPermissionLauncher)
            else {
                handleJoinNowButton(false)
                checkInternetConnection!!.visibility = View.VISIBLE
                checkInternetConnection!!.text = getString(R.string.connecting_label)
                CallManager.joinCall(object : JoinCallActionListener {
                    override fun onFailure(error: Error) {
                        LogMessage.d(TAG, "$CALL_UI #joinCall onFailure()")
                        checkInternetConnection!!.visibility = View.GONE
                        handleOnFailure(error)
                    }

                    override fun onSuccess() {
                        LogMessage.d(TAG, "$CALL_UI #joinCall onSuccess()")
                        finish()
                    }

                })
            }
        } else checkInternetConnection!!.visibility = View.VISIBLE
    }

    private fun toggleVideoMute() {
        LogMessage.d(TAG, "$CALL_UI toggleVideoMute()")
        muteVideoImage.isActivated = !muteVideoImage.isActivated
        val isVideoMuted = !muteVideoImage.isActivated
        if (!isVideoMuted) CallManager.startVideoCapture()
        CallManager.muteVideo(isVideoMuted,  object : CallActionListener {
            override fun onResponse(isSuccess: Boolean, message: String) {
                LogMessage.d(TAG, "$CALL_UI muteVideo onResponse()")
                if (isVideoMuted) showUserProfilePic()
                else {
                    videoLocalView!!.visibility = View.VISIBLE
                    userProfilePicView!!.visibility = View.GONE
                }
            }
        })
    }

    private fun toggleMic() {
        LogMessage.d(TAG, "$CALL_UI toggleMic()")
        muteAudioImage.isActivated = !muteAudioImage.isActivated
        CallManager.muteAudio(!muteAudioImage.isActivated)
    }

    override fun onDestroy() {
        super.onDestroy()
        isFromOnCreate = false
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
        if (::onGoingCallPreviewScreenBinding.isInitialized) {
            onGoingCallPreviewScreenBinding.viewVideoLocal.release()
            CallManager.cleanUpJoinCallViaLink()
        }
    }

    private fun updateGroupMemberDetails(callUsers: java.util.ArrayList<String>) {
        if (callUsers.size > 1) {
            onGoingCallPreviewScreenBinding.viewUsersProfileImage.layoutMembersImage.visibility =
                View.VISIBLE
            val membersName = CallUtils.setGroupMemberProfile(
                this, callUsers,
                onGoingCallPreviewScreenBinding.viewUsersProfileImage.imageCallMember1,
                onGoingCallPreviewScreenBinding.viewUsersProfileImage.imageCallMember2,
                onGoingCallPreviewScreenBinding.viewUsersProfileImage.imageCallMember3,
                onGoingCallPreviewScreenBinding.viewUsersProfileImage.imageCallMember4
            )
            onGoingCallPreviewScreenBinding.toolbar.toolbarTitle.text =
                if (callUsers.size > 1) membersName.toString() else Constants.EMPTY_STRING
        }
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            if (dialogType == CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL) {
                progressDialog!!.showProgress()
                //Current call disconnect
                CallManager.disconnectCall(object : CallActionListener {
                    override fun onResponse(isSuccess: Boolean, message: String) {
                        joinCallPreviewInitialization()
                    }
                })
            } else if (dialogType == CommonAlertDialog.DIALOGTYPE.DIALOG_SINGLE) {
                finish()
                return
            }
        } else {
            finish()
            return
        }
    }

    override fun listOptionSelected(position: Int) {
        //Not yet implemented
    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        if (userJid == jid && status) {
            showToast(getString(R.string.group_block_message_label))
            val dashboardIntent = Intent(applicationContext, DashboardActivity::class.java)
            dashboardIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(dashboardIntent)
            finish()
        }
    }
}