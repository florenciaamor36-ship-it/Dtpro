package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundEffectHelper {
    private const val PREFS_NAME = "dtunnel_sound_prefs"
    private const val KEY_SOUNDS_ENABLED = "pref_sounds_enabled"

    private val scope = CoroutineScope(Dispatchers.Default)

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUNDS_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, enabled).apply()
    }

    /**
     * Tono futurista/cyber de conexión exitosa (2 tonos armónicos ascendentes)
     */
    fun playConnectSound(context: Context? = null) {
        if (context != null && !isSoundEnabled(context)) return
        scope.launch {
            try {
                playToneSweep(startFreq = 587.33, endFreq = 880.0, durationMs = 120) // D5 a A5
                kotlinx.coroutines.delay(60)
                playToneSweep(startFreq = 880.0, endFreq = 1174.66, durationMs = 180) // A5 a D6
            } catch (_: Exception) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Tono de desconexión (tono descendente suave)
     */
    fun playDisconnectSound(context: Context? = null) {
        if (context != null && !isSoundEnabled(context)) return
        scope.launch {
            try {
                playToneSweep(startFreq = 880.0, endFreq = 440.0, durationMs = 160) // A5 a A4 descendente
            } catch (_: Exception) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Tono de error / bloqueo
     */
    fun playErrorSound(context: Context? = null) {
        if (context != null && !isSoundEnabled(context)) return
        scope.launch {
            try {
                playToneSweep(startFreq = 330.0, endFreq = 220.0, durationMs = 200)
            } catch (_: Exception) {
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 180)
                } catch (_: Exception) {}
            }
        }
    }

    private fun playToneSweep(startFreq: Double, endFreq: Double, durationMs: Int) {
        val sampleRate = 44100
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val generatedSnd = ByteArray(2 * numSamples)

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            val angle = 2.0 * Math.PI * i / (sampleRate / currentFreq)
            
            // Envelope suave para evitar "clics" de audio al inicio y fin
            val envelope = when {
                progress < 0.1 -> progress / 0.1
                progress > 0.8 -> (1.0 - progress) / 0.2
                else -> 1.0
            }

            val sample = (sin(angle) * 0.7 * envelope * Short.MAX_VALUE).toInt().toShort()
            generatedSnd[2 * i] = (sample.toInt() and 0x00ff).toByte()
            generatedSnd[2 * i + 1] = (sample.toInt() and 0xff00 ushr 8).toByte()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(generatedSnd.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(generatedSnd, 0, generatedSnd.size)
        audioTrack.play()
        
        Thread.sleep(durationMs.toLong() + 30)
        audioTrack.stop()
        audioTrack.release()
    }
}
