package com.karthikhegde.meterpod

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.karthikhegde.meterpod.converter.UnitCatalog
import com.karthikhegde.meterpod.converter.UnitCategory
import com.karthikhegde.meterpod.converter.UnitDefinition
import kotlin.math.abs

/**
 * Two-sided unit converter for one category: pick a unit on each side, type
 * a number into either field, and the other side updates live.
 *
 * The two EditTexts each have a TextWatcher, and each watcher would
 * normally re-trigger the other's watcher when it programmatically sets
 * text - the [isUpdating] guard flag prevents that feedback loop.
 */
class UnitConverterCategoryFragment : Fragment() {

    private lateinit var category: UnitCategory
    private lateinit var leftUnit: UnitDefinition
    private lateinit var rightUnit: UnitDefinition

    private lateinit var categoryTitleText: TextView
    private lateinit var leftUnitButton: TextView
    private lateinit var rightUnitButton: TextView
    private lateinit var leftValueInput: EditText
    private lateinit var rightValueInput: EditText

    private var isUpdating = false
    private var lastEditedSide = Side.LEFT

    private enum class Side { LEFT, RIGHT }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_unit_converter_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = arguments?.getString(ARG_CATEGORY_ID) ?: return
        category = UnitCatalog.byId(categoryId) ?: return
        leftUnit = category.units[0]
        rightUnit = category.units.getOrElse(1) { category.units[0] }

        categoryTitleText = view.findViewById(R.id.categoryTitleText)
        leftUnitButton = view.findViewById(R.id.leftUnitButton)
        rightUnitButton = view.findViewById(R.id.rightUnitButton)
        leftValueInput = view.findViewById(R.id.leftValueInput)
        rightValueInput = view.findViewById(R.id.rightValueInput)

        categoryTitleText.text = category.name
        updateUnitButtons()

        leftUnitButton.setOnClickListener { showUnitPicker(isLeft = true) }
        rightUnitButton.setOnClickListener { showUnitPicker(isLeft = false) }

        leftValueInput.addTextChangedListener(watcherFor(Side.LEFT))
        rightValueInput.addTextChangedListener(watcherFor(Side.RIGHT))

        leftValueInput.setText("1")
    }

    private fun watcherFor(side: Side) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating) return
            lastEditedSide = side
            recompute()
        }
    }

    private fun recompute() {
        isUpdating = true
        try {
            if (lastEditedSide == Side.LEFT) {
                val input = leftValueInput.text.toString().toDoubleOrNull()
                if (input != null) {
                    val base = leftUnit.toBase(input)
                    rightValueInput.setText(formatResult(rightUnit.fromBase(base)))
                } else {
                    rightValueInput.setText("")
                }
            } else {
                val input = rightValueInput.text.toString().toDoubleOrNull()
                if (input != null) {
                    val base = rightUnit.toBase(input)
                    leftValueInput.setText(formatResult(leftUnit.fromBase(base)))
                } else {
                    leftValueInput.setText("")
                }
            }
        } finally {
            isUpdating = false
        }
    }

    private fun showUnitPicker(isLeft: Boolean) {
        val names = category.units.map { "${it.displayName} (${it.symbol})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select unit")
            .setItems(names) { _, which ->
                val selected = category.units[which]
                if (isLeft) leftUnit = selected else rightUnit = selected
                updateUnitButtons()
                recompute()
            }
            .show()
    }

    private fun updateUnitButtons() {
        leftUnitButton.text = "${leftUnit.displayName} (${leftUnit.symbol}) ▾"
        rightUnitButton.text = "${rightUnit.displayName} (${rightUnit.symbol}) ▾"
    }

    private fun formatResult(value: Double): String {
        if (value.isNaN()) return ""
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        if (value == 0.0) return "0"

        val absValue = abs(value)
        return if (absValue < 0.0001 || absValue >= 1.0e9) {
            String.format("%.6e", value)
        } else {
            var text = String.format("%.6f", value)
            if (text.contains(".")) text = text.trimEnd('0').trimEnd('.')
            text
        }
    }

    companion object {
        private const val ARG_CATEGORY_ID = "category_id"

        fun newInstance(categoryId: String): UnitConverterCategoryFragment {
            return UnitConverterCategoryFragment().apply {
                arguments = Bundle().apply { putString(ARG_CATEGORY_ID, categoryId) }
            }
        }
    }
}
