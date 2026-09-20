package com.karthikhegde.meterpod.sensors

import android.hardware.Sensor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.R

class SensorListAdapter(
    private val sensors: List<Sensor>,
    private val onClick: (Sensor) -> Unit
) : RecyclerView.Adapter<SensorListAdapter.SensorViewHolder>() {

    class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.sensorNameText)
        val typeText: TextView = view.findViewById(R.id.sensorTypeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = sensors[position]
        holder.nameText.text = sensor.name
        // stringType is API 20+; minSdk here is 24, so it's always available.
        holder.typeText.text = sensor.stringType
        holder.itemView.setOnClickListener { onClick(sensor) }
    }

    override fun getItemCount(): Int = sensors.size
}
