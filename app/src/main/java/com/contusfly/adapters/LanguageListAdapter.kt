package com.contusfly.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.contusfly.databinding.RowLanguageListBinding
import com.contusfly.utils.Constants
import com.contusfly.utils.SharedPreferenceManager
import com.location.googletranslation.pojo.Languages


/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class LanguageListAdapter(activity: Activity, list: List<Languages>) : RecyclerView.Adapter<LanguageListAdapter.LanguageViewHolder>() {

    private var mActivity: Activity? = null

    private var mLanguageList: List<Languages>? = null

    /**
     * Instantiates
     *
     * @param context
     */
    init {
        this.mActivity = activity
        this.mLanguageList = list
    }

    class LanguageViewHolder(var viewBinding: RowLanguageListBinding) : RecyclerView.ViewHolder(viewBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val adapterBinding = RowLanguageListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(adapterBinding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val item = mLanguageList!![position]
        holder.viewBinding.languageText.text = item.name
        holder.viewBinding.languageSelected.visibility = if (SharedPreferenceManager.getString(
                Constants.GOOGLE_TRANSLATION_LANGUAGE_CODE) == item.language) View.VISIBLE else View.INVISIBLE
        holder.viewBinding.languageText.setOnClickListener {
            SharedPreferenceManager.setString(Constants.GOOGLE_LANGUAGE_NAME, item.name)
            SharedPreferenceManager.setString(Constants.GOOGLE_TRANSLATION_LANGUAGE_CODE, item.language)
            mActivity!!.onBackPressed()
        }
    }

    override fun getItemCount(): Int {
        return mLanguageList!!.size
    }
}