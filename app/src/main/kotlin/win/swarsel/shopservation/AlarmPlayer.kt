package win.swarsel.shopservation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object AlarmPlayer {
    private const val TAG = "AlarmPlayer"

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var previousVolume: Int? = null

    val isPlaying: Boolean get() = player != null

    @Synchronized
    fun start(context: Context) {
        if (player != null) return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audio.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
        }.onFailure { Log.w(TAG, "could not raise alarm volume", it) }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.e(TAG, "alarm playback failed", it) }.getOrNull()

        startVibrate(context)
    }

    @Synchronized
    fun stop(context: Context) {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null

        vibrator?.cancel()
        vibrator = null

        previousVolume?.let { prev ->
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, prev, 0) }
        }
        previousVolume = null
    }

    private fun startVibrate(context: Context) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        vibrator = v
        val pattern = longArrayOf(0, 600, 400)
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
    }
}
