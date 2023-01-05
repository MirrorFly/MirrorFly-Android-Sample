package com.contusfly.activities

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.contus.flycommons.Constants
import com.contus.flycommons.LogMessage
import com.contus.flycommons.Prefs
import com.contus.webrtc.api.CallLogManager
import com.contusfly.*
import com.contusfly.backup.BackupConstants
import com.contusfly.backup.BackupConstants.ACCOUNT_PERMS_REQUEST_CODE
import com.contusfly.backup.BackupConstants.REQUEST_CODE_CHOOSE_ACCOUNT
import com.contusfly.backup.BackupConstants.REQUEST_CODE_SIGN_IN
import com.contusfly.backup.BackupRestoreParent
import com.contusfly.backup.TimeAgo
import com.contusfly.backup.WorkManagerController
import com.contusfly.chat.RealPathUtil
import com.contusfly.databinding.ActivityBackUpBinding
import com.contusfly.utils.ChatUtils
import com.contusfly.utils.MediaPermissions
import com.contusfly.utils.PickFileUtils
import com.contusfly.utils.RequestCode
import com.contusfly.views.PermissionAlertDialog
import com.contusflysdk.api.FlyCore
import com.contusflysdk.api.models.RecentChat
import com.contusflysdk.backup.BackupManager
import com.contusflysdk.backup.RestoreManager
import com.contusflysdk.utils.Utils
import com.google.api.client.googleapis.media.MediaHttpUploader
import kotlinx.android.synthetic.main.activity_back_up.*
import kotlinx.android.synthetic.main.activity_back_up.driveEmail
import kotlinx.android.synthetic.main.activity_restore.*
import kotlinx.android.synthetic.main.backup_dialog.view.*
import kotlinx.android.synthetic.main.connectivity_dialog.view.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.coroutines.CoroutineContext


