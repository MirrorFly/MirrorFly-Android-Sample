package com.contusfly.starredMessages.presenter

import com.contusfly.R
import com.contusflysdk.api.FlyMessenger.getMessageOfId
import com.contusfly.starredMessages.view.IStarredMessagesView


/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object FavouriteActionUtils {

    /**
     * Checks if the message is favourite message or not and makes the star actions visible or invisible
     */
    fun validateFavouriteAction(chatView: IStarredMessagesView) {
        val message = getMessageOfId(chatView.getClickedStarredMessages()!![chatView.getClickedStarredMessages()!!.size - 1]!!)
        if (message!!.isMessageStarred()) {
            if (chatView.getClickedStarredMessages()!!.isNotEmpty() &&
                    chatView.getMenu()!!.findItem(R.id.action_unfavourite).isVisible ||
                    chatView.getClickedStarredMessages()!!.size == 1) {
                chatView.getMenu()!!.findItem(R.id.action_unfavourite).isVisible = true
                chatView.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
            } else if (chatView.getClickedStarredMessages()!!.size > 1) {
                chatView.getMenu()!!.findItem(R.id.action_unfavourite).isVisible = false
                chatView.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
            }
        } else {
            if (chatView.getClickedStarredMessages()!!.isNotEmpty() &&
                    chatView.getMenu()!!.findItem(R.id.action_favourite).isVisible ||
                    chatView.getClickedStarredMessages()!!.size == 1) {
                chatView.getMenu()!!.findItem(R.id.action_unfavourite).isVisible = false
                chatView.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
            } else if (chatView.getClickedStarredMessages()!!.size > 1) {
                chatView.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
                chatView.getMenu()!!.findItem(R.id.action_unfavourite).isVisible = false
            }
        }
    }
}