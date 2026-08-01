package win.swarsel.shopservation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    const val CHANNEL_WATCH = "watch"
    const val CHANNEL_ALARM = "alarm"
    const val ID_WATCH = 1
    const val ID_ALARM = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                "Watching",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Ongoing notification while shopservation polls for new finds" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Fires when a find matches one of your alarm rules"
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
        )
    }

    fun watchNotification(context: Context, status: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setContentTitle("shopservation is watching")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    fun updateWatch(context: Context, status: String) {
        context.getSystemService(NotificationManager::class.java)
            .notify(ID_WATCH, watchNotification(context, status))
    }

    fun fireAlarm(context: Context, hits: List<Listing>) {
        ensureChannels(context)
        val first = hits.first()
        val title = if (hits.size == 1) "Match: ${first.title}" else "${hits.size} matching finds!"
        val text = buildString {
            append(first.source)
            if (first.price > 0) append(" · ${Rule.fmtPrice(first.price)} ${first.currency}".trimEnd())
            if (hits.size > 1) append(" (+${hits.size - 1} more)")
        }

        val fullScreen = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val fullScreenPi = PendingIntent.getActivity(
            context, 0, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            context, 1,
            Intent(context, PollService::class.java).setAction(PollService.ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(ID_ALARM, n)
        AlarmPlayer.start(context)
    }

    fun clearAlarm(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_ALARM)
    }

    fun fireReminder(context: Context, dues: List<Reminders.Due>) {
        if (dues.isEmpty()) return
        ensureChannels(context)
        val first = dues.first()
        val title = if (dues.size == 1) {
            "⏳ ${Reminders.label(first)}"
        } else {
            "⏳ ${dues.size} auctions ending soon"
        }
        val text = buildString {
            append(first.monitor.title.ifBlank { first.monitor.url })
            if (first.monitor.price.isNotBlank()) append(" · ${first.monitor.price}")
        }

        val fullScreen = Intent(context, AlarmActivity::class.java)
            .setAction(AlarmActivity.ACTION_REMINDER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val fullScreenPi = PendingIntent.getActivity(
            context, 2, fullScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            context, 3,
            Intent(context, PollService::class.java).setAction(PollService.ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(ID_ALARM, n)
        AlarmPlayer.startReminder(context)
    }
}
