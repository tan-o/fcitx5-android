package org.fcitx.fcitx5.android.data.translation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fcitx.fcitx5.android.utils.appContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

object Maimemo {
    private val keyFile get() = File(appContext.noBackupFilesDir, "maimemo-token")
    val configured get() = keyFile.isFile
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("maimemo", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("maimemo", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveToken(value: String) {
        if (value.isBlank()) { keyFile.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        keyFile.writeText(Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" +
            Base64.encodeToString(cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
    }
    private fun token(): String {
        check(configured) { "请先设置墨墨开放 API Token" }
        val parts = keyFile.readLines()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
    private fun request(path: String): JSONObject {
        val connection = URL("https://open.maimemo.com/open/api/v1/memo/$path").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer ${token()}")
            check(connection.responseCode in 200..299) { "墨墨 HTTP ${connection.responseCode}" }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun lookup(spelling: String): String = withContext(Dispatchers.IO) {
        val voc = request("vocabulary?spelling=${URLEncoder.encode(spelling.trim(), "UTF-8")}").getJSONObject("voc")
        val items = request("interpretations?voc_id=${URLEncoder.encode(voc.getString("id"), "UTF-8")}").getJSONArray("interpretations")
        val meanings = (0 until items.length()).joinToString("\n\n") { items.getJSONObject(it).getString("interpretation") }
        "${voc.getString("spelling")}\n\n" + meanings.ifBlank { "已找到单词，暂无你创建的释义。墨墨开放 API 不提供完整词典释义。" }
    }
}
