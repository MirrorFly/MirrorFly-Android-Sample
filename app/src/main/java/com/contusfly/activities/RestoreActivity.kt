package com.contusfly.activities

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.contus.flycommons.LogMessage
import com.contus.flycommons.Prefs
import com.contus.flycommons.SharedPreferenceManager
import com.contusfly.*
import com.contusfly.backup.*
import com.contusfly.backup.models.BackupInfo
import com.contusfly.databinding.ActivityRestoreBinding
import com.contusfly.utils.ChatUtils
import com.contusfly.utils.Constants
import com.contusfly.utils.MediaPermissions
import com.contusfly.views.PermissionAlertDialog
import com.contusflysdk.api.GroupManager
import com.contusflysdk.backup.RestoreManager
import com.contusflysdk.model.Backup
import com.contusflysdk.utils.Utils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.media.MediaHttpDownloader
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.android.synthetic.main.activity_back_up.*
import kotlinx.android.synthetic.main.activity_back_up.driveEmail
import kotlinx.android.synthetic.main.activity_restore.*
import kotlinx.android.synthetic.main.backup_dialog.view.*
import kotlinx.android.synthetic.main.frequency_dialog.view.*
import kotlinx.coroutines.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class RestoreActivity : BackupRestoreParent(), CoroutineScope,
    BackupRestoreParent.CommonBackupDialogListener {

    private lateinit var activityRestoreBinding: ActivityRestoreBinding

    lateinit var driveHelper: DriveHelper

    private var genericDialog: AlertDialog? = null
    private var isDriveBackup = true

    private var driveFile: File? = null

    private var fileId = emptyString()
    private var fileName = emptyString()
    var fileSize = 0L
    private var fileSizeString = emptyString()
    private var lastBackupTimeInLong = 0L

    /**
     * Work manger instance
     */
    private val workManager: WorkManager = WorkManager.getInstance(this)

    /**
     * Ids of the workers
     */
    private lateinit var driveWorkerId: UUID
    private lateinit var restoreWorkerID: UUID

    /**
     * Workers Progress LiveData Observers
     */
    private lateinit var restoreWorker: LiveData<WorkInfo>
    private lateinit var driveWorker: LiveData<WorkInfo>

    private var isExisting: Boolean = false

    protected val permissionAlertDialog: PermissionAlertDialog by lazy { PermissionAlertDialog(this) }

    private val downloadPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
            ?: ChatUtils.checkWritePermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (writePermissionGranted) {
            activityRestoreBinding.restore.performClick()
        }
    }

    /*
    Google Account Initialization
     */
    private val account: Account? by lazy {
        val accountAsString = Prefs.getString(BackupConstants.GOOGLE_ACCOUNT)
        if (accountAsString.isEmpty()) null else Utils.getGSONInstance()
            .fromJson(accountAsString, Account::class.java)
    }

    /*
    Credential initialization for Drive Access
     */
    private val credential: GoogleAccountCredential? by lazy {
        val cred = GoogleAccountCredential.usingOAuth2(
            this,
            Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        cred.selectedAccount = account
        return@lazy cred
    }

    /*
    Drive Object Initialization
     */
    private val drive: Drive? by lazy {
        if (account == null) {
            null
        } else {
            Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(com.contus.flycommons.getString(R.string.drive_backup_folder_name))
                .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityRestoreBinding = ActivityRestoreBinding.inflate(layoutInflater)
        setContentView(activityRestoreBinding.root)
        Prefs.save(BackupConstants.BACKUP_FREQUENCY, BackupConstants.DAILY)
        isExisting = intent.getBooleanExtra("isExisting", false)
        setUpViews()
        setCommonBackupDialogListener(this)
        Prefs.save(BackupConstants.SHOULD_SHOW_RESTORE, true)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.GET_ACCOUNTS),
            BackupConstants.REQUEST_CODE_SIGN_IN
        )
    }

    private fun setUpViews() {
        if (!isExisting) {
            showUINewUser()
        } else {
            showUIOldUser()
        }
    }

    private fun showUINewUser() {
        activityRestoreBinding.textTitle.text = getString(R.string.back_up)
        activityRestoreBinding.mainTitle.text = getString(R.string.add_account)
        activityRestoreBinding.infoText.text = getString(R.string.new_user_info)
        activityRestoreBinding.newAccountBox.show()
        activityRestoreBinding.lastBackupDate.gone()
        activityRestoreBinding.lastBackupSize.gone()
        activityRestoreBinding.accountBox.gone()
        activityRestoreBinding.dividerOne.gone()
        activityRestoreBinding.autoBox.gone()
        activityRestoreBinding.scheduleBox.gone()
        activityRestoreBinding.dividerTwo.gone()
        activityRestoreBinding.initialBox.gone()
        activityRestoreBinding.finalBox.gone()
        activityRestoreBinding.newUserBox.gone()
    }

    private fun showUIOldUser() {
        activityRestoreBinding.textTitle.text = getString(R.string.looking_backup)
        activityRestoreBinding.mainTitle.text = getString(R.string.add_account)
        activityRestoreBinding.infoText.text = getString(R.string.restore_info)
        activityRestoreBinding.newAccountBox.gone()
        activityRestoreBinding.lastBackupDate.show()
        activityRestoreBinding.lastBackupSize.show()
        activityRestoreBinding.accountBox.show()
        activityRestoreBinding.dividerOne.show()
        activityRestoreBinding.autoBox.show()
        activityRestoreBinding.scheduleBox.gone()
        activityRestoreBinding.dividerTwo.show()
        activityRestoreBinding.initialBox.gone()
        activityRestoreBinding.finalBox.show()
        activityRestoreBinding.workProgress.gone()
        activityRestoreBinding.progressText.gone()
        activityRestoreBinding.newUserBox.gone()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        activityRestoreBinding.skip.setOnClickListener {
            Prefs.save(BackupConstants.AUTO_BACKUP, false)
            Prefs.save(BackupConstants.BACKUP_FREQUENCY, emptyString())
            Prefs.save(BackupConstants.NEXT_BACKUP_TIME, emptyString())
            Prefs.save(BackupConstants.DRIVE_EMAIL, emptyString())
            WorkManagerController.cancelRestoreWorkers()
            goToProfile()
        }

        activityRestoreBinding.finalSkip.setOnClickListener {
            if (!isExisting) {
                Prefs.save(BackupConstants.AUTO_BACKUP, false)
                Prefs.save(BackupConstants.BACKUP_FREQUENCY, emptyString())
                Prefs.save(BackupConstants.NEXT_BACKUP_TIME, emptyString())
                Prefs.save(BackupConstants.DRIVE_EMAIL, emptyString())
            }
            RestoreManager.cancelRestore()
            WorkManagerController.cancelRestoreWorkers()
            goToProfile()
        }
        activityRestoreBinding.autoSwitch.setOnCheckedChangeListener { _, isChecked ->
            onAutoSwitchClicked(isChecked)
        }

        activityRestoreBinding.scheduleBox.setOnClickListener {
            showFrequencyDialog()
        }

        activityRestoreBinding.accountBox.setOnClickListener {
            onAccountClicked()
        }

        activityRestoreBinding.newAccountBox.setOnClickListener {
            onAccountClicked()
        }
        activityRestoreBinding.actionNext.setOnClickListener {
            goToProfile()
        }
        activityRestoreBinding.newUserSkip.setOnClickListener {
            Prefs.save(BackupConstants.AUTO_BACKUP, false)
            Prefs.save(BackupConstants.BACKUP_FREQUENCY, emptyString())
            Prefs.save(BackupConstants.NEXT_BACKUP_TIME, emptyString())
            Prefs.save(BackupConstants.DRIVE_EMAIL, emptyString())
            goToProfile()
        }

        activityRestoreBinding.restore.setOnClickListener {
            if (ChatUtils.checkWritePermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) onRestoreClicked() else MediaPermissions.requestContactStorageAccess(
                this,
                permissionAlertDialog,
                downloadPermissionLauncher
            )
        }
    }

    private fun onRestoreClicked() {
        checkInternetAndExecute {
            activityRestoreBinding.initialBox.gone()
            activityRestoreBinding.finalBox.show()
            activityRestoreBinding.workProgress.show()
            activityRestoreBinding.progressText.show()
            if (isDriveBackup) {
                val workerIds = WorkManagerController.runDriveRestoreWorkers(
                    fileId,
                    fileName
                )
                driveWorkerId = workerIds.first
                initDriveListeners(driveWorkerId)
                restoreWorkerID = workerIds.second
                initRestoreListener(restoreWorkerID)
                progressText.text = "Downloading : 0.0KB  of $fileSizeString (0%)"
            }
        }
    }

    private fun onAccountClicked() {
        launch {
            val isNetworkUp = checkInternetUp()
            withContext(Dispatchers.Main.immediate) {
                if (isNetworkUp) getAllGoogleAccounts() else showToast(getString(R.string.msg_no_internet))
            }
        }
    }

    private fun onAutoSwitchClicked(isChecked: Boolean) {
        Prefs.save(BackupConstants.AUTO_BACKUP, isChecked)
        if (Prefs.getString(BackupConstants.DRIVE_EMAIL).isNullOrEmpty()) {
            activityRestoreBinding.autoSwitch.isChecked = false
            showToast(getString(R.string.select_google_account))
        } else {
            if (isChecked) {
                activityRestoreBinding.scheduleBox.show()
                showFrequencyDialog()
            } else {
                activityRestoreBinding.scheduleBox.gone()
            }
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    private fun goToProfile() {
        startActivity(
            Intent(this, ProfileStartActivity::class.java).putExtra(
                Constants.IS_FIRST_LOGIN,
                true
            )
                .putExtra(Constants.FROM_SPLASH, true)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        GroupManager.getAllGroups(true) { _, _, _ -> }
        finish()
    }

    /**
     * Sets the Frequency text of the current Back up
     */
    private fun setFrequencyText() {
        val frequencyOption = Prefs.getString(BackupConstants.BACKUP_FREQUENCY)
        activityRestoreBinding.frequencyText.text = frequencyOption.capitalize()
    }

    private suspend fun checkInternetUp(): Boolean = hasActiveInternet()

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
            startActivityForResult(intent, BackupConstants.REQUEST_CODE_CHOOSE_ACCOUNT)

        } else {

            MediaPermissions.requestAccountPermissions(
                this,
                BackupConstants.ACCOUNT_PERMS_REQUEST_CODE
            )

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            BackupConstants.REQUEST_CODE_SIGN_IN -> {
                if (resultCode == Activity.RESULT_OK && data != null)
                    handleSignInResult(data)
                else if (Prefs.getString(BackupConstants.DRIVE_EMAIL).isNotEmpty())
                    clientLogin(Prefs.getString(BackupConstants.DRIVE_EMAIL), true)
                setDriveEmailUI()
            }
            BackupConstants.ACCOUNT_PERMS_REQUEST_CODE -> if (resultCode == RESULT_OK) getAllGoogleAccounts()
            BackupConstants.REQUEST_CODE_CHOOSE_ACCOUNT -> {
                if (resultCode == RESULT_OK) {
                    val accountName = data?.extras?.get(AccountManager.KEY_ACCOUNT_NAME) ?: "NONE"
                    Prefs.save(BackupConstants.DRIVE_EMAIL, accountName.toString())
                    Prefs.save(BackupConstants.NEED_RELOGIN, false)
                    driveEmail.text = accountName.toString()
                    setNewUserMail(accountName.toString())
                    checkAndLoginMail(accountName.toString())
                }
            }
        }
    }

    private fun setNewUserMail(mail: String) {
        activityRestoreBinding.driveEmail.text = mail
        if (!isExisting) {
            activityRestoreBinding.newAccountBox.gone()
            activityRestoreBinding.accountBox.show()
            activityRestoreBinding.dividerOne.show()
            activityRestoreBinding.autoBox.show()
            activityRestoreBinding.scheduleBox.gone()
            activityRestoreBinding.dividerTwo.show()
            activityRestoreBinding.initialBox.gone()
            activityRestoreBinding.finalBox.gone()
            activityRestoreBinding.newUserBox.show()
            activityRestoreBinding.newUserSkip.show()
            activityRestoreBinding.actionNext.show()
        }
    }

    /**
     * Handle the Successful Sign in of Google Account with Drive Permission
     * @param resultData Intent sign in data
     */
    private fun handleSignInResult(resultData: Intent) {

        GoogleSignIn.getSignedInAccountFromIntent(resultData)
            .addOnSuccessListener { googleAccount: GoogleSignInAccount ->
                Prefs.save(BackupConstants.DRIVE_EMAIL, googleAccount.email.toString())
                Prefs.save(
                    BackupConstants.GOOGLE_ACCOUNT,
                    Utils.getGSONInstance().toJson(googleAccount.account)
                )
                Prefs.save(BackupConstants.NEED_RELOGIN, false)
                driveEmail.text = googleAccount.email.toString()

                if (isExisting && drive != null) {

                    // The DriveHelper encapsulates all REST API and SAF functionality. Its instantiation is required before handling any onClick actions.
                    driveHelper = DriveHelper(drive!!)
                    launch {
                        queryDriveFiles()
                    }
                }
            }
    }

    private fun setDriveEmailUI() {
        val myEmail = Prefs.getString(BackupConstants.DRIVE_EMAIL)
        activityRestoreBinding.driveEmail.text =
            if (myEmail.isNullOrEmpty()) getString(R.string.select_gmail_account) else myEmail
    }

    /**
     * Query the User's Drive Files for Backup file availability
     */
    private suspend fun queryDriveFiles() {

        val fileIdOfBackupFile = driveHelper.queryFiles("Backup_")

        when {
            fileIdOfBackupFile.first == BackupConstants.DRIVE_BACKUP_FILE_QUERY_EXCEPTION -> {
                withContext(Dispatchers.Main.immediate) {
                    genericDialog?.dismiss()
                    LogMessage.e(TAG, "DRIVE_BACKUP_FILE_QUERY_EXCEPTION ")
                    showToast(fileIdOfBackupFile.second)
                }
            }
            fileIdOfBackupFile.first.isEmpty() -> {
                withContext(Dispatchers.Main.immediate) {
                    genericDialog?.dismiss()
                    LogMessage.e(TAG, "newUser")
                    setUpNoBackUpUI()
                    if (!isExisting) {
                        showToast(getString(R.string.no_backup_msg_available))
                    }
                }
            }
            fileIdOfBackupFile.first.isNotEmpty() -> {
                LogMessage.e(
                    TAG,
                    "fileIdOfBackupFile.first  ${fileIdOfBackupFile.first} "
                )
                getFileInfo(fileIdOfBackupFile.first)
            }
        }

    }

    private fun setUpNoBackUpUI() {
        activityRestoreBinding.mainTitle.text = getString(R.string.backup_not_found)
        activityRestoreBinding.lastBackupDate.text = getString(R.string.__dash)
        activityRestoreBinding.lastBackupSize.text = getString(R.string.size_)
        activityRestoreBinding.viewRestoreAnimationImage.visibility = View.VISIBLE
        activityRestoreBinding.animationBox.gone()
        activityRestoreBinding.initialBox.gone()
        activityRestoreBinding.newUserBox.show()
        activityRestoreBinding.newUserSkip.show()
        activityRestoreBinding.actionNext.show()
        activityRestoreBinding.finalBox.gone()
    }

    private fun setUpBackUpDataUI(backupData: Backup) {
        activityRestoreBinding.newUserBox.gone()
        activityRestoreBinding.mainTitle.text = getString(R.string.backup_found)
        activityRestoreBinding.lastBackupDate.text =
            TimeAgo.getTimeAgo(backupData.backupTime.toLong(), this)
        activityRestoreBinding.lastBackupSize.text =
            "Size: ${getFileSizeInStringFormat(backupData.backupSize.toLong())}"
        activityRestoreBinding.viewRestoreAnimationImage.visibility = View.VISIBLE
        activityRestoreBinding.animationBox.gone()
        activityRestoreBinding.initialBox.show()
        activityRestoreBinding.finalBox.gone()
    }

    /**
     * Provides the info about the backup file in Users's Google Drive
     *
     * @param fileId String id of the backup file
     */
    private suspend fun getFileInfo(id: String) {


        driveFile = driveHelper.getFile(id)

        LogMessage.e(
            TAG,
            "getFileInfo driveFile ${driveFile!!.name} \n ${driveFile!!.id} \n ${driveFile!!.size}"
        )
        LogMessage.e(TAG, "getFileInfo modifiedTime \n ${driveFile!!.createdTime} ")

        val lastBackupDate = parseAndReturnDateAsString(
            driveFile!!.createdTime.toString().replace("Z", "")
        ).toString()

        val backupData = Backup()
        backupData.id = driveFile!!.id
        backupData.backupSize = driveFile!!["size"].toString()
        backupData.backupType = "DRIVE"
        backupData.backupTime = lastBackupDate
        fileId = id
        fileName = driveFile!!.name
        Prefs.save(BackupConstants.BACKUP_FILE_SIZE, backupData.backupSize)
        Prefs.save(BackupConstants.BACKUP_FILE_DATE, backupData.backupTime)
        val backupInfo =
            BackupInfo(driveFile!!["size"].toString(), lastBackupDate, false, driveFile!!.id)
        Prefs.save(BackupConstants.BACKUP_INFO, Utils.getGSONInstance().toJson(backupInfo))

        withContext(Dispatchers.Main.immediate) {
            genericDialog?.dismiss()
            setUpBackUpDataUI(backupData)
        }
    }

    /**
     * Parse the date and return as time in milli seconds
     */
    @SuppressLint("SimpleDateFormat")
    private fun parseAndReturnDateAsString(cd: String): Long {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        input.timeZone = TimeZone.getTimeZone("UTC")
        val output = SimpleDateFormat("dd MMM yyyy | hh.mm aa")
        output.timeZone = TimeZone.getDefault()
        var finalTime = ""
        try {
            val date = input.parse(cd)
            finalTime = output.format(date)
            lastBackupTimeInLong = output.parse(finalTime).time
            return lastBackupTimeInLong
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        return 0L
    }

    /**
     * Initialization of Drive Downlaod worker and its the Observers
     *
     * @param id UUID of the worker
     */
    private fun initDriveListeners(id: UUID) {

        driveWorker = workManager.getWorkInfoByIdLiveData(id)

        driveWorker.observe(this, androidx.lifecycle.Observer {

            it?.let {

                val workerInfo = it

                when (workerInfo.state) {

                    WorkInfo.State.RUNNING -> {

                        val progressData = workerInfo.progress
                        val reason = progressData.getString(BackupConstants.REASON)

                        when (reason) {

                            MediaHttpDownloader.DownloadState.MEDIA_IN_PROGRESS.name -> {
                                val progressValue =
                                    progressData.getInt(BackupConstants.PROGRESS, 0)
                                if (progressValue > 0) {
                                    activityRestoreBinding.workProgress.progress = progressValue
                                    progressText.text =
                                        "Downloading : ${getFileSizeInStringFormat((fileSize / 100) * progressValue)}  of ${fileSizeString} (${progressValue}%)"
                                }
                                activityRestoreBinding.workProgress.progress = progressValue
                            }
                            MediaHttpDownloader.DownloadState.MEDIA_COMPLETE.name -> activityRestoreBinding.workProgress.progress =
                                100
                            BackupConstants.NO_DRIVE_BACKUP_FOLDER_AVAILABLE,
                            BackupConstants.NO_DRIVE_BACKUP_FILE_AVAILABLE -> {
                                showToast(getString(R.string.no_backup_msg))
                                goToProfile()
                            }
                            else ->
                                Log.d("RESTORE_ACTIVITY_DRIVE", "${reason}")

                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        LogMessage.d(TAG, "Drive Worker is ${it.state}")
                        Prefs.save(BackupConstants.SHOULD_SHOW_RESTORE, true)
                        resetUIInCaseOfFailure()
                    }
                    else ->
                        Log.d("RESTORE_ACTIVITY_DRIVE", "${workerInfo.state}")
                }
            }
        })
    }

    /**
     * Initialization of Restore Data worker and its the Observers
     *
     * @param id UUID of the worker
     */
    private fun initRestoreListener(id: UUID) {
        restoreWorker = workManager.getWorkInfoByIdLiveData(id)
        restoreWorker.observe(this, androidx.lifecycle.Observer {

            it?.let {

                val workerInfo = it
                when (workerInfo.state) {

                    WorkInfo.State.RUNNING -> {

                        val progressData = workerInfo.progress
                        val progressValue = progressData.getInt(BackupConstants.PROGRESS, 0)

                        activityRestoreBinding.workProgress.progress = progressValue

                        if (progressValue == 100) {
                            progressText?.text =
                                "${getString(R.string.restoring_msg)} ($progressValue%)"
                            progressText.text = getString(R.string.clean_up_data)
                        } else {
                            progressText?.text =
                                "${getString(R.string.restoring_msg)} ($progressValue%)"
                        }

                    }
                    WorkInfo.State.SUCCEEDED -> {
                        activityRestoreBinding.workProgress.progress = 100
                        goToProfile()
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        LogMessage.d(TAG, "Restore Worker is ${it.state}")
                        Prefs.save(BackupConstants.SHOULD_SHOW_RESTORE, true)
                        resetUIInCaseOfFailure()
                    }
                    else -> {
                        Log.d("RESTORE_ACTIVITY_REST", "${workerInfo.state}")
                    }
                }

            }

        })
    }

    private fun resetUIInCaseOfFailure() {
        progressText.text = getString(R.string.downloading)
        activityRestoreBinding.workProgress.progress = 0
        WorkManagerController.cancelRestoreWorkers()
        activityRestoreBinding.initialBox.show()
        activityRestoreBinding.finalBox.gone()
        activityRestoreBinding.workProgress.gone()
        activityRestoreBinding.progressText.gone()
        activityRestoreBinding.viewRestoreAnimationImage.show()
        activityRestoreBinding.animationBox.gone()
    }

    override fun onDialogClosed() {
        setFrequencyText()
    }
}