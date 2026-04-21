package com.example.timechanger

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var fromCityInput: AutoCompleteTextView
    private lateinit var toCityInput: AutoCompleteTextView
    private lateinit var pickTimeButton: Button
    private lateinit var convertButton: Button
    private lateinit var selectedTimeText: TextView
    private lateinit var resultText: TextView

    private var selectedHour: Int = 12
    private var selectedMinute: Int = 0

    private val cityToZone = mapOf(
        "Tbilisi" to "Asia/Tbilisi",
        "Moscow" to "Europe/Moscow",
        "London" to "Europe/London",
        "Paris" to "Europe/Paris",
        "Berlin" to "Europe/Berlin",
        "Rome" to "Europe/Rome",
        "Madrid" to "Europe/Madrid",
        "New York" to "America/New_York",
        "Los Angeles" to "America/Los_Angeles",
        "Chicago" to "America/Chicago",
        "Tokyo" to "Asia/Tokyo",
        "Seoul" to "Asia/Seoul",
        "Dubai" to "Asia/Dubai",
        "Beijing" to "Asia/Shanghai",
        "Delhi" to "Asia/Kolkata",
        "Sydney" to "Australia/Sydney"
    )

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fromCityInput = findViewById(R.id.fromCityInput)
        toCityInput = findViewById(R.id.toCityInput)
        pickTimeButton = findViewById(R.id.pickTimeButton)
        convertButton = findViewById(R.id.convertButton)
        selectedTimeText = findViewById(R.id.selectedTimeText)
        resultText = findViewById(R.id.resultText)

        setupCityDropdowns()
        updateSelectedTimeLabel()

        fromCityInput.setText("Tbilisi", false)
        toCityInput.setText("London", false)

        pickTimeButton.setOnClickListener {
            showTimePicker()
        }

        convertButton.setOnClickListener {
            convertTime()
        }
    }

    private fun setupCityDropdowns() {
        val cities = cityToZone.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, cities)

        fromCityInput.setAdapter(adapter)
        toCityInput.setAdapter(adapter)
    }

    private fun updateSelectedTimeLabel() {
        val time = LocalTime.of(selectedHour, selectedMinute)
        selectedTimeText.text = "Selected time: ${time.format(timeFormatter)}"
    }

    private fun showTimePicker() {
        val dialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                updateSelectedTimeLabel()
            },
            selectedHour,
            selectedMinute,
            true
        )
        dialog.show()
    }

    private fun convertTime() {
        val fromCity = fromCityInput.text.toString().trim()
        val toCity = toCityInput.text.toString().trim()

        if (fromCity !in cityToZone.keys) {
            Toast.makeText(this, "Choose a valid source city", Toast.LENGTH_SHORT).show()
            return
        }

        if (toCity !in cityToZone.keys) {
            Toast.makeText(this, "Choose a valid target city", Toast.LENGTH_SHORT).show()
            return
        }

        val fromZone = ZoneId.of(cityToZone[fromCity]!!)
        val toZone = ZoneId.of(cityToZone[toCity]!!)

        val sourceDate = LocalDate.now(fromZone)
        val sourceTime = LocalTime.of(selectedHour, selectedMinute)
        val sourceDateTime = LocalDateTime.of(sourceDate, sourceTime)

        val zonedSource = sourceDateTime.atZone(fromZone)
        val zonedTarget = zonedSource.withZoneSameInstant(toZone)

        val convertedTime = zonedTarget.toLocalTime().format(timeFormatter)
        val convertedDate = zonedTarget.toLocalDate()

        resultText.text = buildString {
            append("${sourceTime.format(timeFormatter)} in $fromCity\n")
            append("= $convertedTime in $toCity")

            if (convertedDate != sourceDate) {
                append("\nDate changes to: $convertedDate")
            }
        }
    }
}