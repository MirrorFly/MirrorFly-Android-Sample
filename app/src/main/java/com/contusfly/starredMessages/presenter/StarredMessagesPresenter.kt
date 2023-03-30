package com.contusfly.starredMessages.presenter

import com.contus.flycommons.FlyCallback
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.starredMessages.view.IStarredMessagesView
import com.contusfly.utils.LogMessage
import com.contusflysdk.api.FlyMessenger.getFavouriteMessages
import com.contusflysdk.api.FlyMessenger.getMessageOfId

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class StarredMessagesPresenter : IStarredMessagesPresenter {

    /**
     * Instance of the IStarredMessagesView which having the methods for Activity
     */
    private var starredMessagesView: IStarredMessagesView? = null

    override fun attach(iChatView: IStarredMessagesView?) {
        starredMessagesView = iChatView
    }

    override fun detach() {
        this.starredMessagesView = null
    }

    override fun setChatAdapter() {
        try {
            starredMessagesView!!.setChatMessages(getFavouriteMessages().toMutableList())
            starredMessagesView!!.getChatAdapter()!!.setStarredMessages(starredMessagesView!!.getChatMessages())
            starredMessagesView!!.getChatRecylerView()!!.setAdapter(starredMessagesView!!.getChatAdapter())
        } catch (e: Exception) {
            LogMessage.e(TAG, e)
        }
    }

    override fun updateList(chatMsgId: String?) {
        try {
            val messageData = getMessageOfId(chatMsgId!!)
            if (messageData != null) {
                Utils.checkMsgID(starredMessagesView!!, chatMsgId, messageData)
            }
            starredMessagesView!!.getChatAdapter()!!.notifyDataSetChanged()
            starredMessagesView!!.getContext()!!.invalidateOptionsMenu()
        } catch (e: java.lang.Exception) {
            LogMessage.e(TAG, e)
        }
    }

    override fun prepareActionMode() {
        starredMessagesView!!.getMenu()!!.findItem(R.id.action_favourite).isVisible = false


        // Validate and show action menu while user selecting the message


        // Validate and show action menu while user selecting the message
        if (starredMessagesView!!.getClickedStarredMessages()!!.isEmpty() && starredMessagesView!!.getActionMode() != null)
            starredMessagesView!!.getActionMode()!!.finish()
        else if (starredMessagesView!!.getClickedStarredMessages()!!.size == 1 &&
                starredMessagesView!!.getActionMode() != null) {
            starredMessagesView!!.getActionMode()!!.title = java.lang.String.valueOf(
                    starredMessagesView!!.getClickedStarredMessages()!!.size)
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_unfavourite).isVisible = false
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_info).isVisible = false
            PresenterMediaUtils.prepareSingleMenuItem(starredMessagesView!!)
            FavouriteActionUtils.validateFavouriteAction(starredMessagesView!!)
            starredMessagesView!!.getActionMode()!!.title = java.lang.String.valueOf(
                    starredMessagesView!!.getClickedStarredMessages()!!.size)
        } else if (starredMessagesView!!.getActionMode() != null) {
            starredMessagesView!!.getActionMode()!!.title = java.lang.String.valueOf(starredMessagesView!!.getClickedStarredMessages()!!.size)
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_info).isVisible = false
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_copy).isVisible = false
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_share).isVisible = false
            starredMessagesView!!.getMenu()!!.findItem(R.id.action_favourite).isVisible = false
            FavouriteActionUtils.validateFavouriteAction(starredMessagesView!!)
        }
    }

    override fun refreshSelectedMessages() {
        starredMessagesView!!.getChatAdapter()!!.setStarredMessageMessages(starredMessagesView!!.getClickedStarredMessages())
        starredMessagesView!!.getChatAdapter()!!.notifyDataSetChanged()
    }
}