open class BackUpActivity : BackupRestoreParent(), CoroutineScope,
    BackupRestoreParent.CommonBackupDialogListener {

    private lateinit var activityBackUpBinding: ActivityBackUpBinding

    private var isDriveBackup = false

    /**
     * Work manger instance
     */
    private val workManager: WorkManager = WorkManager.getInstance(this)

    /**
     * Ids of the workers
     */
    private lateinit var backupWorkerID: UUID
    private lateinit var driveUploadWorkerID: UUID
    private lateinit var restoreWorkerID: UUID

    /**
     * Workers Progress LiveData Observers
     */
    private lateinit var backupWorker: LiveData<WorkInfo>
    private lateinit var driveUploadWorker: LiveData<WorkInfo>
    private lateinit var restoreDataWorker: LiveData<WorkInfo>

    private var genericDialog: AlertDialog? = null
    private var backupProgressBar: ProgressBar? = null
    private var titleTv: AppCompatTextView? = null
    private var backupProgressText: AppCompatTextView? = null
    private var fileSize = 0L
    private var fileSizeString = "0 KB"
    private var filePath = emptyString()
    private var totalCount: Long = 0L
    private var isUploadInEnqueuedState = false
    private var alertDialog: AlertDialog? = null
    private var isOnlyBackup: Boolean = false

    protected val permissionAlertDialog: PermissionAlertDialog by lazy { PermissionAlertDialog(this) }


    private val uploadWorkerGeneric by lazy {
        workManager.getWorkInfosByTagLiveData(BackupConstants.DRIVE_WORKER_TAG)
    }

    private val backupWorkerGeneric =
        workManager.getWorkInfosByTagLiveData(BackupConstants.BACKUP_WORKER_TAG)


    private val downloadPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
            ?: ChatUtils.checkWritePermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (writePermissionGranted) {
            activityBackUpBinding.backup.performClick()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityBackUpBinding = ActivityBackUpBinding.inflate(layoutInflater)
        setContentView(activityBackUpBinding.root)
        setLastBackupInfo()
        Prefs.save(BackupConstants.BACKUP_TYPE, Constants.DRIVE_BACKUP)
        isDriveBackup = Prefs.getString(BackupConstants.BACKUP_TYPE) == Constants.DRIVE_BACKUP
        setCommonBackupDialogListener(this)
        if (isDriveBackup) {
            activityBackUpBinding.backupInfoText.text = getString(R.string.backup_info_text)
            setDriveEmailUI()
        } else {
            activityBackUpBinding.driveBox.gone()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.GET_ACCOUNTS), 202)
    }

    private fun setDriveEmailUI() {
        val myEmail = Prefs.getString(BackupConstants.DRIVE_EMAIL)
        activityBackUpBinding.driveEmail.text =
            if (myEmail.isNullOrEmpty()) getString(R.string.select_gmail_account) else myEmail
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        getRecentChatCount()
        val autoBackupOption = Prefs.getBoolean(BackupConstants.AUTO_BACKUP)
        activityBackUpBinding.autoSwitch.isChecked = autoBackupOption

        if (autoBackupOption) activityBackUpBinding.scheduleBox.show() else activityBackUpBinding.scheduleBox.gone()

        setConnectivityText()

        setFrequencyText()


        activityBackUpBinding.autoSwitch.setOnCheckedChangeListener { _, isChecked ->
            onAutoSwitchClicked(isChecked)
        }

        activityBackUpBinding.scheduleBox.setOnClickListener {
            showFrequencyDialog()
        }

        activityBackUpBinding.backupOverBox.setOnClickListener {
            showConnectivityDialog()
        }

        activityBackUpBinding.backBtn.setOnClickListener {
            finish()
        }

        activityBackUpBinding.backup.setOnClickListener {
            isOnlyBackup = false
            onBackupClicked()
        }

        activityBackUpBinding.cancelBackup.setOnClickListener {
            if (isDriveBackup && ::driveUploadWorkerID.isInitialized) workManager.cancelWorkById(
                driveUploadWorkerID
            )
            BackupManager.cancelBackup()
            LogMessage.e(TAG, "cancelBackup BACKUP_FILE_PATH empty")
            Prefs.save(BackupConstants.BACKUP_FILE_PATH, emptyString())
            resetBackupUI()
        }

        activityBackUpBinding.localCancelBackup.setOnClickListener {
            if (isOnlyBackup && ::backupWorkerID.isInitialized) {
                workManager.cancelWorkById(backupWorkerID)
                BackupManager.cancelBackup()
            } else if (::restoreWorkerID.isInitialized) {
                RestoreManager.cancelRestore()
                workManager.cancelWorkById(restoreWorkerID)
            }
            LogMessage.e(TAG, "cancelBackup BACKUP_FILE_PATH empty")
            Prefs.save(BackupConstants.BACKUP_FILE_PATH, emptyString())
            resetBackupUI()
        }

        activityBackUpBinding.accountBox.setOnClickListener {
            launch {
                val isNetworkUp = checkInternetUp()
                withContext(Dispatchers.Main.immediate) {
                    if (isNetworkUp) getAllGoogleAccounts() else showToast(getString(R.string.msg_no_internet))
                }
            }
        }

        activityBackUpBinding.downloadBackup.setOnClickListener {
            isOnlyBackup = true
            onDownloadClicked()
        }

        activityBackUpBinding.restoreData.setOnClickListener {
            onRestoreClicked()
        }

        genericWorkerObserver()

    }

    private fun onDownloadClicked() {
        if (totalCount <= 0)
            showToast(getString(R.string.no_msg_backup))
        else if (ChatUtils.checkWritePermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            onlyBackUpInit()
        } else
            MediaPermissions.requestContactStorageAccess(
                this,
                permissionAlertDialog,
                downloadPermissionLauncher
            )
    }

    private fun onRestoreClicked() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (MediaPermissions.isReadFilePermissionAllowed(this)
                && MediaPermissions.isWriteFilePermissionAllowed(this)
            )
                PickFileUtils.pickFile(this)
            else
                MediaPermissions.requestStorageAccess(
                    this,
                    permissionAlertDialog,
                    filePermissionLauncher
                )
        } else {
            PickFileUtils.pickFile(this)
        }
    }

    private fun onBackupClicked() {
        isUploadInEnqueuedState = false
        if (totalCount <= 0)
            showToast(getString(R.string.no_msg_backup))
        else if (ChatUtils.checkWritePermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        ) {
            startBackUp()
        } else
            MediaPermissions.requestContactStorageAccess(
                this,
                permissionAlertDialog,
                downloadPermissionLauncher
            )
    }

    private fun startBackUp() {
        if (Prefs.getString(BackupConstants.DRIVE_EMAIL).isNullOrEmpty())
            showToast(getString(R.string.select_google_account))
        else {
            launch {
                val isNetworkUp = checkInternetUp()
                withContext(Dispatchers.Main.immediate) {
                    if (isNetworkUp) {
                        if (WorkManagerController.checkGoogleLogin()) {
                            showBackupDialog()
                            initWorkers()
                        } else showToast(getString(R.string.google_logout_info))
                    } else showToast(getString(R.string.msg_no_internet))
                }
            }
        }
    }

    private fun initWorkers() {
        if (WorkManagerController.checkWifiLogic()
            || !WorkManagerController.isNetConnected()
            || WorkManagerController.checkRoaming(this@BackUpActivity)
            || isOnlyBackup
        ) onlyBackUpInit() else autoBackUpInit()
    }

    private fun autoBackUpInit() {
        if (!Prefs.getBoolean(BackupConstants.IS_AUTO_BACKUP)) {
            val workerIds =
                WorkManagerController.backUpDriveUpload(true)
            backupWorkerID = workerIds.first
            initBackupWorker(backupWorkerID)
            driveUploadWorkerID = workerIds.second
            LogMessage.e(
                TAG,
                "activityBackUpBinding.backup driveUploadWorkerID initDriveWorker"
            )
            initDriveWorker(driveUploadWorkerID)
        } else {
            val workerIds =
                WorkManagerController.backUpDriveUploadIsAutoBack(
                    true
                )
            backupWorkerID = workerIds
            LogMessage.e(
                TAG,
                "activityBackUpBinding.backup backupWorkerID auto backup"
            )
            initBackupWorker(backupWorkerID)
        }
    }

    private fun onlyBackUpInit() {
        if (isOnlyBackup) showDownloadUI("Downloading : 0.0KB  of $fileSizeString (0%)")
        backupWorkerID =
            WorkManagerController.runBackupOnly()
        LogMessage.e(
            TAG,
            "activityBackUpBinding.backup only backupWorkerID"
        )
        initBackupWorker(backupWorkerID)
    }

    /**
     * Updates the UI when downloading backup file is in downloading state
     *
     * @param uploadInfoText String either no internet or progress status string
     */
    private fun showDownloadUI(infoText: String) {
        activityBackUpBinding.localProgressText.text = infoText
        activityBackUpBinding.localWorkProgress.progress = 0
        activityBackUpBinding.downloadBackup.gone()
        activityBackUpBinding.restoreData.gone()
        activityBackUpBinding.localProgressBox.show()
    }

    private fun onAutoSwitchClicked(isChecked: Boolean) {
        Prefs.save(BackupConstants.AUTO_BACKUP, isChecked)
        if (Prefs.getString(BackupConstants.DRIVE_EMAIL).isNullOrEmpty()) {
            activityBackUpBinding.autoSwitch.isChecked = false
            showToast(getString(R.string.select_google_account))
        } else {
            if (isChecked) {
                activityBackUpBinding.scheduleBox.show()
                showFrequencyDialog()
            } else {
                activityBackUpBinding.scheduleBox.gone()
            }
        }
    }

    private fun getRecentChatCount() {
        FlyCore.getRecentChatList { isSuccess, _, data ->
            if (isSuccess) {
                val list =
                    LinkedList(data[com.contusfly.utils.Constants.SDK_DATA] as MutableList<RecentChat>)
                totalCount = list.size.toLong()
            }
        }

        val callEntryCount = CallLogManager.getCallLogs()

        if (callEntryCount.isNotEmpty()) {
            totalCount += callEntryCount.size
        }

    }

    private fun genericWorkerObserver() {
        uploadWorkerGeneric.observe(this, androidx.lifecycle.Observer {
            if (it!!.isNotEmpty()) {
                val workerInfo = it.first()
                LogMessage.e(TAG, "uploadWorkerGeneric")
                when (workerInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> setFilePathIfEmpty()
                    WorkInfo.State.RUNNING -> onUploadWorkerGenericRunning(workerInfo!!)
                    WorkInfo.State.SUCCEEDED -> setLastBackupInfo()
                    else -> Log.d(TAG, "${workerInfo.state}")
                }
            }
        })

        backupWorkerGeneric.observe(this, androidx.lifecycle.Observer {
            if (it!!.isNotEmpty()) {
                val workerInfo = it.first()
                when (workerInfo.state) {
                    WorkInfo.State.RUNNING -> onBackupWorkerGenericRunning(workerInfo!!)
                    else -> Log.d(TAG, "${workerInfo.state}")
                }
            }
        })

    }

    private fun onBackupWorkerGenericRunning(workerInfo: WorkInfo) {
        if (!::backupWorkerID.isInitialized) {
            backupWorkerID = workerInfo.id
            initBackupWorker(workerInfo.id)
            showBackupDialog()
        }
    }

    private fun onUploadWorkerGenericRunning(workerInfo: WorkInfo) {
        LogMessage.e(TAG, "uploadWorkerGeneric RUNNING")
        setFilePathIfEmpty()
        if (isDriveBackup && !::driveUploadWorkerID.isInitialized) {
            LogMessage.e(
                TAG,
                "uploadWorkerGeneric driveUploadWorkerID initDriveWorker"
            )
            driveUploadWorkerID = workerInfo.id
            initDriveWorker(driveUploadWorkerID)
        }
    }

    private fun setFilePathIfEmpty() {
        if (filePath.isEmpty() && !Prefs.getString(BackupConstants.BACKUP_FILE_PATH)
                .isNullOrEmpty()
        ) {
            setFileInfo(Prefs.getString(BackupConstants.BACKUP_FILE_PATH)!!)
            activityBackUpBinding.uploadingProgressText.text =
                "Uploading : 0 KB  of $fileSizeString (0%)"
        }
    }

    private fun setFileInfo(fileLocalPath: String) {
        if (fileSize == 0L && fileLocalPath.isNotEmpty()) {
            LogMessage.e(TAG, "setFileInfo $fileLocalPath")
            filePath = fileLocalPath
            fileSize = File(filePath).length()
            fileSizeString = getFileSizeInStringFormat(fileSize)!!
            Log.d("BACKUP_BACKUP", "${filePath} $fileSize")
        }
    }


    /**
     * Initialization of  Drive upload worker and its the Observers
     *
     * @param id UUID of the worker
     */
    private fun initDriveWorker(id: UUID) {

        driveUploadWorker = workManager.getWorkInfoByIdLiveData(id)

        driveUploadWorker.observe(this, androidx.lifecycle.Observer {

            it?.let {

                try {

                    val workerInfo = it

                    when (workerInfo.state) {

                        WorkInfo.State.ENQUEUED -> showUploadUI("Uploading : 0 KB  of $fileSizeString (0%)")

                        WorkInfo.State.RUNNING -> {
                            onDriveWorkerRunning(workerInfo)
                        }

                        WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> resetBackupUI()
                        WorkInfo.State.SUCCEEDED -> onDriveWorkerSucceeded()
                        else -> Log.d("BACKUP_DRIVE_OBSERVER", "${workerInfo.state}")
                    }

                } catch (e: Throwable) {
                    LogMessage.e(e)
                }
            }

        })


    }

    private fun onDriveWorkerRunning(workerInfo: WorkInfo) {
        genericDialog?.dismiss()

        val progressData = workerInfo.progress
        showProgressView()
        val filePath =
            progressData.getString(BackupConstants.BACKUP_FILE_PATH)!!
        setFileInfo(filePath)

        val reason = progressData.getString(BackupConstants.REASON)
            ?: emptyString()

        Log.d("BACKUP_DRIVE_OBSERVER", " reason=> $reason")

        updateDriveReasonUI(reason, progressData)
    }

    private fun onDriveWorkerSucceeded() {
        backupProgressBar?.progress = 100
        showToast(getString(R.string.backup_success_msg))
        resetBackupUI()
    }

    private fun updateDriveReasonUI(reason: String, progressData: Data) {
        when (reason) {
            MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS.name -> {

                val progressValue =
                    progressData.getInt(BackupConstants.PROGRESS, 0)
                if (progressValue > 0) {
                    activityBackUpBinding.workProgress.progress = progressValue
                    activityBackUpBinding.uploadingProgressText.text =
                        "Uploading : ${getFileSizeInStringFormat((fileSize / 100) * progressValue)}  of ${fileSizeString} (${progressValue}%)"
                }
            }
            MediaHttpUploader.UploadState.MEDIA_COMPLETE.name -> {

                activityBackUpBinding.workProgress.progress = 100
                activityBackUpBinding.uploadingProgressText.text =
                    "Uploaded :$fileSizeString  of $fileSizeString (100%)"

                resetBackupUI()
                launch {
                    delay(250L)
                    withContext(Dispatchers.Main.immediate) {
                        setLastBackupInfo()
                    }
                }
            }

            "403" -> showToast(getString(R.string.drive_space_issue_msg))
            "401" -> {
                setDriveEmailUI()
                showToast(progressData.getString(BackupConstants.MESSAGE))
            }
            "100" -> showToast(progressData.getString(BackupConstants.MESSAGE))
            else -> Log.d(
                "BACKUP_DRIVE_OBSERVER",
                "else branch reason $reason ${
                    Utils.getGSONInstance().toJson(progressData)
                }"
            )
        }
    }

    private fun showProgressView() {
        if (activityBackUpBinding.progressBox.visibility == View.GONE) {
            activityBackUpBinding.progressBox.show()
            genericDialog?.dismiss()
            activityBackUpBinding.backup.gone()
        }
    }

    /**
     * Updates the UI when backup is in uploading state
     *
     * @param uploadInfoText String either no internet or progress status string
     */
    private fun showUploadUI(uploadInfoText: String) {
        isUploadInEnqueuedState = true
        activityBackUpBinding.uploadingProgressText.text = uploadInfoText
        activityBackUpBinding.workProgress.progress = 0
        genericDialog!!.dismiss()
        activityBackUpBinding.backup.gone()
        activityBackUpBinding.progressBox.show()
    }

    /**
     * Resets the UI related to backup
     */
    private fun resetBackupUI() {
        setLastBackupInfo()
        activityBackUpBinding.progressBox.gone()
        genericDialog?.dismiss()
        activityBackUpBinding.backup.show()
        activityBackUpBinding.workProgress.progress = 0
        isUploadInEnqueuedState = false
    }

    /**
     * Initialization of Back up messages worker and its the Observers
     *
     * @param id UUID of the worker
     */
    private fun initBackupWorker(id: UUID) {

        backupWorker = workManager.getWorkInfoByIdLiveData(id)

        backupWorker.observe(this, androidx.lifecycle.Observer {

            it?.let {

                val workerInfo = it
                val progressData = workerInfo.progress
                val progressValue = progressData.getInt(BackupConstants.PROGRESS, 0)
                LogMessage.e(TAG, "progressValue $progressValue")
                when (workerInfo.state) {

                    WorkInfo.State.RUNNING -> {

                        val progressData = workerInfo.progress
                        val progressValue = progressData.getInt(BackupConstants.PROGRESS, 0)

                        backupProgressBar?.progress = progressValue
                        backupProgressText?.text =
                            "${getString(R.string.please_wait_msg)} ($progressValue%)"
                        updateProgress("${getString(R.string.please_wait_msg)} ($progressValue%)")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        setFileInfo(it.outputData.getString(BackupConstants.BACKUP_FILE_PATH)!!)
                        LogMessage.i(
                            TAG,
                            "Success ${it.outputData.getString(BackupConstants.BACKUP_FILE_PATH)!!}"
                        )
                        onBackupWorkerSucceeded()
                        Log.d("DRIVE_T", " Backup WorkInfo.State.SUCCEEDED")
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> resetBackupUI()
                    else -> LogMessage.d(TAG, " WORK Manager State ${workerInfo.state}")
                }
            }

        })

    }

    private fun onBackupWorkerSucceeded() {
        if (!WorkManagerController.isNetConnected())
            showAlertDialog(true)
        else if ((WorkManagerController.checkRoaming(this) && !WorkManagerController.isConnectedToWifi()))
            showAlertDialog(isRoamingLogic = true)
        else if (WorkManagerController.checkWifiLogic())
            showAlertDialog()
        else {
            if (isOnlyBackup) {
                showToast(getString(R.string.downloaded_backup_info))
                resetDownloadUI()
            } else if (isDriveBackup) {
                launch {
                    if (!WorkManagerController.checkIfAWorkerIsAlreadyScheduledOrNot(
                            BackupConstants.DRIVE_WORKER_TAG
                        )
                    ) {
                        driveUploadWorkerID = WorkManagerController.runDriveUpload()
                        LogMessage.e(
                            TAG,
                            "backupWorker.observe driveUploadWorkerID initDriveWorker"
                        )
                        initDriveWorker(driveUploadWorkerID)
                    }
                }
                if (WorkManagerController.isNetConnected())
                    showUploadUI("Uploading : 0 KB  of $fileSizeString (0%)")
            }
        }
    }

    private fun resetDownloadUI() {
        activityBackUpBinding.localWorkProgress.progress = 100
        activityBackUpBinding.downloadBackup.show()
        activityBackUpBinding.restoreData.show()
        activityBackUpBinding.localProgressBox.gone()
    }

    private fun showAlertDialog(
        isNetDisconnected: Boolean = false,
        isRoamingLogic: Boolean = false
    ) {

        genericDialog?.dismiss()

        val alertDialogBuilder = AlertDialog.Builder(this)

        when {
            isNetDisconnected -> {
                if (Prefs.getBoolean(BackupConstants.WIFI_BACKUP_ONLY)) {
                    alertDialogBuilder.setMessage(getString(R.string.connect_wifi_info))
                } else {
                    alertDialogBuilder.setMessage(getString(R.string.connect_wifi_cell_info))
                }

                alertDialogBuilder.setPositiveButton(getString(R.string.ok_label)) { dialog, which ->
                    dialog.dismiss()
                }

            }
            isRoamingLogic -> {

                alertDialogBuilder.setMessage(getString(R.string.roaming_info))

                alertDialogBuilder.setPositiveButton(getString(R.string.ok_label)) { dialog, which ->
                    startUploadingWorkers(true)
                    dialog.dismiss()
                }

            }
            else -> {
                alertDialogBuilder.setMessage(getString(R.string.wifi_alert))

                alertDialogBuilder.setTitle(getString(R.string.over_cell_backup))

                alertDialogBuilder.setPositiveButton(getString(R.string.back_up)) { dialog, which ->
                    startUploadingWorkers()
                    dialog.dismiss()
                }

                alertDialogBuilder.setNegativeButton(getString(R.string.action_cancel)) { dialog, which ->
                    resetBackupUI()
                    dialog.dismiss()
                }
            }
        }

        alertDialogBuilder.setCancelable(false)

        alertDialog = alertDialogBuilder.create()

        alertDialog!!.show()
    }

    private fun startUploadingWorkers(wifiOnly: Boolean = false) {
        checkInternetAndExecute {
            if (isDriveBackup) {
                driveUploadWorkerID = WorkManagerController.runDriveUpload(wifiOnly)
                LogMessage.e(TAG, "startUploadingWorkers driveUploadWorkerID initDriveWorker")
                initDriveWorker(driveUploadWorkerID)
            }
        }
    }

    /**
     * Shows the Backing up messages dialog
     */
    private fun showBackupDialog(isAuthentication: Boolean = false) {
        if (!isOnlyBackup) {
            if (genericDialog == null) {
                val builder = AlertDialog.Builder(this)
                val dialogView: View = layoutInflater.inflate(R.layout.backup_dialog, null)
                builder.setView(dialogView)

                titleTv = dialogView.dialog_title
                backupProgressBar = dialogView.back_up_progress
                backupProgressText = dialogView.back_up_text

                if (isAuthentication) {
                    titleTv?.text = getString(R.string.authenticating)
                    backupProgressText!!.text = getString(R.string.authenticating_drive)
                } else {
                    titleTv?.text = getString(R.string.backing_up_msg)
                    backupProgressText!!.text = getString(R.string.please_wait_msg)
                }

                builder.setCancelable(false)
                genericDialog = builder.create()
                genericDialog!!.show()
            } else {
                if (isAuthentication) {
                    titleTv?.text = getString(R.string.authenticating)
                    backupProgressText!!.text = getString(R.string.authenticating_drive)
                } else {
                    titleTv?.text = getString(R.string.backing_up_msg)
                    backupProgressText!!.text = getString(R.string.please_wait_msg)
                }
                genericDialog!!.show()
            }
        }

    }

    /**
     * Sets the Last backup info in the UI
     */
    private fun setLastBackupInfo() {
        val date = Prefs.getString(BackupConstants.BACKUP_FILE_DATE)
        val size = Prefs.getString(BackupConstants.BACKUP_FILE_SIZE)
        if (date != "") {
            activityBackUpBinding.lastBackupDate.text = TimeAgo.getTimeAgo(date.toLong(), this)
        }
        if (size != "") {
            activityBackUpBinding.lastBackupSize.text =
                "${getFileSizeInStringFormat(size.toLong())}"
        }
    }

    /**
     * Shows the Backup Connectivity Dialog
     */
    private fun showConnectivityDialog() {

        var connectDialog: AlertDialog? = null
        val cBuilder = AlertDialog.Builder(this)
        val connectDialogView: View = layoutInflater.inflate(R.layout.connectivity_dialog, null)
        cBuilder.setView(connectDialogView)
        val wifiImage = connectDialogView.wifiImage
        val cellImage = connectDialogView.cellImage
        val isWifiOnly = Prefs.getBoolean(BackupConstants.WIFI_BACKUP_ONLY)

        if (isWifiOnly) {
            setImageForImageView(0, listOf(wifiImage, cellImage))
        } else {
            setImageForImageView(1, listOf(wifiImage, cellImage))
        }

        connectDialogView.wifiOnlyBox.setOnClickListener {
            setImageForImageView(0, listOf(wifiImage, cellImage))
            Prefs.save(BackupConstants.WIFI_BACKUP_ONLY, true)
            connectDialog?.dismiss()
        }

        connectDialogView.cellularBox.setOnClickListener {
            setImageForImageView(1, listOf(wifiImage, cellImage))
            Prefs.save(BackupConstants.WIFI_BACKUP_ONLY, false)
            connectDialog?.dismiss()
        }

        connectDialogView.cancel.setOnClickListener {
            connectDialog?.dismiss()
        }

        connectDialog = cBuilder.create()
        connectDialog.setOnDismissListener {
            setConnectivityText()
        }
        connectDialog.show()
    }

    private fun getAllGoogleAccounts() {

        if (isPermissionsAllowed(Manifest.permission.GET_ACCOUNTS)) {

            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf(BackupConstants.ACCOUNT_TYPE),
                null,
                null,
                null,
                null
            )
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_ACCOUNT)
        } else {
            MediaPermissions.requestAccountPermissions(this, ACCOUNT_PERMS_REQUEST_CODE)

        }

    }

    /**
     * Sets the Frequency text of the current Back up
     */
    private fun setFrequencyText() {
        val frequencyOption = Prefs.getString(BackupConstants.BACKUP_FREQUENCY)
        activityBackUpBinding.frequencyText.text = frequencyOption.capitalize()
    }

    /**
     * Sets the text of frequency backup
     */
    private fun setConnectivityText() {
        Prefs.getBoolean(BackupConstants.WIFI_BACKUP_ONLY).let {
            if (it) {
                activityBackUpBinding.connectivityText.text = getString(R.string.wifi_only)
            } else {
                activityBackUpBinding.connectivityText.text = getString(R.string.wifi_cellular)
            }
        }
    }


    private suspend fun checkInternetUp(): Boolean = hasActiveInternet()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    companion object {

        const val TAG = "BackUpActivity"

    }


    /*
    * Storage Permissions Result handling
    */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                activityBackUpBinding.backup.performClick()
            } else {
                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                if (!showRationale)
                    Toast.makeText(
                        this,
                        getString(R.string.need_permission_backup),
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_SIGN_IN -> {
                if (resultCode == Activity.RESULT_OK && data != null)
                    handleSignInResult(data, driveEmail)
                else if (Prefs.getString(BackupConstants.DRIVE_EMAIL).isNotEmpty())
                    clientLogin(Prefs.getString(BackupConstants.DRIVE_EMAIL), true)
                setDriveEmailUI()
                genericDialog?.dismiss()
            }
            ACCOUNT_PERMS_REQUEST_CODE -> if (resultCode == RESULT_OK) getAllGoogleAccounts()

            REQUEST_CODE_CHOOSE_ACCOUNT -> {
                if (resultCode == RESULT_OK) {
                    val accountName = data?.extras?.get(AccountManager.KEY_ACCOUNT_NAME) ?: ""
                    Prefs.save(BackupConstants.DRIVE_EMAIL, accountName.toString())
                    Prefs.save(BackupConstants.NEED_RELOGIN, false)
                    driveEmail.text = accountName.toString()
                    checkAndLoginMail(accountName.toString())
                }
            }

            RequestCode.PICK_FILE -> data?.let { checkFileToRestore(data.data) }
        }
    }

    override fun onDialogClosed() {
        setFrequencyText()
    }

    private fun checkFileToRestore(filePathUri: Uri?) {
        filePathUri.let {
            try {
                val fileRealPath = RealPathUtil.getRealPath(this, filePathUri)
                if (fileRealPath != null && fileRealPath.isNotEmpty())
                    startRestore(fileRealPath)
                else
                    showFileValidation()
            } catch (e: Exception) {
                LogMessage.e(e)
            }

        }
    }

    private fun startRestore(fileRealPath: String) {
        showDownloadUI("${getString(R.string.restoring_msg)} (0%)")
        restoreWorkerID = WorkManagerController.runRestoreOnly(fileRealPath)
        initRestoreWorker(restoreWorkerID)
    }

    private fun initRestoreWorker(id: UUID) {
        restoreDataWorker = workManager.getWorkInfoByIdLiveData(id)
        restoreDataWorker.observe(this, androidx.lifecycle.Observer {

            it?.let {

                val workerInfo = it
                when (workerInfo.state) {

                    WorkInfo.State.RUNNING -> {
                        val progressData = workerInfo.progress
                        val progressValue = progressData.getInt(BackupConstants.PROGRESS, 0)
                        LogMessage.i(TAG, "initRestoreWorker RUNNING progressValue$progressValue")
                        updateProgress("${getString(R.string.restoring_msg)} ($progressValue%)")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        LogMessage.i(TAG, "initRestoreWorker SUCCEEDED")
                        showToast(getString(R.string.restore_success_info))
                        resetDownloadUI()
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        LogMessage.d(TAG, "Restore Worker is ${it.state}")
                        showToast(getString(R.string.restore_failed_info))
                    }
                    else -> {
                        Log.d("RESTORE_ACTIVITY_REST", "${workerInfo.state}")
                    }
                }

            }

        })
    }

    private fun updateProgress(info: String) {
        localProgressText?.text = info
    }


}