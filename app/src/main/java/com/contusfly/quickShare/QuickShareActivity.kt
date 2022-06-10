package com.contusfly.quickShare

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.contus.xmpp.chat.utils.LibConstants
import com.contusfly.*
import com.contusfly.activities.*
import com.contusfly.adapters.SectionedShareAdapter
import com.contusfly.chat.FileMimeType
import com.contusfly.chat.RealPathUtil
import com.contusfly.chat.ShareMessagesController
import com.contusfly.databinding.ActivityQuickShareBinding
import com.contusfly.interfaces.RecyclerViewItemClick
import com.contusfly.models.ContactShareModel
import com.contusfly.models.FileObject
import com.contusfly.models.ProfileDetailsShareModel
import com.contusfly.utils.*
import com.contusfly.utils.MediaPermissions.isPermissionAllowed
import com.contusfly.viewmodels.ForwardMessageViewModel
import com.contusfly.views.*
import com.contusfly.views.CommonAlertDialog.CommonDialogClosedListener
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.ChatManager.biometricActivty
import com.contusflysdk.api.ChatManager.pinActivity
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.contacts.ContactManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.helpers.ResourceHelper
import com.contusflysdk.model.ContactMessage
import com.contusflysdk.models.Contact
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import dagger.android.AndroidInjection
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

