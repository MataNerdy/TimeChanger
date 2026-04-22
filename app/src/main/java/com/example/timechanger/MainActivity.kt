package com.example.timechanger

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var cityInput: AutoCompleteTextView
    private lateinit var addClockButton: Button
    private lateinit var clocksRecyclerView: RecyclerView
    private lateinit var clockAdapter: ClockAdapter

    private val selectedZones = mutableListOf<String>()

    private val uiHandler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            clockAdapter.refreshTimes()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var zoneSuggestions: List<String>

    // Простые алиасы для популярных городов, которые не совпадают с IANA ID
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

    companion object {
        private const val PREFS_NAME = "world_clock_prefs"
        private const val KEY_SELECTED_ZONES = "selected_zones"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cityInput = findViewById(R.id.cityInput)
        addClockButton = findViewById(R.id.addClockButton)
        clocksRecyclerView = findViewById(R.id.clocksRecyclerView)

        zoneSuggestions = ZoneId.getAvailableZoneIds()
            .toList()
            .sorted()

        setupAutocomplete()
        setupRecyclerView()

        loadSavedZones()

        if (selectedZones.isEmpty()) {
            addZoneIfNeeded("Asia/Tbilisi", save = false)
            addZoneIfNeeded("Europe/London", save = false)
            addZoneIfNeeded("America/New_York", save = false)
            addZoneIfNeeded("Asia/Tokyo", save = false)
            saveZones()
        }

        addClockButton.setOnClickListener {
            addClockFromInput()
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

    private fun setupAutocomplete() {
        val suggestionItems = (zoneSuggestions + cityAliases.keys)
            .distinct()
            .sorted()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            suggestionItems
        )
        cityInput.setAdapter(adapter)
    }

    private fun setupRecyclerView() {
        clockAdapter = ClockAdapter(selectedZones) { zoneId ->
            selectedZones.remove(zoneId)
            clockAdapter.notifyDataSetChanged()
            saveZones()
        }

        clocksRecyclerView.layoutManager = LinearLayoutManager(this)
        clocksRecyclerView.adapter = clockAdapter
    }

    private fun addClockFromInput() {
        val rawInput = cityInput.text.toString().trim()

        if (rawInput.isEmpty()) {
            Toast.makeText(this, "Enter a city or timezone", Toast.LENGTH_SHORT).show()
            return
        }

        val normalized = findMatchingZone(rawInput)
        if (normalized == null) {
            Toast.makeText(this, "City/timezone not found", Toast.LENGTH_SHORT).show()
            return
        }

        addZoneIfNeeded(normalized, save = true)
        cityInput.setText("")
    }

    private fun addZoneIfNeeded(zoneId: String, save: Boolean = true) {
        if (selectedZones.contains(zoneId)) {
            Toast.makeText(this, "This clock is already added", Toast.LENGTH_SHORT).show()
            return
        }

        selectedZones.add(zoneId)
        selectedZones.sort()
        clockAdapter.notifyDataSetChanged()

        if (save) {
            saveZones()
        }
    }

    private fun findMatchingZone(input: String): String? {
        // 1) точный алиас
        cityAliases.entries.firstOrNull { it.key.equals(input, ignoreCase = true) }?.let {
            return it.value
        }

        // 2) точное совпадение по IANA ID
        zoneSuggestions.firstOrNull { it.equals(input, ignoreCase = true) }?.let {
            return it
        }

        // 3) совпадение по "человеческому" имени из конца ID
        zoneSuggestions.firstOrNull {
            it.substringAfterLast("/")
                .replace("_", " ")
                .equals(input, ignoreCase = true)
        }?.let {
            return it
        }

        // 4) частичное совпадение по алиасам
        cityAliases.entries.firstOrNull { it.key.contains(input, ignoreCase = true) }?.let {
            return it.value
        }

        // 5) частичное совпадение по IANA ID и красивому названию
        return zoneSuggestions.firstOrNull {
            it.contains(input, ignoreCase = true) ||
                    it.substringAfterLast("/")
                        .replace("_", " ")
                        .contains(input, ignoreCase = true)
        }
    }

    private fun saveZones() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SELECTED_ZONES, selectedZones.toSet())
            .apply()
    }

    private fun loadSavedZones() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_SELECTED_ZONES, emptySet()) ?: emptySet()

        selectedZones.clear()
        selectedZones.addAll(saved.sorted())
    }
}