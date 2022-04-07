package com.contusfly.call.groupcall

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.contus.call.CallActions
import com.contus.flycommons.Constants
import com.contus.flycommons.LogMessage
import com.contus.flycommons.SharedPreferenceManager
import com.contus.webrtc.CallDirection
import com.contus.webrtc.CallStatus
import com.contus.webrtc.CallType
import com.contus.webrtc.WebRtcCallService
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.utils.CallConstants.Companion.DRAWABLE_SIZE
import com.contus.webrtc.utils.GroupCallUtils
import com.contus.webrtc.utils.GroupCallUtils.getCallType
import com.contus.webrtc.utils.GroupCallUtils.isOneToOneCall
import com.contusfly.R
import com.contusfly.adapters.BaseViewHolder
import com.contusfly.call.SetDrawable
import com.contusfly.gone
import com.contusfly.show
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.utils.ChatUtils
import com.contusflysdk.utils.MediaUtils
import com.contusflysdk.utils.Utils
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GroupCallAdapter(val context: Context) : RecyclerView.Adapter<GroupCallAdapter.CallUserViewHolder>() {

    /**
     * The call user list to display in the recycler view.
     */
    var callUserList: MutableList<String>? = null
    private var mService: WebRtcCallService? = null
    private var actualScreenHeight = 0
    private var actualScreenWidth = 0

    /**
     * Surface views map
     */
    var callUsersSurfaceViews = ConcurrentHashMap<String, SurfaceViewRenderer>(8)

    /**
     * contains the surface view initialisation status
     */
    var surfaceViewStatusMap = ConcurrentHashMap<SurfaceViewRenderer, Boolean>(8)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallUserViewHolder {
        return CallUserViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.call_user_item, parent, false)
        )
    }

    override fun getItemCount(): Int {
        return if (callUserList == null) 0 else callUserList!!.size
    }

    override fun onBindViewHolder(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(
            TAG,
            "onBindViewHolder position: $position userJid:${callUserList!![position]}"
        )
        if (!(surfaceViewStatusMap[holder.viewVideoSurface!!] != null && surfaceViewStatusMap[holder.viewVideoSurface!!] == true)) {
            LogMessage.i(TAG, "#surface initializing surface view: ${holder.viewVideoSurface}")
            holder.viewVideoSurface?.init(CallManager.getRootEglBase()?.eglBaseContext, null)
            holder.viewVideoSurface?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            surfaceViewStatusMap[holder.viewVideoSurface!!] = true
        }

        setUserInfo(holder, position)
        setSurfaceViewToVideoSink(holder, position)
        setMirrorView(holder, position)
        setUpVideoMuted(holder, position)
        setUpAudioMuted(holder, position)
        updateViewSize(holder, position)
        updateConnectionStatus(holder, position)
    }

    private fun setMirrorView(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG setMirrorView position: $position userJid:${callUserList!![position]}")
        if (callUserList!![position] == GroupCallUtils.getLocalUserJid()) {
            holder.viewVideoSurface?.setMirror(true)
        } else {
            holder.viewVideoSurface?.setMirror(false)
        }
    }

    private fun setUpAudioMuted(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG setUpAudioMuted position: $position userJid:${callUserList!![position]}")
        if (!(isOneToOneCall() && getCallType() == CallType.VIDEO_CALL) && ((callUserList!![position] == GroupCallUtils.getLocalUserJid() && GroupCallUtils.isAudioMuted()) || CallManager.isRemoteAudioMuted(callUserList!![position]))) {
            holder.imgAudioMuted.show()
        } else {
            holder.imgAudioMuted.gone()
        }
    }

    private fun setUpVideoMuted(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG setUpVideoMuted position: $position userJid:${callUserList!![position]}")
        when (callUserList!![position]) {
            GroupCallUtils.getLocalUserJid() -> if (GroupCallUtils.isVideoMuted()) {
                setUserInfo(holder, position)
                hideSurface(holder)
            } else showSurface(holder)
            else -> if (CallManager.isRemoteVideoMuted(callUserList!![position])
                    || CallManager.isRemoteVideoPaused(callUserList!![position])
                    || CallManager.getRemoteProxyVideoSink(callUserList!![position]) == null) {
                setUserInfo(holder, position)
                hideSurface(holder)
            } else {
                showSurface(holder)
            }
        }
    }

    private fun isFullSpanPosition(position: Int): Boolean {
        return (position == callUserList!!.size - 1 || (position == 0 && callUserList!!.size == 2)) && isFullSpan()
    }

    private fun isFullSpan(): Boolean {
        return when {
            callUserList!!.size <= 3 -> true
            callUserList!!.size % 2 == 0 -> false
            else -> true
        }
    }

    private fun setUserInfo(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG setUserInfo position: $position userJid:${callUserList!![position]}")
        if (callUserList!![position] == GroupCallUtils.getLocalUserJid()) {
            setLocalUserInfo(holder, position)
        } else {
            setRemoteUserInfo(holder, position)
        }
    }

    private fun setLocalUserInfo(holder: CallUserViewHolder, position: Int) {
        if (GroupCallUtils.isVideoMuted() && (GroupCallUtils.isCallConnected() || GroupCallUtils.getCallDirection() == CallDirection.INCOMING_CALL)) {
            holder.txtUserName.show()
            holder.imgUserImage.show()
        } else {
            holder.txtUserName.gone()
            holder.imgUserImage.gone()
        }
        holder.txtUserName.text = Constants.YOU
        val image = SharedPreferenceManager.instance
                .getString(SharedPreferenceManager.USER_PROFILE_IMAGE)
        val profileDetails = ContactManager.getProfileDetails(callUserList!![position])
        val userName = Utils.returnEmptyStringIfNull(SharedPreferenceManager.instance
                .getString(SharedPreferenceManager.USER_PROFILE_NAME))

        val setDrawable = SetDrawable(context, profileDetails)
        val icon = setDrawable.setDrawableForGroupCall(userName, DRAWABLE_SIZE, true)
        MediaUtils.loadImageWithGlideSecure(context, image, holder.imgUserImage, icon)
    }

    private fun setRemoteUserInfo(holder: CallUserViewHolder, position: Int) {
        val profileDetails = ContactManager.getProfileDetails(callUserList!![position])
        if (profileDetails != null) {
            val name = StringBuilder(profileDetails.name)
            val image = profileDetails.image

            val setDrawable = SetDrawable(context, profileDetails)
            val icon: Drawable?

            holder.txtUserName.text = name.toString()
            holder.imgUserImage.scaleType = ImageView.ScaleType.CENTER_CROP
            icon = setDrawable.setDrawableForGroupCall(name.toString(), DRAWABLE_SIZE, false)
            MediaUtils.loadImageWithGlideSecure(context, image, holder.imgUserImage, icon)

        } else {
            holder.txtUserName.text = Utils.getFormattedPhoneNumber(ChatUtils.getUserFromJid(callUserList!![position]))
            holder.imgUserImage.scaleType = ImageView.ScaleType.CENTER
            MediaUtils.loadImageWithGlideSecure(context, "", holder.imgUserImage, ContextCompat.getDrawable(context, R.drawable.ic_group_call_user_default_pic))
        }
    }

    private fun setSurfaceViewToVideoSink(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "#surface setSurfaceViewToVideoSink position: $position userJid:${callUserList!![position]}")
        if (mService != null) {
            when {
                callUserList!![position] == GroupCallUtils.getLocalUserJid() -> {
                    showSurface(holder)
                    try {
                        CallManager.getLocalProxyVideoSink()?.setTarget(holder.viewVideoSurface)
                    }catch (e:Exception){
                        LogMessage.e(TAG,e)
                    }
                }
                CallManager.getRemoteProxyVideoSink(callUserList!![position]) != null -> {
                    LogMessage.d(TAG, "#surface setSurfaceViewToVideoSink update remote user view for ${callUserList!![position]}")
                    showSurface(holder)
                    CallManager.getRemoteProxyVideoSink(callUserList!![position])!!.setTarget(holder.viewVideoSurface)
                }
                else -> hideSurface(holder)
            }
        }
    }

    private fun swapSurfaceViewToVideoSink(holder: CallUserViewHolder) {
        LogMessage.d(TAG, "$TAG swapSurfaceViewToVideoSink")
        if (mService != null) {
            CallManager.getLocalProxyVideoSink()!!.setTarget(null)
            CallManager.getLocalProxyVideoSink()!!.setTarget(holder.viewVideoSurface)
        }
    }

    private fun unSwapSurfaceViewToVideoSink(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG unSwapSurfaceViewToVideoSink position: $position userJid:${callUserList!![position]}")
        if (CallManager.getRemoteProxyVideoSink(callUserList!![position]) != null)
            CallManager.getRemoteProxyVideoSink(callUserList!![position])?.setTarget(holder.viewVideoSurface)
        else
            hideSurface(holder)
    }

    override fun onBindViewHolder(holder: CallUserViewHolder, position: Int, payloads: MutableList<Any>) {
        LogMessage.d(TAG, "$TAG onBindViewHolder position: $position userJid:${callUserList!![position]}")
        callUsersSurfaceViews[callUserList!![position]] = holder.viewVideoSurface!!
        LogMessage.d(TAG, "put surface view for : ${callUserList!![position]}")
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            LogMessage.d(TAG, "$TAG onBindViewHolder no payload")

        } else {
            val bundle = payloads[0] as Bundle
            LogMessage.d(TAG, "$TAG onBindViewHolder has payload")
            for (key in bundle.keySet()) {
                LogMessage.d(TAG, "$TAG onBindViewHolder key: $key")
                when (key) {
                    CallActions.NOTIFY_REMOTE_VIEW_HIDE -> {
                        hideSurface(holder)
                    }
                    CallActions.NOTIFY_REMOTE_VIEW_SHOW -> {
                        showSurface(holder)
                    }
                    CallActions.NOTIFY_REMOTE_VIEW_RELEASE -> {
                        LogMessage.i(TAG, "#surface release view: ${holder.viewVideoSurface} #1")
                        holder.viewVideoSurface?.release()
                        holder.viewVideoSurface?.clearImage()
                        surfaceViewStatusMap[holder.viewVideoSurface!!] = false
                        callUsersSurfaceViews.remove(callUserList!![position])
                    }
                    CallActions.NOTIFY_LOCAL_VIEW_MIRROR -> {
                        holder.viewVideoSurface?.setMirror(true)
                    }
                    CallActions.NOTIFY_LOCAL_VIEW_NOT_MIRROR -> {
                        holder.viewVideoSurface?.setMirror(false)
                    }
                    CallActions.NOTIFY_VIEW_SIZE_UPDATED -> {
                        updateViewSize(holder, position)
                    }
                    CallActions.NOTIFY_VIEW_MUTE_UPDATED -> {
                        setUpAudioMuted(holder, position)
                    }
                    CallActions.NOTIFY_VIEW_VIDEO_MUTE_UPDATED -> {
                        setUpVideoMuted(holder, position)
                    }
                    CallActions.NOTIFY_VIEW_STATUS_UPDATED -> {
                        updateConnectionStatus(holder, position)
                    }
                    CallActions.NOTIFY_CONNECT_TO_SINK -> {
                        setSurfaceViewToVideoSink(holder, position)
                    }
                    CallActions.NOTIFY_SWAP_VIDEO_SINK -> {
                        swapSurfaceViewToVideoSink(holder)
                    }
                    CallActions.NOTIFY_UN_SWAP_VIDEO_SINK -> {
                        unSwapSurfaceViewToVideoSink(holder, position)
                    }
                }
            }
        }
    }

    private fun showBottomInfo(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG showBottomInfo()")
        if (callUserList!![position] == GroupCallUtils.getLocalUserJid() ||
            GroupCallUtils.getCallStatus(callUserList!![position]) == CallStatus.CONNECTED) {
            holder.txtUserName.show()
            holder.callerNameBgLayout.show()
        }
    }

    private fun hideBottomInfo(holder: CallUserViewHolder) {
        LogMessage.d(TAG, "$TAG hideBottomInfo()")
        holder.txtUserName.gone()
        holder.callerNameBgLayout.gone()
    }

    private fun hideSurface(holder: CallUserViewHolder) {
        LogMessage.d(TAG, "$TAG hideSurface()")
        if (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL)
            showSurface(holder)
        else {
            holder.viewVideoSurface?.gone()
            holder.callerNameBgLayout.show()
            holder.imgUserImage.show()
            holder.viewOverlay.show()
        }
    }

    private fun showSurface(holder: CallUserViewHolder) {
        LogMessage.d(TAG, "$TAG showSurface()")
        holder.viewVideoSurface?.show()
        holder.callerNameBgLayout.show()
        holder.imgUserImage.gone()
        holder.viewOverlay.gone()
    }

    private fun updateViewSize(holder: CallUserViewHolder, position: Int) {
        LogMessage.d(TAG, "$TAG updateViewSize position: $position userJid:${callUserList!![position]}")
        if (callUserList!![position] == GroupCallUtils.getLocalUserJid()) {
            setUserInfo(holder, position)
        }
        val gridLayoutParams = holder.rootLayout.layoutParams as StaggeredGridLayoutManager.LayoutParams
        gridLayoutParams.isFullSpan = isFullSpanPosition(position)
        gridLayoutParams.height = viewHeight
        gridLayoutParams.width = StaggeredGridLayoutManager.LayoutParams.MATCH_PARENT
        holder.rootLayout.layoutParams = gridLayoutParams

        if ((callUserList!!.size == 5 || callUserList!!.size == 7) && position == callUserList!!.size - 1)
            holder.rootLayout.setPadding(actualScreenWidth / 4, 0, actualScreenWidth / 4, 0)
        else
            holder.rootLayout.setPadding(0, 0, 0, 0)

        setupBottomInfoView(holder, position)
    }

    private val viewHeight: Int
        get() = when {
            callUserList!!.size < 2 -> StaggeredGridLayoutManager.LayoutParams.MATCH_PARENT
            callUserList!!.size <= 3 -> actualScreenHeight / 2
            else -> actualScreenHeight / (callUserList!!.size / 2 + callUserList!!.size % 2)
        }

    fun addUser(userJid: String) {
        if (userJid.isBlank())
            return
        LogMessage.d(TAG, "$TAG addUser() userJid:${userJid}")
        if (callUserList == null)
            callUserList = mutableListOf()
        if (callUserList!!.size == 0) {
            callUserList!!.add(userJid)
            notifyItemInserted(callUserList!!.indexOf(userJid))
        } else if (!callUserList!!.contains(userJid)) {
            if (userJid == GroupCallUtils.getLocalUserJid() || !callUserList!!.contains(GroupCallUtils.getLocalUserJid())) {
                callUserList!!.add(userJid)
                notifyItemInserted(callUserList!!.indexOf(userJid))
                //durationHandler.postDelayed({ notifyItemInserted(callUserList!!.indexOf(userJid)) }, 100)
            } else {
                callUserList!!.add(callUserList!!.size - 1, userJid)
                notifyItemInserted(callUserList!!.indexOf(userJid))
                //durationHandler.postDelayed({ notifyItemInserted(callUserList!!.indexOf(userJid)) }, 100)
            }
        } else {
            updateConnectionStatus(callUserList!!.indexOf(userJid))
        }
    }

    fun addUsers(userList: ArrayList<String>) {
        LogMessage.d(TAG, "$TAG addUsers() userJid:${userList}")
        if (callUserList == null)
            callUserList = mutableListOf()
        if (callUserList!!.size == 0) {
            callUserList!!.addAll(userList)
            notifyDataSetChanged()
        } else {
            callUserList!!.addAll(0, userList)
            notifyDataSetChanged()
        }
    }

    private fun updateConnectionStatus(index: Int) {
        LogMessage.d(TAG, "$TAG updateConnectionStatus() position: $index userJid:${callUserList!![index]} callStatus:${GroupCallUtils.getCallStatus(callUserList!![index])}")
        val bundle = Bundle()
        bundle.putInt(CallActions.NOTIFY_VIEW_STATUS_UPDATED, 1)
        notifyItemChanged(index, bundle)
    }

    private fun updateConnectionStatus(holder: CallUserViewHolder, index: Int) {
        LogMessage.d(TAG, "$TAG updateConnectionStatus position: $index userJid:${callUserList!![index]} callStatus:${GroupCallUtils.getCallStatus(callUserList!![index])}")
        when (GroupCallUtils.getCallStatus(callUserList!![index])) {
            CallStatus.RINGING, CallStatus.CONNECTING, CallStatus.CALLING, CallStatus.DISCONNECTED -> {
                if (callUserList!![index] != GroupCallUtils.getLocalUserJid()) {
                    showStatusInView(holder, index)
                } else
                    holder.callerStatusLayout.gone()
            }
            CallStatus.RECONNECTING, CallStatus.ON_HOLD -> {
                showStatusInView(holder, index)
            }
            else -> {
                setUpVideoMuted(holder, index)
                holder.callerStatusLayout.gone()
                setupBottomInfoView(holder, index)
                if (GroupCallUtils.isOnGoingVideoCall() && callUserList!![index] == GroupCallUtils.getLocalUserJid() && !GroupCallUtils.isVideoMuted()) {
                    holder.imgUserImage.gone()
                }
            }
        }
    }

    private fun setupBottomInfoView(holder: CallUserViewHolder, position: Int) {
        if (callUserList!!.size == 1) {
            hideBottomInfo(holder)
        } else {
            showBottomInfo(holder, position)
        }
    }

    private fun showStatusInView(holder: CallUserViewHolder, index: Int) {
        LogMessage.d(TAG, "$TAG showStatusInView position: $index userJid:${callUserList!![index]}")
        if (isOneToOneCall() && getCallType() == CallType.VIDEO_CALL) {
            holder.callerStatusLayout.gone()
            showSurface(holder)
        } else {
            setUpVideoMuted(holder, index)
            holder.callerStatusLayout.show()
            holder.callerStatusTextView.text = GroupCallUtils.getCallStatus(callUserList!![index])
            holder.callerNameBgLayout.gone()
            holder.txtUserName.gone()
            if ((CallStatus.CONNECTING == GroupCallUtils.getCallStatus(callUserList!![index]) ||
                            CallStatus.CALLING == GroupCallUtils.getCallStatus(callUserList!![index])) && GroupCallUtils.isOnGoingVideoCall()) {
                holder.imgUserImage.show()
            }
        }
    }

    fun removeUser(userJid: String) {
        if (callUserList == null)
            return
        LogMessage.d(TAG, "$TAG removeUser() userJid:${userJid}")
        val index = callUserList!!.indexOf(userJid)
        if (index >= 0) {
            //notify item changed will not work here, since we are immediately removing view
            val surfaceViewRenderer = callUsersSurfaceViews.remove(userJid)
            if (surfaceViewRenderer != null) {
                surfaceViewRenderer.release()
                surfaceViewRenderer.clearImage()
                surfaceViewStatusMap[surfaceViewRenderer] = false
                LogMessage.i(TAG, "#surface release view: $surfaceViewRenderer #2")
            }
            callUserList!!.remove(userJid)
            notifyItemRemoved(index)
        }
    }

    fun setCallService(mService: WebRtcCallService?) {
        this.mService = mService
    }

    fun setScreenHeight(actualScreenHeight: Int) {
        this.actualScreenHeight = actualScreenHeight
    }

    fun setScreenWidth(actualScreenWidth: Int) {
        this.actualScreenWidth = actualScreenWidth
    }

    inner class CallUserViewHolder(view: View) : BaseViewHolder(view) {
        /**
         * The name of the User.
         */
        var txtUserName: TextView = view.findViewById(R.id.text_user_name)

        /**
         * The status of the Audio mute
         */
        var imgAudioMuted: ImageView = view.findViewById(R.id.image_audio_muted)

        /**
         * The image view of the User.
         */
        var imgUserImage: ImageView = view.findViewById(R.id.img_profile_image)

        var viewOverlay: View = view.findViewById(R.id.view_overlay)

        /**
         * Remote user video view
         */
        var viewVideoSurface: SurfaceViewRenderer? = view.findViewById(R.id.view_video_surface)

        var rootLayout: RelativeLayout = view.findViewById(R.id.root_layout)

        var callerNameBgLayout: LinearLayout = view.findViewById(R.id.caller_name_bg_layout)

        var callerStatusLayout: RelativeLayout = view.findViewById(R.id.caller_status_layout)

        var callerStatusTextView: TextView = view.findViewById(R.id.caller_status_text_view)
    }

    companion object {
        private val TAG = GroupCallAdapter::class.java.simpleName
    }
}