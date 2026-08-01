package win.swarsel.shopservation

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ListingCache(context: Context) {

    private val file = File(context.cacheDir, "listings.json")
    private val prefs = context.getSharedPreferences("shopservation", Context.MODE_PRIVATE)

    var updatedAt: Long
        get() = prefs.getLong("listingsCachedAt", 0L)
        private set(v) = prefs.edit().putLong("listingsCachedAt", v).apply()

    var total: Int
        get() = prefs.getInt("listingsCachedTotal", 0)
        private set(v) = prefs.edit().putInt("listingsCachedTotal", v).apply()

    fun ageMillis(now: Long = System.currentTimeMillis()): Long {
        val at = updatedAt
        return if (at == 0L) Long.MAX_VALUE else now - at
    }

    fun isFresh(maxAgeMillis: Long, now: Long = System.currentTimeMillis()): Boolean =
        file.exists() && ageMillis(now) <= maxAgeMillis

    fun load(): List<Listing> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Listing.fromJson(arr.getJSONObject(it)) }
        }.onFailure { Log.w(TAG, "cache unreadable, ignoring", it) }.getOrElse { emptyList() }
    }

    fun save(listings: List<Listing>, total: Int) {
        runCatching {
            val arr = JSONArray()
            listings.forEach { l ->
                arr.put(JSONObject().apply {
                    put("source", l.source)
                    put("searchId", l.searchId)
                    put("externalId", l.externalId)
                    put("title", l.title)
                    put("price", l.price)
                    put("currency", l.currency)
                    put("url", l.url)
                    put("imageUrl", l.imageUrl)
                    put("saleType", l.saleType)
                })
            }
            file.writeText(arr.toString())
            this.total = total
            updatedAt = System.currentTimeMillis()
        }.onFailure { Log.w(TAG, "could not write cache", it) }
    }

    fun mergeNewest(page1: List<Listing>, total: Int, cap: Int) {
        if (page1.isEmpty()) return
        val existing = load()
        if (existing.isEmpty()) {
            save(page1, total)
            return
        }
        val byKey = LinkedHashMap<String, Listing>(page1.size + existing.size)
        page1.forEach { byKey[it.key] = it }
        existing.forEach { byKey.putIfAbsent(it.key, it) }
        val merged = byKey.values.toList()
        save(if (cap > 0 && merged.size > cap) merged.subList(0, cap) else merged, total)
    }

    fun isComplete(limit: Int): Boolean {
        val have = load().size
        if (have == 0) return false
        val t = total
        return t == 0 || have >= t || (limit > 0 && have >= limit)
    }

    fun clear() {
        runCatching { file.delete() }
        prefs.edit().remove("listingsCachedAt").remove("listingsCachedTotal").apply()
    }

    private companion object {
        const val TAG = "ListingCache"
    }
}
