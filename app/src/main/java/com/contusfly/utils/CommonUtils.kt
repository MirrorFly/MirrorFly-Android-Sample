package com.contusfly.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.ContactsContract
import android.util.DisplayMetrics
import android.view.View
import com.contus.flycommons.LogMessage
import com.contusfly.R
import com.contusfly.TAG
import com.contusfly.activities.OtpActivity
import com.contusfly.databinding.BottomSheetEditProfileImageBinding
import com.contusflysdk.api.models.ContactChatMessage
import com.contusflysdk.utils.RequestCode
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import eu.janmuller.android.simplecropimage.CropImage
import java.io.File

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class CommonUtils {

    companion object {
        /**
         * @param context context required to get system service
         * @return true , if PIP is not disabled by the user
         */
        fun isPipModeAllowed(context: Context): Boolean {
            val appOpsManager: AppOpsManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                return AppOpsManager.MODE_ALLOWED == appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), context.packageName)
            }
            return false
        }

        /**
         * opens the pip mode setting for the current app
         *
         * @param context context
         */
        fun openPipModeSettings(context: Context) {
            val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
                    Uri.parse("package:" + context.packageName))
            context.startActivity(intent)
        }

        /**
         * @param v view to get coordinates on screen
         * @return coordinates of the view on the screen
         */
        fun locateView(v: View?): Rect? {
            val coordinates = IntArray(2)
            if (v == null) return null
            try {
                v.getLocationOnScreen(coordinates)
            } catch (npe: NullPointerException) {
                //Happens when the view doesn't exist on screen anymore.
                return null
            }
            val location = Rect()
            location.left = coordinates[0]
            location.top = coordinates[1]
            return location
        }

        /**
         * This method converts dp unit to equivalent pixels, depending on device density.
         *
         * @param context Context to get resources and device specific display metrics
         * @param dp      A value in dp (density independent pixels) unit. Which we need to convert into pixels
         * @return A int value to represent px equivalent to dp depending on device density
         */
        fun convertDpToPixel(context: Context, dp: Int): Int {
            return dp * (context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT)
        }

        /**
         * Checks if is net connected.
         *
         * @param context The instance of context
         * @return boolean True if is net connected
         */
        fun isNetConnected(context: Context): Boolean {
            val conMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            return conMgr.activeNetworkInfo != null && conMgr.activeNetworkInfo!!.isConnected
        }

        /**
         * Sign out from the gPlus account.
         *
         * @param context the startupActivityContext of the calling parent.
         */
        fun navigateUserToLoggedOutUI(context: Context) {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                val mIntent = Intent(context, OtpActivity::class.java)
                mIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(mIntent)
            }, 1000)
        }

        /**
         * Open the add-contact screen with pre-filled info. The received contact will be stored in
         * local contact. The [ContactMessage] will be used to parse the contact details
         * and to store the contact
         *
         * @param context        Activity context
         * @param contactMessage Instance of ContactMessage
         */
        fun addContactInMobile(context: Activity, contactMessage: ContactChatMessage) {
            try {
                val intent = Intent(Intent.ACTION_INSERT)
                intent.type = ContactsContract.Contacts.CONTENT_TYPE
                intent.putExtra(ContactsContract.Intents.Insert.PHONE, contactMessage.getContactPhoneNumbers()[0])
                // Check if the contact details contains more than one contact to store
                // second contact
                if (contactMessage.getContactPhoneNumbers().size > 1) intent.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, contactMessage.getContactPhoneNumbers()[1])
                // Check if the contact details contains more than two contact to store
                // third contact
                if (contactMessage.getContactPhoneNumbers().size > 2) intent.putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, contactMessage.getContactPhoneNumbers()[2])
                intent.putExtra(ContactsContract.Intents.Insert.NAME, contactMessage.getContactName())
                context.startActivityForResult(intent, Constants.CONTACT_REQ_CODE)
            } catch (e: Exception) {
                LogMessage.e(TAG, e)
            }
        }

        @SuppressLint("StaticFieldLeak")
        private var bottomSheetDialog: BottomSheetDialog? = null

        /**
         * Show bottom list in the Alter dialog.
         *
         * @param context  Instance of Context
         * @param listener Instance of DialogInterface.OnClickListener
         */
        fun showBottomSheetView(context: Activity, hasRemovePhoto: Boolean, listener: DialogInterface.OnClickListener) {
            val bottomSheetEditProfileImageBinding : BottomSheetEditProfileImageBinding = BottomSheetEditProfileImageBinding.inflate(context.layoutInflater)
            bottomSheetDialog = BottomSheetDialog(context)
            bottomSheetDialog!!.setContentView(bottomSheetEditProfileImageBinding.root)
            val bottomSheetBehavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheetEditProfileImageBinding.root.parent as View)
            bottomSheetBehavior.setBottomSheetCallback(bottomSheetCallback)
            val textViewTakePhoto = bottomSheetEditProfileImageBinding.actionTake
            val textViewChooseFromGallery = bottomSheetEditProfileImageBinding.actionGallery
            val textViewRemovePhoto = bottomSheetEditProfileImageBinding.actionRemove
            val emptySpace = bottomSheetEditProfileImageBinding.space
            textViewRemovePhoto.visibility = if (hasRemovePhoto) View.VISIBLE else View.GONE
            emptySpace.visibility = if (hasRemovePhoto) View.GONE else View.VISIBLE
            textViewTakePhoto.setOnClickListener { view: View? ->
                listener.onClick(dialogInterface, R.id.action_take)
                bottomSheetDialog!!.dismiss()
            }
            textViewChooseFromGallery.setOnClickListener { view: View? ->
                listener.onClick(dialogInterface, R.id.action_gallery)
                bottomSheetDialog!!.dismiss()
            }
            textViewRemovePhoto.setOnClickListener { view: View? ->
                listener.onClick(dialogInterface, R.id.action_remove)
                bottomSheetDialog!!.dismiss()
            }
            bottomSheetDialog!!.show()
        }

        var dialogInterface: DialogInterface = object : DialogInterface {
            override fun cancel() {
                /*No Implementation Needed*/
            }

            override fun dismiss() {
                /*No Implementation Needed*/
            }
        }

        var bottomSheetCallback: BottomSheetCallback = object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) bottomSheetDialog!!.dismiss()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                /*No Implementation Needed*/
            }
        }

        /**
         * Crop the image from the Any size of image at Square.
         *
         * @param context Instance of Context
         * @param file    Instance of File
         */
        fun cropImage(context: Activity, file: File) {
            val intent = Intent(context, CropImage::class.java)
            intent.putExtra(CropImage.IMAGE_PATH, file.path)
            intent.putExtra(CropImage.SCALE, true)
            intent.putExtra(CropImage.ASPECT_X, 5)
            intent.putExtra(CropImage.ASPECT_Y, 5)
            context.startActivityForResult(intent, RequestCode.CROP_IMAGE)
        }

        /**
         * Get the jabber id of the user
         *
         * @param user    User name
         * @return String Jabber id
         */
        fun getJidFromUser(user: String?): String? {
            return user + "@" + SharedPreferenceManager.getString(Constants.XMPP_DOMAIN)
        }
    }
}