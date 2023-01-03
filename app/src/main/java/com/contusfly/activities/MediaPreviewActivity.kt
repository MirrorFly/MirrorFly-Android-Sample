package com.contusfly.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.widget.ContentFrameLayout
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.ViewPager
import com.contus.flycommons.LogMessage
import com.contus.flycommons.TAG
import com.contusfly.*
import com.contusfly.activities.parent.ChatParent
import com.contusfly.adapters.MediaPreviewAdapter
import com.contusfly.adapters.MediaPreviewPagerAdapter
import com.contusfly.chat.*
import com.contusfly.databinding.ActivityMediaPreviewBinding
import com.contusfly.di.factory.AppViewModelFactory
import com.contusfly.interfaces.MessageListener
import com.contusfly.mediapicker.model.Image
import com.contusfly.models.FileObject
import com.contusfly.models.MediaPreviewModel
import com.contusfly.utils.*
import com.contusfly.viewmodels.MediaPreviewViewModel
import com.contusfly.views.DoProgressDialog
import com.contusfly.views.KeyboardHeightProvider
import com.contusflysdk.AppUtils
import com.contusflysdk.api.ChatManager
import com.contusflysdk.api.contacts.ProfileDetails
import com.contusflysdk.api.models.ChatMessage
import com.contusflysdk.utils.FilePathUtils
import com.contusflysdk.utils.Utils
import com.contusflysdk.views.CustomToast
import com.fxn.pix.Options
import com.fxn.pix.Pix
import dagger.android.AndroidInjection
import io.github.rockerhieu.emojicon.EmojiconGridFragment
import io.github.rockerhieu.emojicon.EmojiconsFragment
import io.github.rockerhieu.emojicon.emoji.Emojicon
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList


