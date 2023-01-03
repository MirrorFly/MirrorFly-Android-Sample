package com.contusfly.utils

import com.contusfly.interfaces.GetGroupUsersNameCallback
import com.contusfly.isValidIndex
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import java.util.*

object ProfileDetailsUtils {

    /**
     * Sort the profile list by nick name of the profile detail
     *
     * @param profilesList List of profile
     * @return List<ProfileDetails> Sorted profile list
    </ProfileDetails> */
    fun sortProfileList(profilesList: List<ProfileDetails>?): List<ProfileDetails> {
        profilesList?.let {
            return it.sortedBy { profileDetails -> profileDetails.name?.toLowerCase() }
        }
        return listOf()
    }

    /**
     * Returns group users names Separated by comma
     *
     * @param groupJid Group Jid
     * @return String User names Separated by comma
     */
    fun getGroupUsersNames(groupJid: String?, getGroupUsersNameCallback: GetGroupUsersNameCallback) {
        groupJid?.let {
            GroupManager.getGroupMembersList(false, groupJid) { isSuccess, throwable, data ->
                if (isSuccess) {
                    val groupUsers = data[Constants.SDK_DATA] as ArrayList<ProfileDetails>
                    val userNames = mutableListOf<String>()
                    for (user in groupUsers.take(10)) {
                        if (user.jid.equals(SharedPreferenceManager.getCurrentUserJid(), ignoreCase = true))
                            userNames.add("You")
                        else
                            userNames.add(user.name)
                    }
                    Collections.sort(userNames, String.CASE_INSENSITIVE_ORDER);
                    getGroupUsersNameCallback.onGroupUsersNamePrepared(userNames.joinToString(","))
                }
            }
        }
    }



    /**
     * Sort the group user profile list by nick name of the profile detail
     * and move current user to last in the list
     *
     * @param profilesList List of profile
     * @return List<ProfileDetails> Sorted profile list
    </Roster> */
    @JvmStatic
    fun sortGroupProfileList(profilesList: MutableList<ProfileDetails>?): MutableList<ProfileDetails> {
        profilesList?.let {
            val index = it.indexOfFirst { it.jid == com.contus.flycommons.SharedPreferenceManager.instance.currentUserJid }
            val user = if (index.isValidIndex())
                it[index]
            else
                ContactManager.getProfileDetails(com.contus.flycommons.SharedPreferenceManager.instance.currentUserJid)
            it.remove(user)
            if(it.contains(ContactManager.getProfileDetails(com.contus.flycommons.SharedPreferenceManager.instance.currentUserJid)))
                it.remove(ContactManager.getProfileDetails(com.contus.flycommons.SharedPreferenceManager.instance.currentUserJid))
            val sortedList = it.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.name })).toMutableList()
            if(index>=0) {
                user?.nickName = AppConstants.YOU
                user?.name  = AppConstants.YOU
                user?.let { sortedList.add(user) }
            }
            return sortedList
        }
        return mutableListOf()
    }
}