package com.contusfly.activities

import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.contus.webrtc.api.CallManager
import com.contusfly.BuildConfig
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.databinding.ActivityQrCodeScannerBinding
import com.contusfly.utils.LogMessage
import com.contusfly.utils.UserInterfaceUtils.Companion.setUpToolBar
import com.contusflysdk.AppUtils
import com.contusflysdk.api.WebLoginDataManager
import com.contusflysdk.model.WebLogin
import com.contusflysdk.utils.UpDateWebPassword
import com.contusflysdk.views.CustomToast
import com.github.nkzawa.socketio.client.IO
import com.github.nkzawa.socketio.client.Socket
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException
import java.util.*

class QrCodeScannerActivity : BaseActivity(), BarcodeCallback {

    private lateinit var qrCodeScannerBinding: ActivityQrCodeScannerBinding

    /**
     * The reference of the UpdatedWebPassword helper object.
     */
    private var updateWebPassword: UpDateWebPassword? = null

    /**
     * Initialize the instance of the [Socket] for multiplexing.
     */
    private var mSocket: Socket? = null

    /**
     * The view reference of the BarcodeView object.
     */
    private var barcodeView: DecoratedBarcodeView? = null

    private var webLoginSuccess = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qrCodeScannerBinding = ActivityQrCodeScannerBinding.inflate(layoutInflater)
        setContentView(qrCodeScannerBinding.root)
        try {
            mSocket = IO.socket(CallManager.getSignalServerUrl())
        } catch (e: URISyntaxException) {
            LogMessage.e(TAG, e)
        }
        connectSocket()

        updateWebPassword = UpDateWebPassword()

        val toolbar: Toolbar = qrCodeScannerBinding.toolbar
        setSupportActionBar(toolbar)
        setUpToolBar(this, toolbar, supportActionBar, resources.getString(R.string.scan_code))
        barcodeView = qrCodeScannerBinding.barcodeView
        val tvWebLoginUrl: TextView = qrCodeScannerBinding.tvWebLoginUrl
        tvWebLoginUrl.text = "Visit " + BuildConfig.WEB_CHAT_LOGIN.toString() + " on your computer and scan the QR code"
        val intent = Intent()
        intent.putExtra(Intents.Scan.PROMPT_MESSAGE, "")
        barcodeView!!.initializeFromIntent(intent)
        barcodeView!!.decodeSingle(this)
    }

    /**
     * Connects the Socket.IO Client.
     */
    private fun connectSocket() {
        webLoginSuccess = true
        mSocket!!.connect().on(Socket.EVENT_CONNECT) { args: Array<Any?>? -> }.on(Socket.EVENT_CONNECT_ERROR) { args: Array<Any?>? ->
            runOnUiThread {
                if(AppUtils.isNetConnected(this)) {
                    CustomToast.show(this, getString(R.string.error_occurred_label))
                    finish()
                }
            }
        }.on("loginStatus") { args: Array<Any?>? ->
            runOnUiThread {
                LogMessage.d(TAG, "Web Connection Response" + Arrays.toString(args))
                val jsonObject = JSONObject(args?.get(0).toString())
                if (jsonObject.getInt("statusCode") == 200 && webLoginSuccess) {
                    val vibrator = getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
                    if (vibrator.hasVibrator()) {
                        vibrator.vibrate(50)
                    }
                    webLoginSuccess = false
                    finish()
                } else if (webLoginSuccess) {
                    webLoginSuccess = false
                    WebLoginDataManager.webLoginDetailsCleared()
                    CustomToast.show(this, jsonObject.getString("message"))
                    finish()
                }
            }
        }
    }

    override fun barcodeResult(result: BarcodeResult?) {
        try {
            LogMessage.d(TAG, result!!.result.text)
            val webLogin = WebLoginDataManager.getBarcodeResult(result.result.text)
            if (!WebLoginDataManager.isWebLoginDetailsAvailable(webLogin)) {
                // Insert the web login details into the respective table.
                WebLoginDataManager.insertWebLoginDetails(webLogin)
                //Emit Object to Socket
                WebLoginDataManager.webLoginProcess(mSocket, webLogin!!.qrUniqeToken)
            }
            else finish()
        } catch (e: JSONException) {
            LogMessage.e(TAG, e)
        }
    }

    override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {
        //Do nthg
    }

    override fun onResume() {
        super.onResume()
        barcodeView!!.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView!!.pauseAndWait()
    }

    /**
     * Called when the activity has detected the user's press of the back
     * key. The default implementation simply finishes the current activity,
     * but you can override this to do whatever you want.
     */
    override fun onBackPressed() {
        finish()
    }
}