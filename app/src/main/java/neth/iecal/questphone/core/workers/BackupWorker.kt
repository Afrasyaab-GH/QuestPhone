package neth.iecal.questphone.core.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import neth.iecal.questphone.backed.repositories.AppWidgetConfigDao
import neth.iecal.questphone.backed.repositories.QuestRepository
import neth.iecal.questphone.backed.repositories.StatsRepository
import neth.iecal.questphone.backed.repositories.UserRepository
import neth.iecal.questphone.core.utils.BackupManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BackupWorkerEntryPoint {
        fun userRepository(): UserRepository
        fun questRepository(): QuestRepository
        fun statsRepository(): StatsRepository
        fun widgetConfigDao(): AppWidgetConfigDao
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                BackupWorkerEntryPoint::class.java
            )

            val userRepository = entryPoint.userRepository()
            val questRepository = entryPoint.questRepository()
            val statsRepository = entryPoint.statsRepository()
            val widgetConfigDao = entryPoint.widgetConfigDao()

            val settingsSp = applicationContext.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
            val password = settingsSp.getString("backup_password", null)

            val backupJson = BackupManager.createBackup(
                applicationContext,
                userRepository,
                questRepository,
                statsRepository,
                widgetConfigDao,
                password
            )

            // Save to backups directory
            val backupDir = File(applicationContext.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "auto_backup_$timestamp.json")
            backupFile.writeText(backupJson)

            // Keep only the last 5 auto-backups to prevent storage bloat
            val backupFiles = backupDir.listFiles()?.sortedBy { it.lastModified() }
            if (backupFiles != null && backupFiles.size > 5) {
                val deleteCount = backupFiles.size - 5
                for (i in 0 until deleteCount) {
                    backupFiles[i].delete()
                }
            }

            Log.i("BackupWorker", "Automatic weekly backup created successfully: ${backupFile.name}")
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Automatic backup worker execution failed", e)
            Result.failure()
        }
    }
}
