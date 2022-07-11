package com.contusfly.call.groupcall

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.flycommons.Constants
import com.contus.flycommons.LogMessage
import com.contus.flycommons.SharedPreferenceManager
import com.contus.flynetwork.ApiCalls
import com.contus.webrtc.api.CallManager
import com.contus.call.utils.GroupCallUtils
import com.contusfly.*
import com.contusfly.call.groupcall.listeners.RecyclerViewUserItemClick
import com.contusfly.di.factory.AppViewModelFactory
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CustomRecyclerView
import com.contusflysdk.AppUtils
import com.contusflysdk.activities.FlyBaseActivity
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.views.CustomToast
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext


/**
 * A simple [Fragment] subclass.
 * Use the [AddParticipantFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AddParticipantFragment : Fragment(), RecyclerViewUserItemClick, CoroutineScope, CommonAlertDialog.CommonDialogClosedListener {

    @Inject
    lateinit var callViewModelFactory: AppViewModelFactory
    private val viewModel: CallViewModel by viewModels {
        callViewModelFactory
    }

    @Inject
    lateinit var apiCalls: ApiCalls

    private lateinit var addParticipantsLayout: RelativeLayout

    private lateinit var addParticipantsTextView: TextView

    /**
     * Display the contact list and searched list in the recycler view
     */
    private lateinit var listContact: CustomRecyclerView

    private lateinit var emptyView: TextView

    private lateinit var onGoingCallLink: String

    private lateinit var callLinkView: LinearLayout

    private lateinit var callLink: AppCompatTextView

    private lateinit var callLinkCopyIcon: ImageView

    private lateinit var groupId: String

    private lateinit var blockedUserJid: String

    private var callConnectedUserList: ArrayList<String>? = null

    private var isRefreshing = false

    /**
     * The common alert dialog to display the alert dialogs in the alert view
     */
    private lateinit var commonAlertDialog: CommonAlertDialog

    /**
     * Validate if the call is one to one call
     */
    private var isAddUsersToOneToOneCall: Boolean = false

    private val mAdapter by lazy {
        UserSelectionAdapter(requireContext(), true)
    }

    /**
     * Get Selected users count in CallNow button
     */
    private val selectedUserCount: String
        get() {
            return if (mAdapter.selectedList.isEmpty()) {
                addParticipantsLayout.visibility = View.GONE
                addParticipantsLayout.isEnabled = false
                getString(R.string.msg_add_participant)
            } else {
                addParticipantsLayout.visibility = View.VISIBLE
                addParticipantsLayout.isEnabled = true
                String.format(getString(R.string.msg_add_participants), mAdapter.selectedList.size)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            groupId = it.getString(Constants.GROUP_ID, "")
            isAddUsersToOneToOneCall = it.getBoolean(ADD_USERS_TO_ONE_TO_ONE_CALL, false)
            callConnectedUserList = it.getStringArrayList(CONNECTED_USER_LIST)
            callConnectedUserList?.let { list ->
                if (list.contains(SharedPreferenceManager.instance.currentUserJid))
                    list.remove(SharedPreferenceManager.instance.currentUserJid)
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onDetach() {
        GroupCallUtils.setIsAddUsersToTheCall(false)
        FlyBaseActivity.hideSoftKeyboard(requireActivity())
        super.onDetach()
    }

    override fun onResume() {
        GroupCallUtils.setIsAddUsersToTheCall(true)
        isRefreshing = false
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (isAddUsersToOneToOneCall)
            viewModel.getInviteUserList(callConnectedUserList)
        else
            viewModel.getInviteUserListForGroup(groupId, callConnectedUserList)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_add_participant, container, false)
        initView(view)
        setListeners()
        setObservers()
        return view
    }

    private fun setObservers() {
        requireActivity().let {
            viewModel.profileUpdatedLiveData.observe(viewLifecycleOwner, { userJid ->
                mAdapter.updateRoster(userJid)
            })

            viewModel.inviteUserList.observe(viewLifecycleOwner, {
                if (it.isEmpty()) {
                    val message = if (isAddUsersToOneToOneCall) requireContext().getString(R.string.all_members_already_in_call) else requireContext().getString(R.string.all_members_already_in_group_call)
                    emptyView.text = message
                    emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F)
                }
                mAdapter.setProfileDetails(it)
                mAdapter.notifyDataSetChanged()
            })
        }
    }

    private fun initView(view: View) {
        onGoingCallLink = CallManager.getCallLink()

        callLinkView = view.findViewById(R.id.call_link_view)
        callLink = view.findViewById(R.id.call_link)
        callLinkCopyIcon = view.findViewById(R.id.call_link_copy)
        emptyView = view.findViewById(R.id.text_empty_view)
        emptyView.text = getString(R.string.msg_no_results)
        emptyView.setTextColor(ResourcesCompat.getColor(resources, R.color.color_text_grey, null))
        mAdapter.setHasStableIds(true)
        listContact = view.findViewById(R.id.view_contact_list)
        setContactAdapter()

        if (onGoingCallLink.isNotEmpty()) {
            callLinkView.visibility = View.VISIBLE
            callLink.text = onGoingCallLink
            callLinkCopyIcon.setOnClickListener {
                val clipboardManager  = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData  = ClipData.newPlainText("text", BuildConfig.WEB_CHAT_LOGIN + onGoingCallLink)
                clipboardManager .setPrimaryClip(clipData )
                CustomToast.show(context, getString(R.string.link_copied_clipboard))
            }
        }

        addParticipantsLayout = view.findViewById(R.id.add_participants_layout)
        addParticipantsTextView = view.findViewById(R.id.add_participants_text_view)
    }

    private fun setContactAdapter() {
        listContact.apply {
            layoutManager = LinearLayoutManager(context)
            setItemViewCacheSize(0)
            setHasFixedSize(true)
            setEmptyView(emptyView)
            itemAnimator = null
            adapter = mAdapter
        }
        mAdapter.setRecyclerViewUsersItemOnClick(this)
    }

    private fun setListeners() {
        commonAlertDialog = CommonAlertDialog(context)
        commonAlertDialog.setOnDialogCloseListener(this)
        addParticipantsLayout.setOnClickListener {
            if (mAdapter.selectedList.isNotEmpty()) {
                addParticipantsLayout.isEnabled = false
                CallManager.inviteUsersToOngoingCall(mAdapter.selectedList)
                requireActivity().supportFragmentManager.popBackStackImmediate()
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_select_atleast_one), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onItemClicked(position: Int, roster: ProfileDetails?) {
        addParticipantsTextView.text = selectedUserCount
    }

    override fun onUserSelectRestriction() {
        val availableCount =
            CallManager.getMaxCallUsersCount() - (GroupCallUtils.getAvailableCallUsersList().size + 1) //plus 1 for own user
        if (availableCount == 1)
            Toast.makeText(
                requireContext(),
                String.format(
                    requireContext().getString(R.string.msg_user_call_limit_one_user),
                    availableCount
                ),
                Toast.LENGTH_SHORT
            ).show()
        else
            Toast.makeText(
                requireContext(),
                String.format(getString(R.string.max_members_in_call), CallManager.getMaxCallUsersCount()),
                Toast.LENGTH_SHORT
            ).show()
    }

    override fun onSelectBlockedUser(profile: ProfileDetails) {
        blockedUserJid = profile.jid
        commonAlertDialog.showAlertDialog(String.format(requireContext().getString(R.string.unblock_message_label), ContactManager.getDisplayName(profile.jid)),
            requireContext().getString(R.string.yes_label), requireContext().getString(R.string.no_label),
            CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, true)
    }

    fun refreshUsersList() {
        LogMessage.i(TAG, "${com.contus.call.CallConstants.CALL_UI} refreshUsersList")
        getRefreshedProfilesList()
    }

    fun refreshUser(jid: String) {
        LogMessage.i(TAG, "${com.contus.call.CallConstants.CALL_UI} refreshUser")
        val index = mAdapter.profileDetailsList?.indexOfFirst { it.jid == jid }
        if (index != null && index.isValidIndex()) {
            updateProfileDetails(jid)
        }
    }

    fun removeUser(jid: String) {
        LogMessage.i(TAG, "${com.contus.call.CallConstants.CALL_UI} removeUser")
        mAdapter.removeUser(jid)
    }

    fun onAdminBlockedStatus(jid: String, type: String, status: Boolean) {
        LogMessage.i(TAG, "OnAdminBlockedStatus jid = $jid, type = $type, status = $status")
        if (status && mAdapter.selectedList.isNotEmpty()) {
            val isJidSelected = mAdapter.selectedList.any { it == jid }
            val index = mAdapter.selectedList.indexOf(jid)
            if (isJidSelected && index.isValidIndex()) {
                mAdapter.selectedList.removeAt(index)
            }
            addParticipantsTextView.text = selectedUserCount
        }
        getRefreshedProfilesList()
    }

    private fun getRefreshedProfilesList() {
        lifecycleScope.launchWhenStarted {
            if (isAddUsersToOneToOneCall)
                viewModel.getInviteUserList(callConnectedUserList)
            else
                viewModel.getInviteUserListForGroup(groupId, callConnectedUserList)
        }
    }

    fun filterResult(searchKey: String) {
        mAdapter.filter(searchKey)
        mAdapter.notifyDataSetChanged()
    }

    companion object {

        /**
         * key constant for add user for existing call action
         */
        const val ADD_USERS_TO_ONE_TO_ONE_CALL = "add_users_to_one_to_one_call"

        const val CONNECTED_USER_LIST = "connected_user_list"

        /**
         * The constructor used to create and initialize a new instance of this class object, with the
         * specified initialization parameters.
         *
         * @return a new object created by calling the constructor of this object representation.
         */
        @JvmStatic
        fun newInstance(
            groupId: String?,
            isOneToOneCall: Boolean,
            callUsersList: ArrayList<String>?
        ) = AddParticipantFragment().apply {
            arguments = Bundle().apply {
                putString(Constants.GROUP_ID, groupId)
                putBoolean(ADD_USERS_TO_ONE_TO_ONE_CALL, isOneToOneCall)
                putStringArrayList(CONNECTED_USER_LIST, callUsersList)
            }
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            if (AppUtils.isNetConnected(requireContext())) {
                FlyCore.unblockUser(blockedUserJid) { isSuccess, _, _ ->
                    if (isSuccess) {
                        updateProfileDetails(blockedUserJid)
                    } else {
                        CustomToast.show(requireContext(), com.contusfly.utils.Constants.ERROR_SERVER)
                        blockedUserJid = emptyString()
                    }
                }
            } else {
                CustomToast.show(requireContext(), getString(R.string.msg_no_internet))
                blockedUserJid = emptyString()
            }
        }
    }

    override fun listOptionSelected(position: Int) {
        //Do nthg
    }

    /*
    * Update Profile Details */
    private fun updateProfileDetails(userJid: String) {
        val profileDetails = ContactManager.getProfileDetails(userJid)
        mAdapter.updateProfileDetails(profileDetails)
        blockedUserJid = emptyString()
    }
}