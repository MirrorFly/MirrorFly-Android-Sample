package com.contusfly.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.contusfly.getChatType
import com.contusfly.isValidIndex
import com.contusfly.models.ProfileDetailsShareModel
import com.contusfly.utils.Constants
import com.contusfly.utils.ProfileDetailsUtils
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.RecentChat
import java.util.ArrayList
import javax.inject.Inject

class ForwardMessageViewModel @Inject constructor() : ViewModel() {

    private var groupList: MutableList<ProfileDetails>? = null
    private var recentList: MutableList<ProfileDetails>? = null
    private var friendsList: MutableList<ProfileDetails>? = null
    val profileDetailsShareModelList = MutableLiveData<List<ProfileDetailsShareModel>>()


    fun loadForwardChatList() {

        GroupManager.getAllGroups(false) { isSuccess, throwable, data ->
            groupList = mutableListOf()
            if (isSuccess) {
                groupList!!.addAll(ProfileDetailsUtils.sortProfileList(data[Constants.SDK_DATA] as ArrayList<ProfileDetails>))
            }
            isForwardChatListLoaded()
        }

        FlyCore.getRecentChatList { isSuccess, throwable, data ->
            recentList = mutableListOf()
            if (isSuccess) {
                val recentChatList = data[Constants.SDK_DATA] as MutableList<RecentChat>
                recentChatList.take(3).forEach {
                    val profileDetails = FlyCore.getUserProfile(it.jid)
                    profileDetails?.let {
                        recentList!!.add(it)
                    }
                }
            }
            isForwardChatListLoaded()
        }

        FlyCore.getFriendsList(false) { isSuccess, throwable, data ->
            friendsList = mutableListOf()
            if (isSuccess) {
                val profileDetails = data[Constants.SDK_DATA] as MutableList<ProfileDetails>
                friendsList!!.addAll(ProfileDetailsUtils.sortProfileList(profileDetails))
            }
            isForwardChatListLoaded()
        }
    }

    private fun isForwardChatListLoaded() {
        if (groupList != null && recentList != null && friendsList != null)
            loadProfileDetailsShareModel()
    }

    private fun loadProfileDetailsShareModel() {
        val profileShareModelList = mutableListOf<ProfileDetailsShareModel>()
        recentList!!.forEach { profileDetail ->
            val profileDetailsShareModel = ProfileDetailsShareModel("recentChat", profileDetail)
            profileShareModelList.add(profileDetailsShareModel)
            val index = friendsList!!.indexOfFirst { it.jid == profileDetail.jid }
            if (index.isValidIndex())
                friendsList!!.removeAt(index)
            val groupIndex = groupList!!.indexOfFirst { it.jid == profileDetail.jid }
            if (groupIndex.isValidIndex())
                groupList!!.removeAt(groupIndex)
        }

        friendsList!!.forEach { profileDetail ->
            val profileDetailsShareModel = ProfileDetailsShareModel(profileDetail.getChatType(), profileDetail)
            profileShareModelList.add(profileDetailsShareModel)
        }
        groupList!!.forEach { profileDetail ->
            val profileDetailsShareModel = ProfileDetailsShareModel(profileDetail.getChatType(), profileDetail)
            profileShareModelList.add(profileDetailsShareModel)
        }

        profileDetailsShareModelList.postValue(profileShareModelList)
    }
}