class QuickShareActivity : BaseActivity(), RecyclerViewItemClick, FilesDialogFragment.DialogFragmentInterface,
    CommonDialogClosedListener {

    private lateinit var quickShareBinding: ActivityQuickShareBinding

    private val CONTACT = "text/x-vcard"
    private val TEXT = "text/plain"
    private val USERS = "USERS"

    private var permissionDenied = false

    private var isFileValidationsVerified = true

    val SUPPORTED_IMAGE_VIDEO_FORMATS = arrayOf("jpg", "jpeg", "png", "webp", "gif", "mp4")
    val SUPPORTED_AUDIO_FORMATS = arrayOf("wav", "mp3", "aac")
    val supportedFormats = arrayOf("pdf", "txt", "rtf", "xls", "ppt", "pptx", "zip", "rar", "xlsx", "doc", "docx", "wav", "mp3", "mp4", "aac", "jpg", "jpeg", "png", "webp", "gif", "pptx", "csv")
    var formats = listOf(*supportedFormats)
    var videoImageFormats = listOf(*SUPPORTED_IMAGE_VIDEO_FORMATS)
    var searchKey: String? = null

    var mTempData: List<ProfileDetails>? = null

    /**
     * Contains live and contus profile list
     */
    var liveAndContusContact: List<ProfileDetails>? = null

    /**
     * Contains groups profile list
     */
    var myGroups: List<ProfileDetails>? = null

    /**
     * Contains broadcast profile list
     */
    var myBroadcasts: List<ProfileDetails>? = null

    var recentRosterList: ArrayList<ProfileDetailsShareModel>? = null
    var liveAndContusContactList: ArrayList<ProfileDetailsShareModel>? = null
    var myGroupsList: List<ProfileDetailsShareModel>? = null

    /**
     * Contains recent chat profile list
     */
    var recentRoster: List<ProfileDetails>? = null

    /**
     * List holds the media files
     */
    var fileList: ArrayList<FileObject>? = null
    var contactShareModels: ArrayList<ContactShareModel>? = null

    /**
     * Dialog to show Invalid media files
     */
    var dialogFragment: DialogFragment? = null
    var i = 0

    /**
     * View to show selected Users
     */
    private var selectedUsers: CustomTextView? = null

    /**
     * Next Textview to go to preview
     */
    private var next: CustomTextView? = null

    /**
     * List View for Contacts
     */
    private var mRecyclerViewRecent: CustomRecyclerView? = null

    /**
     * The list of rosters to populate the list
     */
    private var mainRosterList: List<ProfileDetailsShareModel>? = null

    /**
     * Adapter for Contacts List
     */
    private lateinit var mContactsAdapter: SectionedShareAdapter

    private val viewModel: ForwardMessageViewModel by viewModels()

    /**
     * Holds the type of incoming share type
     */
    @ShareType
    private var shareType: String? = null
    private var noOfFiles = 0
    private var isMediaScanSuccess = false

    /**
     * The progress dialog of the activity When run the background tasks
     */
    private var progressDialog: DoProgressDialog? = null

    private var commonAlertDialog: CommonAlertDialog? = null

    private var videoLimit: Long = 0
    private var audioLimit: Long = 0
    private var fileLimit: Long = 0
    private var imageLimit: Long = 0

    internal lateinit var intent: Intent

    /**
     * View to the files number
     */
    @Inject
    lateinit var shareMessagesController: ShareMessagesController

    private val permissionAlertDialog: PermissionAlertDialog by lazy { PermissionAlertDialog(this) }

    private val galleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: ChatUtils.checkMediaPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if(readPermissionGranted) {
            Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
            //Permissions are granted, handle the shared file now
            handleIntent()
        } else {
            Toast.makeText(this, "Storage Permission Denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        SharedPreferenceManager.setBoolean(Constants.QUICK_SHARE, true)
        checkConditionForPin()
        super.onCreate(savedInstanceState)
        if (!SharedPreferenceManager.getBoolean(Constants.IS_LOGGED_IN)) {
            startActivity(Intent(this, StartActivity::class.java))
            Toast.makeText(context, getString(R.string.need_to_login), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        quickShareBinding = ActivityQuickShareBinding.inflate(layoutInflater)
        setContentView(quickShareBinding.root)

        progressDialog = DoProgressDialog(this)
        commonAlertDialog = CommonAlertDialog(this)
        commonAlertDialog!!.setOnDialogCloseListener(this)
    }

    override fun userUpdatedHisProfile(jid: String) {
        super.userUpdatedHisProfile(jid)
        updateProfileDetails(jid)
    }

    /**
     * To handle callback of any user's profile deleted
     */
    override fun userDeletedHisProfile(jid: String) {
        super.userDeletedHisProfile(jid)
        val position = getPositionOfProfile(jid)
        if (position >= 0) {
            mContactsAdapter.removeProfileDetails(position, jid)
            selectedUsers!!.text = getSelectedUserNames()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        setLimitValues()
        mainRosterList = ArrayList()
        fileList = ArrayList()
        initViews()
        setObservers()
        viewModel.loadForwardChatList(null)
        if (checkPermission()) {
            //handle sharing only when permissions are granted
            handleIntent()
        }
    }

    private fun setObservers() {
        viewModel.profileDetailsShareModelList.observe(this, {
            it?.let {
                myGroupsList = it
                mContactsAdapter.setProfileDetails(it)
                mRecyclerViewRecent!!.adapter = mContactsAdapter
                mContactsAdapter.notifyDataSetChanged()
            }
        })
    }

    private fun initViews() {
        quickShareBinding.toolBar.setTitle(R.string.quick_share_title)
        setSupportActionBar(quickShareBinding.toolBar)
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        this.supportActionBar!!.setHomeAsUpIndicator(R.drawable.ic_close)

        /**
         * Empty view for the recent chat.
         */
        val mEmptyView = quickShareBinding.emptyData.textEmptyView
        selectedUsers = quickShareBinding.selectedUsers
        next = quickShareBinding.next
        mEmptyView.text = getString(R.string.msg_no_results)
        mEmptyView.setTextColor(ResourceHelper.getColor(R.color.color_text_grey))
        mRecyclerViewRecent = quickShareBinding.viewListRecent
        mRecyclerViewRecent!!.layoutManager = LinearLayoutManager(context)
        mRecyclerViewRecent!!.setEmptyView(mEmptyView)
        mRecyclerViewRecent!!.itemAnimator = null
        mContactsAdapter = SectionedShareAdapter(context!!, commonAlertDialog!!)
        mContactsAdapter.selectedList = ArrayList()
        mContactsAdapter.selectedProfileDetailsList = ArrayList()
        mTempData = ArrayList<ProfileDetails>()
        mContactsAdapter.setContactRecyclerViewItemOnClick(this)
    }

    /**
     * Filter the list from the search key
     *
     * @param filterKey The search key from the search bar
     */
    fun filterList(filterKey: String?) {
        searchKey = filterKey
        mContactsAdapter.filter(filterKey!!)
        mContactsAdapter.notifyDataSetChanged()
    }

    /**
     * Intent must be handled only when storage permissions are granted
     */
    private fun handleIntent() {
        /**
         * Handle incoming intent
         */
        intent = getIntent()
        /**
         * holds the type on incoming URI
         */
        val receivedType = intent.type
        if (receivedType != null && receivedType.equals(CONTACT, ignoreCase = true)) {
            shareType = ShareType.CONTACT
            val uri = intent.extras!![Intent.EXTRA_STREAM] as Uri?
            contactShareModels = generateContactShareModel(parseVcard(uri!!)!!)
        } else if (receivedType != null && receivedType.equals(TEXT, ignoreCase = true)) {
            val fileURI = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (fileURI == null) shareType = ShareType.TEXT
            else handleSingleFileShare()
        } else if (intent.action != null && intent.action.equals(Intent.ACTION_SEND, ignoreCase = true)) handleSingleFileShare()
        else if (intent.action != null && intent.action.equals(Intent.ACTION_SEND_MULTIPLE, ignoreCase = true)) handleMultipleFileShare()
        else Toast.makeText(applicationContext, "Unsupported Format", Toast.LENGTH_LONG).show()
        clickListeners()
    }

    private fun checkConditionForPin() {
        if (AppLifecycleListener.isPinEnabled || (AppLifecycleListener.fromOnCreate && AppLifecycleListener.isPinEnabled)) {
            AppLifecycleListener.isFromQuickShareForBioMetric = true
            AppLifecycleListener.isFromQuickShareForPin = true
            openPinActivity()
        }
    }

    private fun openPinActivity() {
        if (SharedPreferenceManager.getBoolean(Constants.BIOMETRIC)) {
            val pinIntent = Intent(this, biometricActivty)
            pinIntent.putExtra(Constants.GO_TO, Constants.QUICK_SHARE)
            pinIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ChatManager.applicationContext.startActivity(pinIntent)
        } else {
            val pinIntent = Intent(this, pinActivity)
            pinIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            pinIntent.putExtra(Constants.GO_TO, Constants.QUICK_SHARE)
            ChatManager.applicationContext.startActivity(pinIntent)
        }
    }

    fun handleSingleFileShare() {
        progressDialog!!.showProgress()
        i = 1
        noOfFiles = 1
        shareType = ShareType.MEDIA
        val fileObject = FileObject()
        val fileURI = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        RealPathUtil.setIntentType(getIntent().type!!)
        if (extraText != null) fileObject.caption = extraText
        else fileObject.caption = ""

        convertFileSchemeToUri(fileURI!!, fileObject, i)
    }

    fun handleMultipleFileShare() {
        progressDialog!!.showProgress()
        shareType = ShareType.MEDIA
        val urlList = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        noOfFiles = urlList!!.size
        i = 0
        if (urlList.size > 10) {
            progressDialog!!.dismiss()
            Toast.makeText(applicationContext, R.string.msg_max_file_share, Toast.LENGTH_LONG).show()
            onBackPressed()
        } else {
            for (uri in urlList) {
                i++
                Log.d("URI_LIST", uri.toString())
                val fileObject: FileObject = FileObject()
                convertFileSchemeToUri(uri, fileObject, i)
            }
        }
    }

    fun clickListeners() {
        next!!.setOnClickListener { v: View? ->
            if (FlyCore.isBusyStatusEnabled()) {
                showBusyAlert()
                return@setOnClickListener
            }
            handleNextClick()
        }
    }

    private fun handleNextClick() {
        if (mContactsAdapter.selectedList.isEmpty()) {
            Toast.makeText(this, "Select at least one User to Share ", Toast.LENGTH_SHORT).show()
        } else {
            when {
                shareType === ShareType.TEXT -> {
                    progressDialog!!.showProgress()
                    composeTextMessage(intent.getStringExtra(Intent.EXTRA_TEXT))
                }
                shareType === ShareType.CONTACT -> {
                    goToContactPreview()
                }
                else -> shareFiles()
            }
        }
    }

    private fun shareFiles() {
        val jidList = java.util.ArrayList<String>()
        val chatTypeList = java.util.ArrayList<String>()
        for (model in mContactsAdapter.selectedList) {
            jidList.add(model.profileDetails.jid)
            chatTypeList.add(model.profileDetails.getChatType())
        }
        initializeDialog()
        if (isMediaScanSuccess && isFileValidationsVerified)
            shareFiles(jidList, chatTypeList)
        else if (isFileValidationsVerified) Toast.makeText(this, "Media Scan Failed", Toast.LENGTH_SHORT).show()
    }

    private fun shareFiles(jidList: java.util.ArrayList<String>,
                           chatTypeList: java.util.ArrayList<String>) {
        val mediaFileList = ArrayList<FileObject>()
        val otherFileList = ArrayList<FileObject>()
        val uriList = java.util.ArrayList<Uri>()
        val profileDetails = getSelectedProfileDetailsList()
        for (fileObj in fileList!!) {
            if (videoImageFormats.contains(fileObj.fileExtension.toLowerCase())) {
                if (fileObj.uri != null) uriList.add(fileObj.uri!!)
                mediaFileList.add(fileObj)
            } else otherFileList.add(fileObj)
        }

        when {
            mediaFileList.isNotEmpty() -> {
                if (AppUtils.isNetConnected(this) && otherFileList.isNotEmpty())
                    sendOtherFiles(otherFileList, profileDetails, false)
                else mediaFileList.addAll(otherFileList)
                startActivity(
                    Intent(this, MediaPreviewActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .setType(intent.type)
                        .putExtra(Constants.INTENT_KEY_SHARE, "share")
                        .putStringArrayListExtra(Constants.INTENT_KEY_JID_LIST, jidList)
                        .putStringArrayListExtra(Constants.INTENT_KEY_CHAT_TYPE_LIST, chatTypeList)
                        .putParcelableArrayListExtra("FILE_OBJECTS", mediaFileList)
                        .putParcelableArrayListExtra(USERS, profileDetails)
                        .putExtra(Constants.INTENT_KEY_RECEIVED_FILES, uriList)
                )
                finish()
            }
            otherFileList.isNotEmpty() -> {
                progressDialog = DoProgressDialog(this)
                progressDialog!!.showProgress()
                sendOtherFiles(otherFileList, profileDetails,true)
            }
            else -> Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendOtherFiles(otherFileList: java.util.ArrayList<FileObject>, profileDetails: java.util.ArrayList<ProfileDetails>?, isNavigationEnable: Boolean) {
        if (AppUtils.isNetConnected(this)) {
            val usersJID = java.util.ArrayList<String>()
            for (user in profileDetails!!) usersJID.add(user.jid)
            shareMessagesController.sendMediaMessagesForSingleUser(otherFileList, usersJID)

            if (isNavigationEnable) {
                val handler = Handler()
                handler.postDelayed({
                    progressDialog!!.dismiss()
                    navigateToAppropriateScreen(profileDetails)
                    finish()
                }, 500)
            }
        } else if (isNavigationEnable) {
            progressDialog!!.dismiss()
            CustomToast.show(context, getString(R.string.msg_no_internet))
        }
    }

    private fun navigateToAppropriateScreen(profileDetails: java.util.ArrayList<ProfileDetails>?) {
        if (profileDetails!!.size == 1) {
            val userRoster: ProfileDetails = profileDetails!![0]
            startActivity(Intent(this, ChatActivity::class.java)
                .putExtra(LibConstants.JID, userRoster.jid)
                .putExtra(Constants.CHAT_TYPE, userRoster.getChatType().toString())
                .putExtra(Constants.FROM_QUICK_SHARE, true))
        } else if (profileDetails!!.size > 1) {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    /**
     * Dialog action to show whether the busy status to be enabled or disabled...
     */
    private fun showBusyAlert() {
        commonAlertDialog!!.dialogAction = CommonAlertDialog.DialogAction.STATUS_BUSY
        commonAlertDialog!!.showAlertDialog(
            getString(R.string.msg_disable_busy_status), getString(R.string.action_yes),
            getString(R.string.action_no), CommonAlertDialog.DIALOGTYPE.DIALOG_DUAL, false
        )
    }

    fun getStringSizeLengthFile(size: Long): String {
        val df = DecimalFormat("0.00")
        val sizeKb = 1024.0f
        val sizeMb = sizeKb * sizeKb
        val sizeGb = sizeMb * sizeKb
        val sizeTerra = sizeGb * sizeKb
        if (size < sizeMb) return df.format((size / sizeKb).toDouble()) + " Kb"
        else if (size < sizeGb) return df.format((size / sizeMb).toDouble()) + " Mb"
        else if (size < sizeTerra) return df.format((size / sizeGb).toDouble()) + " Gb"
        return ""
    }

    fun milliSecondsToTimer(milliseconds: Long): String {
        var finalTimerString = ""
        var minuteString = ""
        var secondsString = ""

        //Convert total duration into time
        val hours = (milliseconds / (1000 * 60 * 60)).toInt()
        val minutes = (milliseconds % (1000 * 60 * 60)).toInt() / (1000 * 60)
        val seconds = (milliseconds % (1000 * 60 * 60) % (1000 * 60) / 1000).toInt()
        // Add hours if there
        if (hours > 0) finalTimerString = hours.toString() + "h "
        if (minutes > 0) minuteString = minutes.toString() + "m "

        secondsString = seconds.toString() + "s"
        finalTimerString = finalTimerString + minuteString + secondsString

        return finalTimerString
    }

    fun setLimitValues() {
        val videoLimitString: String =
            SharedPreferenceManager.getString(Constants.VIDEO_LIMIT)
        val audioLimitString: String =
            SharedPreferenceManager.getString(Constants.AUDIO_LIMIT)
        val fileLimitString: String =
            SharedPreferenceManager.getString("fileSizeLimit")
        videoLimit = if (videoLimitString.isEmpty()) 30000000 else java.lang.Long.valueOf(videoLimitString)
        audioLimit = if (audioLimitString.isEmpty()) 30000000 else java.lang.Long.valueOf(audioLimitString) * 1000
        fileLimit = if (fileLimitString.isEmpty()) 20000000 else java.lang.Long.valueOf(fileLimitString) * 1024
        imageLimit = 10000000
    }

    private fun parseVcard(uri: Uri): ArrayList<ContactMessage>? {
        val contactMessages = ArrayList<ContactMessage>()
        val cr = contentResolver
        var stream: InputStream? = null
        try {
            stream = cr.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            LogMessage.e(TAG, e.message)
        }
        val fileContent = StringBuilder()
        var ch: Int
        try {
            while (stream!!.read().also { ch = it } != -1) fileContent.append(ch.toChar())
        } catch (e: IOException) {
            LogMessage.e(TAG, e.message)
        }
        val data = String(fileContent)
        val vcardString = data.split("BEGIN:VCARD".toRegex()).toTypedArray()
        for (vcard in vcardString) {
            val contactMessage = ContactMessage()
            contactMessage.phoneNumber = ArrayList()
            contactMessage.activeStatus = ArrayList()
            Log.d("LINE_VCARD", vcard)
            val lines = vcard.split("\\r?\\n".toRegex()).toTypedArray()
            for (l in lines) {
                if (l.contains("FN:")) contactMessage.name = l.substring(3)
                if (l.contains("TEL;")) {
                    contactMessage.phoneNumber.add(
                        l.replace(" ", "").replace("-", "").substring(l.indexOf(':') + 1)
                    )
                    contactMessage.activeStatus.add("0")
                }
            }
            if (!contactMessage.phoneNumber.isEmpty()) {
                contactMessages.add(contactMessage)
                Log.d("CONTACT_MESSAGE", Utils.getGSONInstance().toJson(contactMessage))
            }
        }
        return contactMessages
    }

    override fun onResume() {
        super.onResume()
        AppLifecycleListener.isFromQuickShareForBioMetric = true
        AppLifecycleListener.isFromQuickShareForPin = true
        if (isPermissionAllowed(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    MediaPermissions.isWriteFilePermissionAllowed(this) && permissionDenied) {
            permissionDenied = false
            handleIntent()
        }
    }

    /**
     * @return if permission not granted return FALSE, else TRUE
     */
    private fun checkPermission(): Boolean {
        if (!(isPermissionAllowed(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    MediaPermissions.isWriteFilePermissionAllowed(this))) {
            permissionDenied = true
            MediaPermissions.requestStorageAccess(this, permissionAlertDialog, galleryPermissionLauncher)
            return false
        }
        return true
    }

    private fun getSelectedUserNames(): String? {
        val stringBuilder = java.lang.StringBuilder()
        return if (mContactsAdapter.selectedList.isEmpty()) {
            "No user selected"
        } else {
            for (model in mContactsAdapter.selectedList) {
                stringBuilder.append(model.profileDetails.name)
                stringBuilder.append(", ")
            }
            val selectedNames = stringBuilder.toString()
            selectedNames.substring(0, selectedNames.length - 2)
        }
    }

    private fun getInvalidFiles(): List<FileObject>? {
        val invalidFiles = java.util.ArrayList<FileObject>()
        for (fileObject in fileList!!) {
            for ((_, value) in fileObject.fileValidation!!.entries) {
                if (!value && !invalidFiles.contains(fileObject)) invalidFiles.add(fileObject)
            }
        }
        return invalidFiles
    }

    private fun initializeDialog() {
        val invalidList = getInvalidFiles()
        isFileValidationsVerified = true
        if (invalidList!!.isNotEmpty()) {
            isFileValidationsVerified = false
            val ft = supportFragmentManager.beginTransaction()
            val prev = supportFragmentManager.findFragmentByTag("dialog")
            if (prev != null) {
                ft.remove(prev)
            }
            ft.addToBackStack(null)
            dialogFragment = FilesDialogFragment.newInstance(invalidList)
            dialogFragment!!.isCancelable = false
            dialogFragment!!.show(ft, "dialog")
            progressDialog!!.dismiss()
        }
    }

    @FileMimeType
    private fun validateMimeType(mimeType: String?): String? {
        return if (mimeType == null) {
            FileMimeType.UNSUPPORTED_FORMAT
        } else {
            when (mimeType.split("/".toRegex()).toTypedArray()[0]) {
                "video" -> FileMimeType.VIDEO
                "audio" -> FileMimeType.AUDIO
                "image" -> FileMimeType.IMAGE
                else -> FileMimeType.APPLICATION
            }
        }
    }

    private fun convertFileSchemeToUri(mainURI: Uri, fileObject: FileObject, i: Int) {
        val file = File(RealPathUtil.getRealPath(this, mainURI)!!)
        val fileAbsolutePath = file.absolutePath
        val fileExtension = fileAbsolutePath.substring(fileAbsolutePath.lastIndexOf('.') + 1)

        fileObject.filePath = fileAbsolutePath
        fileObject.fileExtension = fileExtension

        MediaScannerConnection.scanFile(this, arrayOf(fileAbsolutePath), null) { path: String, uri: Uri? ->
            // Use the FileProvider to get a content URI
            isMediaScanSuccess = uri != null
            fileObject.uri = uri!!
            getFileNameSizeType(file.absolutePath, uri, fileObject, i)
        }
    }

    private fun getFileNameSizeType(
        absolutePath: String,
        uri: Uri,
        fileObject: FileObject,
        i: Int
    ) {
        val cr = applicationContext.contentResolver
        var fileName = ""
        val mimeType = getMimeTypeFromFilePath(uri)
        var fileSize = 0L
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )
        cr.query(uri, projection, null, null, null)?.use { metaCursor ->
            if (metaCursor.moveToFirst()) {
                fileName = metaCursor.getString(0)
                fileSize = metaCursor.getLong(1)
            }
        }

        var name = ""
        if (fileName.isEmpty()) {
            val split: List<String> = fileObject.filePath.split("/")
            name = split[split.size - 1]
            fileObject.name = name
        } else fileObject.name = fileName

        if (mimeType.isEmpty()) {
            val mime = name.substring(name.lastIndexOf('.') + 1)
            fileObject.fileMimeType = validateMimeType(mime)!!
        } else fileObject.fileMimeType = validateMimeType(mimeType)!!

        fileObject.size = fileSize

        if (fileObject.fileMimeType == FileMimeType.AUDIO || fileObject.fileMimeType == FileMimeType.VIDEO)
            getMediaDurationFromFilePath(absolutePath, fileObject)

        fileObject.caption = Constants.EMPTY_STRING

        validateFileObject(fileObject, i)
    }

    private fun getMimeTypeFromFilePath(uri: Uri): String {
        return  if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            val cr = context!!.contentResolver
            cr.getType(uri) ?: ""
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase())?:""
        }
    }

    private fun validateFileObject(fileObject: FileObject, i: Int) {
        val fileValidation = HashMap<String, Boolean>()
        fileValidation[FileValidation.TYPE] = true
        fileValidation[FileValidation.SIZE] = true
        fileValidation[FileValidation.DURATION] = true

        if (!formats.contains(fileObject.fileExtension.toLowerCase()) || fileObject.fileMimeType == FileMimeType.UNSUPPORTED_FORMAT)
            fileValidation[FileValidation.TYPE] = false

        validateVideoObject(fileObject, fileValidation)
        validateAudioObject(fileObject, fileValidation)
        validateImageObject(fileObject, fileValidation)
        validateFileObject(fileObject, fileValidation)
        fileObject.fileValidation = fileValidation
        fileObject.readableSize = getStringSizeLengthFile(fileObject.size)
        fileList!!.add(fileObject)

        if (i == noOfFiles) {
            Timer().schedule(1000) {
                progressDialog!!.dismiss()
                if (!AppLifecycleListener.isFromQuickShareForBioMetric || !AppLifecycleListener.isFromQuickShareForPin)
                    initializeDialog()
            }
        }
    }

    private fun validateVideoObject(fileObject: FileObject, fileValidation: HashMap<String, Boolean>) {
        if (fileObject.fileMimeType == FileMimeType.VIDEO) {
            val size: Long = fileObject.size
            if (size > videoLimit) {
                fileValidation[FileValidation.SIZE] = false
            }
            fileObject.readableDuration = milliSecondsToTimer(fileObject.duration)
        }
    }

    private fun validateAudioObject(fileObject: FileObject, fileValidation: HashMap<String, Boolean>) {
        if (fileObject.fileMimeType == FileMimeType.AUDIO) {
            val size: Long = fileObject.size
            if (size > audioLimit) {
                fileValidation[FileValidation.SIZE] = false
            }
            fileObject.readableDuration = milliSecondsToTimer(fileObject.duration)
        }
    }


    private fun validateFileObject(fileObject: FileObject, fileValidation: HashMap<String, Boolean>) {
        if (fileObject.fileMimeType == FileMimeType.APPLICATION) {
            val size: Long = fileObject.size
            if (size > fileLimit) {
                fileValidation[FileValidation.SIZE] = false
            }
        }
    }

    private fun validateImageObject(fileObject: FileObject, fileValidation: HashMap<String, Boolean>) {
            if (fileObject.fileMimeType == FileMimeType.IMAGE) {
                val size: Long = fileObject.size
                if (size > imageLimit) {
                    fileValidation[FileValidation.SIZE] = false
                }
            }
        }

    private fun getMediaDurationFromFilePath(absolutePath: String, fileObject: FileObject) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        fileObject.duration = duration!!.toLong()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)

        val menuItem = menu!!.findItem(R.id.action_search)
        val searchView = menuItem.actionView as SearchView
        searchView.queryHint = getString(R.string.action_search)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return false
            }

            override fun onQueryTextChange(searchString: String): Boolean {
                filterList(searchString)
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            finishQuickShare()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onItemClicked(position: Int, profileDetails: ProfileDetails?) {
        selectedUsers!!.text = getSelectedUserNames()
    }

    override fun onlyForwardUserRestriction() {
        Toast.makeText(this, getText(R.string.msg_you_can_share_up_to_five_people), Toast.LENGTH_SHORT).show()
    }

    override fun removeFile(fileObject: FileObject?) {
        fileList!!.remove(fileObject!!)
        if (fileList!!.isEmpty()) finishQuickShare()
    }

    override fun exitShare() {
        finishQuickShare()
    }

    override fun onDialogClosed(dialogType: CommonAlertDialog.DIALOGTYPE?, isSuccess: Boolean) {
        if (commonAlertDialog!!.dialogAction === CommonAlertDialog.DialogAction.STATUS_BUSY && isSuccess) {
            FlyCore.enableDisableBusyStatus(false)
            handleNextClick()
        } else if (isSuccess && mContactsAdapter.blockedUser.isNotEmpty()) {
            if (AppUtils.isNetConnected(this)) {
                FlyCore.unblockUser(mContactsAdapter.blockedUser) { success, _, _ ->
                    if (isSuccess) {
                        updateProfileDetails(mContactsAdapter.blockedUser)
                        mContactsAdapter.blockedUser = emptyString()
                    } else {
                        mContactsAdapter.blockedUser = emptyString()
                        CustomToast.show(this, Constants.ERROR_SERVER)
                    }
                }
            } else {
                CustomToast.show(this, getString(R.string.msg_no_internet))
                mContactsAdapter.blockedUser = emptyString()
            }
        } else {
            mContactsAdapter.blockedUser = emptyString()
        }
    }

    /*
    * Update Profile Details */
    private fun updateProfileDetails(userJid: String) {
        val position = getPositionOfProfile(userJid)
        if (position >= 0) {
            val profileDetails = ContactManager.getProfileDetails(userJid)
            mContactsAdapter.updateProfileDetails(position, profileDetails)
        } else
            viewModel.loadForwardChatList(userJid)
    }

    private fun getPositionOfProfile(jid: String): Int {
        viewModel.profileDetailsShareModelList.value?.forEachIndexed { index, item ->
            if (item.profileDetails.jid!!.equals(jid, ignoreCase = true))
                return index
        }
        return -1
    }

    override fun listOptionSelected(position: Int) {
        //do nothing
    }

    fun composeTextMessage(shareText: String?) {
        val profileDetails = getSelectedProfileDetailsList()
        shareMessagesController.sendTextMessage(shareText!!, profileDetails!!)
        progressDialog!!.dismiss()
        navigateToAppropriateScreen(profileDetails)
        finish()
    }

    private fun goToContactPreview() {
        startActivity(
            Intent(context, PickContactActivity::class.java)
                .putExtra("QUICK_SHARE", true)
                .putExtra("LIST", true)
                .putParcelableArrayListExtra("CONTACTS", contactShareModels)
                .putParcelableArrayListExtra(USERS, getSelectedProfileDetailsList())
        )
        finish()
    }

    private fun generateContactShareModel(contactMessages: ArrayList<ContactMessage>): ArrayList<ContactShareModel>? {
        val contactShareModelArrayList = java.util.ArrayList<ContactShareModel>()
        for (contactMessage in contactMessages) {
            val contactsList = java.util.ArrayList<Contact>()
            for (no in contactMessage.phoneNumber) {
                val c = Contact()
                c.contactName = contactMessage.name
                c.isSaved = false
                c.contactNos = no
                c.selected = 1
                contactsList.add(c)
            }
            contactShareModelArrayList.add(ContactShareModel(contactMessage.name, contactsList))
        }
        return contactShareModelArrayList
    }

    private fun getSelectedProfileDetailsList(): java.util.ArrayList<ProfileDetails>? {
        val userList = java.util.ArrayList<ProfileDetails>()
        for (model in mContactsAdapter.selectedList) {
            userList.add(model.profileDetails)
        }
        return userList
    }

    private fun finishQuickShare() {
        AppLifecycleListener.isFromQuickShareForBioMetric = false
        AppLifecycleListener.isFromQuickShareForPin = false
        if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask()
        else finish()
    }

    override fun onBackPressed() {
        finishQuickShare()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLifecycleListener.isFromQuickShareForBioMetric = false
        AppLifecycleListener.isFromQuickShareForPin = false
    }

    override fun onAdminBlockedOtherUser(jid: String, type: String, status: Boolean) {
        super.onAdminBlockedOtherUser(jid, type, status)
        if (status && mContactsAdapter.selectedList.any { it.profileDetails.jid == jid }) {
            val index = mContactsAdapter.selectedList.indexOfFirst { profileDetailsShareModel -> profileDetailsShareModel.profileDetails.jid == jid }
            if (index.isValidIndex()) mContactsAdapter.selectedList.removeAt(index)
        }
        viewModel.loadForwardChatList(null)
        selectedUsers!!.text = getSelectedUserNames()
    }
}