package com.karthikhegde.meterpod.converter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.R

class CategoryAdapter(
    private var categories: List<UnitCategory>,
    private val onClick: (UnitCategory) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val badgeColors = listOf(
        "#3A7BD5", "#E53935", "#43A047", "#FB8C00", "#8E24AA", "#00897B"
    ).map { Color.parseColor(it) }

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeText: TextView = view.findViewById(R.id.badgeText)
        val nameText: TextView = view.findViewById(R.id.categoryNameText)
        val countText: TextView = view.findViewById(R.id.unitCountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_unit_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.badgeText.text = category.badgeText
        holder.nameText.text = category.name
        holder.countText.text = "${category.units.size} units"

        val color = badgeColors[position % badgeColors.size]
        val drawable = holder.badgeText.background.mutate() as? GradientDrawable
        drawable?.setColor(color)

        holder.itemView.setOnClickListener { onClick(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateItems(newCategories: List<UnitCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
