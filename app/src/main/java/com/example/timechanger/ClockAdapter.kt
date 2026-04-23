package com.example.timechanger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ClockAdapter(
    private val items: MutableList<ClockItem>,
    private val homeZoneId: String,
    private val onRemove: (ClockItem) -> Unit,
    private val onEdit: (ClockItem) -> Unit
) : RecyclerView.Adapter<ClockAdapter.ClockViewHolder>() {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")

    class ClockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cityNameText: TextView = itemView.findViewById(R.id.cityNameText)
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val zoneText: TextView = itemView.findViewById(R.id.zoneText)
        val modeText: TextView = itemView.findViewById(R.id.modeText)
        val editButton: Button = itemView.findViewById(R.id.editButton)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clock, parent, false)
        return ClockViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClockViewHolder, position: Int) {
        val item = items[position]

        val now = if (item.zoneId != null) {
            ZonedDateTime.now(ZoneId.of(item.zoneId))
        } else {
            val homeNow = ZonedDateTime.now(ZoneId.of(homeZoneId))
            homeNow.plusHours((item.offsetHours ?: 0).toLong())
        }

        holder.cityNameText.text = item.name
        holder.timeText.text = now.format(timeFormatter)
        holder.dateText.text = now.format(dateFormatter)

        if (item.zoneId != null) {
            holder.zoneText.text = item.zoneId
            holder.modeText.text = "Timezone mode"
        } else {
            holder.zoneText.text = "From home zone: $homeZoneId"
            val offset = item.offsetHours ?: 0
            val sign = if (offset >= 0) "+" else ""
            holder.modeText.text = "Manual offset: ${sign}${offset}h"
        }

        holder.editButton.setOnClickListener {
            onEdit(item)
        }

        holder.removeButton.setOnClickListener {
            onRemove(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun refreshTimes() {
        notifyDataSetChanged()
    }
}