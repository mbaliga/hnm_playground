package dev.hnm.workbench.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.hnm.workbench.core.playback.FloatStream
import dev.hnm.workbench.core.playback.readAll

/**
 * Plays a `core` audio [FloatStream] through an [AudioTrack] in static mode (the clips are short). The
 * mono float stream is converted to 16-bit PCM; `USAGE_MEDIA` lets it mix with the haptic so the two
 * land together.
 */
class AudioPlayer {
    private var current: AudioTrack? = null

    fun play(stream: FloatStream) {
        release()
        val sampleRate = stream.sampleRate
        val floats = stream.readAll()
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
        track.write(pcm, 0, pcm.size)
        track.play()
        current = track
    }

    fun release() {
        current?.let {
            runCatching { it.stop() }
            it.release()
        }
        current = null
    }
}
