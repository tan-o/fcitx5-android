package org.fcitx.fcitx5.android.data.rime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.fcitx.fcitx5.android.utils.appContext
import java.security.MessageDigest

class RimeGitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val TAG = "rime-git"
        private const val OP_CLONE = "clone"
        private const val OP_UPDATE = "update"

        fun enqueueClone(url: String): String {
            val normalized = url.trim()
            RimeManager.repositoryFor(normalized)
            val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
            return enqueue(
                "rime-git-clone-$hash",
                workDataOf("operation" to OP_CLONE, "url" to normalized)
            )
        }

        fun enqueueUpdate(repositoryName: String): String {
            RimeManager.repository(repositoryName)
            return enqueue(
                "rime-git-update-$repositoryName",
                workDataOf("operation" to OP_UPDATE, "repository" to repositoryName)
            )
        }

        fun workName(tags: Set<String>): String? = tags.firstOrNull { it.startsWith("$TAG:") }
            ?.substringAfter(':')

        private fun enqueue(workName: String, input: androidx.work.Data): String {
            val request = OneTimeWorkRequestBuilder<RimeGitWorker>()
                .setInputData(input)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .addTag(TAG)
                .addTag("$TAG:$workName")
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                request
            )
            return workName
        }
    }

    private var lastTask = ""
    private var lastReport = 0L

    override suspend fun doWork(): Result {
        return try {
            report(RimeManager.GitProgress("等待连接远端", 0, 0), force = true)
            when (inputData.getString("operation")) {
                OP_CLONE -> {
                    val url = requireNotNull(inputData.getString("url"))
                    val existing = RimeManager.repositoryFor(url)
                    val repository = if (existing.resolve(".git").isDirectory) {
                        RimeManager.update(existing, ::report)
                        existing
                    } else {
                        RimeManager.clone(url, ::report)
                    }
                    report(RimeManager.GitProgress("正在部署并启用方案", 0, 0), force = true)
                    RimeManager.deploy(repository)
                }
                OP_UPDATE -> {
                    val repository = RimeManager.repository(
                        requireNotNull(inputData.getString("repository"))
                    )
                    RimeManager.update(repository, ::report)
                    report(RimeManager.GitProgress("正在重新部署方案", 0, 0), force = true)
                    RimeManager.deploy(repository)
                }
                else -> error("未知的 Rime Git 操作")
            }
            Result.success(workDataOf("text" to "方案已部署并启用"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf("text" to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private fun report(progress: RimeManager.GitProgress) = report(progress, force = false)

    private fun report(progress: RimeManager.GitProgress, force: Boolean) {
        val now = System.nanoTime()
        val completed = progress.total > 0 && progress.completed >= progress.total
        if (!force && progress.task == lastTask && !completed && now - lastReport < 250_000_000) return
        lastTask = progress.task
        lastReport = now
        val text = if (progress.total > 0) {
            val percent = (progress.completed.toLong() * 100 / progress.total).coerceIn(0, 100)
            "${progress.task} · $percent%"
        } else progress.task
        val data = workDataOf(
            "task" to progress.task,
            "completed" to progress.completed,
            "total" to progress.total,
            "text" to text
        )
        setProgressAsync(data)
        setForegroundAsync(foregroundInfo(text, progress.completed, progress.total))
    }

    private fun foregroundInfo(text: String, completed: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("rime-git", "Rime 方案任务", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val percent = if (total > 0) {
            (completed.toLong() * 100 / total).toInt().coerceIn(0, 100)
        } else 0
        val notification = NotificationCompat.Builder(applicationContext, "rime-git")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Rime 方案")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, total <= 0)
            .addAction(
                0,
                "取消",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()
        val notificationId = 7300 + (id.hashCode() and 0x3ff)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
