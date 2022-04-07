package com.contusfly.call.groupcall

import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.flycommons.Constants
import com.contus.flycommons.getData
import com.contus.webrtc.CallType
import com.contus.webrtc.api.CallManager
import com.contus.webrtc.api.CallManager.isOnTelephonyCall
import com.contus.call.utils.CallConstants
import com.contusfly.R
import com.contusfly.activities.BaseActivity
import com.contusfly.call.CallPermissionUtils
import com.contusfly.call.groupcall.listeners.RecyclerViewUserItemClick
import com.contusfly.isOnAnyCall
import com.contusfly.showAlertDialog
import com.contusfly.utils.FirebaseUtils.Companion.setAnalytics
import com.contusfly.utils.SharedPreferenceManager
import com.contusfly.utils.UserInterfaceUtils
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CustomRecyclerView
import com.contusflysdk.AppUtils
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.GroupManager
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.views.CustomToast

/**
 * This class used to display the list of contacts from selected group in the Recycler view with
 * multi select option and then choose the list to the create group audio/video call.
 *
 * @author ContusTeam <developers@contus.in>
 * @version 3.0
 */
class UsersSelectionActivity : BaseActivity(), RecyclerViewUserItemClick, View.OnClickListener, CommonAlertDialog.CommonDialogClosedListener {

    private lateinit var listRosters: CustomRecyclerView
    private lateinit var callNowTextView: TextView
    private lateinit var callNowLayout: RelativeLayout
    private lateinit var callNowIcon: ImageView

    /**
     * The adapter of the contacts for group creation selection.
     */
    private var adapterUsers: UserSelectionAdapter? = null

    /**
     * The instance of the CommonAlertDialog
     */
    private var mDialog: CommonAlertDialog? = null

    /**
     * Blocked user jid
     */
    private var blockedUserJid: String? = null

    /**
     * Search view of the list  of contacts.
     */
    private var searchKey: SearchView? = null

    /**
     * The instance of the DoProgressDialog
     */
    private var progressDialog: ProgressDialog? = null

    /**
     * Validate if user block/unblock request sent
     */
    private var isUnblockRequested = false

    private lateinit var callType: String
    private lateinit var groupJid: String

