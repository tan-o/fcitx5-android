package org.fcitx.fcitx5.android.data.rime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object WanxiangModel {
    private const val fileName = "wanxiang-lts-zh-hans.gram"
    val installed: Boolean get() = File(RimeManager.userDir, fileName).isFile
    val installedSize: Long get() = File(RimeManager.userDir, fileName).length()
    suspend fun update(progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val metadata = URL("https://api.github.com/repos/amzxyz/RIME-LMDG/releases/tags/LTS").openConnection() as HttpsURLConnection
        val asset = try {
            metadata.connectTimeout = 15000
            metadata.readTimeout = 30000
            check(metadata.responseCode == 200) { "无法查询模型版本：HTTP ${metadata.responseCode}" }
            val assets = JSONObject(metadata.inputStream.bufferedReader().use { it.readText() }).getJSONArray("assets")
            (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name") == fileName }
        } finally { metadata.disconnect() }
        val digest = asset.getString("digest").removePrefix("sha256:")
        check(digest.matches(Regex("[a-f0-9]{64}"))) { "上游没有提供 SHA256" }
        val size = asset.getLong("size")
        check(size > 0) { "上游模型大小无效" }
        withContext(Dispatchers.Main) { progress("下载模型 0%（${size / 1024 / 1024} MiB）") }
        val downloadUrl = URL(asset.getString("browser_download_url"))
        check(downloadUrl.protocol == "https" && downloadUrl.host == "github.com")
        val temporary = File(RimeManager.userDir, "$fileName.downloading")
        val connection = downloadUrl.openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            check(connection.responseCode == 200) { "下载失败：HTTP ${connection.responseCode}" }
            val hash = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            connection.inputStream.use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    received += n
                    check(received <= size) { "模型大小不符" }
                    hash.update(buffer, 0, n)
                    output.write(buffer, 0, n)
                    if (received - reported >= 8 * 1024 * 1024) {
                        reported = received
                        withContext(Dispatchers.Main) { progress("下载模型 ${received * 100 / size}%（${received / 1024 / 1024}/${size / 1024 / 1024} MiB）") }
                    }
                }
            } }
            check(received == size && hash.digest().joinToString("") { "%02x".format(it) } == digest) { "模型完整性校验失败" }
            check(temporary.renameTo(File(RimeManager.userDir, fileName))) { "无法安装模型" }
            RimeManager.redeploy()
        } finally { connection.disconnect(); temporary.delete() }
    }
}
