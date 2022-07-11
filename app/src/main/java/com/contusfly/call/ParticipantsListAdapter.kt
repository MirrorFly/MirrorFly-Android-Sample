package com.contusfly.call

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.contus.webrtc.api.CallManager
import com.contusfly.R
import com.contusfly.adapters.BaseViewHolder
import com.contusfly.call.groupcall.isNull
import com.contusfly.databinding.RowParticipantsListItemBinding
import com.contusfly.isValidIndex
import com.contusfly.loadUserProfileImage
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.utils.Utils
import java.util.ArrayList

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class ParticipantsListAdapter(private val context: Context) : RecyclerView.Adapter<ParticipantsListAdapter.ParticipantsListViewHolder>(){

    /**
     * This class containing the view of the participants items
     */
    class ParticipantsListViewHolder(var viewBinding: RowParticipantsListItemBinding) : BaseViewHolder(viewBinding.root)

    private var profilesUserList: ArrayList<String>? = null

    fun setParticipantsProfiles(callConnectedUserList: ArrayList<String>?) {
        this.profilesUserList = callConnectedUserList
    }

    fun updateParticipantsDetails(jid: String) {
        val userIndex = profilesUserList!!.indexOfFirst { userJid -> userJid == jid }
        if (userIndex.isValidIndex()) {
            jid.let {
                this.profilesUserList!![userIndex] = jid
                notifyItemChanged(userIndex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantsListViewHolder {
       val binding = RowParticipantsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ParticipantsListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ParticipantsListViewHolder, position: Int) {
        val profileJid = profilesUserList!![position]
        val profile = ContactManager.getProfileDetails(profileJid)
        val userName = Utils.returnEmptyStringIfNull(profile?.name)
        //Set User Name
        holder.viewBinding.textUserName.text = userName
        //Load Profile Pic
        if (profile != null)
            holder.viewBinding.imageChatPicture.loadUserProfileImage(context, profile)
        else
            holder.viewBinding.imageChatPicture.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_profile))
        setUserMuteAndUnMuteStatus(profileJid, holder.viewBinding)
    }

    /*
    * Set Audio, Video Mute/UnMute Status*/
    private fun setUserMuteAndUnMuteStatus(profileJid: String, viewBinding: RowParticipantsListItemBinding) {
        val isAudioMuted = CallManager.isRemoteAudioMuted(profileJid)
        val isVideoMuted = CallManager.isRemoteVideoMuted(profileJid) || CallManager.isRemoteVideoPaused(profileJid)
                || CallManager.getRemoteProxyVideoSink(profileJid).isNull()

        viewBinding.imageMuteAudio.isActivated = isAudioMuted
        viewBinding.imageMuteVideo.isActivated = isVideoMuted
    }

    override fun getItemCount(): Int {
        return profilesUserList!!.size
    }
}