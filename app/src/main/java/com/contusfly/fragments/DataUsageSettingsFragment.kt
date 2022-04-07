package com.contusfly.fragments

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ExpandableListView
import androidx.fragment.app.Fragment
import com.contus.flycommons.SharedPreferenceManager
import com.contusfly.R
import com.contusfly.activities.SettingsActivity
import com.contusfly.adapters.DataUsageSettingsAdapter
import com.contusfly.databinding.FragmentDataUsageSettingsBinding
import com.contusfly.services.NonStickyService
import com.contusflysdk.models.MediaDownloadSettingsModel
import com.contusflysdk.utils.SettingsUtil


/**
 * This class provides an option to the user for setting up the data usage preferences based on the
 * type of data connection network.
 *
 * @author ContusTeam <developers></developers>@contus.in>
 * @version 3.0
 */
class DataUsageSettingsFragment : Fragment(), ExpandableListView.OnChildClickListener {
    // to toggle between up and down arrows
    var clickFlag = 0

    /**
     * Returns the user preferred data usage settings for the
     * [ConnectivityManager.TYPE_MOBILE]
     * type data connection.
     *
     * @return The user preferred settings for the [ConnectivityManager.TYPE_MOBILE] type data
     * connection.
     */
    // Data usage preference for the {@link ConnectivityManager#TYPE_MOBILE}
    // type data connection.
    var mobileDataSettingsModel: MediaDownloadSettingsModel? = null
        private set

    private lateinit var fragmentDataUsageSettingsBinding: FragmentDataUsageSettingsBinding

    /**
     * Returns the user preferred data usage settings for the [ConnectivityManager.TYPE_WIFI]
     * type data connection.
     *
     * @return The user preferred settings for the [ConnectivityManager.TYPE_WIFI] type data
     * connection.
     */
    // Data usage preference for the {@link ConnectivityManager#TYPE_WIFI}
    // type data connection.
    var wifiDataSettingsModel: MediaDownloadSettingsModel? = null
        private set

    // The Activity to which this fragment is currently associated with.
    private var settingsActivity: SettingsActivity? = null

    // The settings object used to manage user preferred data usage settings.
    private var settingsUtil: SettingsUtil? = null

    // The Intent object to start the non sticky service.
    private var nonStickyServiceIntent: Intent? = null

    // The adapter object used to provide data and child views in an expandable list view.
    private var dataUsageSettingsAdapter: DataUsageSettingsAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsActivity = activity as SettingsActivity?
        settingsActivity!!.setActionBarTitle(resources.getString(R.string.data_usage_settings))
        settingsUtil = SettingsUtil()
        mobileDataSettingsModel =
            settingsUtil!!.getMediaSetting(SharedPreferenceManager.CONNECTION_TYPE_MOBILE)
        wifiDataSettingsModel =
            settingsUtil!!.getMediaSetting(SharedPreferenceManager.CONNECTION_TYPE_WIFI)

