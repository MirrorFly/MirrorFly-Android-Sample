package com.contusfly.call.groupcall

import android.content.Context
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.contus.flycommons.Constants
import com.contus.flycommons.LogMessage
import com.contusfly.*
import com.contusfly.adapters.ViewPagerAdapter
import com.contusfly.call.OnGngCallParticipantsList
import com.contusfly.databinding.FragmentParticipantsListBinding
import com.contusfly.utils.UserInterfaceUtils
import com.google.android.material.tabs.TabLayout
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.ArrayList
import kotlin.coroutines.CoroutineContext
import android.app.Activity


class ParticipantsListFragment : Fragment(), CoroutineScope {

    private lateinit var participantsListBinding: FragmentParticipantsListBinding

    private lateinit var tabLayout: TabLayout

    private lateinit var mViewPager: ViewPager

    private lateinit var mAdapter: ViewPagerAdapter

    private lateinit var groupId: String

    private var callConnectedUserList: ArrayList<String>? = null

    private lateinit var participantsTextView: TextView
    private lateinit var addParticipantsTextView: TextView

    //View Fragments
    private lateinit var onGngCallParticipantsListFragment: OnGngCallParticipantsList
    private lateinit var addParticipantsListFragment: AddParticipantFragment

    /**
     * Validate if the call is one to one call
     */
    private var isAddUsersToOneToOneCall: Boolean = false

    /**
     * Search view of the list  of contacts.
     */
    private var searchKey: SearchView? = null

    private var tabPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        arguments?.let {
            groupId = it.getString(Constants.GROUP_ID, "")
            isAddUsersToOneToOneCall = it.getBoolean(AddParticipantFragment.ADD_USERS_TO_ONE_TO_ONE_CALL, false)
            callConnectedUserList = it.getStringArrayList(AddParticipantFragment.CONNECTED_USER_LIST)
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        participantsListBinding = FragmentParticipantsListBinding.inflate(inflater, container, false)
        return participantsListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        val toolbar = participantsListBinding.toolBar.toolbar
        (activity as AppCompatActivity?)!!.setSupportActionBar(toolbar)
        UserInterfaceUtils.setUpToolBar(requireActivity(), toolbar, (activity as AppCompatActivity?)!!.supportActionBar, Constants.EMPTY_STRING)
        toolbar.navigationIcon?.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
            ContextCompat.getColor(requireContext(), R.color.color_blue), BlendModeCompat.SRC_ATOP)
        toolbar.setNavigationOnClickListener { requireActivity().supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE) }
        mAdapter = ViewPagerAdapter(childFragmentManager, addFragmentsToViewPagerAdapter(),
            arrayOf(getString(R.string.label_participants), getString(R.string.label_add_participants)))
        tabLayout = participantsListBinding.tabs
        mViewPager = participantsListBinding.viewPager

        setUpViewPager()
        setUpTabLayout()
        setUpTabColors(0)
    }

    private fun setUpTabLayout() {
        tabLayout.show()
        tabLayout.setupWithViewPager(mViewPager)

        val viewChats = layoutInflater.inflate(R.layout.custom_tabs, null)
        val viewCalls = layoutInflater.inflate(R.layout.custom_tabs, null)

        tabLayout.getTabAt(0)?.customView = viewChats
        tabLayout.getTabAt(1)?.customView = viewCalls

        participantsTextView = viewChats.findViewById(R.id.text)
        addParticipantsTextView = viewCalls.findViewById(R.id.text)

        participantsTextView.text = getString(R.string.label_participants)
        addParticipantsTextView.text = getString(R.string.label_add_participants)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener{
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tabPosition = tab!!.position
                setUpTabColors(tabPosition)
                if (tabPosition == 0) {
                    hideKeyboard(requireContext())
                    addParticipantsListFragment.filterResult(Constants.EMPTY_STRING)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                LogMessage.d(TAG, "${com.contus.call.CallConstants.CALL_UI} tab position == ${tab!!.position}")
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                LogMessage.d(TAG, "${com.contus.call.CallConstants.CALL_UI} tab position == ${tab!!.position}")
            }

        })
    }

    fun hideKeyboard(context: Context) {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // Check if no view has focus
        val currentFocusedView = (context as Activity).currentFocus
        currentFocusedView?.let {
            inputMethodManager.hideSoftInputFromWindow(
                currentFocusedView.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    private fun setUpViewPager() {
        mViewPager.offscreenPageLimit = 2
        mViewPager.adapter = mAdapter
        mViewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
    }

    private fun addFragmentsToViewPagerAdapter(): ArrayList<Fragment> {
        val fragmentsArray = ArrayList<Fragment>(2)
        onGngCallParticipantsListFragment = OnGngCallParticipantsList.newInstance(callConnectedUserList)
        addParticipantsListFragment = AddParticipantFragment.newInstance(
            groupId,
            groupId.isEmpty(),
            callConnectedUserList
        )
        fragmentsArray.add(onGngCallParticipantsListFragment)
        fragmentsArray.add(addParticipantsListFragment)
        return fragmentsArray
    }

    fun refreshUsersList() {
        LogMessage.d(TAG, "${com.contus.call.CallConstants.CALL_UI} refreshUsersList")
        addParticipantsListFragment.refreshUsersList()
    }

    fun refreshUser(jid: String) {
        LogMessage.d(TAG, "${com.contus.call.CallConstants.CALL_UI} refreshUser == $jid")
        addParticipantsListFragment.refreshUser(jid)
        onGngCallParticipantsListFragment.refreshUser(jid)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_search_group_call, menu)
        if (tabPosition == 0) hideMenu(menu!!.findItem(R.id.action_search))
        else showMenu(menu!!.findItem(R.id.action_search))
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val menuItem = menu.findItem(R.id.action_search)
        searchKey = menuItem.actionView as SearchView
        searchKey!!.setOnSearchClickListener {
            searchKey!!.maxWidth = Int.MAX_VALUE
        }
        searchKey!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(searchValue: String): Boolean {
                addParticipantsListFragment.filterResult(searchValue.trim())
                return false
            }
        })

        menuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                tabLayout.gone()
                searchKey!!.maxWidth = Integer.MAX_VALUE
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                tabLayout.show()
                return true
            }
        })

        super.onPrepareOptionsMenu(menu)
    }

    companion object {
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
        ) = ParticipantsListFragment().apply {
            arguments = Bundle().apply {
                putString(Constants.GROUP_ID, groupId)
                putBoolean(AddParticipantFragment.ADD_USERS_TO_ONE_TO_ONE_CALL, isOneToOneCall)
                putStringArrayList(AddParticipantFragment.CONNECTED_USER_LIST, callUsersList)
            }
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    private fun setUpTabColors(position: Int) {
        when (position) {
            0 -> {
                participantsTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_tab_text_indicator))
                addParticipantsTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.dashboard_toolbar_text_color))
            }
            1 -> {
                participantsTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.dashboard_toolbar_text_color))
                addParticipantsTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_tab_text_indicator))
            }
        }
    }

    fun updateUserJoined(userJid: String) {
        onGngCallParticipantsListFragment.updateUserJoined(userJid)
    }

    fun updateUserLeft(userJid: String) {
        onGngCallParticipantsListFragment.updateUserLeft(userJid)
    }

    fun handleMuteEvents(userJid: String) {
        onGngCallParticipantsListFragment.handleMuteEvents(userJid)
    }

}