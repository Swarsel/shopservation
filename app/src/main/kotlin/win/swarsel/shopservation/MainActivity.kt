package win.swarsel.shopservation

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var statusView: TextView
    private lateinit var rulesBox: LinearLayout

    private lateinit var urlInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var intervalInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Notifications.ensureChannels(this)
        requestRuntimePermissions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 56)
        }

        root.addView(header("🔔 shopservation"))
        statusView = TextView(this).apply {
            text = store.lastStatus
            textSize = 13f
            setPadding(0, 8, 0, 24)
        }
        root.addView(statusView)

        root.addView(label("shopservatory server"))
        urlInput = input(store.serverUrl, "https://shopservatory.swarsel.win")
        root.addView(urlInput)

        root.addView(label("email"))
        emailInput = input(store.email, "you@example.com")
        root.addView(emailInput)

        root.addView(label("password"))
        passwordInput = input(store.password, "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(passwordInput)

        root.addView(label("poll interval (seconds)"))
        intervalInput = input(store.pollSeconds.toString(), "60").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(intervalInput)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { saveSettings(); toast("Saved") }
        })
        row.addView(Button(this).apply {
            text = "Test"
            setOnClickListener { testConnection() }
        })
        root.addView(row)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = if (store.watching) "Stop watching" else "Start watching"
            setOnClickListener {
                saveSettings()
                if (store.watching) {
                    PollService.stop(this@MainActivity)
                    AlarmWorker.cancel(this@MainActivity)
                    text = "Start watching"
                    toast("Stopped")
                } else {
                    if (!store.configured()) { toast("Set server, email and password first"); return@setOnClickListener }
                    PollService.start(this@MainActivity)
                    text = "Stop watching"
                    toast("Watching")
                }
            }
        })
        row2.addView(Button(this).apply {
            text = "Test alarm"
            setOnClickListener {
                Notifications.fireAlarm(
                    this@MainActivity,
                    listOf(
                        Listing(
                            source = "test", searchId = 0, externalId = "test",
                            title = "Test alarm — this is what a match sounds like",
                            price = 0.0, currency = "", url = "", saleType = "",
                        )
                    ),
                )
            }
        })
        root.addView(row2)

        root.addView(Button(this).apply {
            text = "Ignore battery optimisation"
            setOnClickListener { requestBatteryExemption() }
        })

        root.addView(header("Alarm rules"))
        root.addView(TextView(this).apply {
            text = "A find triggers the alarm when it matches any rule. " +
                "With no rules, nothing ever alarms."
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })
        rulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rulesBox)
        root.addView(Button(this).apply {
            text = "Add rule"
            setOnClickListener { editRule(null) }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        renderRules()
    }

    override fun onResume() {
        super.onResume()
        statusView.text = store.lastStatus
    }

    private fun saveSettings() {
        store.serverUrl = urlInput.text.toString()
        val newEmail = emailInput.text.toString().trim()
        val newPassword = passwordInput.text.toString()

        if (newEmail != store.email || newPassword != store.password) store.token = ""
        store.email = newEmail
        store.password = newPassword
        store.pollSeconds = intervalInput.text.toString().toIntOrNull() ?: 60
    }

    private fun testConnection() {
        saveSettings()
        statusView.text = "testing…"
        thread {
            val msg = runCatching { Api(store).testConnection() }
                .getOrElse { "failed: ${it.message}" }
            runOnUiThread { statusView.text = msg; toast(msg) }
        }
    }

    private fun renderRules() {
        rulesBox.removeAllViews()
        val rules = store.rules()
        if (rules.isEmpty()) {
            rulesBox.addView(TextView(this).apply {
                text = "No rules yet — nothing will alarm."
                textSize = 13f
            })
            return
        }
        rules.forEach { rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(CheckBox(this).apply {
                isChecked = rule.enabled
                setOnCheckedChangeListener { _, checked ->
                    store.upsertRule(rule.copy(enabled = checked))
                }
            })
            row.addView(TextView(this).apply {
                text = rule.describe()
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { weight = 1f }
                setOnClickListener { editRule(rule) }
            })
            row.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    store.deleteRule(rule.id)
                    renderRules()
                }
            })
            rulesBox.addView(row)
        }
    }

    private fun editRule(existing: Rule?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }
        val kw = input(existing?.keywords ?: "", "pikachu, psa 10")
        val ex = input(existing?.excludeKeywords ?: "", "proxy, reprint")
        val max = input(existing?.maxPrice?.let { Rule.fmtPrice(it) } ?: "", "e.g. 20000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val cur = input(existing?.currency ?: "", "JPY / EUR / USD")
        val src = input(existing?.sources ?: "", "mercari, ebay (blank = all)")

        box.addView(label("title must contain ALL of (comma-separated)")); box.addView(kw)
        box.addView(label("reject if title contains ANY of")); box.addView(ex)
        box.addView(label("max price")); box.addView(max)
        box.addView(label("price currency (required with max price)")); box.addView(cur)
        box.addView(label("only these sources")); box.addView(src)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New rule" else "Edit rule")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val maxVal = max.text.toString().trim().toDoubleOrNull()
                val currency = cur.text.toString().trim().uppercase()
                if (maxVal != null && currency.isBlank()) {
                    toast("A max price needs a currency — listings come in JPY, EUR and USD")
                    return@setPositiveButton
                }
                val rule = Rule(
                    id = existing?.id ?: store.nextRuleId(),
                    enabled = existing?.enabled ?: true,
                    keywords = kw.text.toString().trim(),
                    excludeKeywords = ex.text.toString().trim(),
                    maxPrice = maxVal,
                    currency = currency,
                    sources = src.text.toString().trim(),
                )
                store.upsertRule(rule)
                renderRules()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Already exempt")
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }.onFailure { toast("Could not open battery settings") }
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 20f
        setPadding(0, 32, 0, 8)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setPadding(0, 16, 0, 0)
    }

    private fun input(value: String, hint: String) = EditText(this).apply {
        setText(value)
        this.hint = hint
        setSingleLine()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
