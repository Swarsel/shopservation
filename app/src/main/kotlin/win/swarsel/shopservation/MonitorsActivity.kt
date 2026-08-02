package win.swarsel.shopservation

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MonitorsActivity : Activity() {

    private lateinit var store: Store
    private lateinit var statusView: TextView
    private lateinit var listBox: LinearLayout

    private var showArchived = false
    private var monitors: List<Monitor> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 24)
        }
        root.addView(TextView(this).apply {
            text = "Monitored items"
            textSize = 20f
        })
        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusView)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { load() }
        })
        val archiveBtn = Button(this).apply {
            text = "Show archived"
            setOnClickListener {
                showArchived = !showArchived
                text = if (showArchived) "Hide archived" else "Show archived"
                render()
            }
        }
        bar.addView(archiveBtn)
        root.addView(bar)

        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            addView(listBox)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0,
            ).apply { weight = 1f }
        })

        setContentView(root)
        load()
    }

    private fun load() {
        statusView.text = "loading…"
        thread(isDaemon = true) {
            val result = runCatching { Api(store).fetchState(1) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { st ->
                    monitors = st.monitors
                    render()
                }.onFailure { e ->
                    statusView.text = "could not load: ${e.message}"
                }
            }
        }
    }

    private fun render() {
        val active = monitors.filter { !it.archived }
        val archived = monitors.filter { it.archived }
        val shown = if (showArchived) archived else active

        statusView.text = if (showArchived) {
            "${archived.size} archived · ${active.size} active"
        } else {
            "${active.size} monitored · ${archived.size} archived"
        }

        listBox.removeAllViews()
        if (shown.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = if (showArchived) "\nNothing archived." else "\nNothing monitored yet."
                setTextColor(Color.parseColor("#888888"))
            })
            return
        }
        shown.sortedBy { it.endsAtMillis() ?: Long.MAX_VALUE }.forEach { listBox.addView(row(it)) }
    }

    private fun row(m: Monitor): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 18, 0, 18)
        }
        if (m.imageUrl.isNotBlank()) {
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160).apply { rightMargin = 20 }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#33888888"))
            }
            row.addView(thumb)
            Thumbs.load(this, m.imageUrl, thumb)
            thumb.setOnClickListener { Thumbs.showEnlarged(this, m.imageUrl) }
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { weight = 1f }
        }
        col.addView(TextView(this).apply {
            text = m.title.ifBlank { m.url }
            textSize = 14f
        })
        col.addView(TextView(this).apply {
            text = listOfNotNull(
                m.price.ifBlank { null },
                m.status.ifBlank { null },
                m.saleType.takeIf { it == "auction" },
                endsLabel(m),
            ).joinToString(" · ")
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (m.url.isNotBlank()) {
            buttons.addView(Button(this).apply {
                text = "Open"
                setOnClickListener { open(m.url) }
            })
        }
        if (m.doorzoUrl.isNotBlank()) {
            buttons.addView(Button(this).apply {
                text = "Doorzo"
                setOnClickListener { open(m.doorzoUrl) }
            })
        }
        if (buttons.childCount > 0) col.addView(buttons)

        row.addView(col)
        return row
    }

    private fun endsLabel(m: Monitor): String? {
        val ends = m.endsAtMillis() ?: return null
        val left = ends - System.currentTimeMillis()
        if (left <= 0) return "ended"
        val mins = left / 60_000
        return when {
            mins < 60 -> "${mins}m left"
            mins < 1440 -> "${mins / 60}h ${mins % 60}m left"
            else -> "${mins / 1440}d ${(mins % 1440) / 60}h left"
        }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show() }
    }
}
