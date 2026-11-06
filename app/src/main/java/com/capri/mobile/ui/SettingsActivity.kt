package com.capri.mobile.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.capri.mobile.R
import com.capri.mobile.core.CapriCore
import com.capri.mobile.core.Presets
import com.capri.mobile.core.ReminderScheduler
import com.capri.mobile.core.ReminderStore
import com.capri.mobile.core.SchedulingParser
import com.capri.mobile.core.Settings
import com.capri.mobile.overlay.OverlayService

class SettingsActivity : android.app.Activity() {

    private lateinit var core: CapriCore
    private lateinit var etName: EditText
    private lateinit var spLanguage: Spinner
    private lateinit var spProvider: Spinner
    private lateinit var etBaseUrl: EditText
    private lateinit var etModel: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etTemperature: EditText
    private lateinit var etMaxTokens: EditText
    private lateinit var swTts: Switch
    private lateinit var etPitch: EditText
    private lateinit var etRate: EditText
    private lateinit var etRemoteTts: EditText
    private lateinit var etRemotePrompt: EditText
    private lateinit var swOverlay: Switch
    private lateinit var swRoam: Switch
    private lateinit var etOverlaySize: EditText
    private lateinit var tvFacts: TextView
    private lateinit var tvReminders: TextView
    private lateinit var btnExactAlarm: Button
    private val reminders by lazy { ReminderStore(this) }
    private var binding = true

    private val langs = listOf("Auto", "English", "Spanish", "Portuguese (BR)", "French", "Russian", "Italian", "German", "Polish")
    private val langCodes = listOf("auto", "en", "es", "pt", "fr", "ru", "it", "de", "pl")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        core = CapriCore.get(this)
        core.refresh()

        etName = findViewById(R.id.etName)
        spLanguage = findViewById(R.id.spLanguage)
        spProvider = findViewById(R.id.spProvider)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etModel = findViewById(R.id.etModel)
        etApiKey = findViewById(R.id.etApiKey)
        etTemperature = findViewById(R.id.etTemperature)
        etMaxTokens = findViewById(R.id.etMaxTokens)
        swTts = findViewById(R.id.swTts)
        etPitch = findViewById(R.id.etPitch)
        etRate = findViewById(R.id.etRate)
        etRemoteTts = findViewById(R.id.etRemoteTts)
        etRemotePrompt = findViewById(R.id.etRemotePrompt)
        swOverlay = findViewById(R.id.swOverlay)
        swRoam = findViewById(R.id.swRoam)
        etOverlaySize = findViewById(R.id.etOverlaySize)
        tvFacts = findViewById(R.id.tvMemoryFacts)
        tvReminders = findViewById(R.id.tvReminders)
        btnExactAlarm = findViewById(R.id.btnExactAlarm)

        spLanguage.adapter = spinnerAdapter(langs)
        spProvider.adapter = spinnerAdapter(Presets.all.map { it.name })

        fill(core.settings)
        refreshFacts()
        refreshReminders()

