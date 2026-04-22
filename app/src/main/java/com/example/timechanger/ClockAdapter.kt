package com.example.timechanger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ClockAdapter(
    private val items: MutableList<String>,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<ClockAdapter.ClockViewHolder>() {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")

    class ClockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cityNameText: TextView = itemView.findViewById(R.id.cityNameText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val zoneText: TextView = itemView.findViewById(R.id.zoneText)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clock, parent, false)
        return ClockViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClockViewHolder, position: Int) {
        val zoneId = items[position]
        val now = ZonedDateTime.now(java.time.ZoneId.of(zoneId))

        holder.cityNameText.text = prettifyZoneName(zoneId)
        holder.timeText.text = now.format(timeFormatter)
        holder.dateText.text = now.format(dateFormatter)
        holder.zoneText.text = zoneId

        holder.removeButton.setOnClickListener {
            onRemove(zoneId)
        }
    }

    override fun getItemCount(): Int = items.size

    fun refreshTimes() {
        notifyDataSetChanged()
    }

    private fun prettifyZoneName(zoneId: String): String {
        return zoneId.substringAfterLast("/").replace("_", " ")
    }
}