package com.contusfly.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.contusfly.R
import com.contusfly.activities.SettingsActivity
import com.contusfly.databinding.FragmentAboutHelpBinding
import com.contusflysdk.AppUtils
import com.contusflysdk.views.CustomToast

/**
 * This class is used to display about and help fragment in settings
 */
class AboutHelpFragment : Fragment(),View.OnClickListener {

    /**
     * The Activity to which this fragment is currently associated with.
     */
    private var settingsActivity: SettingsActivity? = null

    private lateinit var binding: FragmentAboutHelpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsActivity = activity as SettingsActivity?
        settingsActivity!!.setActionBarTitle(resources.getString(R.string.about_help))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        binding = FragmentAboutHelpBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setClickListeners()
    }

    private fun setClickListeners() {
        binding.layoutAboutUs.setOnClickListener(this)
        binding.layoutTermsPrivacyPolicy.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.getId()) {
            R.id.layout_about_us -> {
                if (AppUtils.isNetConnected(activity))
                    settingsActivity!!.performFragmentTransaction(ContactUsFragment.newInstance())
                else
                    CustomToast.show(
                        activity,
                        requireActivity().getString(R.string.msg_no_internet)
                    )
            }
            R.id.layout_terms_privacy_policy -> settingsActivity!!.performFragmentTransaction(TermsAndConditionsFragment.newInstance())
            else -> {
                /* No Implementation Needed */
            }
        }
    }

    companion object {
        /**
         * The constructor used to create and initialize a new instance of this class object,
         * with the specified initialization parameters.
         *
         * @return a new object created by calling the constructor of this object representation.
         */
        @JvmStatic
        fun newInstance(): AboutHelpFragment {
            return AboutHelpFragment()
        }
    }
}