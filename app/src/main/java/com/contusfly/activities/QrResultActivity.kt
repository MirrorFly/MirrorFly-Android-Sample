package com.contusfly.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.adapters.WebLoginAdapter
import com.contusfly.databinding.ActivityQrResultBinding
import com.contusfly.views.CommonAlertDialog
import com.contusfly.views.CommonAlertDialog.CommonDialogClosedListener
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.WebLoginDataManager
import com.contusflysdk.model.WebLogin
import com.contusflysdk.utils.UpDateWebPassword
import com.contusflysdk.views.CustomToast
import java.util.*

class QrResultActivity : BaseActivity(), View.OnClickListener, CommonDialogClosedListener {

    private lateinit var qrResultBinding: ActivityQrResultBinding

    /**
     * Instance of recycler view
     */
    var mrecyclerView: RecyclerView? = null

    /**
     * create instance for webLogin list
     */
    private var listWebLogin: List<WebLogin>? = null

    /**
     * instance for adapter class
     */
    var webLoginAdapter: WebLoginAdapter? = null

    /**
     * The dialog for the common alert dialog.
     */
    private var mDialog: CommonAlertDialog? = null

    /**
     * Creating instance of upDatePassword Class
     */
    private var mUpDateWebPassword: UpDateWebPassword? = null

    /**
     * Instance of Button
     */
    var btnLogOutAll: Button? = null

    /**
     * Instance of Image View add button
     */
    var imageViewAddButton: ImageView? = null

    /**
     * Instance of Image View back button
     */
    var imageViewBackButton: ImageView? = null

    /**
     * web login url
     */
    var txtWebUrl: TextView? = null

    /**
     * Recives broadcast when the user logout from WEB CHAT.
     */
    private val webLogReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadAdapter()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qrResultBinding = ActivityQrResultBinding.inflate(layoutInflater)
        setContentView(qrResultBinding.root)
        mUpDateWebPassword = UpDateWebPassword()
        imageViewAddButton = qrResultBinding.scannerToolBar.buttonAdd
        imageViewBackButton = qrResultBinding.scannerToolBar.buttonBack
        btnLogOutAll = qrResultBinding.btnLogoutAllWebchat
        txtWebUrl = qrResultBinding.txtWebUrl
        txtWebUrl!!.text = java.lang.String.format("%s %s", getString(R.string.text_visit_chat_web), BuildConfig.WEB_CHAT_LOGIN)
        mDialog = CommonAlertDialog(this)
        mDialog!!.setOnDialogCloseListener(this)
        imageViewAddButton!!.setOnClickListener(this)
        imageViewBackButton!!.setOnClickListener(this)
        btnLogOutAll!!.setOnClickListener(this)

        LocalBroadcastManager.getInstance(context!!).registerReceiver(webLogReceiver, IntentFilter("Update_webPage"))
        loadAdapter()
    }

    private fun loadAdapter() {
        //Getting the list from the data base table
        listWebLogin = WebLoginDataManager.getWebLoginDetails()
        if (listWebLogin!!.isNotEmpty()) {
            webLoginAdapter = WebLoginAdapter(this)
            webLoginAdapter!!.setAdapterData(listWebLogin)
            mrecyclerView = qrResultBinding.recyclerViewWebChatLogin
            mrecyclerView!!.setHasFixedSize(true)
            mrecyclerView!!.layoutManager = LinearLayoutManager(this)
            mrecyclerView!!.adapter = webLoginAdapter
        } else finish()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_add -> {
                WebLoginDataManager.webLoginDetailsCleared()
                startActivity(Intent(applicationContext, QrCodeScannerActivity::class.java))
                finish()
            }
            R.id.btn_logout_all_webchat -> logoutAllWeb()
            R.id.button_back -> finish()
            else -> {
                /* No Implementation Needed */
            }
        }
    }

    /**
     * when the back button pressed this activity closed
     */
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    /**
     * Alert dialogue to get the conformation to delete the database
     */
    private fun logoutAllWeb() {
        mDialog!!.showAlertDialog(getString(R.string.alert_dialouge_logout), getString(R.string.action_yes),
            getString(R.string.action_no), CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false)
    }

    /**
     * override the parent method for update web password
     *
     * @param isError Check if any error in response
     */
    override fun onWebChatPasswordChanged(isError: Boolean) {
        super.onWebChatPasswordChanged(isError)
        if (isError) CustomToast.show(this, getString(R.string.error_occurred_label))
        finish()
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (isSuccess) {
            WebLoginDataManager.webLoginDetailsCleared()
            mUpDateWebPassword!!.upDatePassword()
            logoutWebUser()
        }
    }

    /**
     * Logged out the user signed in web
     */
    private fun logoutWebUser() {
        if (AppUtils.isNetConnected(this)) {
            if (listWebLogin!!.isNotEmpty()) {
                for (i in listWebLogin!!.indices) {
                    ChatManager.logoutWebUser(listWebLogin!![i].qrUniqeToken)
                }
            }
        } else CustomToast.show(this, getString(R.string.msg_no_internet))
    }

    override fun listOptionSelected(position: Int) {
        //Do nthg
    }
}