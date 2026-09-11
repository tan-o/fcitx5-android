package org.fcitx.fcitx5.android.input.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineVoiceSession(
    scope: CoroutineScope,
    private val status: (String) -> Unit,
    private val result: (String) -> Unit
) {
    @Volatile private var recording = true
    private val audioLock = Any()
    private var audio: AudioRecord? = null
    private val job: Job = scope.launch {
        try {
            check(OfflineSpeechModel.installed) { "请先在语音设置中下载 Qwen3-ASR 模型" }
            status("正在聆听粤语／普通话／英文，点击停止结束录音（最长 15 秒）")
            val samples = record()
            currentCoroutineContext().ensureActive()
            if (samples.size < 1600) { status("录音太短，请重试"); return@launch }
            status("正在本机识别…")
            val text = OfflineSpeechModel.transcribe(samples)
            currentCoroutineContext().ensureActive()
            if (text.isBlank()) status("没有识别到文字，请重试") else result(text)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status(e.localizedMessage ?: "语音识别失败") }
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "设备不支持 16 kHz 单声道录音" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        val output = FloatArray(16000 * 15)
        var count = 0
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            synchronized(audioLock) {
                audio = recorder
                if (recording) recorder.startRecording()
            }
            val buffer = ShortArray(1600)
            while (recording && count < output.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, output.size - count))
                if (n < 0) { check(!recording) { "录音失败：$n" }; break }
                for (i in 0 until n) output[count++] = buffer[i] / 32768f
            }
        } finally {
            synchronized(audioLock) {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                recorder.release()
                audio = null
            }
        }
        output.copyOf(count)
    }

    fun finishRecording() {
        recording = false
        synchronized(audioLock) { audio?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } }
    }
    fun cancel() { job.cancel(); finishRecording() }
}
