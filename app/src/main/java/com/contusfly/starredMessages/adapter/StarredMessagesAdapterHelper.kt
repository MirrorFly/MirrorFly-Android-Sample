package com.contusfly.starredMessages.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.contus.flycommons.ChatTypeEnum
import com.contusfly.R
import com.contusfly.adapters.ChatAdapter
import com.contusfly.adapters.base.BaseChatAdapterHelper
import com.contusfly.adapters.holders.*
import com.contusfly.utils.ChatUserTimeUtils
import com.contusfly.utils.Constants
import com.contusflysdk.api.FlyMessenger.cancelMediaUploadOrDownload
import com.contusflysdk.api.contacts.ContactManager.getDisplayName
import com.contusflysdk.api.models.ChatMessage

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class StarredMessagesAdapterHelper() : BaseChatAdapterHelper() {

    /**
     * Show or Hides the sender name view based on position
     *
     * @param senderNameHolder Sender name view holder
     * @param messageItem      It consist of message details
     */
    fun showHideSenderName(senderNameHolder: SenderNameHolder, messageItem: ChatMessage) {
        senderNameHolder.showFavouriteNameView()
        var messageDate: String = ChatUserTimeUtils.getDateFromTimestamp(messageItem.getMessageSentTime())!!
        if (messageDate.contains("1970")) messageDate = Constants.EMPTY_STRING
        if (messageItem.getMessageChatType() == ChatTypeEnum.groupchat) {
            if (messageItem.isMessageSentByMe()) {
                val toUser = getDisplayName(messageItem.getChatUserJid())
                senderNameHolder.favouriteSenderReceiverName(Constants.YOU, toUser, messageDate)
            } else {
                val fromUser = getDisplayName(messageItem.getChatUserJid())
                senderNameHolder.favouriteSenderReceiverName(fromUser, messageItem.getSenderUserName(), messageDate)
            }
        } else if (messageItem.isMessageSentByMe()) {
            senderNameHolder.favouriteSenderReceiverName(Constants.YOU, messageItem.getSenderUserName(), messageDate)
        } else {
            senderNameHolder.favouriteSenderReceiverName(messageItem.getSenderUserName(), Constants.YOU, messageDate)
        }
    }

    /**
     * Get the View holder of the chat view.
     *
     * @param parent   Parent view group
     * @param viewType Type of the view
     * @return RecyclerView.HorizontalViewHolder Holder for the view
     */
    fun getItemViewHolder(parent: ViewGroup?, viewType: Int, inflater: LayoutInflater?): RecyclerView.ViewHolder? {
        val holder: RecyclerView.ViewHolder
        val view: View
        when (viewType) {
            ChatAdapter.TYPE_TEXT_SENDER -> {
                view = inflater!!.inflate(R.layout.row_starred_txt_sender_item, parent, false)
                holder = TextSentViewHolder(view)
            }
            ChatAdapter.TYPE_TEXT_RECEIVER -> {
                view = inflater!!.inflate(R.layout.row_starred_txt_receiver_item, parent, false)
                holder = TextReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_IMAGE_SENDER -> {
                view = inflater!!.inflate(R.layout.row_starred_img_sender_item, parent, false)
                holder = ImageSentViewHolder(view)
            }
            ChatAdapter.TYPE_IMAGE_RECEIVER -> {
                view = inflater!!.inflate(R.layout.row_starred_img_receiver_item, parent, false)
                holder = ImageReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_VIDEO_SENDER -> {
                view = inflater!!.inflate(R.layout.row_starred_video_sender_item, parent, false)
                holder = VideoSentViewHolder(view)
            }
            ChatAdapter.TYPE_VIDEO_RECEIVER -> {
                view = inflater!!.inflate(R.layout.row_starred_video_receiver_item, parent, false)
                holder = VideoReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_LOCATION_SENDER -> {
                view = inflater!!.inflate(R.layout.row_starred_location_sender_item, parent, false)
                holder = LocationSentViewHolder(view)
            }
            ChatAdapter.TYPE_LOCATION_RECEIVER -> {
                view = inflater!!.inflate(R.layout.row_starred_location_receiver_item, parent, false)
                holder = LocationReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_CONTACT_SENDER -> {
                view = inflater!!.inflate(R.layout.list_starred_contact_sent_item, parent, false)
                holder = ContactSentViewHolder(view)
            }
            ChatAdapter.TYPE_CONTACT_RECEIVER -> {
                view = inflater!!.inflate(R.layout.list_starred_contact_received_item, parent, false)
                holder = ContactReceivedViewHolder(view)
            }
            else -> holder = getMediaItemViewHolder(parent!!, viewType, inflater)!!
        }
        return holder
    }

    /**
     * Handle view holders for the views based on the type
     *
     * @param parent   Parent view
     * @param viewType Type of the view
     * @return RecyclerView.HorizontalViewHolder Holder for the view
     */
    private fun getMediaItemViewHolder(parent: ViewGroup, viewType: Int, inflater: LayoutInflater?): RecyclerView.ViewHolder? {
        var holder: RecyclerView.ViewHolder? = null
        val view: View
        when (viewType) {
            ChatAdapter.TYPE_AUDIO_SENDER -> {
                view = inflater!!.inflate(R.layout.list_starred_audio_sent_item, parent, false)
                holder = AudioSentViewHolder(view)
            }
            ChatAdapter.TYPE_AUDIO_RECEIVER -> {
                view = inflater!!.inflate(R.layout.list_starred_audio_received_item, parent, false)
                holder = AudioReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_FILE_SENDER -> {
                view = inflater!!.inflate(R.layout.list_starred_file_sent_item, parent, false)
                holder = FileSentViewHolder(view)
            }
            ChatAdapter.TYPE_FILE_RECEIVER -> {
                view = inflater!!.inflate(R.layout.list_starred_file_received_item, parent, false)
                holder = FileReceivedViewHolder(view)
            }
            ChatAdapter.TYPE_MSG_NOTIFICATION -> {
                view = inflater!!.inflate(R.layout.row_chat_notification_item, parent, false)
                holder = NotificationViewHolder(view)
            }
        }
        return holder
    }

    /**
     * Enable the views and make the cancel click listener
     *
     * @param progressBar Progressing
     * @param cancel      Cancel image view
     */
    fun starredMediaUploadView(progressBar: ProgressBar, cancel: ImageView, viewProgress: RelativeLayout?) {
        progressBar.visibility = View.GONE
        cancel.visibility = View.GONE
        cancel.setOnClickListener(null)
        if (viewProgress != null) viewProgress.visibility = View.GONE
    }

    /**
     * Enable the media cancel in the chat view
     *
     * @param mid    Message id of the message
     * @param cancel Cancel image view
     */
    override fun enableMediaCancel(mid: String?, cancel: RelativeLayout) {
        cancel.setOnClickListener { v: View? -> cancelMediaUploadOrDownload(mid!!) }
    }
}