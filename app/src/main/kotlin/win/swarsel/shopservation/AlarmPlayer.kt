package win.swarsel.shopservation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

object AlarmPlayer {
    private const val TAG = "AlarmPlayer"

    private var player: MediaPlayer? = null
    private var ringtone: android.media.Ringtone? = null
    private var siren: Siren? = null
    private var vibrator: Vibrator? = null
    private var previousVolume: Int? = null

    val isPlaying: Boolean get() = player != null || siren != null || ringtone != null

    @Synchronized
    fun start(context: Context) {
        val store = Store(context)
        start(context, store.alarmSoundUri, store.alarmSoundLabel, store.alarmVolumePercent, store.alarmVibrate)
    }

    @Synchronized
    fun startReminder(context: Context) {
        val store = Store(context)
        start(context, store.reminderSoundUri, store.reminderSoundLabel, store.reminderVolumePercent, store.alarmVibrate)
    }

    @Synchronized
    fun start(context: Context, soundUri: String, soundLabel: String, volumePercent: Int, vibrate: Boolean) {
        if (isPlaying) return
        val store = Store(context)

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val wanted = (max * volumePercent / 100).coerceIn(1, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, wanted, 0) }
            .onFailure { Log.w(TAG, "could not set alarm volume", it) }

        if (soundUri.isBlank()) {
            siren = Siren().also { it.start() }
        } else {
            val uri = Uri.parse(soundUri)
            player = runCatching { buildPlayer(context, uri) }
                .onFailure { Log.w(TAG, "MediaPlayer failed for $uri, trying Ringtone", it) }
                .getOrNull()
            if (player == null) {
                ringtone = runCatching {
                    RingtoneManager.getRingtone(context, uri)?.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        play()
                    }
                }.onFailure { Log.e(TAG, "Ringtone playback failed for $uri", it) }.getOrNull()
            }
            if (player == null && ringtone == null) {
                store.lastSoundError = "could not play $soundLabel; used the siren instead"
                siren = Siren().also { it.start() }
            } else {
                store.lastSoundError = ""
            }
        }

        if (vibrate) startVibrate(context)
    }

    @Synchronized
    fun stop(context: Context) {
        player?.let { p ->
            runCatching {
                if (p.isPlaying) p.stop()
                p.release()
            }
        }
        player = null

        ringtone?.let { r -> runCatching { if (r.isPlaying) r.stop() } }
        ringtone = null

        siren?.stopAndJoin()
        siren = null

        vibrator?.cancel()
        vibrator = null

        previousVolume?.let { prev ->
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0) }
        }
        previousVolume = null
    }

    fun checkPlayable(context: Context, uri: Uri): String? {
        var p: MediaPlayer? = null
        val direct = try {
            p = buildPreparedPlayer(context, uri)
            null
        } catch (e: Exception) {
            e.message ?: e::class.java.simpleName
        } finally {
            runCatching { p?.release() }
        }
        if (direct == null) return null

        val viaRingtone = runCatching {
            RingtoneManager.getRingtone(context, uri) != null
        }.getOrDefault(false)
        return if (viaRingtone) null else direct
    }

    fun defaultSystemAlarmUri(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    private fun buildPreparedPlayer(context: Context, uri: Uri): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            setVolume(1f, 1f)
            prepare()
        }

    private fun buildPlayer(context: Context, uri: Uri): MediaPlayer =
        buildPreparedPlayer(context, uri).apply { start() }

    private fun startVibrate(context: Context) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator = v
        val pattern = longArrayOf(0, 600, 400)
        runCatching { v.vibrate(VibrationEffect.createWaveform(pattern, 0)) }
            .onFailure { Log.w(TAG, "vibrate failed", it) }
    }

    private class Siren {
        private val rate = 44100
        @Volatile private var running = false
        private var track: AudioTrack? = null
        private var worker: Thread? = null

        fun start() {
            running = true
            worker = thread(name = "shopservation-siren", isDaemon = true) {
                val t = runCatching { build() }.getOrNull() ?: return@thread
                track = t
                runCatching {
                    t.play()
                    val chunk = ShortArray(rate / 20)
                    var phase = 0.0
                    var n = 0L
                    while (running) {
                        for (i in chunk.indices) {
                            val secs = n.toDouble() / rate
                            val sweep = secs % 0.7 / 0.7
                            val freq = 700.0 + 1500.0 * sweep
                            phase += 2 * PI * freq / rate
                            if (phase > 2 * PI) phase -= 2 * PI
                            val square = if (sin(phase) >= 0) 1.0 else -1.0
                            val shaped = 0.72 * square + 0.28 * sin(phase)
                            chunk[i] = (shaped * Short.MAX_VALUE * 0.92).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                            n++
                        }
                        t.write(chunk, 0, chunk.size)
                    }
                }.onFailure { Log.e(TAG, "siren failed", it) }
                runCatching {
                    t.stop()
                    t.release()
                }
            }
        }

        fun stopAndJoin() {
            running = false
            worker?.runCatching { join(500) }
            worker = null
            track = null
        }

        private fun build(): AudioTrack {
            val min = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(rate / 5)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(min)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .apply { setVolume(AudioTrack.getMaxVolume()) }
        }
    }
}
