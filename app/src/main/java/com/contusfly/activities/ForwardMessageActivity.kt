package com.contusfly.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.R
import com.contusfly.adapters.SectionedShareAdapter
import com.contusfly.chat.MessageUtils
import com.contusfly.databinding.ActivityForwardMessageBinding
import com.contusfly.emptyString
import com.contusfly.getChatType
import com.contusfly.interfaces.RecyclerViewItemClick
import com.contusfly.isValidIndex
import com.contusfly.utils.Constants
import com.contusfly.viewmodels.ForwardMessageViewModel
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.ShareDialog
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatActionListener
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.helpers.ResourceHelper
import com.contusflysdk.views.CustomToast
import dagger.android.AndroidInjection
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class ForwardMessageActivity : BaseActivity(), CoroutineScope, RecyclerViewItemClick, CommonAlertDialog.CommonDialogClosedListener {

    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        println("Coroutine Exception :  ${exception.printStackTrace()}")
    }

    private lateinit var binding: ActivityForwardMessageBinding

    /**
     * Used for search
     */
    private lateinit var searchKey: String

    /**
     * Adapter for Contacts List
     */
    private lateinit var mContactsAdapter: SectionedShareAdapter

    private lateinit var shareDialog: ShareDialog

    private lateinit var inputIntent: Intent

    private val forwardMediaMessageIds: ArrayList<String> by lazy { ArrayList() }

    private val viewModel: ForwardMessageViewModel by viewModels()

    /**
     * The common alert dialog to display the alert dialogs in the alert view
     */
    private var commonAlertDialog: CommonAlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivityForwardMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        commonAlertDialog = CommonAlertDialog(this)
        commonAlertDialog!!.setOnDialogCloseListener(this)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        initViews()

        inputIntent = intent

        setClickListeners()
        setObservers()

        val selectedMessages = intent.getStringArrayListExtra(Constants.CHAT_MESSAGE)
        selectedMessages?.let {
            forwardMediaMessageIds.addAll(it)
        }

        shareDialog = ShareDialog(this)

        viewModel.loadForwardChatList(null)
    }

    private fun setClickListeners() {
        binding.next.setOnClickListener {
            if (mContactsAdapter.selectedList.isNotEmpty()) {
                context?.let { LocalBroadcastManager.getInstance(it).sendBroadcast(Intent(Constants.FORWARDED_MESSAGE_ITEM)) }
                shareFiles()
            }
        }
    }

    override fun onGroupProfileUpdated(groupJid: String) {
        super.onGroupProfileUpdated(groupJid)
        updateProfileDetails(groupJid)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        val menuItem = menu.findItem(R.id.action_search)
        val searchView = menuItem.actionView as SearchView

        searchView.setOnSearchClickListener {
            searchView.maxWidth = Int.MAX_VALUE
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(searchString: String): Boolean {
                filterList(searchString)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            finishForwardMedia(false)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViews() {
        binding.toolBar.setTitle(R.string.share_title)
        setSupportActionBar(binding.toolBar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        this.supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_close)

        binding.emptyData.textEmptyView.text = getString(R.string.msg_no_results)
        binding.emptyData.textEmptyView.setTextColor(ResourceHelper.getColor(R.color.color_text_grey))

        binding.viewListRecent.apply {
            layoutManager = LinearLayoutManager(context)
            setEmptyView(binding.emptyData.root)
            setHasFixedSize(true)
            itemAnimator = null
        }

        mContactsAdapter = SectionedShareAdapter(context!!, commonAlertDialog!!)
        mContactsAdapter.selectedList = ArrayList()
        mContactsAdapter.selectedProfileDetailsList = ArrayList()
        mContactsAdapter.setContactRecyclerViewItemOnClick(this)
    }

    private fun setObservers() {

        viewModel.profileDetailsShareModelList.observe(this) {
            it?.let {
                mContactsAdapter.setProfileDetails(it)
                binding.viewListRecent.adapter = mContactsAdapter
                mContactsAdapter.notifyDataSetChanged()
            }
        }

        viewModel.newIndex.observe(this) {
            if (it.first.isValidIndex())
                mContactsAdapter.insertProfileDetails(it.first, it.second)
        }
    }

    /**
     * To handle broadcast of any user's profile changes
     * like Profile pic, Profile msg
     */
    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        updateProfileDetails(jid)
    }

    /**
     * To handle callback of any user's profile deleted
     */
    override fun userDeletedHisProfile(jid: String) {
        super.userDeletedHisProfile(jid)
        val position = getPositionOfProfile(jid)
        if (position.isValidIndex()) {
            mContactsAdapter.removeProfileDetails(position, jid)
            binding.selectedUsers.text = selectedUserNames
        }
    }

    private fun getPositionOfProfile(jid: String): Int {
        viewModel.profileDetailsShareModelList.value?.forEachIndexed { index, item ->
            if (item.profileDetails!!.jid!!.equals(jid, ignoreCase = true))
                return index
        }

        return -1
    }


    /**
     * Filter the list from the search key
     *
     * @param filterKey The search key from the search bar
     */
    fun filterList(filterKey: String) {
        searchKey = filterKey.trim()
        mContactsAdapter.filter(searchKey)
        mContactsAdapter.notifyDataSetChanged()
    }

    private fun shareFiles() {
        shareDialog.initializeAndShowShareDialog("", "Forwarding Message")
        val jidList = ArrayList<String>()
        val chatTypeList = ArrayList<String>()
        for (model in mContactsAdapter.selectedList) {
            jidList.add(model.profileDetails.jid)
            chatTypeList.add(model.profileDetails.getChatType())
        }
        sendMediaFilesForSingleUser()
    }

    private fun sendMediaFilesForSingleUser() {
        if (AppUtils.isNetConnected(this)) {
            sendMediaMessages()
        } else {
            shareDialog.dismissShareDialog()
            CustomToast.show(context, getString(R.string.msg_no_internet))
        }
    }


    private fun sendMediaMessages() {
        validateRecalledMessage()
        launch(exceptionHandler) {
            ChatManager.forwardMessagesToMultipleUsers(forwardMediaMessageIds, selectedRosterList, object : ChatActionListener {
                override fun onResponse(isSuccess: Boolean, message: String) {
                    if (!isSuccess)
                        CustomToast.show(context, message)
                    finishForwardMedia(true)
                }
            })
        }
    }

    private fun validateRecalledMessage() {
        val recalledMessagesIds = MessageUtils.filterRecalledMessages(forwardMediaMessageIds)
        if (recalledMessagesIds.isNotEmpty()) {
            val diff = forwardMediaMessageIds.size - recalledMessagesIds.size
            if (diff == 0)
                if (forwardMediaMessageIds.size == 1) {
                    CustomToast.showShortToast(context, getString(R.string.cannot_forward_recalled_message))
                    finish()
                } else
                    CustomToast.showShortToast(context, String.format(getString(R.string.cannot_forward_recalled_messages), recalledMessagesIds.size))
            else
                CustomToast.showShortToast(context, String.format(getString(R.string.cannot_forward_recalled_messages), diff))
            forwardMediaMessageIds.removeAll(recalledMessagesIds)
        }
    }

    override fun onItemClicked(position: Int, profileDetails: ProfileDetails?) {
        binding.selectedUsers.text = selectedUserNames
        if (mContactsAdapter.selectedList.isNotEmpty()) {
            binding.next.visibility = View.VISIBLE
        } else if (mContactsAdapter.selectedList.isEmpty()) {
            binding.next.visibility = View.INVISIBLE
        }
    }

    override fun onlyForwardUserRestriction() {
        Toast.makeText(context, context!!.getString(R.string.maximum_user_forward_validation), Toast.LENGTH_SHORT).show()
    }

    private val selectedUserNames: String
        get() {
            val stringBuilder = StringBuilder()
            return if (mContactsAdapter.selectedList.isEmpty()) {
                "No user selected"
            } else {
                for (model in mContactsAdapter.selectedList) {
                    stringBuilder.append(model.profileDetails.name)
                    stringBuilder.append(", ")
                }
                val selectedNames = stringBuilder.toString()
                selectedNames.substring(0, selectedNames.length - 2)
            }
        }

    private val selectedRosterList: ArrayList<String>
        get() {
            val userList = ArrayList<String>()
            for (model in mContactsAdapter.selectedList) {
                userList.add(model.profileDetails.jid)
            }
            return userList
        }


    private fun finishForwardMedia(isSuccess: Boolean) {
        shareDialog.dismissShareDialog()
        finish()
        if (isSuccess && selectedRosterList.size == 1){
            val intent = Intent(context, ChatActivity::class.java)
            startActivity(intent.putExtra(LibConstants.JID,  mContactsAdapter.selectedList[0].profileDetails.jid)
                .putExtra(Constants.CHAT_TYPE, mContactsAdapter.selectedList[0].profileDetails.getChatType())
                .putExtra("externalCall", true))
        }
    }

    override fun onBackPressed() {
        finishForwardMedia(false)
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess && mContactsAdapter.blockedUser.isNotEmpty()) {
            if (AppUtils.isNetConnected(this)) {
                FlyCore.unblockUser(mContactsAdapter.blockedUser) { isSuccess, _, _ ->
                    if (isSuccess) {
                        updateProfileDetails(mContactsAdapter.blockedUser)
                        mContactsAdapter.blockedUser = emptyString()
                    } else {
                        mContactsAdapter.blockedUser = emptyString()
                        CustomToast.show(this, Constants.ERROR_SERVER)
                    }
                }
            } else {
                CustomToast.show(this, getString(R.string.msg_no_internet))
                mContactsAdapter.blockedUser = emptyString()
            }
        } else {
            mContactsAdapter.blockedUser = emptyString()
        }
    }

    /*
    * Update Profile Details */
    private fun updateProfileDetails(userJid: String) {
        val position = getPositionOfProfile(userJid)
        if (position >= 0) {
            val profileDetails = ContactManager.getProfileDetails(userJid)
            mContactsAdapter.updateProfileDetails(position, profileDetails)
        } else
            viewModel.loadForwardChatList(userJid)
    }

    override fun listOptionSelected(position: Int) {
        //Do nthg
    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        if (status && mContactsAdapter.selectedList.any { it.profileDetails.jid == jid }) {
            val index = mContactsAdapter.selectedList.indexOfFirst { profileDetailsShareModel -> profileDetailsShareModel.profileDetails.jid == jid }
            if (index.isValidIndex()) mContactsAdapter.selectedList.removeAt(index)
        }
        viewModel.loadForwardChatList(null)
        binding.selectedUsers.text = selectedUserNames
    }
}