package com.contusfly.starredMessages.presenter

import android.content.Intent
import com.contusfly.R
import com.contusfly.activities.SettingsActivity
import com.contusfly.getDeleteChatType
import com.contusfly.starredMessages.view.IStarredMessagesInteractor
import com.contusfly.utils.Constants
import com.contusfly.utils.SharedPreferenceManager
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.api.ChatActionListener
import com.contusflysdk.api.ChatManager.deleteMessagesForMe


/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class StarredMessagesInteractor : com.contusfly.starredMessages.presenter.IStarredMessagesInteractor {

    /**
     * Instance of the IStarredMessagesInteractor which having the methods for Activity
     */
    private var iStarredMessagesInteractor: IStarredMessagesInteractor? = null

    override fun attach(iChatInteractor: com.contusfly.starredMessages.view.IStarredMessagesInteractor?) {
        iStarredMessagesInteractor = iChatInteractor
    }

    override fun handleDialogResponse() {
        /*
          Handle if Clear conversation or delete chat dialog closed.
         */

        /*
          Handle if Clear conversation or delete chat dialog closed.
         */
        val action: CommonAlertDialog.DialogAction = iStarredMessagesInteractor!!.getAlertDialog()!!.getDialogAction()
        if (action === CommonAlertDialog.DialogAction.DELETE_CHAT) {
            val isMediaDelete = SharedPreferenceManager.getBoolean(Constants.DELETE_MEDIA_FROM_PHONE)
            /*
              Alert dialog while user trying to delete the chat
             */
            for (message in iStarredMessagesInteractor!!.getSelectedStarredMessages()!!) {
                deleteMessagesForMe(message.getChatUserJid(), listOf(message.getMessageId()),
                    message.getDeleteChatType(), isMediaDelete, object : ChatActionListener {
                        override fun onResponse(isSuccess: Boolean, message: String) { }
                    })
            }
            iStarredMessagesInteractor!!.getClickedStarredMessages().clear()
            iStarredMessagesInteractor!!.getSelectedStarredMessages()!!.clear()
            iStarredMessagesInteractor!!.updateAdapter()
            iStarredMessagesInteractor!!.getActionMode()!!.finish()
        } else if (action === CommonAlertDialog.DialogAction.STATUS_BUSY) {
            iStarredMessagesInteractor!!.getActivity()!!.startActivity(Intent(iStarredMessagesInteractor!!.getActivity(),
                    SettingsActivity::class.java).putExtra(Constants.FRAGMENT_TYPE, Constants.TYPE_NOTIFICATION))
        }
    }

    override fun deleteMessageAlert(): Boolean {
        var messageToShow: String? = null
        /*
          Check the selected messages count to sow valid message alert
         */
        /*
          Check the selected messages count to sow valid message alert
         */
        if (iStarredMessagesInteractor!!.getClickedStarredMessages().size == 1) {
            messageToShow = iStarredMessagesInteractor!!.getActivity()!!.getString(R.string.msg_are_you_sure_delete_message)
        } else if (iStarredMessagesInteractor!!.getClickedStarredMessages().size > 1) {
            messageToShow = iStarredMessagesInteractor!!.getActivity()!!.getString(R.string.msg_are_you_sure_delete_messages)
        }

        iStarredMessagesInteractor!!.getAlertDialog()!!.setDialogAction(CommonAlertDialog.DialogAction.DELETE_CHAT)
        iStarredMessagesInteractor!!.getAlertDialog()!!.showAlertDialog(messageToShow,
                iStarredMessagesInteractor!!.getActivity()!!.getString(R.string.action_delete),
                iStarredMessagesInteractor!!.getActivity()!!.getString(R.string.action_cancel),
                CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false)
        return true
    }
}