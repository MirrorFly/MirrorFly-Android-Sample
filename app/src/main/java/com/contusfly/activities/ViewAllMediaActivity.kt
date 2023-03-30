package com.contusfly.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import com.contus.flycommons.ChatType
import com.contusfly.R
import com.contusfly.adapters.SectionsPagerAdapter
import com.contusfly.databinding.ActivityViewAllMediaBinding
import com.contusfly.showToast
import com.contusfly.utils.Constants
import com.contusfly.utils.ProfileDetailsUtils
import com.contusfly.utils.UserInterfaceUtils
import com.contusfly.viewmodels.ViewAllMediaViewModel
import com.contusfly.views.CustomToast
import com.contusflysdk.api.ChatManager

class ViewAllMediaActivity : BaseActivity() {

    private lateinit var binding: ActivityViewAllMediaBinding

    val viewModel : ViewAllMediaViewModel by viewModels()

    /**
     * Roster id of the chat user which displaying the media
     */
    private var profileId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewAllMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val mToolbar: Toolbar = binding.toolbar
        setSupportActionBar(mToolbar)
        mToolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.color_black))
        UserInterfaceUtils.setUpToolBar(this, mToolbar, supportActionBar, Constants.EMPTY_STRING)

        profileId = intent.getStringExtra(Constants.ROSTER_JID)
        val profileInfo = profileId?.let { ProfileDetailsUtils.getProfileDetails(it) }
        if (profileInfo != null) {
            mToolbar.title = profileInfo.name
        }
        if(ChatManager.getAvailableFeatures().isViewAllMediaEnabled){
            profileId?.let {
                viewModel.getMediaList(it)
                viewModel.getDocsList(it)
                viewModel.getLinksList(it)
            }
        } else {
            CustomToast.show(this,resources.getString(R.string.fly_error_forbidden_exception))
        }

    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        if (profileId == jid && status && type == ChatType.TYPE_GROUP_CHAT) {
            showToast(getString(R.string.group_block_message_label))
            startDashboardActivity()
        }
    }

    private fun startDashboardActivity() {
        val dashboardIntent = Intent(applicationContext, DashboardActivity::class.java)
        dashboardIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(dashboardIntent)
        finish()
    }
}