package com.example.timechanger

data class ClockItem(
    val name: String,
    val offsetHours: Int? = null,
    val zoneId: String? = null
)