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

    private val homeZoneId = "Asia/Tbilisi"
    private lateinit var zoneSuggestions: List<String>

    private val timeInputFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val timeOutputFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        homeZoneText = findViewById(R.id.homeZoneText)
        showAddClockButton = findViewById(R.id.showAddClockButton)
        clocksRecyclerView = findViewById(R.id.clocksRecyclerView)

        homeZoneText.text = "Home zone: $homeZoneId"

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

        val suggestionItems = (zoneSuggestions + cityAliases.keys)
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestionItems
        )
        addTimezoneInput.setAdapter(adapter)

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

        val suggestionItems = (zoneSuggestions + cityAliases.keys)
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestionItems
        )
        editTimezoneInput.setAdapter(adapter)

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

    private fun showConvertDialog(item: ClockItem) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_convert_time, null)

        val homeLabelText = dialogView.findViewById<TextView>(R.id.homeLabelText)
        val targetLabelText = dialogView.findViewById<TextView>(R.id.targetLabelText)
        val homeTimeInput = dialogView.findViewById<EditText>(R.id.homeTimeInput)
        val targetTimeInput = dialogView.findViewById<EditText>(R.id.targetTimeInput)
        val homeDayInfoText = dialogView.findViewById<TextView>(R.id.homeDayInfoText)
        val targetDayInfoText = dialogView.findViewById<TextView>(R.id.targetDayInfoText)

        homeLabelText.text = "Tbilisi time"
        targetLabelText.text = "${item.name} time"

        var isProgrammaticUpdate = false

        val homeZone = ZoneId.of(homeZoneId)
        val targetZone = getTargetZoneForClock(item)

        fun convertHomeToTarget(text: String) {
            if (isProgrammaticUpdate) return
            if (text.isBlank()) return

            try {
                val homeTime = LocalTime.parse(text, timeInputFormatter)
                val homeDate = LocalDate.now(homeZone)
                val homeDateTime = ZonedDateTime.of(homeDate, homeTime, homeZone)
                val targetDateTime = homeDateTime.withZoneSameInstant(targetZone)

                isProgrammaticUpdate = true
                targetTimeInput.setText(targetDateTime.format(timeOutputFormatter))
                homeDayInfoText.text = "Today"
                targetDayInfoText.text = describeRelativeDay(homeDate, targetDateTime.toLocalDate())
                isProgrammaticUpdate = false
            } catch (_: Exception) {
            }
        }

        fun convertTargetToHome(text: String) {
            if (isProgrammaticUpdate) return
            if (text.isBlank()) return

            try {
                val targetTime = LocalTime.parse(text, timeInputFormatter)
                val targetDate = LocalDate.now(targetZone)
                val targetDateTime = ZonedDateTime.of(targetDate, targetTime, targetZone)
                val homeDateTime = targetDateTime.withZoneSameInstant(homeZone)

                isProgrammaticUpdate = true
                homeTimeInput.setText(homeDateTime.format(timeOutputFormatter))
                targetDayInfoText.text = "Today"
                homeDayInfoText.text = describeRelativeDay(targetDate, homeDateTime.toLocalDate())
                isProgrammaticUpdate = false
            } catch (_: Exception) {
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