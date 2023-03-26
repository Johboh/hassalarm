package com.fjun.hassalarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fjun.hassalarm.databinding.RowIgnoredAppsBinding

/**
 * Adapter for showing banned/ignored apps.
 */
class BanAdapter(private val interactionListener: (packageName: String) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var packages: List<String> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun set(packages: List<String>) {
        this.packages = packages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = RowIgnoredAppsBinding.inflate(LayoutInflater.from(parent.context))
        return Row(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = holder as Row
        row.bind(packages[position])
    }

    override fun getItemCount(): Int = packages.size

    override fun getItemId(position: Int): Long
        = packages[position].hashCode().toLong()

    private inner class Row(private val binding: RowIgnoredAppsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(packageName: String) {
            with(binding) {
                title.text = packageName
                remove.setOnClickListener {
                    interactionListener(packageName)
                }
            }
        }
    }
}