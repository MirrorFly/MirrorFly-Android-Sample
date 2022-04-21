package com.contusfly.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.contus.flycommons.Constants
import com.contus.flycommons.emptyStringFE
import com.contus.flycommons.getData
import com.contusfly.*
import com.contusfly.databinding.ActivityUserInfoBinding
import com.contusfly.network.NetworkConnection
import com.contusfly.utils.AppConstants
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.utils.MediaUtils
import com.contusflysdk.utils.Utils
import com.google.android.material.appbar.AppBarLayout
import net.opacapp.multilinecollapsingtoolbar.CollapsingToolbarLayout
import java.io.File

class UserInfoActivity : BaseActivity() {

    private lateinit var binding: ActivityUserInfoBinding

    private lateinit var mAppBarLayout: AppBarLayout

    private lateinit var mCoordinatorLayout: CoordinatorLayout

    private lateinit var collapsingToolbar: CollapsingToolbarLayout

    private lateinit var userProfileDetails: ProfileDetails

    /**
     * check weather the collapsed or not
     */
    private var isToolbarCollapsed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        userProfileDetails = intent.getParcelableExtra(AppConstants.PROFILE_DATA)!!
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setToolbar()
        getLastSeenData()
        setUserData()
        binding.muteSwitch.setOnCheckedChangeListener { _, isChecked ->
            FlyCore.updateChatMuteStatus(userProfileDetails.jid, isChecked)
        }
        binding.textMedia.setOnClickListener {
            launchActivity<ViewAllMediaActivity> {
                putExtra(Constants.ROSTER_JID, userProfileDetails.jid)
            }
        }
        observeNetworkListener()
        mediaValidation()
    }

    private fun observeNetworkListener() {
        val networkConnection = NetworkConnection(applicationContext)
        networkConnection.observe(this, {
            if (!it)
                getLastSeenData()
        })
    }

    override fun onConnected() {
        super.onConnected()
        getLastSeenData()
    }

    private fun setToolbar() {

        val toolbar = binding.toolbar
        collapsingToolbar = binding.collapsingToolbar
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        mAppBarLayout = binding.appBarLayout
        mCoordinatorLayout = binding.coordinatorLayout
        mAppBarLayout.post {
            val heightPx = collapsingToolbar.height
            setAppBarOffset(heightPx / 3)
        }
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.navigationIcon!!.applyMultiplyColorFilter(ContextCompat.getColor(this, R.color.color_black))
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = Constants.EMPTY_STRING
        mAppBarLayout.addOnOffsetChangedListener(AppBarLayout.BaseOnOffsetChangedListener { _: AppBarLayout?, verticalOffset: Int ->
            if (collapsingToolbar.height + verticalOffset < 2 * ViewCompat.getMinimumHeight(collapsingToolbar))
                toolbar.navigationIcon!!.applySourceColorFilter(ContextCompat.getColor(this, R.color.color_black))
            else toolbar.navigationIcon!!.applySourceColorFilter(ContextCompat.getColor(this, R.color.color_white))
        } as AppBarLayout.BaseOnOffsetChangedListener<*>)

    }

    private fun setAppBarOffset(offsetPx: Int) {
        val params = mAppBarLayout.layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior as AppBarLayout.Behavior
        behavior.onNestedPreScroll(mCoordinatorLayout, mAppBarLayout, binding.nestedScrollView, 0, offsetPx, intArrayOf(0, 0), 0)
    }

    private fun setToolbarTitle(title: String) {

        collapsingToolbar.title = title
        (collapsingToolbar.parent as AppBarLayout)
                .addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _: AppBarLayout?, i: Int ->
                    isToolbarCollapsed = i != 0
                    invalidateOptionsMenu()
                })
    }

    private fun setProfileImage(image: String) {

        if (image.startsWith("/storage/emulated/") && File(Utils.returnEmptyStringIfNull(image)).exists()) {
            MediaUtils.loadImageWithGlide(this, image, binding.profileImage, null)
        } else {
            MediaUtils.loadImageWithGlideSecure(this, if (image.isBlank()) null else image, binding.profileImage, null)
        }
    }

    private fun setMuteNotificationStatus(isMute: Boolean) {
        if (!FlyCore.isUserUnArchived(userProfileDetails.jid)) {
            binding.muteSwitch.isEnabled = false
            binding.muteSwitch.alpha = 0.5F
        }
        binding.muteSwitch.isChecked = isMute
    }

    private fun setUserData() {
        binding.emailText.text = userProfileDetails.email
        binding.mobileNumberText.text = userProfileDetails.mobileNumber
        binding.statusText.text = userProfileDetails.status
        setMuteNotificationStatus(userProfileDetails.isMuted)
        setToolbarTitle(userProfileDetails.name)
        if (userProfileDetails.isBlockedMe) {
            binding.profileImage.isEnabled = false
            binding.statusText.visibility = View.GONE
            binding.statusTitle.visibility = View.GONE
            binding.statusDivider.visibility = View.GONE
            setProfileImage(emptyStringFE())
        } else {
            setProfileImage(userProfileDetails.image ?: emptyStringFE())
            binding.profileImage.setOnClickListener { redirectToImageView() }
        }
    }

    override fun userCameOnline(jid: String) {
        super.userCameOnline(jid)
        if (jid == userProfileDetails.jid) {
            getLastSeenData()
        }
    }

    override fun userWentOffline(jid: String) {
        super.userWentOffline(jid)
        if (jid == userProfileDetails.jid) {
            getLastSeenData()
        }
    }

    /**
     * Redirect to user image preview
     */
    private fun redirectToImageView() {
        if (Utils.returnEmptyStringIfNull(userProfileDetails.image).isNotEmpty()) {
            startActivity(
                    Intent(this, ImageViewActivity::class.java)
                            .putExtra(com.contusfly.utils.Constants.GROUP_OR_USER_NAME, userProfileDetails.name)
                            .putExtra(Constants.MEDIA_URL, Utils.returnEmptyStringIfNull(userProfileDetails.image)
                            )
            )
        }
    }


    private fun getLastSeenData() {
        netConditionalCall({
            if (binding.subTitle.text.isEmpty()) {
                ContactManager.getUserLastSeenTime(userProfileDetails.jid, object : ContactManager.LastSeenListener {
                    override fun onFailure(message: String) {
                        binding.subTitle.text = com.contusfly.utils.Constants.EMPTY_STRING
                    }

                    override fun onSuccess(lastSeenTime: String) {
                        binding.subTitle.text = lastSeenTime
                    }

                })
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    ContactManager.getUserLastSeenTime(userProfileDetails.jid, object : ContactManager.LastSeenListener {
                        override fun onFailure(message: String) {
                            binding.subTitle.text = com.contusfly.utils.Constants.EMPTY_STRING
                        }

                        override fun onSuccess(lastSeenTime: String) {
                            binding.subTitle.text = lastSeenTime
                        }

                    })
                }, 500)
            }
        }, {
            binding.subTitle.text = com.contusfly.utils.Constants.EMPTY_STRING
        })
    }

    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        if (jid == userProfileDetails.jid) {
            ContactManager.getUserProfile(jid, fetchFromServer = false, saveAsFriend = false) { isSuccess, _, data ->
                if (isSuccess) {
                    userProfileDetails = data.getData() as ProfileDetails
                    setUserData()
                }
            }
        }
    }

    override fun userBlockedMe(jid: String) {
        super.userBlockedMe(jid)
        if (jid == userProfileDetails.jid) {
            ContactManager.getUserProfile(jid, fetchFromServer = false, saveAsFriend = false) { isSuccess, _, data ->
                if (isSuccess) {
                    userProfileDetails = data.getData() as ProfileDetails
                    setUserData()
                    binding.subTitle.text = com.contusfly.utils.Constants.EMPTY_STRING
                }
            }
        }
    }

    override fun userUnBlockedMe(jid: String) {
        super.userUnBlockedMe(jid)
        if (jid == userProfileDetails.jid) {
            ContactManager.getUserProfile(jid, fetchFromServer = false, saveAsFriend = false) { isSuccess, _, data ->
                if (isSuccess) {
                    userProfileDetails = data.getData() as ProfileDetails
                    setUserData()
                    getLastSeenData()
                }
            }
        }

    }

    /**
     * To verify is there any media is present in conversation
     *
     */
    private fun mediaValidation() {
        binding.textMedia.visibility = View.VISIBLE
    }
}