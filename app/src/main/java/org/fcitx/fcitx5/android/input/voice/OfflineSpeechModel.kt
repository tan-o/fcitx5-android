package org.fcitx.fcitx5.android.input.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.fcitx.fcitx5.android.utils.appContext
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

object OfflineSpeechModel {
    private const val name = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
    private const val size = 878702423L
    private const val sha256 = "393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96"
    private val lock = Mutex()
    private val directory get() = File(appContext.noBackupFilesDir, "qwen3-asr")
    private val files = listOf("conv_frontend.onnx", "encoder.int8.onnx", "decoder.int8.onnx",
        "tokenizer/merges.txt", "tokenizer/vocab.json", "tokenizer/tokenizer_config.json")
    val installed get() = File(directory, "verified").isFile && files.all { File(directory, it).isFile }

    suspend fun download(progress: (Long, Long, String) -> Unit) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (installed) return@withLock
            check(appContext.noBackupFilesDir.usableSpace > 2L * 1024 * 1024 * 1024) { "下载和解压需要至少 2 GiB 可用空间" }
            val archive = File(appContext.noBackupFilesDir, "$name.downloading")
            val stage = File(appContext.noBackupFilesDir, "qwen3-asr-installing")
            stage.deleteRecursively()
            check(stage.mkdirs())
            val connection = URL("https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$name.tar.bz2").openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                check(connection.responseCode == 200) { "下载失败：HTTP ${connection.responseCode}" }
                val hash = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var reported = 0L
                withContext(Dispatchers.Main) { progress(0, size, "下载语音模型") }
                connection.inputStream.use { input -> archive.outputStream().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        received += n
                        check(received <= size) { "模型大小不符" }
                        hash.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        if (received - reported >= 1024 * 1024 || received == size) {
                            reported = received
                            withContext(Dispatchers.Main) { progress(received, size, "下载语音模型") }
                        }
                    }
                } }
                check(received == size && hash.digest().joinToString("") { "%02x".format(it) } == sha256) { "语音模型完整性校验失败" }
                withContext(Dispatchers.Main) { progress(0, 0, "正在解压模型…") }
                TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
                    val buffer = ByteArray(1024 * 1024)
                    var expanded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = tar.nextEntry ?: break
                        val path = entry.name.removePrefix("$name/")
                        if (path !in files) continue
                        check(entry.isFile && !entry.isSymbolicLink && !entry.isLink)
                        val output = File(stage, path)
                        output.parentFile!!.mkdirs()
                        output.outputStream().use { stream ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = tar.read(buffer)
                                if (n < 0) break
                                expanded += n
                                check(expanded <= 1200L * 1024 * 1024) { "模型解压大小异常" }
                                stream.write(buffer, 0, n)
                            }
                        }
                    }
                }
                check(files.all { File(stage, it).length() > 0 }) { "语音模型文件不完整" }
                File(stage, "verified").writeText(sha256)
                check(!directory.exists() || directory.deleteRecursively())
                check(stage.renameTo(directory)) { "无法安装语音模型" }
            } finally { connection.disconnect(); archive.delete(); stage.deleteRecursively() }
        }
    }

    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            check(installed) { "请先在语音设置中下载 Qwen3-ASR 模型" }
            fun path(file: String) = File(directory, file).absolutePath
            val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(featureDim = 128),
                modelConfig = OfflineModelConfig(qwen3Asr = OfflineQwen3AsrModelConfig(
                    convFrontend = path("conv_frontend.onnx"), encoder = path("encoder.int8.onnx"),
                    decoder = path("decoder.int8.onnx"), tokenizer = path("tokenizer"),
                    maxTotalLen = 512, maxNewTokens = 128, hotwords = VoiceEngine.hotwords
                ), numThreads = 2, provider = "cpu")
            ))
            try {
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(samples, 16000)
                    recognizer.decode(stream)
                    currentCoroutineContext().ensureActive()
                    recognizer.getResult(stream).text
                } finally { stream.release() }
            } finally { recognizer.release() }
        }
    }
}
