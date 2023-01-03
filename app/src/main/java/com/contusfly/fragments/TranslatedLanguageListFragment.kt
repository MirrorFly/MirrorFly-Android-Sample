package com.contusfly.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.activities.SettingsActivity
import com.contusfly.adapters.LanguageListAdapter
import com.contusfly.databinding.FragmentTranslatedLanguageListBinding
import com.contusfly.views.DoProgressDialog
import com.contusflysdk.AppUtils
import com.location.googletranslation.GoogleTranslation
import com.location.googletranslation.pojo.Languages


/**
 * A simple [Fragment] subclass.
 * Use the [TranslatedLanguageListFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TranslatedLanguageListFragment : Fragment() {

    private lateinit var translatedLanguageListBinding: FragmentTranslatedLanguageListBinding

    /**
     * Progress dialog for the background process
     */
    private lateinit var progressDialog: DoProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressDialog = DoProgressDialog(requireContext())
        val settingsActivity = activity as SettingsActivity?
        settingsActivity?.setActionBarTitle(getString(R.string.choose_language))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        translatedLanguageListBinding = FragmentTranslatedLanguageListBinding.inflate(inflater, container, false)
        return translatedLanguageListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val errorMessageTextView = translatedLanguageListBinding.errorMsgText
        val recyclerView: RecyclerView = translatedLanguageListBinding.recyclerViewLanguageList
        progressDialog.showProgress()
        GoogleTranslation.getInstance().getLanguageList(activity, "en", BuildConfig.GOOGLE_TRANSLATE_KEY,
            object : GoogleTranslation.GoogleLanguageListListener {
                override fun onSuccess(list: List<Languages>) {
                    val languageListAdapter = LanguageListAdapter(activity!!, list)
                    recyclerView.layoutManager = LinearLayoutManager(context)
                    recyclerView.adapter = languageListAdapter
                    progressDialog.dismiss()
                }

                override fun onFailed(s: String) {
                    errorMessageTextView.visibility = View.VISIBLE
                    if (!AppUtils.isNetConnected(context)) errorMessageTextView.setText(R.string.msg_no_internet)
                    else errorMessageTextView.text = s
                    progressDialog.dismiss()
                }
            })
    }

    companion object {
        /**
         * Creating the instance of Activity
         *
         * @return Instance of TranslatedLanguageListFragment
         */
        fun newInstance(): TranslatedLanguageListFragment {
            return TranslatedLanguageListFragment()
        }
    }
}