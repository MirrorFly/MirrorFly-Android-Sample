package com.contusfly.call.groupcall.helpers

import android.content.Context
import com.contus.call.CallConstants.CALL_UI
import com.contus.flycommons.LogMessage
import com.contus.call.utils.GroupCallUtils
import com.contusfly.*
import com.contusfly.call.groupcall.getOnGoingCallStatus
import com.contusfly.call.groupcall.isCallNotConnected
import com.contusfly.call.groupcall.isOutgoingCall
import com.contusfly.call.groupcall.utils.CallUtils
import com.contusfly.databinding.LayoutCallNotConnectedBinding
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.utils.Utils

class CallNotConnectedViewHelper(
    private val context: Context,
    private val binding: LayoutCallNotConnectedBinding
) {

    fun updateCallStatus() {
        binding.textCallStatus.text = GroupCallUtils.getOnGoingCallStatus(context)
    }

    private fun showCallStatus() {
        binding.textCallStatus.show()
    }

    /*
    * Set up call details before user attend the call
    * */
    fun setUpCallUI() {
        if (GroupCallUtils.isCallNotConnected()) {
            binding.layoutCallNotConnected.show()
            showCallStatus()
            updateCallStatus()
            updateCallMemberDetails(GroupCallUtils.getAvailableCallUsersList())
            showCallerImage()
            CallUtils.setIsListViewAnimated(false)
        } else
            binding.layoutCallNotConnected.gone()
    }

    private fun showCallerImage() {
        if (GroupCallUtils.isOneToOneCall()) {
            binding.layoutGroupCallMembersImage.layoutMembersImage.gone()
            if (GroupCallUtils.isOutgoingCall()) {
                binding.layoutOutgoingProfile.show()
                binding.rippleBg.startRippleAnimation()
                binding.callerProfileImage.gone()
            } else {
                binding.layoutOutgoingProfile.gone()
                binding.callerProfileImage.show()
            }
        } else {
            binding.layoutOutgoingProfile.gone()
            binding.callerProfileImage.gone()
            binding.layoutGroupCallMembersImage.layoutMembersImage.show()
        }
    }

    fun showRetryLayout() {
        if (GroupCallUtils.isOneToOneCall())
            binding.rippleBg.stopRippleAnimation()
        updateCallStatus()
    }

    fun hideRetryLayout() {
        if (GroupCallUtils.isOneToOneCall())
            binding.rippleBg.startRippleAnimation()
        updateCallStatus()
    }

    /**
     * This method is getting the caller name and profile picture
     *
     * @param callUsers list of Users in Call
     */
    fun updateCallMemberDetails(callUsers: ArrayList<String>) {
        LogMessage.d(TAG, "$CALL_UI getProfile $callUsers")
        showCallerImage()
        if (GroupCallUtils.isOneToOneCall()) {
            val profileDetails = if (GroupCallUtils.getEndCallerJid().contains("@"))
                ContactManager.getProfileDetails(GroupCallUtils.getEndCallerJid())
            else null

            profileDetails?.let {
                binding.callerProfileImage.loadUserProfileImage(context, profileDetails)
                binding.receiverProfileImage.loadUserProfileImage(context, profileDetails)
                val name = Utils.returnEmptyStringIfNull(profileDetails.name)
                LogMessage.d(TAG, "$CALL_UI getProfile name: $name")
                binding.textCallerName.text = name
            }
        } else {
            updateGroupMemberDetails(callUsers)
        }
    }

    private fun updateGroupMemberDetails(callUsers: java.util.ArrayList<String>) {
        val membersName = CallUtils.setGroupMemberProfile(
            context,
            callUsers,
            binding.layoutGroupCallMembersImage.imageCallMember1,
            binding.layoutGroupCallMembersImage.imageCallMember2,
            binding.layoutGroupCallMembersImage.imageCallMember3,
            binding.layoutGroupCallMembersImage.imageCallMember4
        )
        binding.textCallerName.text = membersName.toString()
    }
}