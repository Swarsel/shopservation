package win.swarsel.shopservation

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

object RulePreview {

    fun show(context: Context, rule: Rule, onDone: (() -> Unit)? = null) {
        val store = Store(context)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }
        val status = TextView(context).apply {
            text = "checking the finds on the server…"
            textSize = 13f
        }
        body.addView(status)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Would match")
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton("Close") { _, _ -> onDone?.invoke() }
            .create()
        dialog.show()

        val handler = android.os.Handler(context.mainLooper)
        thread {
            val res = runCatching {
                collect(context, store) { fetched, total ->
                    handler.post {
                        status.text = if (total > 0) "checking $fetched of $total find(s)…"
                            else "checking $fetched find(s)…"
                    }
                }
            }
            handler.post {
                res.onSuccess { scan ->
                    val listings = scan.listings
                    val hits = listings.filter { Matcher.matches(rule, it) }
                    status.text = buildString {
                        append("${hits.size} of ${listings.size} find(s) would match")
                        if (scan.truncated) {
                            append(" (checked the newest ${listings.size} of ${scan.total})")
                        }
                        append(":")
                    }
                    if (hits.isEmpty()) {
                        list.addView(TextView(context).apply {
                            text = "\nNothing matches yet. Loosen the keywords, or check the price currency."
                            textSize = 13f
                            setTextColor(Color.parseColor("#888888"))
                        })
                    }
                    hits.take(50).forEach { l ->
                        list.addView(TextView(context).apply {
                            text = "\n• ${l.title}" +
                                (if (l.priceLabel.isNotBlank()) "\n   ${l.priceLabel}" else "") +
                                "\n   ${l.source}"
                            textSize = 13f
                        })
                    }
                    if (hits.size > 50) {
                        list.addView(TextView(context).apply {
                            text = "\n… and ${hits.size - 50} more"
                            textSize = 12f
                            setTextColor(Color.parseColor("#888888"))
                        })
                    }
                }.onFailure { e ->
                    status.text = "could not load finds: ${e.message}"
                }
            }
        }
    }

    data class Scan(val listings: List<Listing>, val total: Int, val truncated: Boolean)

    private const val MAX_PAGES = 2000

    private fun collect(context: Context, store: Store, onProgress: (Int, Int) -> Unit): Scan {
        val cache = ListingCache(context)
        val cached = cache.load()
        if (cached.isNotEmpty() && (cache.total == 0 || cached.size >= cache.total)) {
            onProgress(cached.size, cache.total)
            return Scan(cached, if (cache.total > 0) cache.total else cached.size, truncated = false)
        }

        val api = Api(store)
        val limit = store.previewLimit
        val all = mutableListOf<Listing>()
        var page = 1
        var total = 0
        while (page <= MAX_PAGES) {
            val st = api.fetchState(page)
            total = st.total
            all += st.listings
            onProgress(all.size, st.total)
            if (page >= st.pages) break
            if (limit > 0 && all.size >= limit) break
            page++
        }
        cache.save(all, total)
        return Scan(all, total, truncated = all.size < total)
    }
}
