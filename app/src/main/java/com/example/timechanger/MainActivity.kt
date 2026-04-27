package com.example.timechanger

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var homeZoneText: TextView
    private lateinit var showAddClockButton: Button
    private lateinit var clocksRecyclerView: RecyclerView
    private lateinit var clockAdapter: ClockAdapter

    private val selectedClocks = mutableListOf<ClockItem>()

    private var homeZoneId = "Asia/Tbilisi"
    private lateinit var zoneSuggestions: List<String>

    private val timeInputFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val timeOutputFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val popularZones = listOf(
        "Asia/Tbilisi",
        "Europe/Moscow",
        "Europe/London",
        "Europe/Berlin",
        "Europe/Paris",
        "Europe/Rome",
        "Europe/Madrid",
        "America/New_York",
        "America/Chicago",
        "America/Los_Angeles",
        "America/Vancouver",
        "Asia/Dubai",
        "Asia/Tokyo",
        "Asia/Seoul",
        "Asia/Shanghai",
        "Asia/Kolkata",
        "Australia/Sydney"
    )

    private val cityAliases = mapOf(
        "Seattle" to "America/Los_Angeles",
        "San Francisco" to "America/Los_Angeles",
        "Los Angeles" to "America/Los_Angeles",
        "Vancouver" to "America/Vancouver",
        "New York City" to "America/New_York",
        "Washington" to "America/New_York",
        "Beijing" to "Asia/Shanghai",
        "Kyiv" to "Europe/Kyiv",
        "Kiev" to "Europe/Kyiv"
    )

    private val uiHandler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            clockAdapter.refreshTimes()
            uiHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val PREFS_NAME = "world_clock_prefs"
        private const val KEY_CLOCKS_JSON = "clocks_json"

        private const val KEY_HOME_ZONE_ID = "home_zone_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadHomeZone()
        homeZoneText = findViewById(R.id.homeZoneText)
        showAddClockButton = findViewById(R.id.showAddClockButton)
        clocksRecyclerView = findViewById(R.id.clocksRecyclerView)

        homeZoneText.text = "Home zone: $homeZoneId"
        homeZoneText.setOnClickListener {
            showHomeZonePicker()
        }
        zoneSuggestions = ZoneId.getAvailableZoneIds()
            .toList()
            .sorted()

        setupRecyclerView()
        loadClocks()

        if (selectedClocks.isEmpty()) {
            selectedClocks.add(ClockItem(name = "Tbilisi", zoneId = "Asia/Tbilisi"))
            selectedClocks.add(ClockItem(name = "London", zoneId = "Europe/London"))
            selectedClocks.add(ClockItem(name = "Seattle", offsetHours = -11))
            saveClocks()
            clockAdapter.updateHomeZone(homeZoneId)
            clockAdapter.notifyDataSetChanged()
        }

        showAddClockButton.setOnClickListener {
            showAddClockDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        uiHandler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(ticker)
    }

    private fun saveHomeZone() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HOME_ZONE_ID, homeZoneId)
            .apply()
    }

    private fun loadHomeZone() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        homeZoneId = prefs.getString(KEY_HOME_ZONE_ID, "Asia/Tbilisi") ?: "Asia/Tbilisi"
    }

    private fun showHomeZonePicker() {
        val zones = popularZones.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select home timezone")
            .setItems(zones) { _, which ->
                val selected = zones[which]

                homeZoneId = selected
                homeZoneText.text = "Home zone: $homeZoneId"

                saveHomeZone()

                // обновляем часы
                clockAdapter.notifyDataSetChanged()
            }
            .show()
    }
    private fun setupRecyclerView() {
        clockAdapter = ClockAdapter(
            items = selectedClocks,
            homeZoneId = homeZoneId,
            onRemove = { item ->
                selectedClocks.remove(item)
                clockAdapter.notifyDataSetChanged()
                saveClocks()
            },
            onEdit = { item ->
                showEditDialog(item)
            },
            onConvert = { item ->
                showConvertDialog(item)
            }
        )

        clocksRecyclerView.layoutManager = LinearLayoutManager(this)
        clocksRecyclerView.adapter = clockAdapter
    }

    private fun showAddClockDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_add_clock, null)

        val addNameInput = dialogView.findViewById<EditText>(R.id.addNameInput)
        val addOffsetInput = dialogView.findViewById<EditText>(R.id.addOffsetInput)
        val addTimezoneInput = dialogView.findViewById<AutoCompleteTextView>(R.id.addTimezoneInput)

        val suggestionItems = (popularZones + cityAliases.keys)
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestionItems
        )
        addTimezoneInput.setAdapter(adapter)
        addTimezoneInput.threshold = 0

        addTimezoneInput.setOnClickListener {
            addTimezoneInput.showDropDown()
        }

        addTimezoneInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                addTimezoneInput.showDropDown()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add clock")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = addNameInput.text.toString().trim()
                val offsetText = addOffsetInput.text.toString().trim()
                val timezoneRaw = addTimezoneInput.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter clock name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val zoneId = if (timezoneRaw.isNotEmpty()) {
                    findMatchingZone(timezoneRaw)
                } else {
                    null
                }

                if (timezoneRaw.isNotEmpty() && zoneId == null) {
                    Toast.makeText(this, "Timezone not found", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val offset = if (offsetText.isNotEmpty()) {
                    offsetText.toIntOrNull()
                } else {
                    null
                }

                if (zoneId == null && offset == null) {
                    Toast.makeText(this, "Set either offset or timezone", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val item = ClockItem(
                    name = name,
                    offsetHours = offset,
                    zoneId = zoneId
                )

                selectedClocks.add(item)
                selectedClocks.sortBy { it.name.lowercase() }
                clockAdapter.notifyDataSetChanged()
                saveClocks()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(item: ClockItem) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_edit_clock, null)

        val editNameInput = dialogView.findViewById<EditText>(R.id.editNameInput)
        val editOffsetInput = dialogView.findViewById<EditText>(R.id.editOffsetInput)
        val editTimezoneInput = dialogView.findViewById<AutoCompleteTextView>(R.id.editTimezoneInput)

        editNameInput.setText(item.name)
        editOffsetInput.setText(item.offsetHours?.toString() ?: "")
        editTimezoneInput.setText(item.zoneId ?: "", false)

        val suggestionItems = (popularZones + cityAliases.keys)
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestionItems
        )
        editTimezoneInput.setAdapter(adapter)
        editTimezoneInput.threshold = 0

        editTimezoneInput.setOnClickListener {
            editTimezoneInput.showDropDown()
        }

        editTimezoneInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editTimezoneInput.showDropDown()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Edit clock")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = editNameInput.text.toString().trim()
                val newOffsetText = editOffsetInput.text.toString().trim()
                val newTimezoneRaw = editTimezoneInput.text.toString().trim()

                if (newName.isEmpty()) {
                    Toast.makeText(this, "Clock name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newZoneId = if (newTimezoneRaw.isNotEmpty()) {
                    findMatchingZone(newTimezoneRaw)
                } else {
                    null
                }

                if (newTimezoneRaw.isNotEmpty() && newZoneId == null) {
                    Toast.makeText(this, "Timezone not found", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newOffset = if (newOffsetText.isNotEmpty()) {
                    newOffsetText.toIntOrNull()
                } else {
                    null
                }

                if (newZoneId == null && newOffset == null) {
                    Toast.makeText(this, "Set either offset or timezone", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val index = selectedClocks.indexOf(item)
                if (index != -1) {
                    selectedClocks[index] = ClockItem(
                        name = newName,
                        offsetHours = newOffset,
                        zoneId = newZoneId
                    )
                    selectedClocks.sortBy { it.name.lowercase() }
                    clockAdapter.notifyDataSetChanged()
                    saveClocks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun describeRelativeDay(baseDate: LocalDate, otherDate: LocalDate): String {
        return when {
            otherDate.isEqual(baseDate) -> "Today"
            otherDate.isEqual(baseDate.minusDays(1)) -> "Yesterday"
            otherDate.isEqual(baseDate.plusDays(1)) -> "Tomorrow"
            otherDate.isBefore(baseDate) -> "${java.time.temporal.ChronoUnit.DAYS.between(otherDate, baseDate)} days earlier"
            else -> "${java.time.temporal.ChronoUnit.DAYS.between(baseDate, otherDate)} days later"
        }
    }

    private fun normalizeTimeInput(raw: String): String? {
        val text = raw.trim().replace(" ", "")

        if (text.isEmpty()) return null

        // 8:30 -> 08:30, 15:30 -> 15:30
        if (":" in text) {
            val parts = text.split(":")
            if (parts.size != 2) return null

            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

            if (hour !in 0..23 || minute !in 0..59) return null

            return "%02d:%02d".format(hour, minute)
        }

        if (!text.all { it.isDigit() }) return null

        return when (text.length) {
            // 8 -> 08:00
            1 -> {
                val hour = text.toIntOrNull() ?: return null
                if (hour !in 0..9) return null
                "%02d:00".format(hour)
            }

            // 15 -> 15:00
            2 -> {
                val hour = text.toIntOrNull() ?: return null
                if (hour !in 0..23) return null
                "%02d:00".format(hour)
            }

            // 120 -> 01:20, 930 -> 09:30
            3 -> {
                val hour = text.substring(0, 1).toIntOrNull() ?: return null
                val minute = text.substring(1, 3).toIntOrNull() ?: return null
                if (hour !in 0..9 || minute !in 0..59) return null
                "%02d:%02d".format(hour, minute)
            }

            // 1430 -> 14:30
            4 -> {
                val hour = text.substring(0, 2).toIntOrNull() ?: return null
                val minute = text.substring(2, 4).toIntOrNull() ?: return null
                if (hour !in 0..23 || minute !in 0..59) return null
                "%02d:%02d".format(hour, minute)
            }

            else -> null
        }
    }

    private fun showConvertDialog(item: ClockItem) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_convert_time, null)

        val homeLabelText = dialogView.findViewById<TextView>(R.id.homeLabelText)
        val targetLabelText = dialogView.findViewById<TextView>(R.id.targetLabelText)
        val homeTimeInput = dialogView.findViewById<EditText>(R.id.homeTimeInput)
        val targetTimeInput = dialogView.findViewById<EditText>(R.id.targetTimeInput)
        val homeDayInfoText = dialogView.findViewById<TextView>(R.id.homeDayInfoText)
        val targetDayInfoText = dialogView.findViewById<TextView>(R.id.targetDayInfoText)

        homeLabelText.text = "Home time"
        targetLabelText.text = "${item.name} time"

        var isProgrammaticUpdate = false

        val homeZone = ZoneId.of(homeZoneId)
        val targetZone = getTargetZoneForClock(item)

        fun convertHomeToTarget(text: String) {
            if (isProgrammaticUpdate) return
            if (text.isBlank()) return

            try {
                val normalized = normalizeTimeInput(text) ?: return
                val homeTime = LocalTime.parse(normalized, timeOutputFormatter)
                val homeDate = LocalDate.now(homeZone)
                val homeDateTime = ZonedDateTime.of(homeDate, homeTime, homeZone)
                val targetDateTime = homeDateTime.withZoneSameInstant(targetZone)

                isProgrammaticUpdate = true

                targetTimeInput.setText(targetDateTime.format(timeOutputFormatter))
                homeDayInfoText.text = "Today"
                targetDayInfoText.text = describeRelativeDay(homeDate, targetDateTime.toLocalDate())

                isProgrammaticUpdate = false
            } catch (_: Exception) {
                isProgrammaticUpdate = false
            }
        }

        fun convertTargetToHome(text: String) {
            if (isProgrammaticUpdate) return
            if (text.isBlank()) return

            try {
                val normalized = normalizeTimeInput(text) ?: return
                val targetTime = LocalTime.parse(normalized, timeOutputFormatter)
                val targetDate = LocalDate.now(targetZone)
                val targetDateTime = ZonedDateTime.of(targetDate, targetTime, targetZone)
                val homeDateTime = targetDateTime.withZoneSameInstant(homeZone)

                isProgrammaticUpdate = true

                homeTimeInput.setText(homeDateTime.format(timeOutputFormatter))
                targetDayInfoText.text = "Today"
                homeDayInfoText.text = describeRelativeDay(targetDate, homeDateTime.toLocalDate())

                isProgrammaticUpdate = false
            } catch (_: Exception) {
                isProgrammaticUpdate = false
            }
        }

        homeTimeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                convertHomeToTarget(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        targetTimeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                convertTargetToHome(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        homeTimeInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val normalized = normalizeTimeInput(homeTimeInput.text.toString())
                if (normalized != null) {
                    homeTimeInput.setText(normalized)
                    homeTimeInput.setSelection(normalized.length)
                    convertHomeToTarget(normalized)
                }
            }
        }

        targetTimeInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val normalized = normalizeTimeInput(targetTimeInput.text.toString())
                if (normalized != null) {
                    targetTimeInput.setText(normalized)
                    targetTimeInput.setSelection(normalized.length)
                    convertTargetToHome(normalized)
                }
            }
        }

        val nowHome = ZonedDateTime.now(homeZone)
        val nowHomeText = nowHome.format(timeOutputFormatter)
        homeTimeInput.setText(nowHomeText)
        convertHomeToTarget(nowHomeText)

        AlertDialog.Builder(this)
            .setTitle("Convert time: ${item.name}")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun getTargetZoneForClock(item: ClockItem): ZoneId {
        if (item.zoneId != null) {
            return ZoneId.of(item.zoneId)
        }

        val homeNow = ZonedDateTime.now(ZoneId.of(homeZoneId))
        val totalSeconds = homeNow.offset.totalSeconds + (item.offsetHours ?: 0) * 3600
        val zoneOffset = ZoneOffset.ofTotalSeconds(totalSeconds)
        return zoneOffset
    }

    private fun findMatchingZone(input: String): String? {
        cityAliases.entries.firstOrNull { it.key.equals(input, ignoreCase = true) }?.let {
            return it.value
        }

        zoneSuggestions.firstOrNull { it.equals(input, ignoreCase = true) }?.let {
            return it
        }

        zoneSuggestions.firstOrNull {
            it.substringAfterLast("/")
                .replace("_", " ")
                .equals(input, ignoreCase = true)
        }?.let {
            return it
        }

        cityAliases.entries.firstOrNull { it.key.contains(input, ignoreCase = true) }?.let {
            return it.value
        }

        return zoneSuggestions.firstOrNull {
            it.contains(input, ignoreCase = true) ||
                    it.substringAfterLast("/")
                        .replace("_", " ")
                        .contains(input, ignoreCase = true)
        }
    }

    private fun saveClocks() {
        val jsonArray = JSONArray()

        for (item in selectedClocks) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("offsetHours", item.offsetHours)
            obj.put("zoneId", item.zoneId)
            jsonArray.put(obj)
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CLOCKS_JSON, jsonArray.toString())
            .apply()
    }

    private fun loadClocks() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CLOCKS_JSON, null) ?: return

        try {
            val jsonArray = JSONArray(jsonString)
            selectedClocks.clear()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val name = obj.getString("name")
                val offsetHours =
                    if (obj.isNull("offsetHours")) null else obj.getInt("offsetHours")
                val zoneId =
                    if (obj.isNull("zoneId")) null else obj.getString("zoneId")

                selectedClocks.add(
                    ClockItem(
                        name = name,
                        offsetHours = offsetHours,
                        zoneId = zoneId
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
}