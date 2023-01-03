package com.contusfly.call.groupcall

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.contus.flycommons.TAG
import com.contus.webrtc.api.CallManager
import com.contus.call.utils.GroupCallUtils
import com.contusfly.R
import com.contusfly.adapters.BaseViewHolder
import com.contusfly.call.groupcall.listeners.RecyclerViewUserItemClick
import com.contusfly.isItSavedContact
import com.contusfly.isValidIndex
import com.contusfly.loadUserProfileImage
import com.contusfly.utils.EmojiUtils
import com.contusfly.views.CircularImageView
import com.contusfly.views.CustomTextView
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.utils.Utils
import java.util.*
import kotlin.collections.ArrayList

class UserSelectionAdapter(val context: Context, private val isAddUserInCall: Boolean) : RecyclerView.Adapter<UserSelectionAdapter.UserViewHolder>() {

    /**
     * Selected users from the search list.
     */
    var selectedList: ArrayList<String> = ArrayList()

    /**
     * The ProfileDetails list to display in the recycler view.
     */
    var profileDetailsList: ArrayList<ProfileDetails>? = null

    /**
     * Selected rosters from the search list.
     */
    var selectedProfileDetailsList: ArrayList<ProfileDetails> = java.util.ArrayList()

    /**
     * The temporary data of the list to reuse the list.
     */
    val mTempData = java.util.ArrayList<ProfileDetails>()

    /**
     * RecyclerView ClickLister Adapter
     */
    private var onItemClickListener: RecyclerViewUserItemClick? = null

    /**
     * Sets the list data to rosters list clear the temp data and refresh the view
     *
     * @param profileDetailsList the new data
     */
    fun setProfileDetails(profileDetailsList: List<ProfileDetails>) {
        mTempData.clear()
        this.mTempData.addAll(profileDetailsList)
        this.profileDetailsList = java.util.ArrayList()
        this.profileDetailsList!!.addAll(this.mTempData)
    }

