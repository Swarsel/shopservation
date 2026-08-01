package win.swarsel.shopservation

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Poller {
    private const val TAG = "Poller"

    data class Result(val alarmed: List<Listing>, val status: String)

    @Synchronized
    fun pollOnce(context: Context): Result {
        val store = Store(context)
        if (!store.configured()) {
            val s = "not configured"
            store.lastStatus = s
            return Result(emptyList(), s)
        }

        val listings = try {
            Api(store).fetchListings()
        } catch (e: Exception) {
            val s = "${stamp()} — error: ${e.message}"
            store.lastStatus = s
            Log.w(TAG, "poll failed", e)
            return Result(emptyList(), s)
        }

        if (!store.seeded) {
            store.markSeen(listings.map { it.key })
            store.seeded = true
            val s = "${stamp()} — seeded ${listings.size} existing listing(s), watching for new ones"
            store.lastStatus = s
            return Result(emptyList(), s)
        }

        val rules = store.rules()
        val hits = Matcher.selectAlarming(rules, listings, store.seen())

        store.markSeen(listings.map { it.key })

        val s = if (hits.isEmpty()) {
            "${stamp()} — ${listings.size} listing(s), no matches"
        } else {
            "${stamp()} — ${hits.size} match(es)!"
        }
        store.lastStatus = s
        if (hits.isNotEmpty()) store.setLastAlarm(hits)
        return Result(hits, s)
    }

    private fun stamp(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