        spProvider.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding) return
                val preset = Presets.all[position]
                if (preset.name != "Custom / local" && preset.baseUrl.isNotBlank()) {
                    etBaseUrl.setText(preset.baseUrl)
                    etModel.setText(preset.model)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        binding = false

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnOverlayPerm).setOnClickListener {
            try {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                Toast.makeText(this, "Open App info, Display over other apps.", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { battery() }
        findViewById<Button>(R.id.btnNotification).setOnClickListener { notifications() }
        findViewById<Button>(R.id.btnAutostart).setOnClickListener { autostart() }
        findViewById<Button>(R.id.btnClearMemory).setOnClickListener {
            core.memory.clearAll()
            refreshFacts()
            Toast.makeText(this, "Shard wiped. Who were you again?", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnClearChat).setOnClickListener {
            core.clearChat()
            refreshFacts()
            Toast.makeText(this, "Chat history deleted.", Toast.LENGTH_SHORT).show()
        }
        btnExactAlarm.setOnClickListener { exactAlarms() }
        findViewById<Button>(R.id.btnClearReminders).setOnClickListener {
            try {
                ReminderScheduler.cancelAll(this)
                reminders.clear()
                refreshReminders()
                Toast.makeText(this, "All reminders cancelled.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't cancel reminders. Try again.", Toast.LENGTH_SHORT).show()
            }
        }

        val version = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            "Capri Mobile ${info.versionName} (${info.versionCode})"
        } catch (_: Exception) {
            "Capri Mobile"
        }
        findViewById<TextView>(R.id.tvVersion).text = version
        findViewById<TextView>(R.id.tvAbout).text =
            "A native Android port of Capri (github.com/Capri-cmd/capri-ai).\n\n" +
                "Code: MIT License, © 2026 yume_mud (Capri-cmd).\n" +
                "Capri character artwork: drawn for Capri by xilia_world00; not covered by the MIT code license. " +
                "Included here from the upstream project with attribution; do not redistribute the art separately.\n" +
                "Share Tech Mono font: SIL Open Font License.\n\n" +
                "Capri is a non-commercial fan project. Cyberpunk and its characters are trademarks of CD PROJEKT RED. " +
                "This app is not affiliated with or endorsed by them."
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun fill(s: Settings) {
        etName.setText(s.userName)
        spLanguage.setSelection(langCodes.indexOf(s.language).coerceAtLeast(0))
        val pIndex = Presets.all.indexOfFirst { it.name == s.provider }
        if (pIndex >= 0) spProvider.setSelection(pIndex) else spProvider.setSelection(Presets.all.lastIndex)
        etBaseUrl.setText(s.baseUrl)
        etModel.setText(s.model)
        etApiKey.setText(s.apiKey)
        etTemperature.setText(s.temperature.toString())
        etMaxTokens.setText(s.maxTokens.toString())
        swTts.isChecked = s.ttsEnabled
        etPitch.setText(s.pitch.toString())
        etRate.setText(s.rate.toString())
        etRemoteTts.setText(s.remoteTtsUrl)
        etRemotePrompt.setText(s.remoteTtsPrompt)
        swOverlay.isChecked = s.overlayEnabled
        swRoam.isChecked = s.roam
        etOverlaySize.setText(s.overlaySizeDp.toString())
    }

    private fun num(et: EditText, fallback: Float): Float =
        et.text.toString().replace(',', '.').toFloatOrNull() ?: fallback

    private fun save() {
        val old = core.settings
        val provider = Presets.all.getOrNull(spProvider.selectedItemPosition)?.name ?: "Custom / local"
        val s = Settings(
            userName = etName.text.toString().trim().ifBlank { "you" },
            language = langCodes.getOrElse(spLanguage.selectedItemPosition) { "auto" },
            provider = provider,
            baseUrl = etBaseUrl.text.toString().trim(),
            model = etModel.text.toString().trim(),
            apiKey = etApiKey.text.toString().trim(),
            temperature = num(etTemperature, 0.7f).coerceIn(0f, 1.5f),
            maxTokens = etMaxTokens.text.toString().toIntOrNull()?.coerceIn(32, 1024) ?: 160,
            ttsEnabled = swTts.isChecked,
            pitch = num(etPitch, 1.12f).coerceIn(0.5f, 2f),
            rate = num(etRate, 1.05f).coerceIn(0.5f, 2f),
            remoteTtsUrl = etRemoteTts.text.toString().trim(),
            remoteTtsPrompt = etRemotePrompt.text.toString().trim(),
            overlayEnabled = swOverlay.isChecked,
            overlaySizeDp = etOverlaySize.text.toString().toIntOrNull()?.coerceIn(120, 320) ?: 190,
            roam = swRoam.isChecked
        )
        core.prefs.save(s)
        core.notifySettingsChanged()

        if (s.overlayEnabled && !AndroidSettings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_overlay, Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        } else if (s.overlayEnabled) {
            try {
                OverlayService.start(this)
            } catch (_: Exception) {
                Toast.makeText(this, "HyperOS blocked the overlay service. Check autostart/background settings.", Toast.LENGTH_LONG).show()
            }
        } else if (old.overlayEnabled) {
            OverlayService.stop(this)
        }
        Toast.makeText(this, "Config saved. Try not to break me.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun battery() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already exempt. Good choom.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try {
                startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Open Android settings, Battery, and set Capri to unrestricted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun notifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        } else {
            startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun autostart() {
        val candidates = listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartDetailManagementActivity")
        )
        for (c in candidates) {
            try {
                startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(this, "Find Capri and allow autostart.", Toast.LENGTH_LONG).show()
                return
            } catch (_: Exception) { }
        }
        try {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            Toast.makeText(this, "Open HyperOS Settings, Apps, Capri, Autostart.", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshFacts() {
        val facts = core.memory.facts().map { it.text }
        tvFacts.text = if (facts.isEmpty()) {
            "No facts yet. Say “remember that…” and she keeps it."
        } else {
            facts.takeLast(20).joinToString("\n") { "• $it" }
        }
    }

    private fun exactAlarmsAllowed(): Boolean = try {
        Build.VERSION.SDK_INT < 31 ||
            (getSystemService(ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } catch (_: Exception) {
        false
    }

    private fun refreshReminders() {
        val now = System.currentTimeMillis()
        val list = try {
            reminders.upcoming(now, 20)
        } catch (_: Exception) {
            emptyList()
        }
        tvReminders.text = if (list.isEmpty()) {
            "No reminders armed. Ask Capri for one in chat."
        } else {
            list.mapIndexed { i, r ->
                "${i + 1}. ${r.text} — ${SchedulingParser.describe(r.at, pt = true, nowMillis = now)}"
            }.joinToString("\n")
        }
        val exact = exactAlarmsAllowed()
        btnExactAlarm.isEnabled = !exact
        btnExactAlarm.text = if (exact) {
            "Exact alarms allowed ✓"
        } else {
            "Allow exact alarms (on-time reminders)"
        }
    }

    private fun exactAlarms() {
        if (Build.VERSION.SDK_INT < 31 || exactAlarmsAllowed()) {
            Toast.makeText(this, "Exact alarms already allowed.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Open Alarms & reminders and allow Capri.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Open Android settings, Special app access, Alarms & reminders.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tvReminders.isInitialized) refreshReminders()
    }
}
