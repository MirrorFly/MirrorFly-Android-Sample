package com.contusfly.fragments

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.contus.flycommons.ChatType
import com.contus.flycommons.ContactType
import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG
import com.contus.webrtc.CallType
import com.contus.call.utils.GroupCallUtils
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.activities.ChatActivity
import com.contusfly.activities.GroupInfoActivity
import com.contusfly.activities.UserInfoActivity
import com.contusfly.activities.UserProfileImageViewActivity
import com.contusfly.call.CallPermissionUtils
import com.contusfly.databinding.FragmentProfileDialogBinding
import com.contusfly.getChatType
import com.contusfly.loadUserProfileImage
import com.contusfly.utils.AppConstants
import com.contusfly.utils.Constants
import com.contusfly.utils.MediaUtils
import com.contusfly.utils.SharedPreferenceManager
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.utils.Utils
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.IOException

/**
 * A simple [Fragment] subclass.
 * Use the [ProfileDialogFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ProfileDialogFragment : DialogFragment() {

    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        println("Coroutine Exception :  ${exception.printStackTrace()}")
    }

    private lateinit var profileDialogBinding: FragmentProfileDialogBinding

    // Data
    lateinit var callPermissionUtils: CallPermissionUtils
    lateinit var profileDetails: ProfileDetails
    lateinit var rosterImage: String


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        profileDialogBinding = FragmentProfileDialogBinding.inflate(inflater, container, false)
        dialog?.setCanceledOnTouchOutside(true)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
        return profileDialogBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()
        checkUserBlocked()
        setData()
    }

    private fun setListeners() {
        profileDialogBinding.openChatView.setOnClickListener { navigateToChatViewScreen() }
        profileDialogBinding.openUserProfile.setOnClickListener { navigateToProfileInfoScreen() }
        profileDialogBinding.userProfileImageViewer.setOnClickListener { navigateToProfileImageScreen() }
        profileDialogBinding.audioCall.setOnClickListener { makeAudioCall() }
        profileDialogBinding.videoCall.setOnClickListener { makeVideoCall() }
    }

    private fun checkUserBlocked() {
        rosterImage = if (profileDetails.isBlockedMe) "" else profileDetails.image
        profileDialogBinding.userProfileImageViewer.isEnabled = !rosterImage.isEmpty()
    }

    private fun setData() {
        profileDialogBinding.userProfileImageViewer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_light_gray))
        var name = ""
        if (!profileDetails.isItSavedContact && profileDetails.getChatType() != ChatType.TYPE_GROUP_CHAT)
            name = Utils.getFormattedPhoneNumber(profileDetails.jid.split("@".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0])
        else
            name = ContactManager.getProfileDetails(profileDetails.jid)!!.name
        profileDialogBinding.userName.text = name
        ContactManager.getProfileDetails(profileDetails.jid)?.let {
            profileDialogBinding.userProfileImageViewer.setImageDrawable(null)
            if(it.isGroupProfile){
                val isNewlyCreated = SharedPreferenceManager.getBoolean(Constants.NEWLY_CREATED_GROUP)
                val newlyCreatedJid = SharedPreferenceManager.getString(Constants.NEW_GROUP_JID)
                val imageBitmap = SharedPreferenceManager.getString(Constants.NEW_GROUP_IMAGE)
                if (it.image.isNotEmpty() && newlyCreatedJid.isNotEmpty() && imageBitmap.isNotEmpty() && isNewlyCreated &&
                    it.jid.equals(newlyCreatedJid)){
                    profileDialogBinding.userProfileImageViewer.setImageDrawable(
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_grp_bg
                        )!!
                    )
                    try {
                        val imageAsBytes: ByteArray =
                            android.util.Base64.decode(imageBitmap, android.util.Base64.DEFAULT)
                        val image =
                            BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.size)
                        profileDialogBinding.userProfileImageViewer.setImageBitmap(image)
                        val drawable: Drawable = BitmapDrawable(resources, image)
                        profileDialogBinding.userProfileImageViewer.setImageDrawable(drawable)
                        MediaUtils.loadImage(requireContext(), profileDetails.image, profileDialogBinding.userProfileImageViewer, drawable)
                    } catch (e: IOException) {
                        LogMessage.e("ProfileDialogFragment", e)
                    }
                } else {
                    profileDialogBinding.userProfileImageViewer.loadUserProfileImage(
                        requireContext(),
                        it
                    )
                }
            }
            else {
                profileDialogBinding.userProfileImageViewer.loadUserProfileImage(
                    requireContext(),
                    it
                )
            }
        }
        callPermissionUtils = activity?.let { CallPermissionUtils(it, profileDetails.isBlocked, arrayListOf(profileDetails.jid),"",false) }!!

        if (profileDetails.getChatType() == ChatType.TYPE_GROUP_CHAT) {
            profileDialogBinding.videoCallLinearlayout.visibility = View.GONE
            profileDialogBinding.audioCallLinearlayout.visibility = View.GONE
        }
    }

    private fun navigateToChatViewScreen() {
        startActivity(
            Intent(context, ChatActivity::class.java)
            .putExtra(LibConstants.JID, profileDetails.jid)
            .putExtra(Constants.CHAT_TYPE, profileDetails.getChatType())
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        dismissDialog()
    }

    private fun navigateToProfileInfoScreen() {
        if (profileDetails.getChatType() == ChatType.TYPE_GROUP_CHAT) {
            startActivity(Intent(context, GroupInfoActivity::class.java)
                .putExtra(AppConstants.PROFILE_DATA, ContactManager.getProfileDetails(profileDetails.jid)))
        } else {
            startActivity(Intent(context, UserInfoActivity::class.java)
                .putExtra(ContactType.CONTUS_CONTACT, profileDetails.isItSavedContact)
                .putExtra(AppConstants.PROFILE_DATA, ContactManager.getProfileDetails(profileDetails.jid)))
        }

        dismissDialog()
    }

    private fun navigateToProfileImageScreen() {
        var title: String? = ContactManager.getProfileDetails(profileDetails.jid)!!.name
        if (title == null || title.isEmpty())
            title = resources.getString(R.string.action_delete)

        startActivity(Intent(context, UserProfileImageViewActivity::class.java)
            .putExtra(com.contusfly.utils.Constants.GROUP_OR_USER_NAME, title)
            .putExtra("PROFILE", rosterImage))
        dismissDialog()
    }

    private fun makeAudioCall() {
        GroupCallUtils.setIsCallStarted(CallType.AUDIO_CALL)
        callPermissionUtils.audioCall()
        dismissDialog()
    }

    private fun makeVideoCall() {
        GroupCallUtils.setIsCallStarted(CallType.VIDEO_CALL)
        callPermissionUtils.videoCall()
        dismissDialog()
    }


    private fun dismissDialog() {
        dialog?.dismiss()
    }

    fun refreshView(){
        checkUserBlocked()
        setData()
    }

    companion object {
        fun newInstance(profileDetails: ProfileDetails) = ProfileDialogFragment().apply {
            this.profileDetails = profileDetails
        }
    }
}