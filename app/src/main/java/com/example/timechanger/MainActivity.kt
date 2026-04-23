package com.example.timechanger

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.time.ZoneId

class MainActivity : AppCompatActivity() {

    private lateinit var homeZoneText: TextView
    private lateinit var nameInput: EditText
    private lateinit var offsetInput: EditText
    private lateinit var timezoneInput: AutoCompleteTextView
    private lateinit var addClockButton: Button
    private lateinit var clocksRecyclerView: RecyclerView
    private lateinit var clockAdapter: ClockAdapter

    private val selectedClocks = mutableListOf<ClockItem>()

    private val homeZoneId = "Asia/Tbilisi"

    private lateinit var zoneSuggestions: List<String>

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
        nameInput = findViewById(R.id.nameInput)
        offsetInput = findViewById(R.id.offsetInput)
        timezoneInput = findViewById(R.id.timezoneInput)
        addClockButton = findViewById(R.id.addClockButton)
        clocksRecyclerView = findViewById(R.id.clocksRecyclerView)

        homeZoneText.text = "Home zone: $homeZoneId"

        zoneSuggestions = ZoneId.getAvailableZoneIds()
            .toList()
            .sorted()

        setupAutocomplete()
        setupRecyclerView()
        loadClocks()

        if (selectedClocks.isEmpty()) {
            selectedClocks.add(ClockItem(name = "Tbilisi", zoneId = "Asia/Tbilisi"))
            selectedClocks.add(ClockItem(name = "London", zoneId = "Europe/London"))
            selectedClocks.add(ClockItem(name = "Seattle", offsetHours = -11))
            saveClocks()
            clockAdapter.notifyDataSetChanged()
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
        timezoneInput.setAdapter(adapter)
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
            }
        )

        clocksRecyclerView.layoutManager = LinearLayoutManager(this)
        clocksRecyclerView.adapter = clockAdapter
    }

    private fun addClockFromInput() {
        val name = nameInput.text.toString().trim()
        val offsetText = offsetInput.text.toString().trim()
        val timezoneRaw = timezoneInput.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter clock name", Toast.LENGTH_SHORT).show()
            return
        }

        val zoneId = if (timezoneRaw.isNotEmpty()) {
            findMatchingZone(timezoneRaw)
        } else {
            null
        }

        if (timezoneRaw.isNotEmpty() && zoneId == null) {
            Toast.makeText(this, "Timezone not found", Toast.LENGTH_SHORT).show()
            return
        }

        val offset = if (offsetText.isNotEmpty()) {
            offsetText.toIntOrNull()
        } else {
            null
        }

        if (zoneId == null && offset == null) {
            Toast.makeText(this, "Set either offset or timezone", Toast.LENGTH_SHORT).show()
            return
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

        nameInput.setText("")
        offsetInput.setText("")
        timezoneInput.setText("")
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