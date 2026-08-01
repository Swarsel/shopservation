package win.swarsel.shopservation

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Poller {
    private const val TAG = "Poller"

    data class Result(
        val alarmed: List<Listing>,
        val reminders: List<Reminders.Due>,
        val status: String,
    )

    @Synchronized
    fun pollOnce(context: Context): Result {
        val store = Store(context)
        if (!store.configured()) {
            val s = "not configured"
            store.lastStatus = s
            return Result(emptyList(), emptyList(), s)
        }

        val state = try {
            Api(store).fetchState(1)
        } catch (e: Exception) {
            val s = "${stamp()} — error: ${e.message}"
            store.lastStatus = s
            Log.w(TAG, "poll failed", e)
            return Result(emptyList(), emptyList(), s)
        }
        val listings = state.listings
        store.cacheMonitors(state.monitors)
        ListingCache(context).mergeNewest(listings, state.total, store.previewLimit)

        val reminders = if (store.reminderEnabled) {
            Reminders.due(state.monitors, store.reminderLeadMinutes(), System.currentTimeMillis(), store.reminderFired())
        } else {
            emptyList()
        }
        reminders.forEach { store.markReminderFired(it.key) }

        if (!store.seeded) {
            store.markSeen(listings.map { it.key })
            store.seeded = true
            val s = "${stamp()} — seeded ${listings.size} existing listing(s), watching for new ones"
            store.lastStatus = s
            return Result(emptyList(), reminders, s)
        }

        val rules = store.rules()
        val hits = Matcher.selectAlarming(rules, listings, store.seen())

        store.markSeen(listings.map { it.key })

        val bits = mutableListOf<String>()
        bits += if (hits.isEmpty()) "${listings.size} listing(s), no matches" else "${hits.size} match(es)!"
        if (reminders.isNotEmpty()) bits += "${reminders.size} auction reminder(s)"
        val s = "${stamp()} — ${bits.joinToString(" · ")}"
        store.lastStatus = s
        if (hits.isNotEmpty()) store.setLastAlarm(hits)
        return Result(hits, reminders, s)
    }

    private fun stamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
