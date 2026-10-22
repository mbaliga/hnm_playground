package dev.hnm.workbench.android

import android.content.Context
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Prepares a short mono clip in static mode. Starting output is separate so the session owner
 * can finish preparation before scheduling haptics. Physical onset still needs measurement.
 */
class AudioPlayer {
    private var manager: AudioManager? = null
    private var focus: AudioFocusRequest? = null
    fun requestFocus(context: Context, onLoss: () -> Unit) {
        if(current == null) return
        val audioManager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { change -> if(change < 0) onLoss() }
            .setWillPauseWhenDucked(true).build()
        check(audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Sound output is busy. Try again or preview without sound." }
        manager=audioManager;focus=request
    }
    private var current: AudioTrack? = null

    fun prepare(floats: FloatArray, sampleRate: Int) {
        release()
        if (floats.isEmpty()) return
        val pcm = ShortArray(floats.size) { i ->
            (floats[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val bytes = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        current = track
        try {
            // MODE_STATIC is valid in STATE_NO_STATIC_DATA until the first successful write.
            check(track.state != AudioTrack.STATE_UNINITIALIZED) { "Audio output could not be initialized" }
            check(track.write(pcm, 0, pcm.size) == pcm.size) { "Audio output could not load the complete effect" }
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Audio output did not become ready" }
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    fun start() { current?.play() }

    fun release() {
        focus?.let { manager?.abandonAudioFocusRequest(it) }; focus=null;manager=null
        current?.let {
            runCatching { it.stop() }
            it.release()
        }
        current = null
    }
}
