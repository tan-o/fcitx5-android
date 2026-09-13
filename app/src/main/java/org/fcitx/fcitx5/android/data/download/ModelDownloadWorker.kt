package org.fcitx.fcitx5.android.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.data.rime.RimeManager
import org.fcitx.fcitx5.android.utils.appContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val HAND = "handwriting"
        const val WANXIANG = "wanxiang"
        fun enqueue(kind: String): Operation {
            require(kind == HAND || kind == WANXIANG)
            return WorkManager.getInstance(appContext).enqueueUniqueWork("model-$kind", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                    .setInputData(workDataOf("kind" to kind))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        val handwritingFile get() = File(appContext.noBackupFilesDir, "models/ppocrv6-small.onnx")
    }
    private val kind get() = inputData.getString("kind")!!
    private val title get() = if (kind == HAND) "手写模型" else "万象模型"
    private suspend fun progress(received: Long, total: Long, text: String) {
        val percent = if (total > 0) (received * 100 / total).toInt() else 0
        setProgress(workDataOf("received" to received, "total" to total, "text" to text))
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("model-downloads", "模型下载", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "model-downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, percent, total <= 0)
            .addAction(0, "暂停", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        setForeground(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7100 + if (kind == HAND) 0 else 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(7100 + if (kind == HAND) 0 else 1, notification))
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            progress(0, 0, "准备下载；关闭设置不影响任务")
            val url: String
            val digest: String
            val size: Long
            val destination: File
            if (kind == HAND) {
                url = "https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.9.2/onnx/PP-OCRv6/rec/PP-OCRv6_rec_small.onnx"
                digest = "6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884"
                size = 21234383L
                destination = handwritingFile
            } else {
                require(kind == WANXIANG)
                val connection = URL("https://api.github.com/repos/amzxyz/RIME-LMDG/releases/tags/LTS").openConnection() as HttpsURLConnection
                val asset = try {
                    connection.connectTimeout = 15000; connection.readTimeout = 30000
                    check(connection.responseCode == 200) { "无法查询万象版本：${connection.responseCode}" }
                    val assets = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("assets")
                    (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name") == "wanxiang-lts-zh-hans.gram" }
                } finally { connection.disconnect() }
                url = asset.getString("browser_download_url")
                require(URL(url).protocol == "https" && URL(url).host == "github.com")
                digest = asset.getString("digest").removePrefix("sha256:")
                size = asset.getLong("size")
                destination = File(RimeManager.userDir, "wanxiang-lts-zh-hans.gram")
            }
            require(size > 0 && digest.matches(Regex("[a-f0-9]{64}")))
            val partial = File(applicationContext.noBackupFilesDir, "downloads/$digest.part")
            partial.parentFile!!.mkdirs()
            var received = partial.length()
            if (received > size) { check(partial.delete()); received = 0 }
            if (received < size) {
                val connection = URL(url).openConnection() as HttpsURLConnection
                try {
                    connection.connectTimeout = 15000; connection.readTimeout = 30000
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    if (received > 0) connection.setRequestProperty("Range", "bytes=$received-")
                    val code = connection.responseCode
                    if (code == 429 || code >= 500) throw IOException("下载服务暂不可用：$code")
                    check(code == 200 || code == 206) { "下载失败：HTTP $code" }
                    if (code == 206) {
                        val range = connection.getHeaderField("Content-Range").orEmpty()
                        check(range.startsWith("bytes $received-") && range.substringAfterLast('/') == size.toString()) { "续传响应范围不符" }
                    } else received = 0
                    var last = System.nanoTime()
                    connection.inputStream.use { input -> java.io.FileOutputStream(partial, received > 0).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n); received += n
                            check(received <= size) { "模型大小不符" }
                            if (System.nanoTime() - last > 1_000_000_000 || received == size) {
                                last = System.nanoTime()
                                progress(received, size, "${received * 100 / size}% · ${received / 1024 / 1024}/${size / 1024 / 1024} MiB")
                            }
                        }
                    } }
                } finally { connection.disconnect() }
            }
            if (received != size) throw IOException("连接中断，将从已下载位置续传")
            progress(0, 0, "正在校验并安装…")
            val hash = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    hash.update(buffer, 0, n)
                }
            }
            if (hash.digest().joinToString("") { "%02x".format(it) } != digest) {
                partial.delete()
                error("校验失败，请重新下载")
            }
            destination.parentFile!!.mkdirs()
            val stage = File(destination.parentFile, "${destination.name}.installing")
            try {
                partial.copyTo(stage, overwrite = true)
                currentCoroutineContext().ensureActive()
                if (destination.exists()) check(destination.delete()) { "无法替换旧模型" }
                check(stage.renameTo(destination)) { "安装失败" }
            } finally { stage.delete() }
            partial.delete()
            if (kind == WANXIANG) RimeManager.redeploy()
            Result.success(workDataOf("text" to "$title 已安装"))
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            if (runAttemptCount < 8) Result.retry() else Result.failure(workDataOf("text" to "${e.message}；点击下载可续传"))
        } catch (e: Exception) { Result.failure(workDataOf("text" to (e.message ?: "下载失败"))) }
    }
}
