package com.karthikhegde.meterpod

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.converter.CategoryAdapter
import com.karthikhegde.meterpod.converter.UnitCatalog
import com.karthikhegde.meterpod.converter.UnitCategory

/** Unit Converter home: the list of quantity categories (Angle, Length, Mass, ...). */
class UnitConverterHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_unit_converter_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.categoryList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = CategoryAdapter(UnitCatalog.all) { category -> openCategory(category) }
    }

    private fun openCategory(category: UnitCategory) {
        (activity as? MainActivity)?.pushFragment(
            UnitConverterCategoryFragment.newInstance(category.id),
            "unit_converter_${category.id}"
        )
    }
}