        // Request to start the non sticky service which helps to identify, when the app is erased
        // from the recent apps screen in order to save the user preferred data usage settings
        // if any, in the app preferences.
        nonStickyServiceIntent =
            Intent(settingsActivity!!.applicationContext, NonStickyService::class.java)
        settingsActivity!!.startService(nonStickyServiceIntent)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentDataUsageSettingsBinding =
            FragmentDataUsageSettingsBinding.inflate(inflater, container, false)
        return fragmentDataUsageSettingsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentDataUsageSettingsBinding.listDataUsageSettings.setOnChildClickListener(this)
        dataUsageSettingsAdapter = DataUsageSettingsAdapter(this)
        fragmentDataUsageSettingsBinding.listDataUsageSettings.setAdapter(dataUsageSettingsAdapter)
        dataUsageSettingsAdapter?.setCompoundDrawable(R.drawable.ic_down_icon)
        fragmentDataUsageSettingsBinding.listDataUsageSettings.setOnGroupExpandListener { groupPosition: Int ->
            dataUsageSettingsAdapter?.setClickedPosition(groupPosition)
            dataUsageSettingsAdapter?.setCompoundDrawable(R.drawable.ic_up_arrow)
            dataUsageSettingsAdapter?.notifyDataSetChanged()
        }
        fragmentDataUsageSettingsBinding.listDataUsageSettings.setOnGroupCollapseListener { groupPosition: Int ->
            dataUsageSettingsAdapter?.setClickedPosition(groupPosition)
            dataUsageSettingsAdapter?.setCompoundDrawable(R.drawable.ic_down_icon)
            dataUsageSettingsAdapter?.notifyDataSetChanged()
        }
    }

    override fun onPause() {
        super.onPause()
        updateDataSettings()
    }

    private fun updateDataSettings() {
        // Save the user preferred data usage settings in the app preferences for both the type
        // of data connection network.
        if (mobileDataSettingsModel != null) {
            mobileDataSettingsModel!!.dataConnectionNetworkType = ConnectivityManager.TYPE_MOBILE
            settingsUtil!!.saveMediaSettings(mobileDataSettingsModel)
        }
        if (wifiDataSettingsModel != null) {
            wifiDataSettingsModel!!.dataConnectionNetworkType = ConnectivityManager.TYPE_WIFI
            settingsUtil!!.saveMediaSettings(wifiDataSettingsModel)
        }
        // Request to stop the non-sticky service which is started when creating the instance
        // of this fragment object.
        settingsActivity!!.stopService(nonStickyServiceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateDataSettings()
    }

    override fun onChildClick(
        parent: ExpandableListView,
        v: View,
        groupPosition: Int,
        childPosition: Int,
        id: Long
    ): Boolean {
        if (groupPosition == 0) {
            processMobileDataSettings(childPosition)
        } else if (groupPosition == 1) {
            processWifiDataSettings(childPosition)
        }

        // Invokes {@link DataSetObserver#onChanged} on each observer to update the
        // content of child view.
        dataUsageSettingsAdapter?.notifyDataSetChanged()
        return true
    }

    /**
     * Processes the user preferred data usage settings for the data connection network of type
     * [ConnectivityManager.TYPE_MOBILE].
     *
     * @param childPosition The position of the child within the group view.
     */
    private fun processMobileDataSettings(childPosition: Int) {
        if (mobileDataSettingsModel == null) {
            mobileDataSettingsModel = MediaDownloadSettingsModel()
            when (childPosition) {
                0 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadPhotos = true
                }
                1 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadVideos = true
                }
                2 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadAudios = true
                }
                else -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadDocuments = true
                }
            }
        } else {
            when (childPosition) {
                0 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadPhotos =
                        !mobileDataSettingsModel!!.isShouldAutoDownloadPhotos
                }
                1 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadVideos =
                        !mobileDataSettingsModel!!.isShouldAutoDownloadVideos
                }
                2 -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadAudios =
                        !mobileDataSettingsModel!!.isShouldAutoDownloadAudios
                }
                else -> {
                    mobileDataSettingsModel!!.isShouldAutoDownloadDocuments =
                        !mobileDataSettingsModel!!.isShouldAutoDownloadDocuments
                }
            }
        }
        mobileDataSettingsModel!!.dataConnectionNetworkType = ConnectivityManager.TYPE_MOBILE
    }

    /**
     * Processes the user preferred data usage settings for the data connection network of type
     * [ConnectivityManager.TYPE_WIFI].
     *
     * @param childPosition The position of the child within the group view.
     */
    private fun processWifiDataSettings(childPosition: Int) {
        if (wifiDataSettingsModel == null) {
            wifiDataSettingsModel = MediaDownloadSettingsModel()
            when (childPosition) {
                0 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadPhotos = true
                }
                1 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadVideos = true
                }
                2 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadAudios = true
                }
                else -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadDocuments = true
                }
            }
        } else {
            when (childPosition) {
                0 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadPhotos =
                        !wifiDataSettingsModel!!.isShouldAutoDownloadPhotos
                }
                1 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadVideos =
                        !wifiDataSettingsModel!!.isShouldAutoDownloadVideos
                }
                2 -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadAudios =
                        !wifiDataSettingsModel!!.isShouldAutoDownloadAudios
                }
                else -> {
                    wifiDataSettingsModel!!.isShouldAutoDownloadDocuments =
                        !wifiDataSettingsModel!!.isShouldAutoDownloadDocuments
                }
            }
        }
        wifiDataSettingsModel!!.dataConnectionNetworkType = ConnectivityManager.TYPE_WIFI
    }

    companion object {
        /**
         * The constructor used to create and initialize a new instance of this class object, with the
         * specified initialization parameters.
         *
         * @return a new object created by calling the constructor of this object representation.
         */
        fun newInstance(): DataUsageSettingsFragment {
            return DataUsageSettingsFragment()
        }
    }
}
