package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class FindsActivity : Activity() {

    private lateinit var store: Store
    private lateinit var statusView: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var queryInput: EditText
    private lateinit var pageLabel: TextView

    private var page = 1
    private var pages = 1
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
        }

        root.addView(TextView(this).apply {
            text = "🔭 Finds"
            textSize = 22f
        })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        queryInput = EditText(this).apply {
            hint = "filter…"
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f }
        }
        bar.addView(queryInput)
        bar.addView(Button(this).apply {
            text = "Search"
            setOnClickListener { page = 1; load() }
        })
        bar.addView(Button(this).apply {
            text = "↻"
            setOnClickListener { page = 1; load(force = true) }
        })
        root.addView(bar)

        statusView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusView)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0,
            ).apply { weight = 1f }
        })

        val pager = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pager.addView(Button(this).apply {
            text = "‹ prev"
            setOnClickListener { if (page > 1) { page--; load() } }
        })
        pageLabel = TextView(this).apply {
            textSize = 12f
            setPadding(16, 0, 16, 0)
        }
        pager.addView(pageLabel)
        pager.addView(Button(this).apply {
            text = "next ›"
            setOnClickListener { if (page < pages) { page++; load() } }
        })
        root.addView(pager)

        setContentView(root)
        load()
    }

    private fun load(force: Boolean = false) {
        if (loading) return
        val q = queryInput.text.toString().trim()

        if (!force && page == 1 && q.isEmpty()) {
            val cache = ListingCache(this)
            val cached = cache.load()
            if (cached.isNotEmpty() && cache.isFresh(CACHE_MAX_AGE_MS)) {
                pages = 1
                pageLabel.text = "cached"
                val mins = cache.ageMillis() / 60000
                statusView.text = "${cached.size} cached find(s) · ${mins}m old · ↻ to refresh"
                render(cached)
                return
            }
        }

        if (!store.configured()) {
            statusView.text = "Set the server, email and password first."
            return
        }
        loading = true
        statusView.text = "loading…"
        val wanted = page
        thread {
            val res = runCatching { Api(store).fetchState(wanted, q) }
            runOnUiThread {
                loading = false
                res.onSuccess { st ->
                    if (st.page == 1 && q.isEmpty()) {
                        ListingCache(this@FindsActivity).mergeNewest(st.listings, st.total, store.previewLimit)
                    }
                    page = st.page
                    pages = st.pages
                    pageLabel.text = "page ${st.page} / ${st.pages}"
                    statusView.text = "${st.total} find(s)" + if (q.isNotBlank()) " matching \"$q\"" else ""
                    render(st.listings)
                }.onFailure { e ->
                    statusView.text = "failed: ${e.message}"
                }
            }
        }
    }

    private fun render(items: List<Listing>) {
        listBox.removeAllViews()
        if (items.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Nothing here."
                textSize = 13f
            })
            return
        }
        val rules = store.rules()
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 18, 0, 18)
            }
            if (item.imageUrl.isNotBlank()) {
                val thumb = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(160, 160).apply { rightMargin = 20 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.parseColor("#33888888"))
                }
                row.addView(thumb)
                loadThumb(item.imageUrl, thumb)
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { weight = 1f }
            }
            val matched = Matcher.firstMatch(rules, item) != null
            col.addView(TextView(this).apply {
                text = (if (matched) "🔔 " else "") + item.title
                textSize = 14f
            })
            col.addView(TextView(this).apply {
                text = listOfNotNull(
                    item.priceLabel.ifBlank { null },
                    item.source.ifBlank { null },
                    item.saleType.takeIf { it == "auction" },
                ).joinToString(" · ")
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
            })

            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (item.url.isNotBlank()) {
                buttons.addView(Button(this).apply {
                    text = "Open"
                    setOnClickListener { open(item.url) }
                })
            }
            Proxies.doorzoUrl(item)?.let { dz ->
                buttons.addView(Button(this).apply {
                    text = "Doorzo"
                    setOnClickListener { open(dz) }
                })
            }
            if (buttons.childCount > 0) col.addView(buttons)

            row.addView(col)
            listBox.addView(row)
        }
    }

    private fun loadThumb(url: String, into: ImageView) {
        thread(isDaemon = true) {
            val bmp = runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                try {
                    if (conn.responseCode != 200) null
                    else conn.inputStream.use { s ->
                        BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull() ?: return@thread
            runOnUiThread { into.setImageBitmap(bmp as Bitmap) }
        }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 5 * 60 * 1000L
    }
}
