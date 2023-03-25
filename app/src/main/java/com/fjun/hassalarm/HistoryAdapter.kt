package com.fjun.hassalarm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fjun.hassalarm.databinding.RowHistoryBinding
import com.fjun.hassalarm.history.Publish
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(private val onItemClick: (Publish) -> Unit) :
    ListAdapter<Publish, RecyclerView.ViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        Row(RowHistoryBinding.inflate(LayoutInflater.from(parent.context)), onItemClick)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? Row)?.bind(getItem(position))
    }

    class Row(private val bindings: RowHistoryBinding, private val onItemClick: (Publish) -> Unit) :
        RecyclerView.ViewHolder(bindings.root) {

        fun bind(publish: Publish) {
            val context = bindings.root.context
            bindings.timestamp.text =
                context.getString(R.string.history_row_publish_time, timeString(publish.timestamp))
            bindings.successful.setImageResource(
                when (publish.successful) {
                    true -> R.drawable.ic_check_green_24dp
                    false -> R.drawable.ic_error_outline_red_24dp
                }
            )
            bindings.triggerTimestamp.text = context.getString(
                R.string.history_row_publish_trigger_time,
                timeString(publish.triggerTimestamp, includeSeconds = false)
            )

            val hasErrorMessage = !publish.successful && !publish.errorMessage.isNullOrEmpty()
            val hasCreatorPackage = !publish.creatorPackage.isNullOrEmpty()
            bindings.message.text = when {
                hasErrorMessage -> publish.errorMessage
                hasCreatorPackage -> publish.creatorPackage
                else -> ""
            }
            bindings.message.isVisible = hasErrorMessage || hasCreatorPackage
            if (hasErrorMessage) {
                bindings.root.setOnClickListener { onItemClick(publish) }
            } else {
                bindings.root.setOnClickListener(null)
            }
        }

        private fun timeString(timestamp: Long?, includeSeconds: Boolean = true): String {
            if (timestamp == null) {
                return ""
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            return SimpleDateFormat(
                "yy-MM-dd HH:mm" + (if (includeSeconds) {
                    ":ss"
                } else {
                    ""
                }), Locale.getDefault()
            ).format(calendar.time)
        }
    }

    companion object {
        val diffCallback = object : DiffUtil.ItemCallback<Publish>() {
            override fun areItemsTheSame(oldItem: Publish, newItem: Publish): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Publish, newItem: Publish): Boolean =
                oldItem == newItem
        }
    }
}