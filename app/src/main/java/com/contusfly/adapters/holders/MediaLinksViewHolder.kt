package com.contusfly.adapters.holders

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.contusfly.*
import com.contusfly.databinding.RowMediaLinksItemBinding
import com.contusfly.models.GroupedMedia
import com.contusfly.utils.Constants
import com.contusfly.utils.ImageUtils
import com.contusflysdk.api.models.ChatMessage
import com.jakewharton.rxbinding3.view.clicks
import java.util.concurrent.TimeUnit

class MediaLinksViewHolder(val context: Context, var viewBinding: RowMediaLinksItemBinding, val onLinkClicked: (String?) -> Unit, val onLinkMessageClicked: (ChatMessage) -> Unit) : RecyclerView.ViewHolder(viewBinding.root) {

    fun bindValues(messageItem: GroupedMedia.MessageItem) {

        viewBinding.textLinkContent.text = if (messageItem.chatMessage.isTextMessage())
            messageItem.chatMessage.getMessageTextContent()
        else if (messageItem.chatMessage.isImageMessage() || messageItem.chatMessage.isVideoMessage())
            messageItem.chatMessage.getMediaChatMessage().getMediaCaptionText()
        else Constants.EMPTY_STRING

        if (messageItem.chatMessage.isImageMessage() || messageItem.chatMessage.isVideoMessage())
            ImageUtils.loadBase64(context, viewBinding.imageLinkPreview, messageItem.chatMessage.getMediaChatMessage().getMediaThumbImage(), R.drawable.ic_link_placeholder)
        else
            viewBinding.imageLinkPreview.setImageResource(R.drawable.ic_link_placeholder)

        viewBinding.textLinkTitle.text = messageItem.linkMap[Constants.URL]
        viewBinding.textLinkHost.text = messageItem.linkMap[Constants.HOST]
        viewBinding.textLinkDescription.gone()

        viewBinding.layoutLinkContent.clicks().throttleFirst(1000, TimeUnit.MILLISECONDS).subscribe {
            onLinkClicked(messageItem.linkMap[Constants.URL])
        }

        viewBinding.layoutLinkNavigation.clicks().throttleFirst(1000, TimeUnit.MILLISECONDS).subscribe {
            onLinkMessageClicked(messageItem.chatMessage)
        }
    }

    companion object {
        fun create(parent: ViewGroup,onLinkClicked: (String?) -> Unit, onLinkMessageClicked: (ChatMessage) -> Unit): MediaLinksViewHolder {
            val binding = RowMediaLinksItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MediaLinksViewHolder(parent.context, binding, onLinkClicked, onLinkMessageClicked)
        }
    }
}