package com.karthikhegde.meterpod.tools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.karthikhegde.meterpod.R

class ToolsAdapter(
    private var items: List<ToolItem>,
    private val onClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ToolViewHolder>() {

    class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconContainer: FrameLayout = view.findViewById(R.id.iconContainer)
        val titleText: TextView = view.findViewById(R.id.titleText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return ToolViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        val tool = items[position]

        holder.iconContainer.removeAllViews()
        val iconView = tool.createIconView(holder.iconContainer.context)
        holder.iconContainer.addView(
            iconView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        holder.titleText.text = tool.title
        holder.itemView.setOnClickListener { onClick(tool) }
    }

    override fun getItemCount(): Int = items.size

    /** Swaps in a new list of items without recreating the adapter. */
    fun updateItems(newItems: List<ToolItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