    /**
     * Display the list of profile details in the list
     */
    private var profileDetailsList: MutableList<ProfileDetails>? = null
    private var contusProfilesWithBlockedMe: List<ProfileDetails>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_users_selection)
        setAnalytics("View", "Group Call Contact selection", "")
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        UserInterfaceUtils.setUpToolBar(this, toolbar, supportActionBar, getString(R.string.add_participants))

        /*
       * Populate the list for display the contacts
       */
        listRosters = findViewById<CustomRecyclerView>(R.id.view_contact_list)
        listRosters.layoutManager = LinearLayoutManager(this)
        val emptyView = findViewById<TextView>(R.id.text_empty_view)
        emptyView.text = getString(R.string.msg_no_results)
        listRosters.setEmptyView(emptyView)
        callNowTextView = findViewById(R.id.call_now_text_view)
        callNowLayout = findViewById(R.id.call_now_layout)
        callNowIcon = findViewById(R.id.call_now_icon)
        groupJid = intent.getStringExtra(Constants.GROUP_ID)!!
        callType = intent.getStringExtra(CallConstants.CALL_TYPE)!!

        checkCallIcon()
        callNowLayout.setOnClickListener(this)
        mDialog = CommonAlertDialog(this)
        mDialog?.setOnDialogCloseListener(this)

        // Initiate group call user selection
        callNowLayout.visibility = View.VISIBLE

        updateGroupMembersList()
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun updateGroupMembersList() {
        GroupManager.getGroupMembersList(false, groupJid) { isSuccess, _, data ->
            if (isSuccess) {
                val groupMembers: MutableList<ProfileDetails> =
                    data.getData() as ArrayList<ProfileDetails>
                profileDetailsList =
                    groupMembers.filter { it.jid != SharedPreferenceManager.getCurrentUserJid() }
                        .toMutableList()

                if (contusProfilesWithBlockedMe != null) profileDetailsList!!.addAll(contusProfilesWithBlockedMe!!)

                profileDetailsList!!.sortWith { o1: ProfileDetails, o2: ProfileDetails ->
                    o1.name.compareTo(o2.name)
                }
                adapterUsers = UserSelectionAdapter(this, false)
                adapterUsers!!.setProfileDetails(profileDetailsList!!)
                adapterUsers!!.setRecyclerViewUsersItemOnClick(this)
                /*
                 * if group users less than max user in call all the users will be  auto selected
                 */
                if ((profileDetailsList!!.size + 1) <= CallManager.getMaxCallUsersCount()) {
                    profileDetailsList?.forEach { roster ->
                        if (!roster.isBlocked) {
                            adapterUsers!!.selectedList.add(roster.jid)
                            adapterUsers!!.selectedProfileDetailsList.add(roster)
                        }
                    }
                }
                callNowTextView.text = selectedUserCount
                listRosters.adapter = adapterUsers
            }
        }
    }

    /**
     * Set CallNow Icon based on call type
     */
    private fun checkCallIcon() {
        if (callType == CallType.VIDEO_CALL) {
            callNowIcon.setImageResource(R.drawable.ic_video_call_button)
        } else {
            callNowIcon.setImageResource(R.drawable.ic_phone_call_button)
        }
    }

    override fun listOptionSelected(position: Int) {
        //Do nothing
    }

    /**
     * On dialog closed.
     *
     * @param dialogType the dialog type
     * @param isSuccess  the is success
     */
    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            if (AppUtils.isNetConnected(this)) {
                progressDialog = ProgressDialog(this)
                progressDialog!!.show()
                FlyCore.unblockUser(blockedUserJid!!) { isSuccess, _, _ -> }
                isUnblockRequested = true
            } else CustomToast.show(this, getString(R.string.fly_error_msg_no_internet))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search_group_call, menu)
        searchKey = menu!!.findItem(R.id.action_search).actionView as SearchView
        searchKey!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                adapterUsers!!.filter(s)
                adapterUsers!!.notifyDataSetChanged()
                return false
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onItemClicked(position: Int, roster: ProfileDetails?) {
        callNowTextView.text = selectedUserCount
    }

    override fun onUserSelectRestriction() {
        Toast.makeText(
            context,
            String.format(context!!.getString(R.string.msg_user_call_limit), CallManager.getMaxCallUsersCount() - 1),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onSelectBlockedUser(roster: ProfileDetails) {
        showAlertDialog(this, String.format(if (callType == CallType.AUDIO_CALL) getString(R.string.msg_unblockGroupAudioCall) else getString(R.string.msg_unblockGroupVideoCall), ContactManager.getDisplayName(roster.jid)))
    }

    /**
     * Get Selected users count in CallNow button
     */
    private val selectedUserCount: String
        get() {
            return if (adapterUsers!!.selectedList.isEmpty()) {
                callNowLayout.isEnabled = false
                getString(R.string.msg_no_selected_user_call)
            } else {
                callNowLayout.isEnabled = true
                String.format(getString(R.string.msg_selected_user_call), adapterUsers!!.selectedList.size)
            }
        }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.call_now_layout -> {
                checkTypeAndCall()
            }
        }
    }

    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        updateGroupMembersList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            when (requestCode) {
                Constants.RECORD_AUDIO_CODE -> {
                    CallPermissionUtils(this, false, adapterUsers!!.selectedList, groupJid, true).audioCall()
                }
                Constants.VIDEO_CALL_PERMISSION_CODE -> {
                    CallPermissionUtils(this, false, adapterUsers!!.selectedList, groupJid, true).videoCall()
                }
            }
        }
        callNowLayout.isEnabled = true
    }

    /**
     * Make group call
     */
    private fun checkTypeAndCall() {
        if (adapterUsers!!.selectedList.size > 0) {
            when {
                !AppUtils.isNetConnected(this) -> {
                    CustomToast.show(this, getString(R.string.fly_error_msg_no_internet))
                }
                isOnTelephonyCall(this) -> {
                    showAlertDialog(this, getString(R.string.msg_telephony_call_alert))
                }
                isOnAnyCall() -> {
                    showAlertDialog(this, getString(R.string.msg_ongoing_call_alert_for_group_call))
                }
                else -> {
                    callNowLayout.isEnabled = false
                    if (callType == CallType.AUDIO_CALL) {
                        CallPermissionUtils(this, false, adapterUsers!!.selectedList, groupJid, true).audioCall()
                    } else {
                        CallPermissionUtils(this, false, adapterUsers!!.selectedList, groupJid, true).videoCall()
                    }
                }
            }
        } else {
            Toast.makeText(context, context!!.getString(R.string.error_select_atleast_one), Toast.LENGTH_SHORT).show()
        }
    }
}