package com.mphone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallAudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentOutputFile: File? = null
    private var activeContext: Context? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    @Suppress("MissingPermission")
    fun startRecording(context: Context, callerName: String, callerNumber: String = ""): File? {
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            return null
        }

        activeContext = context.applicationContext

        // Configure AudioManager mode for in-call duplex voice recording
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val storageDir = File(context.getExternalFilesDir(null), "CallRecordings").apply {
                if (!exists()) mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cleanNum = ContactMerger.normalizePhoneNumber(callerNumber.ifBlank { callerName })
            val cleanName = ContactMerger.normalizePhoneNumber(callerName).ifBlank { "Caller" }
            val fileTag = cleanNum.ifBlank { cleanName }
            val outputFile = File(storageDir, "Call_REC_${fileTag}_$timestamp.wav")

            // Prioritize hardware microphone sources compatible with Xiaomi HyperOS & Android 14+
            val audioSources = intArrayOf(
                MediaRecorder.AudioSource.MIC,                  // 1: Primary Microphone
                MediaRecorder.AudioSource.VOICE_RECOGNITION,   // 6: Speech Recognition
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 7: Duplex Voice Communication
                MediaRecorder.AudioSource.CAMCORDER,            // 5: Camcorder Mic
                MediaRecorder.AudioSource.DEFAULT               // 0: System Default
            )

            // Prioritize cellular baseband hardware sample rates (16kHz HD VoLTE, 8kHz GSM, 44.1kHz)
            val sampleRates = intArrayOf(16000, 8000, 44100)

            var record: AudioRecord? = null
            var selectedSampleRate = 16000
            var selectedBufferSize = 4096

            for (rate in sampleRates) {
                val minBuf = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf > 0) {
                    val bufSize = maxOf(minBuf, 4096)
                    for (source in audioSources) {
                        try {
                            val candidate = AudioRecord(
                                source,
                                rate,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufSize
                            )
                            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                                record = candidate
                                selectedSampleRate = rate
                                selectedBufferSize = bufSize
                                break
                            }
                        } catch (_: Exception) {
                        }
                    }
                    if (record != null) break
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                return null
            }

            record.startRecording()
            isRecording = true
            currentOutputFile = outputFile
            audioRecord = record

            recordingThread = Thread {
                writeRealVoicePcmToWav(outputFile, record, selectedBufferSize, selectedSampleRate)
            }.apply { start() }

            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return null
        }
    }

    private fun writeRealVoicePcmToWav(outputFile: File, record: AudioRecord, bufferSize: Int, sampleRate: Int) {
        val data = ByteArray(bufferSize)
        var totalAudioLen = 0

        try {
            RandomAccessFile(outputFile, "rw").use { raf ->
                // Write placeholder 44-byte WAV header
                raf.write(ByteArray(44))

                while (isRecording) {
                    val read = record.read(data, 0, bufferSize)
                    if (read > 0) {
                        // Apply 5.0x PCM digital gain boost to amplify spoken in-call voice
                        var i = 0
                        while (i < read - 1) {
                            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                            val shortSample = sample.toShort()
                            val amplified = (shortSample * 5.0f).toInt().coerceIn(-32768, 32767).toShort()
                            data[i] = (amplified.toInt() and 0x00FF).toByte()
                            data[i + 1] = ((amplified.toInt() and 0xFF00) ushr 8).toByte()
                            i += 2
                        }
                        raf.write(data, 0, read)
                        totalAudioLen += read
                    }
                }

                // Rewrite final WAV header with actual recorded voice PCM length
                raf.seek(0)
                writeWavHeaderToRaf(raf, totalAudioLen, sampleRate)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording(): File? {
        val file = currentOutputFile
        isRecording = false

        try {
            audioRecord?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                release()
            }
            recordingThread?.join(2000)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            recordingThread = null
            isRecording = false
            currentOutputFile = null
        }

        // Restore AudioManager mode
        activeContext?.let { ctx ->
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                audioManager?.mode = AudioManager.MODE_IN_CALL
            } catch (_: Exception) {}
        }
        activeContext = null

        if (file == null || !file.exists() || file.length() <= 44L) {
            return null
        }

        return file
    }

    private fun writeWavHeaderToRaf(raf: RandomAccessFile, totalAudioLen: Int, sampleRate: Int) {
        val header = buildWavHeaderBytes(totalAudioLen, sampleRate)
        raf.write(header, 0, 44)
    }

    private fun buildWavHeaderBytes(totalAudioLen: Int, sampleRate: Int): ByteArray {
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * channels).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        return header
    }
}
