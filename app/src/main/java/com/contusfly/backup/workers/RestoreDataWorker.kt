package com.contusfly.backup.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.contus.flycommons.LogMessage
import com.contusfly.backup.BackupConstants
import com.contusfly.backup.WorkManagerController
import com.contusflysdk.backup.RestoreListener
import com.contusflysdk.backup.RestoreManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class RestoreDataWorker(private val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {

        return try {

            val filePath = inputData.getString(BackupConstants.BACKUP_FILE_PATH)!!

            filePath.let {
                val file  = File(it)
                RestoreManager.restoreData(file,object:RestoreListener{
                    override fun onFailure(reason: String) {
                        LogMessage.e(
                            "RestoreDataWorker",
                            "RestoreManager.restoreData() onFailure() reason $reason"
                        )
                        WorkManagerController.retryAttemptLogic(runAttemptCount)
                    }

                    override fun onProgressChanged(percentage: Int) {
                        LogMessage.e(
                            "RestoreDataWorker",
                            "RestoreManager.restoreData() onProgressChanged() percentage $percentage"
                        )
                        GlobalScope.launch{
                            setProgress(workDataOf(BackupConstants.PROGRESS to percentage))
                        }
                    }

                    override fun onSuccess() {
                        LogMessage.e(
                            "RestoreDataWorker",
                            "RestoreManager.restoreData() onSuccess() "
                        )
                    }

                })
            }
            Result.success(Data.Builder().putString(BackupConstants.BACKUP_FILE_PATH, filePath).build())
        } catch (error: Throwable) {
            LogMessage.e(error)
            Result.failure()
        }

    }

}