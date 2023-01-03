package com.contusfly.interfaces

import com.contusflysdk.api.contacts.ProfileDetails


/**
 * Listener to get the click action on item from file list adapter
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
interface RecyclerViewItemClick {

    /**
     * Callback Method for Clicking an item
     *
     * @param position - position
     * @param profileDetails   - ProfileDetails
     */
    fun onItemClicked(position: Int, profileDetails: ProfileDetails?)

    /**
     * Method to notify if user selects more than allowed users
     */
    fun onlyForwardUserRestriction()

}