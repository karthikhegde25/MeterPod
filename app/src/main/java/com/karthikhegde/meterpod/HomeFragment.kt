package com.karthikhegde.meterpod

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.tools.ToolCatalog
import com.karthikhegde.meterpod.tools.ToolsAdapter

/** Home screen: a 3-column grid of every tool in the app. */
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.toolsRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerView.adapter = ToolsAdapter(ToolCatalog.all()) { tool ->
            (activity as? MainActivity)?.navigateToTool(tool)
        }
    }
}
