package com.karthikhegde.meterpod

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.sensors.SensorListAdapter

/** Lists every sensor SensorManager reports for this device (Sensor.TYPE_ALL). */
class SensorTesterListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sensor_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sensorManager = requireContext().getSystemService(SensorManager::class.java)
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL).sortedBy { it.name }

        val recyclerView = view.findViewById<RecyclerView>(R.id.sensorList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = SensorListAdapter(sensors) { sensor ->
            (activity as? MainActivity)?.pushFragment(
                SensorDetailFragment.newInstance(sensor.type, sensor.name),
                "sensor_${sensor.type}_${sensor.name}"
            )
        }
    }
}