    /**
     * Set the updated data to ProfileDetails list and refresh the view
     *
     * @param profileDetails the new data
     */
    fun updateProfileDetails(profileDetails: ProfileDetails?) {
        val userIndex = profileDetailsList!!.indexOfFirst { profile -> profile.jid == profileDetails!!.jid }
        if (userIndex.isValidIndex()) {
            profileDetails?.let {
                this.profileDetailsList!![userIndex] = profileDetails
                val index = mTempData.indexOfFirst { it.jid == profileDetails.jid }
                if (index.isValidIndex()) {
                    mTempData[index] = profileDetails
                    notifyItemChanged(index)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        return UserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.row_select_contact_item, parent, false))
    }

    override fun getItemCount(): Int {
        return mTempData.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val item: ProfileDetails = mTempData[position]
        Log.d(TAG, "$TAG ${item.jid}")
        holder.emailContactIcon.visibility = View.GONE
        setUserInfo(holder, item)
        holder.contactView.setBackgroundResource(R.drawable.recycleritem_ripple)
        holder.header.setOnClickListener(null)

        holder.checkBox.visibility = View.VISIBLE
        val onClickListener = View.OnClickListener { handleContactSelection(item, position, holder) }
        holder.contactView.setOnClickListener(onClickListener)
        holder.checkBox.setOnClickListener(onClickListener)

        enableCheckbox(holder, item)
    }

    fun removeUser(jid: String) {
        val index = mTempData.indexOfFirst { it.jid == jid }
        if (index.isValidIndex()) {
            selectedList.remove(jid)
            val selectedIndex = selectedProfileDetailsList.indexOfFirst { it.jid == jid }
            if (selectedIndex.isValidIndex())
                selectedProfileDetailsList.removeAt(selectedIndex)
            else
                selectedProfileDetailsList.remove(mTempData[index])
            mTempData.removeAt(index)
            notifyItemRemoved(index)
        }
        val userIndex = profileDetailsList?.indexOfFirst { it.jid == jid }
        if (userIndex != null && userIndex.isValidIndex()) {
            profileDetailsList?.removeAt(userIndex)
        }
    }

    /**
     * Enable the checkbox based on the selected list.
     */
    private fun enableCheckbox(holder: UserSelectionAdapter.UserViewHolder, item: ProfileDetails) {
        holder.checkBox.isChecked = selectedList.contains(item.jid)
    }

    /**
     * Handle group contact selection
     *
     * @param item      Selected contact item
     * @param holder    View holder of recycler view
     */
    private fun handleContactSelection(item: ProfileDetails, position: Int, holder: UserSelectionAdapter.UserViewHolder) {
        if (item.isBlocked) {
            holder.checkBox.isChecked = false
            onItemClickListener!!.onSelectBlockedUser(item)
        } else {
            if (!selectedList.contains(item.jid)) {
                if (selectedList.size >= if (isAddUserInCall) (CallManager.getMaxCallUsersCount() -
                            (GroupCallUtils.getAvailableCallUsersList().size + 1))
                    else CallManager.getMaxCallUsersCount() - 1) {
                    onItemClickListener!!.onUserSelectRestriction()
                    holder.checkBox.isChecked = false
                } else {
                    selectedList.add(item.jid)
                    selectedProfileDetailsList.add(item)
                    holder.checkBox.isChecked = true
                    onItemClickListener!!.onItemClicked(position, item)
                }
            } else {
                selectedList.remove(item.jid)
                selectedProfileDetailsList.remove(item)
                holder.checkBox.isChecked = false
                onItemClickListener!!.onItemClicked(position, item)
            }
        }
    }

    /**
     * Set the user info of the user from the Roster
     *
     * @param holder View holder of recycler view
     * @param profileDetails   Roster of the user
     */
    private fun setUserInfo(holder: UserSelectionAdapter.UserViewHolder, profileDetails: ProfileDetails) {
        holder.txtName.text = profileDetails.name
        holder.txtStatus.visibility = View.GONE
        holder.contactLayout.alpha = if (profileDetails.isBlocked) 0.5f else 1.0f
        holder.imgRoster.loadUserProfileImage(context, profileDetails)
        val status = Utils.returnEmptyStringIfNull(profileDetails.status)

        // Set status if status not empty
        if (status.isNotEmpty() && !profileDetails.isBlockedMe) {

            // Emoji utils Which has the emoji related common methods
            holder.txtStatus.visibility = View.VISIBLE

            // Show user status
            EmojiUtils.setEmojiText(holder.txtStatus, status)
        }
    }

    /**
     * Filter the contacts in the recycler view. using the temporary data
     *
     * @param filterKey The search filter key
     */
    fun filter(filterKey: String) {
        mTempData.clear()
        if (TextUtils.isEmpty(filterKey)) {
            mTempData.addAll(profileDetailsList!!)
        } else {
            /*
             * Filter the list from the roster name.
             */
            for (mKey in profileDetailsList!!) {
                if (mKey.isItSavedContact() && mKey.name.toLowerCase(Locale.getDefault()).contains(filterKey.toLowerCase(Locale.getDefault())))
                    mTempData.add(mKey)
            }
            setSearchCount(mTempData)
        }
    }

    private fun setSearchCount(tempData: List<ProfileDetails>) {
        val tempRoster: MutableList<ProfileDetails> = java.util.ArrayList()
        var i = 0
        while (i < tempData.size) {
            tempRoster.add(tempData[i])
            i++
        }
    }

    fun setRecyclerViewUsersItemOnClick(recyclerViewUsersItemClick: RecyclerViewUserItemClick?) {
        onItemClickListener = recyclerViewUsersItemClick
    }

    fun updateRoster(userJid: String) {
        if (profileDetailsList != null) {
            val index = profileDetailsList!!.indexOfFirst { it.jid == userJid }
            if (index >= 0) {
                val tempIndex = mTempData.indexOfFirst { it.jid == userJid }
                if (tempIndex >= 0) {
                    notifyItemChanged(tempIndex)
                }
            }
        }
    }


    inner class UserViewHolder(view: View) : BaseViewHolder(view) {
        /**
         * The name of the Roster.
         */
        var txtName: TextView = view.findViewById(R.id.text_chat_name)

        /**
         * The Layout of the Contact.
         */
        var contactLayout: LinearLayout = view.findViewById(R.id.contact_item)

        /**
         * The status of the Roster
         */
        var txtStatus: CustomTextView = view.findViewById(R.id.text_user_status)

        /**
         * The image view of the Roster.
         */
        var imgRoster: CircularImageView = view.findViewById(R.id.image_chat_picture)

        var emailContactIcon: CircularImageView = view.findViewById(R.id.email_contact_icon)

        /**
         * Enable the check box for multi select.
         */
        var checkBox: CheckBox = view.findViewById(R.id.check_selection)

        var header: LinearLayout = view.findViewById(R.id.header)

        var contactView: LinearLayout = view.findViewById(R.id.contact_view)
    }

}