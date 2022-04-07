package com.contusfly.adapters

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.emoji.widget.EmojiAppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.contus.flycommons.ChatType
import com.contus.flycommons.LogMessage
import com.contus.flycommons.runOnUiThread
import com.contusfly.*
import com.contusfly.databinding.RowShareItemBinding
import com.contusfly.interfaces.GetGroupUsersNameCallback
import com.contusfly.interfaces.RecyclerViewItemClick
import com.contusfly.models.ProfileDetailsShareModel
import com.contusfly.utils.Constants
import com.contusfly.utils.EmojiUtils
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.utils.SharedPreferenceManager
import com.contusfly.views.CommonAlertDialog
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import java.util.*


/**
 * Display the contact list in the Activity from the list of ProfileDetails.
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 1.0
 */
class SectionedShareAdapter(private val context: Context, private val commonAlertDialog: CommonAlertDialog) : RecyclerView.Adapter<SectionedShareAdapter.ShareViewHolder>() {
    /**
     * The ProfileDetails list to display in the recycler view.
     */
    private var profileDetailsList: MutableList<ProfileDetailsShareModel>? = null

    /**
     * The temporary data of the list to reuse the list.
     */
    private var mTempData: MutableList<ProfileDetailsShareModel>? = null

    /**
     * Selected users from the search list.
     */
    var selectedList: MutableList<ProfileDetailsShareModel> = mutableListOf()

    var blockedUser: String = emptyString()

    /**
     * Get the selected users object from the list
     *
     * @return List<ProfileDetails> List of selected users
    </ProfileDetails> */
    /**
     * Selected ProfileDetails from the search list.
     */
    var selectedProfileDetailsList: List<ProfileDetailsShareModel> = ArrayList()

    /**
     * RecyclerView ClickLister Adapter
     */
    private var onItemClickListener: RecyclerViewItemClick? = null

    private var searchKey:String ?= null

    /**
     * Sets the list data to ProfileDetails list clear the temp data and refresh the view
     *
     * @param profileDetailsList the new data
     */
    fun setProfileDetails(profileDetailsList: List<ProfileDetailsShareModel>) {
        if (mTempData != null) mTempData!!.clear()
        else mTempData = mutableListOf()
        mTempData!!.addAll(profileDetailsList)
        this.profileDetailsList = mutableListOf()
        this.profileDetailsList!!.addAll(mTempData!!)
    }

    /**
     * Set the updated data to ProfileDetails list and refresh the view
     *
     * @param profileDetails the new data
     */
    fun updateProfileDetails(position: Int, profileDetails: ProfileDetails?) {
        profileDetails?.let {
            this.profileDetailsList!![position].profileDetails = profileDetails
            val index = mTempData!!.indexOfFirst { it.profileDetails.jid == profileDetails.jid }
            if (index.isValidIndex()) {
                mTempData!![index].profileDetails = profileDetails
                runOnUiThread(Runnable {
                    notifyItemChanged(index)
                })
            }
        }
    }

