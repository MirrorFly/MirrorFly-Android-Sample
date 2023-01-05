package com.contusfly.backup.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.contus.flycommons.LogMessage
import com.contus.flycommons.Prefs
import com.contusfly.backup.BackupConstants
import com.contusfly.backup.WorkManagerController
import com.contusfly.emptyString

/**
 *
 * Worker Class for cleaning backed up and restored data file
 */
class CleanUpWorker(private val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {

        return try {
            LogMessage.e("CleanUpWorker","doWork")
            val fileToBeDeleted = Prefs.getString(BackupConstants.BACKUP_FILE_PATH)
            LogMessage.e("CleanUpWorker","doWork fileToBeDeleted $fileToBeDeleted")
            setProgress(workDataOf(BackupConstants.PROGRESS to 0))

            fileToBeDeleted.let {
                try {
                    LogMessage.e("CleanUpWorker","doWork fileToBeDeleted != null")
                    WorkManagerController.deleteAFileByPath(fileToBeDeleted)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            setProgress(workDataOf(BackupConstants.PROGRESS to 100))
            Prefs.save(BackupConstants.BACKUP_FILE_PATH, emptyString())

            LogMessage.e("CleanUpWorker"," fileToBeDeleted deleted yes")
            return Result.success()

        } catch (error: Throwable) {
            error.printStackTrace()
            Result.failure()
        }

    }

}