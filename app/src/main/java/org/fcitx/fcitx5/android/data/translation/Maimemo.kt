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
        val token = value.trim().removePrefix("Bearer ").trim()
        require(token.isNotEmpty()) { "Token 不能为空" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        keyFile.writeText(Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "\n" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
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
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(body) }.getOrElse { error("墨墨返回了无法解析的响应（HTTP $code）") }
            val error = root.optJSONArray("errors")?.optJSONObject(0)?.optString("msg").orEmpty()
            check(code in 200..299 && root.optBoolean("success")) {
                error.ifBlank { "墨墨 HTTP $code" }
            }
            return root.getJSONObject("data")
        } finally { connection.disconnect() }
    }
    suspend fun lookup(spelling: String): String = withContext(Dispatchers.IO) {
        val query = spelling.trim()
        require(query.matches(Regex("[A-Za-z][A-Za-z .'-]{0,63}"))) { "请输入英文单词或短语" }
        val voc = request("vocabulary?spelling=${URLEncoder.encode(query, "UTF-8")}").getJSONObject("voc")
        val id = URLEncoder.encode(voc.getString("id"), "UTF-8")
        val interpretations = request("interpretations?voc_id=$id").getJSONArray("interpretations")
        val phrases = request("phrases?voc_id=$id").getJSONArray("phrases")
        val notes = request("notes?voc_id=$id").getJSONArray("notes")
        val sections = mutableListOf<String>()
        (0 until interpretations.length()).mapNotNull { index ->
            interpretations.optJSONObject(index)?.optString("interpretation")?.takeIf { it.isNotBlank() }
        }.take(6).takeIf { it.isNotEmpty() }?.let { sections += "释义\n${it.joinToString("\n")}" }
        (0 until phrases.length()).mapNotNull { index ->
            phrases.optJSONObject(index)?.let { item ->
                val phrase = item.optString("phrase")
                val meaning = item.optString("interpretation")
                phrase.takeIf { it.isNotBlank() }?.let { if (meaning.isBlank()) it else "$it\n$meaning" }
            }
        }.take(4).takeIf { it.isNotEmpty() }?.let { sections += "例句\n${it.joinToString("\n\n")}" }
        (0 until notes.length()).mapNotNull { index ->
            notes.optJSONObject(index)?.let { item ->
                val text = item.optString("note")
                text.takeIf { it.isNotBlank() }?.let { note -> item.optString("note_type").takeIf { it.isNotBlank() }?.let { "$it：$note" } ?: note }
            }
        }.take(4).takeIf { it.isNotEmpty() }?.let { sections += "助记\n${it.joinToString("\n")}" }
        "${voc.getString("spelling")}\n\n" + sections.joinToString("\n\n").ifBlank { "已找到词条，暂无可显示的释义、例句或助记。" }
    }
}