class MediaPreviewActivity : BaseActivity(), MediaPreviewAdapter.OnItemClickListener,
    ViewPager.OnPageChangeListener, View.OnClickListener,
    EmojiconsFragment.OnEmojiconBackspaceClickedListener,
    EmojiconGridFragment.OnEmojiconClickedListener {

    private lateinit var mediaPreviewBinding: ActivityMediaPreviewBinding

    @Inject
    lateinit var mediaPreviewViewModelFactory: AppViewModelFactory
    val viewModel by lazy {
        ViewModelProviders.of(this, mediaPreviewViewModelFactory)
            .get(MediaPreviewViewModel::class.java)
    }

    @Inject
    lateinit var messagingClient: MessagingClient

    private var keyboardHeightProvider: KeyboardHeightProvider? = null

    /**
     * Check is from camera or not
     */
    private var isFromCamera = false

    private val parentContent by bindView<ContentFrameLayout>(android.R.id.content)

    private var isResumeFirstTime = true

    /**
     * User jid to send images
     */
    private var toUser: String? = null

    /**
     * Store onclick time to avoid double click
     */
    private var lastClickTime: Long = 0

    /**
     * Share the Key Value
     */
    private var intentKeyShare: String? = null

    /**
     * Instance of the EmojiHandler to access the emoji and text keypad
     */
    private var emojiHandler: EmojiHandler? = null

    private var alreadyOpen = false

    private var showChatKeyboard: Boolean = false

    /**
     * Check is from Chat Screen or not
     */
    private var isFromChatScreen = false

    /**
     * Check is from Quick Share or not
     */
    private var isFromQuickShare = false

    /**
     * Position of the selected message
     */
    private var imagePosition = 0

    /**
     * State of view pager during transition
     */
    private var viewPagerState = 0

    /**
     * Position of view pager
     */
    private var viewPagerPosition = 0

    /**
     * List of selected images
     */
    private var selectedImageList: MutableList<MediaPreviewModel> = mutableListOf()

    private var showingEmojiKeyboard = false

    /**
     * List of selected files from Quick Share
     */
    private var fileObjects: MutableList<FileObject> = mutableListOf()

    /**
     * Adapter for the media list
     */
    lateinit var mediaViewPagerAdapter: MediaPreviewPagerAdapter
    var isSoftKeyboardShown: Boolean = false
    var isResumedNotCalled: Boolean = false

    private lateinit var mediaPreviewAdapter: MediaPreviewAdapter

    /**
     * Selected images from gallery
     */
    private var selectedImages: ArrayList<Image> = arrayListOf()

    /**
     * The progress dialog of the activity When run the background tasks
     */
    private var progressDialog: DoProgressDialog? = null

    /**
     * List of selected files from Quick Share
     */
    private var selectedUsers: List<ProfileDetails>? = null

    /**
     * The Boolean List is contains All image list
     */
    private var isImageList: List<Boolean>? = null

    /**
     * List Contian jid of multiple users
     */
    private var jidList: java.util.ArrayList<String>? = null

    /**
     * List Contian  user chat typelist
     */
    private var chatTypeList: java.util.ArrayList<String>? = null

    /**
     * The Array List Contains Uri List
     */
    private var uriList: java.util.ArrayList<Uri>? = null

    private var remainingMessagesCount = 0

    /**
     * View to the files number
     */
    @Inject
    lateinit var shareMessagesController: ShareMessagesController

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        mediaPreviewBinding = ActivityMediaPreviewBinding.inflate(layoutInflater)
        setContentView(mediaPreviewBinding.root)
        AppLifecycleListener.isFromQuickShareForBioMetric = true
        AppLifecycleListener.isFromQuickShareForPin = true
        keyboardHeightProvider = KeyboardHeightProvider(this)
        keyboardHeightProvider?.addKeyboardListener(getKeyboardListener())
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        AppLifecycleListener.isFromQuickShareForBioMetric = true
        AppLifecycleListener.isFromQuickShareForPin = true

        isFromCamera = intent.getBooleanExtra(Constants.FROM_CAMERA, false)
        intentKeyShare = intent.getStringExtra(Constants.INTENT_KEY_SHARE)
        toUser = intent.getStringExtra(Constants.USER_JID)

        initViews()
        setObservers()
    }

    private fun initViews() {
        toUser?.let {
            viewModel.getProfileDetails(it)
            viewModel.getUnsentMessage(it)
        }

        if (intent.getBooleanExtra(Constants.IS_IMAGE, false)) {
            handlePreviewFromGallery()
        } else if (intentKeyShare != null && intentKeyShare == Constants.SHARE) {
            handleQuickShareInitialOperations()
        } else {
           handlePreviewFromCamera()
        }

        initializeAdapterForViewPager()
        initializeCaptionListener()

        mediaPreviewAdapter = MediaPreviewAdapter(this, selectedImageList, imagePosition, this)
        mediaPreviewBinding.imagesPreviewList.apply {
            adapter = mediaPreviewAdapter
        }

        mediaPreviewBinding.backArrow.setOnClickListener(this)
        mediaPreviewBinding.emoji.setOnClickListener(this)
        mediaPreviewBinding.addMoreMedia.setOnClickListener(this)
        mediaPreviewBinding.deleteMedia.setOnClickListener(this)
        mediaPreviewBinding.sendMedia.setOnClickListener(this)
        mediaPreviewBinding.viewOverlay.setOnClickListener(this)

        emojiHandler = EmojiHandler(this)
        emojiHandler!!.attachKeyboardListeners(mediaPreviewBinding.imageCaption)
        emojiHandler!!.setIconImageView(mediaPreviewBinding.emoji)
        emojiHandler!!.setIsBlackTheme(true)
        emojiHandler!!.setHandledFrom(TAG)

        setAddMoreVisibility()
        setKeyboardHeightListener()
    }

    private fun setKeyboardHeightListener() {
        parentContent.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            parentContent.getWindowVisibleDisplayFrame(r)
            val screenHeight = parentContent.rootView.height
            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            val keypadHeight = screenHeight - r.bottom

            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                if (!isSoftKeyboardShown) {
                    isSoftKeyboardShown = true
                    mediaPreviewBinding.imagesPreviewList.gone()
                    Log.d(TAG, " keyboard is opened")
                    if (emojiHandler!!.isEmojiShowing)
                        emojiHandler!!.hideEmoji()
                }
            } else {
                if (isSoftKeyboardShown) {
                    checkAndShowPreviewList()
                    isSoftKeyboardShown = false
                    Log.d(TAG, " keyboard is closed")
                }
            }
        }
    }

    private fun setCaptionLength(length: Int) {
        val remainingCount = Constants.MAX_CAPTION_LENGTH - length
        if (remainingCount < 30) {
            mediaPreviewBinding.captionCount.show()
            mediaPreviewBinding.captionCount.text = remainingCount.toString()
        } else mediaPreviewBinding.captionCount.gone()
    }

    private fun initializeCaptionListener() {
        mediaPreviewBinding.imageCaption.setHorizontallyScrolling(false)
        mediaPreviewBinding.imageCaption.maxLines = 6
        mediaPreviewBinding.imageCaption.filters =
            arrayOf(InputFilter.LengthFilter(Constants.MAX_CAPTION_LENGTH))

        mediaPreviewBinding.imageCaption.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                /*No Implementation Needed*/
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let {
                    setCaptionLength(it.length)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                /*No Implementation Needed*/
            }
        })

        mediaPreviewBinding.imageCaption.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                mediaPreviewBinding.groupAddMore.gone()
                mediaPreviewBinding.emoji.show()
                mediaPreviewBinding.viewOverlay.show()
                mediaPreviewBinding.groupToUser.gone()
            } else {
                showChatKeyboard = false
                setAddMoreVisibility()
                mediaPreviewBinding.viewOverlay.gone()
                mediaPreviewBinding.emoji.gone()
                mediaPreviewBinding.captionCount.gone()
                mediaPreviewBinding.groupToUser.show()
            }
        }

        mediaPreviewBinding.imageCaption.setOnLongClickListener {
            if (emojiHandler!!.isEmojiShowing) {
                emojiHandler!!.hideEmoji()
                mediaPreviewBinding.imageCaption.requestFocus()
                mediaPreviewBinding.imageCaption.showSoftKeyboard()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                checkAndShowPreviewList()
                showChatKeyboard = false
            }
            false
        }
    }

    private fun initializeAdapterForViewPager() {
        val height = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
        // Real size of the screen (full screen)
        val realSize = Point()
        val realDisplay = windowManager.defaultDisplay
        realDisplay.getRealSize(realSize)
        val finalHeight =
            if (realSize.y - AndroidUtils.getNavBarHeight(this) == height || realSize.y == height) {
                height - AndroidUtils.getStatusBarHeight(this) - AndroidUtils.getNavBarHeight(this)   // Remove Status bar height and Navbar height
            } else height
        mediaPreviewBinding.bottomLayout.post {
            val toolbarHeight = mediaPreviewBinding.toolbarLayout.height
            val bottomLayoutHeight = mediaPreviewBinding.bottomLayout.height
            val linearLayoutParams =
                mediaPreviewBinding.mediaList.layoutParams as LinearLayout.LayoutParams
            linearLayoutParams.height = finalHeight - bottomLayoutHeight - toolbarHeight
        }

        setAdapterForViewPager()
        mediaPreviewBinding.mediaList.addOnPageChangeListener(this)
        mediaPreviewBinding.mediaList.offscreenPageLimit = 2
        mediaPreviewBinding.mediaList.adapter = mediaViewPagerAdapter
        mediaPreviewBinding.mediaList.currentItem = imagePosition
    }

    private fun checkAndShowPreviewList() {
        if (selectedImageList.size <= 1 || showingEmojiKeyboard) {
            mediaPreviewBinding.imagesPreviewList.gone()
            mediaPreviewBinding.deleteMedia.gone()
        } else {
            mediaPreviewBinding.imagesPreviewList.show()
            mediaPreviewBinding.deleteMedia.show()
        }
    }

    private fun onKeyboardVisibilityChanged(shown: Boolean) {
        if (!shown && mediaPreviewBinding.imageCaption.hasFocus() && !showingEmojiKeyboard && !isResumedNotCalled) {
            mediaPreviewBinding.imageCaption.clearFocus()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            isResumedNotCalled = false
        }, 100)
    }

    private fun setUserName(name: String) {
        mediaPreviewBinding.toUserTextView.text = name
    }

    private fun prepareMultipleUserNames() {
        if (jidList?.size!! > 1) {
            val participantsNameList: MutableList<String> = ArrayList()
            selectedUsers!!.forEach {
                if(!it.jid.equals(SharedPreferenceManager.getCurrentUserJid())) {
                    participantsNameList.add(it.name)
                }
            }
            setUserName(participantsNameList.sorted().joinToString(", "))
        }
    }

    private fun setObservers() {
        viewModel.profileDetails.observe(this, {
            it?.let {
                if (isFromQuickShare && jidList?.size!! > 1) {
                    mediaPreviewBinding.imageChatPicture.visibility = View.INVISIBLE
                    return@let
                }
                mediaPreviewBinding.imageChatPicture.loadUserProfileImage(this, it)
                setUserName(it.name)
            }
        })
        viewModel.unsentMessage.observe(this, {
            it?.let {
                if (selectedImageList.isNotEmpty())
                    selectedImageList[0].caption = it
                mediaPreviewBinding.imageCaption.setText(it)
                setCaptionLength(it.length)
            }
        })

        viewModel.selectedMediaList.observe(this, {
            mediaPreviewBinding.previewProgress.previewProgress.gone()
            if (it.isEmpty())
                finish()
            selectedImageList.addAll(it)
            checkAndShowPreviewList()
            initializeAdapterForViewPager()
            viewModel.unsentMessage.value?.let { message ->
                if (selectedImageList.isNotEmpty())
                    selectedImageList[0].caption = message
                mediaPreviewBinding.imageCaption.setText(message)
                setCaptionLength(message.length)
            }
            setAddMoreVisibility()
        })
    }

    private fun handlePreviewFromCamera() {
        if (intent.getBooleanExtra(Constants.FROM_FRONT_CAMERA, false)) {
            val image = Image(
                0, intent
                    .getStringExtra(Constants.FILE_PATH)!!,
                0, false
            )
            selectedImages.add(image)
            val destinationFile = File.createTempFile("temp", null, context?.externalCacheDir)
            viewModel.checkVideoSize(image, destinationFile)
        } else {
            val mediaPreviewModel = MediaPreviewModel(intent.getStringExtra(Constants.FILE_PATH)!!,
                mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }, Constants.EMPTY_STRING, false)
            SharedPreferenceManager.setBoolean(Constants.AUDIO_RECORD_PERMISSION_ASKED, true)
            selectedImageList.add(mediaPreviewModel)
            checkAndShowPreviewList()
            imagePosition = 0
            viewPagerPosition = 0
            mediaPreviewBinding.previewProgress.previewProgress.gone()
        }
    }

    private fun handleQuickShareInitialOperations() {
        isFromQuickShare = true
        progressDialog = DoProgressDialog(this)
        fileObjects = intent.getParcelableArrayListExtra("FILE_OBJECTS")!!
        selectedUsers = intent.getParcelableArrayListExtra("USERS")
        jidList = intent.getStringArrayListExtra(Constants.INTENT_KEY_JID_LIST)
        chatTypeList = intent.getStringArrayListExtra(Constants.INTENT_KEY_CHAT_TYPE_LIST)
        uriList = intent.getParcelableArrayListExtra(Constants.INTENT_KEY_RECEIVED_FILES)
        isImageList = java.util.ArrayList<Boolean>()
        for (uri in uriList!!) createAdapterObject(uri)
        imagePosition = 0
        viewPagerPosition = 0
        remainingMessagesCount = fileObjects.size
        if (fileObjects[0].fileMimeType == FileMimeType.APPLICATION || fileObjects[0].fileMimeType == FileMimeType.AUDIO)
            mediaPreviewBinding.bottomLayout.visibility = View.GONE
        mediaPreviewBinding.imageCaption.setText(fileObjects[0].caption)
        mediaPreviewBinding.previewProgress.previewProgress.gone()

        jidList?.get(0)?.let { viewModel.getProfileDetails(it) }
        checkAndShowPreviewList()
        prepareMultipleUserNames()
    }

    /*
     * The Multiple Image/video  selection path
     *
     * @param uri object type
     */
    private fun createAdapterObject(uri: Uri) {
        var mimeType = contentResolver.getType(uri)
        val multipleImages: MediaPreviewModel
        mimeType = mimeType ?: intent.type
        var filePathFromUri: String? = ""
        filePathFromUri = if (uri.scheme == null) uri.path else RealPathUtil.getRealPath(this@MediaPreviewActivity, uri)
        LogMessage.d(TAG, "file path createAdapterObject = $filePathFromUri")
        LogMessage.d(TAG, "log mime type in createAdapterObject = $mimeType")
        if (mimeType!!.startsWith("image/")) {
            multipleImages = MediaPreviewModel(filePathFromUri!!,
                mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }, Constants.EMPTY_STRING, true)
            selectedImageList.add(multipleImages)
        } else if (mimeType.startsWith("video/")) {
            multipleImages = MediaPreviewModel(filePathFromUri!!,
                mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }, Constants.EMPTY_STRING, false)
            selectedImageList.add(multipleImages)
        }
    }

    private fun handlePreviewFromGallery() {
        if (isFromCamera) {
            val mediaPath = intent.getStringExtra(Constants.FILE_PATH)
            val uri =
                FileProvider.getUriForFile(this, ChatManager.fileProviderAuthority, File(mediaPath))
            revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedImages = ArrayList()
            selectedImages.add(Image(0, mediaPath!!, 0, false))
        } else {
            selectedImages =
                intent.getSerializableExtra(Constants.SELECTED_IMAGES) as ArrayList<Image>
        }
        mediaPreviewBinding.previewProgress.previewProgress.gone()
        selectedImages.forEach {
            Log.d("selectedImages", "selectedImages after path:${it.path}")
            selectedImageList.add(MediaPreviewModel(it.path, Constants.EMPTY_STRING, Constants.EMPTY_STRING, !it.isVideo))
        }
        checkAndShowPreviewList()
        initializeAdapterForViewPager()
        setAddMoreVisibility()
    }

    fun setAdapterForViewPager() {
        if (intentKeyShare != null && intentKeyShare == Constants.SHARE) {
            mediaViewPagerAdapter = MediaPreviewPagerAdapter(supportFragmentManager)
            mediaViewPagerAdapter.setFileObjectList(fileObjects)
        } else {
            /*
             * File path values - comes only recorded video
             * */
            mediaViewPagerAdapter = MediaPreviewPagerAdapter(supportFragmentManager)
            mediaViewPagerAdapter.setImageList(selectedImageList)
        }
    }

    override fun onBackPressed() {
        if (SystemClock.elapsedRealtime() - lastClickTime < 1000) return
        lastClickTime = SystemClock.elapsedRealtime()
        mediaPreviewBinding.imageCaption.clearFocus()
        when {
            emojiHandler!!.isEmojiShowing -> {
                emojiHandler!!.hideEmoji()
                mediaPreviewBinding.imageCaption.clearFocus()
            }
            isFromCamera -> backToCamera()
            isFromQuickShare -> finishQuickShare()
            else -> super.onBackPressed()
        }
    }

    private fun finishQuickShare() {
        AppLifecycleListener.isFromQuickShareForBioMetric = false
        AppLifecycleListener.isFromQuickShareForPin = false
        if (Build.VERSION.SDK_INT >= 21) finishAndRemoveTask()
        else finish()
    }

    private fun backToCamera() {
        val options: Options? = Options.init()
            .setRequestCode(100)
            .setCount(Constants.MAX_MEDIA_SELECTION_RESTRICTION)
            .setOutputPath(Constants.LOCAL_PATH.toUpperCase(Locale.getDefault()))
            .setFrontfacing(false)
            .setPreSelectedUrls(ArrayList())
            .setExcludeVideos(false)
            .setVideoDurationLimitinSeconds(Constants.VIDEO_DURATION_LIMIT)//Prefs.getString(SharedPreferenceManager.VIDEO_LIMIT).toInt())
            .setScreenOrientation(Options.SCREEN_ORIENTATION_PORTRAIT)
            .setPath(Constants.LOCAL_PATH)
            .setAudioPermissionAsked(SharedPreferenceManager.getBoolean(Constants.AUDIO_RECORD_PERMISSION_ASKED))
            .setMediaClass(MediaPreviewActivity::class.java)


        Pix.start(this, options)
        finish()
    }

    override fun onItemClick(view: View?, position: Int) {
        if (isFromQuickShare) fileObjects[viewPagerPosition].caption = mediaPreviewBinding.imageCaption!!.text.toString().trim { it <= ' ' }
        else selectedImageList[viewPagerPosition].caption = mediaPreviewBinding.imageCaption!!.text.toString().trim { it <= ' ' }
        mediaPreviewBinding.mediaList.currentItem = position
        mediaViewPagerAdapter.notifyDataSetChanged()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        /* No Implementation Needed */
    }

    override fun onPageSelected(position: Int) {
        hideKeyboard()
        if (emojiHandler!!.isEmojiShowing) emojiHandler!!.hideEmoji()
        mediaPreviewBinding.imageCaption.clearFocus()
        checkAndShowPreviewList()

        /*
          Check if the current position images contains caption to show
         */
        if (isFromQuickShare) {
            if (fileObjects[position].fileMimeType == FileMimeType.VIDEO || fileObjects[position].fileMimeType == FileMimeType.IMAGE) {
                mediaPreviewBinding.imageCaption.setText(if (fileObjects[position].caption.isEmpty()) "" else fileObjects[position].caption)
                setCaptionLength(mediaPreviewBinding.imageCaption.text.toString().length)
                mediaPreviewBinding.imageCaption.show()
                mediaPreviewBinding.emoji.show()
            } else {
                mediaPreviewBinding.imageCaption.gone()
                mediaPreviewBinding.emoji.gone()
                mediaPreviewBinding.captionCount.gone()
            }
        } else {
            mediaPreviewBinding.imageCaption.setText(
                if (Utils.returnEmptyStringIfNull(selectedImageList[position].caption).isNotEmpty())
                    selectedImageList[position].caption else ""
            )
            setCaptionLength(mediaPreviewBinding.imageCaption.text.toString().length)
        }
        mediaPreviewBinding.imageCaption.setSelection(mediaPreviewBinding.imageCaption.text!!.length)
        /*
         * Update the view pager adapter and horizontal view adapter
         */
        viewPagerPosition = position
        mediaPreviewAdapter.setPosition(position)
        mediaPreviewAdapter.notifyDataSetChanged()
        mediaPreviewBinding.imagesPreviewList.scrollToPosition(position)
    }

    override fun onPageScrollStateChanged(state: Int) {
        /*
          validate and store the caption while position view pager changes
         */
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            if (isFromQuickShare) {
                fileObjects[mediaPreviewBinding.mediaList.currentItem].caption =
                    mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }
            } else {
                selectedImageList[mediaPreviewBinding.mediaList.currentItem].caption =
                    mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }
            }
        }
        viewPagerState = state
        if (isFromQuickShare) mediaViewPagerAdapter.setFileObjectList(fileObjects)
        else mediaViewPagerAdapter.setImageList(selectedImageList)
        mediaViewPagerAdapter.notifyDataSetChanged()
    }

    fun viewPagerOnClick(v: View?) {
        hideKeyboard()
        if (emojiHandler!!.isEmojiShowing) {
            emojiHandler!!.hideEmoji()
            mediaPreviewBinding.imageCaption.clearFocus()
            showingEmojiKeyboard = false
        }
    }

    /**
     * Hide the soft input keyboard from the startupActivityContext of the window
     * that is currently accepting input.
     */
    private fun hideKeyboard() {
        val view = currentFocus
        Utils.hideSoftInput(this, view)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.back_arrow -> {
                onBackPressed()
            }
            R.id.emoji -> {
                if (!emojiHandler!!.isEmojiShowing) {
                    showingEmojiKeyboard = true
                    hideKeyboard()
                } else {
                    showingEmojiKeyboard = false
                }
                emojiHandler!!.setKeypad(mediaPreviewBinding.imageCaption)
                setEmojiKeyBoardListener()
                mediaPreviewBinding.imagesPreviewList.gone()
            }
            R.id.add_more_media -> {
                val resultIntent = Intent()
                resultIntent.putExtra("remaining_list", selectedImages)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            R.id.delete_media -> {
                if (isFromQuickShare) removeSelectedFile() else removeSelectedFileFromPicker()
            }
            R.id.send_media -> {
                sendMedia()
            }
            R.id.view_overlay -> {
                hideKeyboard()
                if (emojiHandler!!.isEmojiShowing) {
                    emojiHandler!!.hideEmoji()
                    mediaPreviewBinding.imageCaption.clearFocus()
                    showingEmojiKeyboard = false
                }
            }
        }
    }

    private fun setEmojiKeyBoardListener() {
        emojiHandler!!.setEmojiKeyBoardListener(object : EmojiHandler.EmojiKeyBoardListener {
            override fun onKeyBoardStateChanged(isOpened: Boolean) {
                showingEmojiKeyboard = isOpened
                checkAndShowPreviewList()
            }
        })
    }

    private fun sendMedia() {
        hideKeyboard()
        mediaPreviewBinding.sendMedia.isEnabled = false
        if (isFromQuickShare) {
            fileObjects[viewPagerPosition].caption = mediaPreviewBinding.imageCaption!!.text.toString().trim { it <= ' ' }
            progressDialog = DoProgressDialog(this)
            progressDialog!!.showProgress()
            startCopyingFilesToMirrorFlyDirectoryAndSend()
        } else {
            selectedImageList[viewPagerPosition].caption = mediaPreviewBinding.imageCaption!!.text.toString().trim { it <= ' ' }
            if (toUser != null) {
                handleCaptionImage(toUser!!)
            }
        }
    }

    private fun startCopyingFilesToMirrorFlyDirectoryAndSend() {
        if (AppUtils.isNetConnected(this)) {
            sendMediaFilesForSingleUser()
        } else {
            progressDialog!!.dismiss()
            CustomToast.show(context, getString(R.string.msg_no_internet))
            mediaPreviewBinding.sendMedia!!.isEnabled = true
        }
    }

    private fun sendMediaFilesForSingleUser() {
        if (AppUtils.isNetConnected(this)) {
            val usersJID = java.util.ArrayList<String>()
            for (user in selectedUsers!!) usersJID.add(user.jid)
            shareMessagesController.sendMediaMessagesForSingleUser(fileObjects, usersJID)

            val handler = Handler()
            handler.postDelayed({
                progressDialog!!.dismiss()
                navigateToAppropriateScreen()
                finish()
            }, 500)
        } else {
            progressDialog!!.dismiss()
            CustomToast.show(context, getString(R.string.msg_no_internet))
        }
    }

    private fun navigateToAppropriateScreen() {
        if (selectedUsers!!.size == 1) {
            val userRoster: ProfileDetails = selectedUsers!![0]
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra(Constants.JID, userRoster.jid)
            intent.putExtra(Constants.CHAT_TYPE, userRoster.getChatType())
            intent.putExtra(Constants.FROM_QUICK_SHARE, true)
            startActivity(intent)
        } else if (selectedUsers!!.size > 1) {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    /**
     * handle caption click
     */
    private fun handleCaptionImage(toUser: String) {
        val replyMessageId = ReplyHashMap.getReplyId(toUser)
        if (intent.getBooleanExtra(Constants.IS_IMAGE, false)) {
            mediaPreviewBinding.previewProgress.previewProgress.show()
            val sentMessages = arrayListOf<ChatMessage>()
            for (item in selectedImageList) {
                val messageObject = if (item.isImage) messagingClient.composeImageMessage(toUser, item.path, item.caption, replyMessageId)
                else messagingClient.composeVideoMessage(toUser, item.path, item.caption, replyMessageId).second
                messageObject?.let {
                    messagingClient.sendMessage(it, object : MessageListener {
                        override fun onSendMessageSuccess(message: ChatMessage) {
                            sentMessages.add(message)
                            if (sentMessages.size == selectedImageList.size) {
                                mediaPreviewBinding.previewProgress.previewProgress.gone()
                                ReplyHashMap.saveReplyId(toUser, Constants.EMPTY_STRING)
                                SharedPreferenceManager.setString(
                                    Constants.REPLY_MESSAGE_USER,
                                    Constants.EMPTY_STRING
                                )
                                SharedPreferenceManager.setString(
                                    Constants.REPLY_MESSAGE_ID,
                                    Constants.EMPTY_STRING
                                )
                                viewModel.setUnSentMessageForAnUser(toUser, Constants.EMPTY_STRING)
                                ChatParent.startActivity(
                                    this@MediaPreviewActivity,
                                    toUser,
                                    viewModel.profileDetails.value!!.getChatType()
                                )
                            }
                        }
                    })
                }
            }
        } else {
            val messageObject = messagingClient.composeVideoMessage(toUser, intent.getStringExtra(Constants.FILE_PATH)!!,
                mediaPreviewBinding.imageCaption.text.toString().trim { it <= ' ' }, replyMessageId).second
            messageObject?.let {
                messagingClient.sendMessage(it, object : MessageListener {
                    override fun onSendMessageSuccess(message: ChatMessage) {
                        mediaPreviewBinding.previewProgress.previewProgress.gone()
                        ReplyHashMap.saveReplyId(toUser, Constants.EMPTY_STRING)
                        SharedPreferenceManager.setString(
                            Constants.REPLY_MESSAGE_USER,
                            Constants.EMPTY_STRING
                        )
                        SharedPreferenceManager.setString(
                            Constants.REPLY_MESSAGE_ID,
                            Constants.EMPTY_STRING
                        )
                        viewModel.setUnSentMessageForAnUser(toUser, Constants.EMPTY_STRING)
                        ChatParent.startActivity(
                            this@MediaPreviewActivity,
                            toUser,
                            viewModel.profileDetails.value!!.getChatType()
                        )
                    }
                })
            }
        }
    }

    private fun removeSelectedFile() {
        if (fileObjects.size == 1) finish() else {
            fileObjects.removeAt(viewPagerPosition)
            setViewPagerAdapter()
        }
    }

    private fun removeSelectedFileFromPicker() {
        if (selectedImageList.size == 1) finish() else {
            selectedImages.removeAt(viewPagerPosition)
            selectedImageList.removeAt(viewPagerPosition)
            setMediaPickerAdapter()
            setAddMoreVisibility()
        }
    }

    private fun setAddMoreVisibility() {
        mediaPreviewBinding.groupAddMore.visibility =
            if (selectedImageList.size < Constants.MAX_MEDIA_SELECTION_RESTRICTION && !isFromCamera) View.VISIBLE else View.GONE
    }

    private fun setMediaPickerAdapter() {
        mediaPreviewAdapter.notifyDataSetChanged()
        setAdapterForViewPager()
        mediaPreviewBinding.mediaList.adapter = mediaViewPagerAdapter
        if (viewPagerPosition == 0
            || viewPagerPosition <= selectedImageList.size - 1
        ) mediaPreviewBinding.mediaList.setCurrentItem(
            viewPagerPosition,
            true
        ) else if (viewPagerPosition == selectedImageList.size) {
            viewPagerPosition -= 1
            mediaPreviewBinding.mediaList.setCurrentItem(viewPagerPosition, true)
        }
        if (selectedImageList.size == 1) viewPagerPosition = 0
        onPageSelected(viewPagerPosition)
    }

    private fun setViewPagerAdapter() {
        mediaViewPagerAdapter = MediaPreviewPagerAdapter(supportFragmentManager)
        mediaViewPagerAdapter.setFileObjectList(fileObjects)
        mediaPreviewBinding.mediaList.adapter = mediaViewPagerAdapter
        if (viewPagerPosition == 0
            || viewPagerPosition <= fileObjects.size - 1
        ) mediaPreviewBinding.mediaList.setCurrentItem(
            viewPagerPosition,
            true
        ) else if (viewPagerPosition == fileObjects.size) {
            viewPagerPosition -= 1
            mediaPreviewBinding.mediaList.setCurrentItem(viewPagerPosition, true)
        }
        if (fileObjects.size == 1) viewPagerPosition = 0
        onPageSelected(viewPagerPosition)
    }

    override fun onResume() {
        super.onResume()
        keyboardHeightProvider?.onResume()
        AppLifecycleListener.isFromQuickShareForBioMetric = true
        AppLifecycleListener.isFromQuickShareForPin = true
        handleCursorAndKeyboardVisibility()
    }

    private fun handleCursorAndKeyboardVisibility() {
        if (showChatKeyboard) {
            mediaPreviewBinding.imageCaption.requestFocus()
            val imm =
                this@MediaPreviewActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(mediaPreviewBinding.imageCaption, InputMethodManager.SHOW_IMPLICIT)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            showChatKeyboard = false
        } else {
            if (showingEmojiKeyboard)
                mediaPreviewBinding.imageCaption.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }
    }

    override fun onPause() {
        super.onPause()
        keyboardHeightProvider?.onPause()
        if (mediaPreviewBinding.imageCaption.hasFocus() && !emojiHandler!!.isEmojiShowing) {
            isResumedNotCalled = true
        }
        if (isSoftKeyboardShown) showChatKeyboard = true
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLifecycleListener.isFromQuickShareForBioMetric = false
        AppLifecycleListener.isFromQuickShareForPin = false
    }

    override fun onEmojiconBackspaceClicked(v: View?) {
        EmojiconsFragment.backspace(mediaPreviewBinding.imageCaption)
    }

    override fun onEmojiconClicked(emojicon: Emojicon) {
        EmojiconsFragment.input(mediaPreviewBinding.imageCaption, emojicon)
    }

    private fun getKeyboardListener() = object : KeyboardHeightProvider.KeyboardListener {
        override fun onHeightChanged(height: Int) {
            val isShown = height > 0
            if (isShown == alreadyOpen) {
                return
            }
            alreadyOpen = isShown
            onKeyboardVisibilityChanged(isShown)
        }
    }
}