package win.swarsel.shopservation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        if (!Store(context).watching) return
        AlarmWorker.schedule(context)
        runCatching { PollService.start(context) }
    }
}
