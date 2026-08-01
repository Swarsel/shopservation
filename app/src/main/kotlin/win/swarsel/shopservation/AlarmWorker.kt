package win.swarsel.shopservation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class AlarmWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        if (!store.watching) return Result.success()

        val result = runCatching { Poller.pollOnce(applicationContext) }.getOrNull()
        if (result != null && result.alarmed.isNotEmpty()) {
            Notifications.fireAlarm(applicationContext, result.alarmed)
        }

        runCatching { PollService.start(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "shopservation-poll"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<AlarmWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.UPDATE, req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
