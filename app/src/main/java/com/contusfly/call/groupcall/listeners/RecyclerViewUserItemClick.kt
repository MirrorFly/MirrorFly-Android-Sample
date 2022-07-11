package com.contusfly.call.groupcall.listeners

import com.contusflysdk.api.contacts.ProfileDetails

interface RecyclerViewUserItemClick {
    /**
     * Callback Method for Clicking an item
     *
     * @param position - position
     * @param roster   - Roster
     */
    fun onItemClicked(position: Int, roster: ProfileDetails?)

    /**
     * Method to notify if user selects more than seven users
     */
    fun onUserSelectRestriction()

    /**
     * Callback Method for Clicking an blocked user item
     *
     * @param roster   - Roster
     */
    fun onSelectBlockedUser(roster: ProfileDetails)
}