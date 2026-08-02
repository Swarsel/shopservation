package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class FindsActivity : Activity() {

    private lateinit var store: Store
    private lateinit var statusView: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var queryInput: EditText
    private lateinit var scroller: ScrollView
    private lateinit var moreButton: Button

    private var items: List<Listing> = emptyList()
    private var shown = 0
    private var rules: List<Rule> = emptyList()
    private var baseStatus = ""

    private var serverPage = 1
    private var serverPages = 1
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
            setOnClickListener { load() }
        })
        bar.addView(Button(this).apply {
            text = "↻"
            setOnClickListener { load(force = true) }
        })
        root.addView(bar)

        statusView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusView)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        moreButton = Button(this).apply {
            text = "Load more"
            visibility = View.GONE
            setOnClickListener { showMore() }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(listBox)
            addView(moreButton)
        }
        scroller = ScrollView(this).apply {
            addView(inner)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0,
            ).apply { weight = 1f }
            setOnScrollChangeListener { v, _, _, _, _ ->
                val sv = v as ScrollView
                val child = sv.getChildAt(0) ?: return@setOnScrollChangeListener
                if (sv.scrollY + sv.height >= child.height - 200) showMore()
            }
        }
        root.addView(scroller)

        setContentView(root)
        load()
    }

    private fun load(force: Boolean = false) {
        if (loading) return
        val q = queryInput.text.toString().trim()
        rules = store.rules()
        serverPage = 1

        if (!force && q.isEmpty()) {
            val cache = ListingCache(this)
            val cached = cache.load()
            if (cached.isNotEmpty() && cache.isFresh(CACHE_MAX_AGE_MS)) {
                serverPages = 1
                val mins = cache.ageMillis() / 60000
                baseStatus = "${cached.size} cached find(s) · ${mins}m old · ↻ to refresh"
                setItems(cached)
                return
            }
        }

        if (!store.configured()) {
            baseStatus = "Set the server, email and password first."
            statusView.text = baseStatus
            return
        }
        loading = true
        baseStatus = "loading…"
        statusView.text = baseStatus
        val wanted = serverPage
        thread {
            val res = runCatching { Api(store).fetchState(wanted, q) }
            runOnUiThread {
                loading = false
                res.onSuccess { st ->
                    if (st.page == 1 && q.isEmpty()) {
                        ListingCache(this@FindsActivity).mergeNewest(st.listings, st.total, store.previewLimit)
                    }
                    serverPage = st.page
                    serverPages = st.pages
                    baseStatus = "${st.total} find(s)" +
                        (if (q.isNotBlank()) " matching \"$q\"" else "")
                    setItems(st.listings)
                }.onFailure { e ->
                    baseStatus = "failed: ${e.message}"
                    statusView.text = baseStatus
                }
            }
        }
    }

    private fun loadNextServerPage() {
        if (loading || serverPage >= serverPages) return
        loading = true
        val q = queryInput.text.toString().trim()
        val wanted = serverPage + 1
        moreButton.text = "loading…"
        thread {
            val res = runCatching { Api(store).fetchState(wanted, q) }
            runOnUiThread {
                loading = false
                moreButton.text = "Load more"
                res.onSuccess { st ->
                    serverPage = st.page
                    serverPages = st.pages
                    items = items + st.listings
                    showMore()
                    updateMoreButton()
                }.onFailure { e ->
                    Toast.makeText(this@FindsActivity, "Could not load more: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setItems(list: List<Listing>) {
        items = list
        shown = 0
        listBox.removeAllViews()
        if (list.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Nothing here."
                textSize = 13f
            })
            moreButton.visibility = View.GONE
            statusView.text = baseStatus
            return
        }
        showMore()
        scroller.post { scroller.scrollTo(0, 0) }
    }

    private fun showMore() {
        if (shown >= items.size) {
            if (serverPage < serverPages) loadNextServerPage()
            updateMoreButton()
            return
        }
        val end = minOf(shown + PAGE_SIZE, items.size)
        for (i in shown until end) listBox.addView(row(items[i]))
        shown = end
        updateMoreButton()
    }

    private fun updateMoreButton() {
        val hasLocal = shown < items.size
        val hasRemote = serverPage < serverPages
        moreButton.visibility = if (hasLocal || hasRemote) View.VISIBLE else View.GONE
        statusView.text = if (items.isEmpty() || !(hasLocal || hasRemote)) baseStatus
            else "$baseStatus · showing $shown of ${items.size}"
    }

    private fun row(item: Listing): LinearLayout {
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
            Thumbs.load(this, item.imageUrl, thumb)
            thumb.setOnClickListener { Thumbs.showEnlarged(this, item.imageUrl) }
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
        Proxies.buyeeUrl(item)?.let { by ->
            buttons.addView(Button(this).apply {
                text = "Buyee"
                setOnClickListener { open(by) }
            })
        }
        if (buttons.childCount > 0) col.addView(buttons)

        row.addView(col)
        return row
    }



    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 5 * 60 * 1000L
        const val PAGE_SIZE = 25
    }
}
