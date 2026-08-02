package win.swarsel.shopservation

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
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
    private lateinit var volumeInput: EditText
    private lateinit var vibrateBox: CheckBox
    private lateinit var soundView: TextView
    private lateinit var reminderBox: CheckBox
    private lateinit var reminderMinutesInput: EditText
    private lateinit var reminderVolumeInput: EditText
    private lateinit var reminderSoundView: TextView
    private lateinit var previewLimitInput: EditText
    private var pendingSoundRequest = 0
    private lateinit var reminderVibrateBox: CheckBox

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

        val server = section(root, "Server", openByDefault = !store.configured())

        server.addView(label("shopservatory server"))
        urlInput = input(store.serverUrl, "https://shopservatory.example.com")
        server.addView(urlInput)

        server.addView(label("email"))
        emailInput = input(store.email, "you@example.com")
        server.addView(emailInput)

        server.addView(label("password"))
        passwordInput = input(store.password, "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        server.addView(passwordInput)

        server.addView(label("poll interval (seconds)"))
        intervalInput = input(store.pollSeconds.toString(), "60").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        server.addView(intervalInput)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Save"
            setOnClickListener { saveSettings(); toast("Saved") }
        })
        row.addView(Button(this).apply {
            text = "Test"
            setOnClickListener { testConnection() }
        })
        server.addView(row)

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
        root.addView(row2)

        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(Button(this).apply {
            text = "Browse finds"
            setOnClickListener { startActivity(Intent(this@MainActivity, FindsActivity::class.java)) }
        })
        row3.addView(Button(this).apply {
            text = "Monitored"
            setOnClickListener { startActivity(Intent(this@MainActivity, MonitorsActivity::class.java)) }
        })
        row3.addView(Button(this).apply {
            text = "Recent matches"
            setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, AlarmActivity::class.java)
                        .setAction(AlarmActivity.ACTION_HISTORY)
                )
            }
        })
        root.addView(row3)

        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row4.addView(Button(this).apply {
            text = "Ignore battery optimisation"
            setOnClickListener { requestBatteryExemption() }
        })
        root.addView(row4)

        val soundSec = section(root, "Alarm sound", openByDefault = false)
        soundView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 4)
        }
        soundSec.addView(soundView)

        val srow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        srow.addView(Button(this).apply {
            text = "Choose sound"
            setOnClickListener { pickSound() }
        })
        srow.addView(Button(this).apply {
            text = "Built-in siren"
            setOnClickListener {
                store.alarmSoundUri = ""
                store.alarmSoundLabel = "Built-in siren"
                renderSound()
                toast("Using the built-in siren")
            }
        })
        soundSec.addView(srow)

        soundSec.addView(label("volume (% of the alarm stream's maximum)"))
        volumeInput = input(store.alarmVolumePercent.toString(), "100").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        soundSec.addView(volumeInput)

        vibrateBox = CheckBox(this).apply {
            text = "vibrate as well"
            isChecked = store.alarmVibrate
            setOnCheckedChangeListener { _, v -> store.alarmVibrate = v }
        }
        soundSec.addView(vibrateBox)

        val trow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        trow.addView(Button(this).apply {
            text = "Play test alarm"
            setOnClickListener {
                saveAlarmSettings()
                AlarmPlayer.start(this@MainActivity)
                toast("Playing — press Stop when you have heard enough")
            }
        })
        trow.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                AlarmPlayer.stop(this@MainActivity)
                Notifications.clearAlarm(this@MainActivity)
            }
        })
        soundSec.addView(trow)

        soundSec.addView(Button(this).apply {
            text = "Test full alarm (notification + screen)"
            setOnClickListener {
                saveAlarmSettings()
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

        val remSec = section(root, "Auction reminders", openByDefault = false)
        remSec.addView(TextView(this).apply {
            text = "Alarms before a monitored auction ends. Uses its own sound and volume."
            textSize = 12f
            setPadding(0, 0, 0, 4)
        })
        reminderBox = CheckBox(this).apply {
            text = "remind me before monitored auctions end"
            isChecked = store.reminderEnabled
            setOnCheckedChangeListener { _, v -> store.reminderEnabled = v }
        }
        remSec.addView(reminderBox)

        remSec.addView(label("minutes before the end (comma-separated, e.g. 60, 10, 2)"))
        reminderMinutesInput = input(store.reminderMinutes, "10").apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        remSec.addView(reminderMinutesInput)

        reminderSoundView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 4)
        }
        remSec.addView(reminderSoundView)

        val rrow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rrow.addView(Button(this).apply {
            text = "Choose sound"
            setOnClickListener { pickReminderSound() }
        })
        rrow.addView(Button(this).apply {
            text = "Built-in siren"
            setOnClickListener {
                store.reminderSoundUri = ""
                store.reminderSoundLabel = "Built-in siren"
                renderSound()
            }
        })
        remSec.addView(rrow)

        remSec.addView(label("reminder volume (%)"))
        reminderVolumeInput = input(store.reminderVolumePercent.toString(), "100").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        remSec.addView(reminderVolumeInput)

        reminderVibrateBox = CheckBox(this).apply {
            text = "vibrate for reminders"
            isChecked = store.reminderVibrate
            setOnCheckedChangeListener { _, v -> store.reminderVibrate = v }
        }
        remSec.addView(reminderVibrateBox)

        val rtrow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        rtrow.addView(Button(this).apply {
            text = "Play test reminder"
            setOnClickListener {
                saveAlarmSettings()
                AlarmPlayer.startReminder(this@MainActivity)
                toast("Playing — press Stop when done")
            }
        })
        rtrow.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                AlarmPlayer.stop(this@MainActivity)
                Notifications.clearAlarm(this@MainActivity)
            }
        })
        remSec.addView(rtrow)

        val rulesSec = section(root, "Alarm rules", openByDefault = true)
        rulesSec.addView(label("\"Would match\" scan limit (0 = all finds)"))
        previewLimitInput = input(store.previewLimit.toString(), "5000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        rulesSec.addView(previewLimitInput)
        rulesSec.addView(TextView(this).apply {
            text = "A find triggers the alarm when it matches any rule. " +
                "With no rules, nothing ever alarms."
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })
        rulesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rulesSec.addView(rulesBox)
        rulesSec.addView(Button(this).apply {
            text = "Add rule"
            setOnClickListener { editRule(null) }
        })

        setContentView(ScrollView(this).apply { addView(root) })
        renderRules()
        renderSound()
    }

    override fun onResume() {
        super.onResume()
        statusView.text = store.lastStatus
    }

    override fun onPause() {
        super.onPause()
        saveAlarmSettings()
    }

    private fun renderSound() {
        soundView.text = "sound: ${store.alarmSoundLabel}" +
            (if (store.alarmSoundUri.isBlank()) " (loud, deliberately unpleasant)" else "") +
            (if (store.lastSoundError.isNotBlank()) "\n⚠ ${store.lastSoundError}" else "")
        reminderSoundView.text = "reminder sound: ${store.reminderSoundLabel}"
    }

    private fun saveAlarmSettings() {
        store.alarmVolumePercent = volumeInput.text.toString().toIntOrNull() ?: 100
        store.alarmVibrate = vibrateBox.isChecked
        volumeInput.setText(store.alarmVolumePercent.toString())
        store.reminderEnabled = reminderBox.isChecked
        store.reminderMinutes = reminderMinutesInput.text.toString()
        store.reminderVolumePercent = reminderVolumeInput.text.toString().toIntOrNull() ?: 100
        reminderVolumeInput.setText(store.reminderVolumePercent.toString())
        store.reminderVibrate = reminderVibrateBox.isChecked
        store.previewLimit = previewLimitInput.text.toString().toIntOrNull() ?: 5000
        previewLimitInput.setText(store.previewLimit.toString())
    }

    private fun pickReminderSound() = pickSoundFor(REQ_REMINDER_SOUND, store.reminderSoundUri)

    private fun pickSound() = pickSoundFor(REQ_SOUND, store.alarmSoundUri)

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun pickSoundFor(requestCode: Int, existingUri: String) {
        val perm = audioPermission()
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            pendingSoundRequest = requestCode
            requestPermissions(arrayOf(perm), REQ_AUDIO_PERM)
            return
        }
        val current = existingUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, requestCode) }
            .onFailure { toast("No sound picker available on this device") }
    }

    @Deprecated("startActivityForResult is the simplest option for a plain Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val forReminder = requestCode == REQ_REMINDER_SOUND
        if (requestCode != REQ_SOUND && !forReminder) return

        val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri == null) {
            if (forReminder) {
                store.reminderSoundUri = ""
                store.reminderSoundLabel = "Built-in siren"
            } else {
                store.alarmSoundUri = ""
                store.alarmSoundLabel = "Built-in siren"
            }
            renderSound()
            return
        }

        if (uri.scheme == "content") {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val err = AlarmPlayer.checkPlayable(this, uri)
        if (err != null) {
            toast("That sound cannot be played: $err")
            return
        }

        val label = runCatching {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Custom sound"
        if (forReminder) {
            store.reminderSoundUri = uri.toString()
            store.reminderSoundLabel = label
        } else {
            store.alarmSoundUri = uri.toString()
            store.alarmSoundLabel = label
            store.lastSoundError = ""
        }
        renderSound()
        toast("Sound set: $label")
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
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete rule?")
                        .setMessage(rule.describe())
                        .setPositiveButton("Delete") { _, _ ->
                            store.deleteRule(rule.id)
                            renderRules()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
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
        val min = input(existing?.minPrice?.let { Rule.fmtPrice(it) } ?: "", "e.g. 500").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val max = input(existing?.maxPrice?.let { Rule.fmtPrice(it) } ?: "", "e.g. 20000").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val cur = input(existing?.currency ?: "", "JPY / EUR / USD")
        val src = input(existing?.sources ?: "", "mercari, ebay (blank = all)")

        box.addView(label("title must contain ALL of (comma-separated)")); box.addView(kw)
        box.addView(label("reject if title contains ANY of")); box.addView(ex)
        box.addView(label("min price")); box.addView(min)
        box.addView(label("max price")); box.addView(max)
        box.addView(label("price currency (required with a price limit)")); box.addView(cur)
        box.addView(label("only these sources")); box.addView(src)

        fun currentRule(): Rule? {
            val minVal = min.text.toString().trim().toDoubleOrNull()
            val maxVal = max.text.toString().trim().toDoubleOrNull()
            val currency = cur.text.toString().trim().uppercase()
            if ((minVal != null || maxVal != null) && currency.isBlank()) {
                toast("A price limit needs a currency — listings come in JPY, EUR and USD")
                return null
            }
            if (minVal != null && maxVal != null && minVal > maxVal) {
                toast("The min price is above the max price, so nothing can match")
                return null
            }
            return Rule(
                id = existing?.id ?: store.nextRuleId(),
                enabled = existing?.enabled ?: true,
                keywords = kw.text.toString().trim(),
                excludeKeywords = ex.text.toString().trim(),
                minPrice = minVal,
                maxPrice = maxVal,
                currency = currency,
                sources = src.text.toString().trim(),
            )
        }

        box.addView(Button(this).apply {
            text = "Would match…"
            setOnClickListener {
                saveAlarmSettings()
                val r = currentRule() ?: return@setOnClickListener
                RulePreview.show(this@MainActivity, r)
            }
        })

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New rule" else "Edit rule")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                val rule = currentRule() ?: return@setPositiveButton
                store.upsertRule(rule)
                renderRules()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_AUDIO_PERM) return
        val want = pendingSoundRequest
        pendingSoundRequest = 0
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            toast("Without audio access the device's own sounds cannot be read; the siren still works")
            return
        }
        when (want) {
            REQ_SOUND -> pickSoundFor(REQ_SOUND, store.alarmSoundUri)
            REQ_REMINDER_SOUND -> pickSoundFor(REQ_REMINDER_SOUND, store.reminderSoundUri)
        }
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

    private fun section(parent: LinearLayout, title: String, openByDefault: Boolean): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (openByDefault) View.VISIBLE else View.GONE
        }
        val head = TextView(this).apply {
            text = (if (openByDefault) "▾ " else "▸ ") + title
            textSize = 18f
            setPadding(0, 28, 0, 8)
            setOnClickListener {
                val open = body.visibility == View.VISIBLE
                body.visibility = if (open) View.GONE else View.VISIBLE
                text = (if (open) "▸ " else "▾ ") + title
            }
        }
        parent.addView(head)
        parent.addView(body)
        return body
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

    private companion object {
        const val REQ_SOUND = 42
        const val REQ_REMINDER_SOUND = 43
        const val REQ_AUDIO_PERM = 44
    }
}
