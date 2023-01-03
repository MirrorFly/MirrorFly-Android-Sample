package com.contusfly.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import com.contusfly.diffCallBacks.ProfileDiffCallback
import com.contusfly.utils.Constants.Companion.SDK_DATA
import com.contusfly.utils.ProfileDetailsUtils
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class ContactViewModel @Inject constructor() : ViewModel() {

    // = = = = = = = = Contact Data = = = = = = = =
    val contactDetailsList = MutableLiveData<List<ProfileDetails>>()
    val contactListAdapter: ArrayList<ProfileDetails> by lazy { ArrayList() }
    val contactDiffResult = MutableLiveData<DiffUtil.DiffResult>()

    fun getContactList(fromGroupInfo: Boolean, groupId: String?) {
        viewModelScope.launch {
            if (fromGroupInfo && !groupId.isNullOrEmpty()) {
                val profileDetails = GroupManager.getUsersListToAddMembersInOldGroup(groupId)
                contactDetailsList.value = updateProfiles(profileDetails)
                getContactDiffResult()
            } else {
                FlyCore.getFriendsList(false) { isSuccess, throwable, data ->
                    if (isSuccess) {
                        val profileDetails = data[SDK_DATA] as MutableList<ProfileDetails>
                        contactDetailsList.value = updateProfiles(profileDetails)
                        getContactDiffResult()
                    }
                }
            }
        }
    }

    private fun updateProfiles(profileDetails: List<ProfileDetails>): List<ProfileDetails> {
        val filteredProfiles = mutableListOf<ProfileDetails>()
        val profiles = ProfileDetailsUtils.sortProfileList(profileDetails)
        profiles.forEach { profileDetail ->
            if (!profileDetail.isAdminBlocked) filteredProfiles.add(profileDetail)
        }
        return filteredProfiles
    }

    fun getUpdatedContactList(fromGroupInfo: Boolean, groupId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (fromGroupInfo && !groupId.isNullOrEmpty()) {
                val profileDetails = GroupManager.getUsersListToAddMembersInOldGroup(groupId)
                viewModelScope.launch(Dispatchers.Main) {
                    contactDetailsList.value = updateProfiles(profileDetails)
                    getContactDiffResult()
                }
            } else {
                FlyCore.getFriendsList(true) { isSuccess, throwable, data ->
                    if (isSuccess) {
                        val profileDetails = data[SDK_DATA] as MutableList<ProfileDetails>
                        viewModelScope.launch(Dispatchers.Main) {
                            contactDetailsList.value = updateProfiles(profileDetails)
                            getContactDiffResult()
                        }
                    }
                }
            }
        }
    }

    private fun getContactDiffResult() {
        viewModelScope.launch {
            val diffResult = getDiffUtilResult(ProfileDiffCallback(contactListAdapter, contactDetailsList.value!!))
            contactListAdapter.clear()
            contactListAdapter.addAll(contactDetailsList.value!!)
            contactDiffResult.value = diffResult
        }
    }

    private suspend fun getDiffUtilResult(diffUtilCallback: DiffUtil.Callback): DiffUtil.DiffResult = withContext(Dispatchers.IO) {
        DiffUtil.calculateDiff(diffUtilCallback)
    }

}