    /**
     * Get the profileDetails from the Contacts adapter
     *
     * @param position Position of the ProfileDetails
     * @return profileDetails Instance of the ProfileDetails
     */
    fun getProfileDetails(position: Int): ProfileDetailsShareModel {
        return mTempData!![position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShareViewHolder {
        val binding = RowShareItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShareViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShareViewHolder, position: Int) {
        try {
            enableHeader(holder, position)
            viewContactsAndGroups(holder, position)
        } catch (e: Exception) {
            LogMessage.e(e)
        }
    }

    /**
     * Display the searched message view item
     *
     * @param holder   Holder of the Chat item
     * @param position Position of the selected item
     */
    private fun viewContactsAndGroups(holder: ShareViewHolder, position: Int) {
        try {
            val item = mTempData!![position]
            setUserInfo(holder, item)
            val onClickListener = View.OnClickListener { handleContactSelection(item, holder, position) }
            holder.viewBinding.centerLayout.setOnClickListener(onClickListener)
            holder.viewBinding.contactItem.setOnClickListener(onClickListener)
            holder.viewBinding.checkSelection.setOnClickListener(onClickListener)
            holder.viewBinding.checkSelection.isChecked = selectedList.contains(item)
            if (item.profileDetails.isBlocked)
                holder.viewBinding.checkSelection.isClickable = false
        } catch (e: Exception) {
            LogMessage.e(e)
        }
    }

    /**
     * Handle group contact selection
     *
     * @param item   Selected contact item
     * @param holder View holder of recycler view
     */
    private fun handleContactSelection(item: ProfileDetailsShareModel, holder: ShareViewHolder, position: Int) {
        if (item.profileDetails.isGroupProfile && !GroupManager.isMemberOfGroup(item.profileDetails.jid, SharedPreferenceManager.getCurrentUserJid())) {
            CustomToast.show(context, context.getString(R.string.user_no_longer_error_message))
            return
        }
        if (!item.profileDetails.isGroupInOfflineMode && !item.profileDetails.isBlocked) {
            if (selectedList.contains(item)) {
                selectedList.remove(item)
                holder.viewBinding.checkSelection.isChecked = false
            } else {
                if (selectedList.size >= Constants.MAX_FORWARD_USER_RESTRICTION) {
                    onItemClickListener!!.onlyForwardUserRestriction()
                    holder.viewBinding.checkSelection.isChecked = false
                } else {
                    selectedList.add(item)
                    holder.viewBinding.checkSelection.isChecked = true
                }
            }
            onItemClickListener!!.onItemClicked(position, item.profileDetails)
        } else if (item.profileDetails.isBlocked){
            blockedUser = item.profileDetails.jid
            commonAlertDialog.showAlertDialog(String.format(context.getString(R.string.unblock_message_label), item.profileDetails.name),
                context.getString(R.string.yes_label), context.getString(R.string.no_label),
                CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, true)
        }
    }

    /**
     * Set the user info of the user from the ProfileDetails
     *
     * @param holder View holder of recycler view
     * @param item   ProfileDetails of the user
     */
    private fun setUserInfo(holder: ShareViewHolder, item: ProfileDetailsShareModel) {
        val profileDetails = item.profileDetails
        if (profileDetails != null) {
            setRosterImage(holder, profileDetails)
            handleStatus(holder.viewBinding.textUserStatus, profileDetails.getChatType(), profileDetails)
            if (profileDetails.isGroupInOfflineMode || profileDetails.isBlocked) {
                holder.viewBinding.contactView.alpha = 0.5f
                holder.viewBinding.contactView.background = null
            } else {
                holder.viewBinding.contactView.alpha = 1.0f
                holder.viewBinding.contactView.setBackgroundResource(R.drawable.recycleritem_ripple)
            }
        } else {
            holder.viewBinding.imageChatPicture.setImageResource(R.drawable.profile_img)
        }
    }

    /**
     * Set the image view of the recent chat for user, broadcast, group chat
     *
     * @param holder Instance of the RecentViewHolder
     * @param profileDetails Instance of the ProfileDetails
     */
    private fun setRosterImage(holder: ShareViewHolder, profileDetails: ProfileDetails) {
        if(searchKey != null && searchKey?.length!! > 0){
            var startIndex = profileDetails.name.toString().checkIndexes(searchKey!!)
            if (startIndex < 0) startIndex = profileDetails.name.toString().toLowerCase().indexOf(searchKey!!, 2)
            val stopIndex = startIndex + searchKey?.length!!
            EmojiUtils.setEmojiTextAndHighLightSearchContact(context, holder.viewBinding.textChatName, profileDetails.name, startIndex, stopIndex)
        }else {
            holder.viewBinding.textChatName.text = profileDetails.name
        }
        holder.viewBinding.imageChatPicture.loadUserProfileImage(context, profileDetails)
    }

    override fun getItemCount(): Int {
        return mTempData!!.size
    }

    /**
     * Filter the contacts in the recycler view. using the temporary data
     *
     * @param filterKey The search filter key
     */
    fun filter(filterKey: String) {
        searchKey = filterKey
        if (mTempData != null) {
            mTempData!!.clear()
            if (TextUtils.isEmpty(filterKey)) {
                mTempData!!.addAll(profileDetailsList!!)
            } else {
                /**
                 * Filter the list from the profileDetails name.
                 */
                handleSearch(mTempData!!, filterKey)
            }
        }
    }

    private fun handleSearch(mTempData: MutableList<ProfileDetailsShareModel>, filterKey: String) {
        for (mKey in profileDetailsList!!) {
            if (mKey.profileDetails.name.contains(filterKey, true))
                mTempData.add(mKey)
        }
    }

    /**
     * Enable the header, that might be Chats or MessagesModel or Contacts.
     *
     * @param holder   View holder of the Chat view
     * @param position Position of the List
     */
    private fun enableHeader(holder: ShareViewHolder, position: Int) {
        /**
         * Enable header if position is zero or previous item is different
         */
        if (position == 0 || canEnableHeader(position)) {
            holder.viewBinding.viewSectionHeader.visibility = View.VISIBLE
            setSearchHeader(holder, position)
        } else {
            holder.viewBinding.viewSectionHeader.visibility = View.GONE
        }
    }


    /**
     * Set the search header in the chat item, which is the Search type
     *
     * @param holder   View holder of the Chat view
     * @param position           Position of the list item
     */
    private fun setSearchHeader(holder: ShareViewHolder, position: Int) {
        val profileDetailsItem = mTempData!![position]
        when {
            profileDetailsItem.type.equals(ChatType.TYPE_GROUP_CHAT, true) -> {
                holder.viewBinding.headerSectionTextView.text = context.getString(R.string.groups)
            }
            profileDetailsItem.type.equals(ChatType.TYPE_CHAT, true) -> {
                holder.viewBinding.headerSectionTextView.text = context.getString(R.string.contacts)
            }
            else -> {
                holder.viewBinding.headerSectionTextView.text = context.getString(R.string.recent_chat)
            }
        }
    }

    fun setContactRecyclerViewItemOnClick(contactRecyclerViewClickListener: RecyclerViewItemClick?) {
        onItemClickListener = contactRecyclerViewClickListener
    }

    /**
     * Check the header is needed for the chat item. Search type of the current item and previous
     * item is different then return true
     *
     * @param position Position of the list item
     * @return boolean True if the header need to enable
     */
    private fun canEnableHeader(position: Int): Boolean {
        return mTempData!![position].type != mTempData!![position - 1]
                .type
    }

    private fun handleStatus(statusTextView: EmojiAppCompatTextView, type: String, profileDetails: ProfileDetails) {
        Log.d("handleStatus", type)
        if (type.equals(ChatType.TYPE_CHAT, true)) {
            setStatusForChatUsers(statusTextView, profileDetails)
        } else {
            setStatusForGroupAndBroadcastUsers(statusTextView, profileDetails)
        }
    }

    private fun setStatusForChatUsers(statusTextView: EmojiAppCompatTextView, profileDetails: ProfileDetails) {
        val status = Utils.returnEmptyStringIfNull(profileDetails.status)
        /**
         * Set status if status not empty
         */
        if (status.isNotEmpty() && !profileDetails.isBlockedMe) {
            /**
             * Emoji utils Which has the emoji related common methods
             */
            statusTextView.visibility = View.VISIBLE
            /**
             * Show user status
             */
            EmojiUtils.setEllipsisText(statusTextView, status)
        } else {
            statusTextView.visibility = View.GONE
        }
    }

    private fun setStatusForGroupAndBroadcastUsers(statusTextView: EmojiAppCompatTextView, profileDetails: ProfileDetails) {
        ProfileDetailsUtils.getGroupUsersNames(profileDetails.jid, object : GetGroupUsersNameCallback {
            override fun onGroupUsersNamePrepared(names: String) {
                Log.d("STATUS_GNB", names)
                statusTextView.visibility = View.VISIBLE
                EmojiUtils.setEmojiText(statusTextView, names)
            }
        })

    }

    /**
     * This class containing the view of the contact items
     */
    class ShareViewHolder(val viewBinding: RowShareItemBinding) : RecyclerView.ViewHolder(viewBinding.root)
}