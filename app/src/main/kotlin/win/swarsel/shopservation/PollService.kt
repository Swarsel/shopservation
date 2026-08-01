package win.swarsel.shopservation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PollService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                AlarmPlayer.stop(this)
                Notifications.clearAlarm(this)
                return START_STICKY
            }
            ACTION_STOP -> {
                Store(this).watching = false
                AlarmPlayer.stop(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val store = Store(this)
        store.watching = true
        startForeground(Notifications.ID_WATCH, Notifications.watchNotification(this, store.lastStatus))
        AlarmWorker.schedule(this)

        if (loop == null) {
            loop = scope.launch {
                while (isActive) {
                    val result = runCatching { Poller.pollOnce(this@PollService) }
                        .onFailure { Log.w(TAG, "poll cycle failed", it) }
                        .getOrNull()
                    if (result != null) {
                        Notifications.updateWatch(this@PollService, result.status)
                        if (result.alarmed.isNotEmpty()) {
                            Notifications.fireAlarm(this@PollService, result.alarmed)
                        }
                    }
                    delay(Store(this@PollService).pollSeconds.toLong() * 1000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PollService"
        const val ACTION_STOP = "win.swarsel.shopservation.STOP"
        const val ACTION_STOP_ALARM = "win.swarsel.shopservation.STOP_ALARM"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PollService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PollService::class.java).setAction(ACTION_STOP))
        }

        fun stopAlarm(context: Context) {
            context.startService(Intent(context, PollService::class.java).setAction(ACTION_STOP_ALARM))
        }
    }
}
