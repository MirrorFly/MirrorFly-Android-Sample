package com.contusfly.backup.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contus.flycommons.Constants
import com.contus.flycommons.LogMessage
import com.contus.flycommons.Prefs
import com.contusfly.backup.BackupConstants
import com.contusfly.backup.WorkManagerController
import com.contusflysdk.backup.BackupListener
import com.contusflysdk.backup.BackupManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


/**
 * Worker Class for managing Backup
 */
class BackUpDataWorker(val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    var filePath: String = ""

    private val isAutoBackup by lazy {
        inputData.getBoolean(BackupConstants.IS_AUTO_BACKUP, false)
    }
    private val isPeriodic by lazy {
        inputData.getBoolean(BackupConstants.IS_PERIODIC, false)
    }

    override suspend fun doWork(): Result {
        try {
            BackupManager.startBackup(object : BackupListener {
                override fun onFailure(reason: String) {
                    LogMessage.e(
                        "BackUpDataWorker",
                        "BackupManager.startBackup() onFailure() reason $reason"
                    )
                    WorkManagerController.retryAttemptLogic(runAttemptCount)
                }

                override fun onProgressChanged(percentage: Int) {
                    LogMessage.e(
                        "BackUpDataWorker",
                        "BackupManager.startBackup() onProgressChanged() percentage $percentage"
                    )
                    GlobalScope.launch{
                        setProgress(workDataOf(BackupConstants.PROGRESS to percentage))
                    }
                }

                override fun onSuccess(backUpFilePath: String) {
                    LogMessage.e(
                        "BackUpDataWorker",
                        "BackupManager.startBackup() onSuccess() backUpFilePath $backUpFilePath"
                    )
                    filePath = backUpFilePath
                    Prefs.save(BackupConstants.BACKUP_FILE_PATH, filePath)
                    onBackupSuccess()
                }
            })


        } catch (error: Throwable) {
            LogMessage.e(error)
            WorkManagerController.retryAttemptLogic(runAttemptCount)
        } finally {
            return Result.success(
                Data.Builder()
                    .putString(BackupConstants.BACKUP_FILE_PATH, filePath)
                    .putBoolean(BackupConstants.IS_UPLOAD, true)
                    .putBoolean(BackupConstants.IS_AUTO_BACKUP, isAutoBackup)
                    .build()
            )
        }

    }

    private fun onBackupSuccess() {
        if (!isAutoBackup)
            LogMessage.e("BackUpDataWorker", " !isAutoBackup filePath $filePath")
        else if (isPeriodic) {
            LogMessage.e("BackUpDataWorker", " isPeriodic isPeriodic $isPeriodic")
            if (Prefs.getString(BackupConstants.BACKUP_TYPE) == Constants.DRIVE_BACKUP) {
                GlobalScope.launch {
                    if (!WorkManagerController.checkIfAWorkerIsAlreadyScheduledOrNot(
                            BackupConstants.DRIVE_WORKER_TAG
                        )
                    ) {
                        WorkManagerController.runDriveUpload()
                    }
                }

            }
        } else LogMessage.e("BackUpDataWorker", " isPeriodic isPeriodic $isPeriodic")
    }
}