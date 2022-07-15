package com.contusfly.interfaces

import com.contusflysdk.api.contacts.ProfileDetails

interface ContactHelperListener {
    fun isSelected(jid: String) : Boolean
    fun onItemClicked(profileClicked: Boolean, profile: ProfileDetails)
}