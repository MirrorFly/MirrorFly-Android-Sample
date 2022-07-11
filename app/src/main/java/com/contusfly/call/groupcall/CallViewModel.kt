package com.contusfly.call.groupcall

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contusfly.call.calllog.CallLogRepository
import com.contusfly.sortProfileList
import com.contusfly.utils.SharedPreferenceManager
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallViewModel @Inject
constructor(private val callLogRepository: CallLogRepository) : ViewModel() {

    val profileUpdatedLiveData = MutableLiveData<String>()

    val inviteUserList = MutableLiveData<List<ProfileDetails>>()

    fun getInviteUserList(callConnectedUserList: ArrayList<String>?) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val userProfilesList = getProfileDetailsWithoutCallMembers(callConnectedUserList)
            inviteUserList.value = getUpdatedProfiles(userProfilesList)
        }
    }

    private fun getUpdatedProfiles(userProfilesList: List<ProfileDetails>): List<ProfileDetails>? {
        val filteredProfiles = mutableListOf<ProfileDetails>()
        userProfilesList.forEach { profileDetail ->
            if (!profileDetail.isAdminBlocked) filteredProfiles.add(profileDetail)
        }
        return filteredProfiles.sortedBy { it.name.toLowerCase() }
    }

    fun getInviteUserListForGroup(groupId: String, callConnectedUserList: ArrayList<String>?) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            var profileDetails: List<ProfileDetails>? = null
            GroupManager.getGroupMembersList(false, groupId) { isSuccess, throwable, data ->
                if (isSuccess) profileDetails = data["data"] as ArrayList<ProfileDetails>
                val groupWithOutCallMembers: MutableList<ProfileDetails> =
                    profileDetails!!.toMutableList()
                inviteUserList.value = getUpdatedProfiles(getFilteredList(callConnectedUserList, groupWithOutCallMembers))
            }
        }
    }

    fun getProfileDetailsWithoutCallMembers(callConnectedUserList: ArrayList<String>?): List<ProfileDetails> {
        val profileDetails = FlyCore.getFriendsList()
        val withOutCallMembers: MutableList<ProfileDetails> = profileDetails.toMutableList()
        return sortProfileList(getFilteredList(callConnectedUserList, getSingleProfiles(withOutCallMembers)))
    }

    private fun getFilteredList(
        callConnectedUserList: ArrayList<String>?,
        profileDetails: MutableList<ProfileDetails>
    ): List<ProfileDetails> {
        return profileDetails.filter {
            !callConnectedUserList!!.contains(it.jid) &&
                    it.jid != SharedPreferenceManager.getCurrentUserJid()
        }
    }

    private fun getSingleProfiles(profiles: MutableList<ProfileDetails>): MutableList<ProfileDetails> {
        val profileList: MutableList<ProfileDetails> = mutableListOf()
        for (profile in profiles) {
            if (!profile.isGroupProfile)
                profileList.add(profile)
        }
        return profileList
    }
}