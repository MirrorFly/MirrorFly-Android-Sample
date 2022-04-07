package com.contusfly.activities

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import com.contusfly.R
import com.contusfly.adapters.SectionsPagerAdapter
import com.contusfly.databinding.ActivityViewAllMediaBinding
import com.contusfly.utils.Constants
import com.contusfly.utils.UserInterfaceUtils
import com.contusfly.viewmodels.ViewAllMediaViewModel
import com.contusflysdk.api.contacts.ContactManager

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
        profileId?.let {
            val profileInfo = ContactManager.getProfileDetails(it)
            if (profileInfo != null) {
                mToolbar.title = profileInfo.name
            }
            viewModel.getMediaList(it)
            viewModel.getDocsList(it)
            viewModel.getLinksList(it)
        }
    }
}