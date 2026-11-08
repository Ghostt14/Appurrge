// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.appurrge.databinding.AppListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Diff-based adapter. Rows are rescaled in code, so the Size slider works live. */
class AppInfoAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconLoader,
    private val onRowClick: (AppInfo) -> Unit
) : ListAdapter<Row, AppInfoAdapter.ViewHolder>(DIFF) {

    /** What the scroll popup describes; follows the sort order. */
    var sortMode = SortMode.DATE_DESC

    /** Index into [ListSize.SCALES]; changing it re-binds every visible row. */
    var sizeLevel = ListSize.DEFAULT
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    private val density = context.resources.displayMetrics.density
    private val dayFormat = DateFormat.getMediumDateFormat(context)
    private val monthFormat = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMyyyy"), Locale.getDefault()
    )

    class ViewHolder(val binding: AppListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var iconJob: Job? = null
        var appliedLevel = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(AppListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        holder.binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onRowClick(getItem(position).app)
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val app = row.app
        val b = holder.binding
        if (holder.appliedLevel != sizeLevel) {
            applySize(b)
            holder.appliedLevel = sizeLevel
        }
        b.tvAppname.text = app.appName
        b.tvDetails.text = dayFormat.format(Date(app.installTime)) + " · " + Formatter.formatShortFileSize(context, app.size)
        b.root.isActivated = row.selected
        b.imgCheck.visibility = if (row.selected) View.VISIBLE else View.GONE

        holder.iconJob?.cancel()
        val cached = icons.cached(app.packageName)
        if (cached != null) {
            holder.iconJob = null
            b.imgIcon.setImageBitmap(cached)
        } else {
            b.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.iconJob = scope.launch { icons.load(app.packageName)?.let { b.imgIcon.setImageBitmap(it) } }
        }
    }

    /** The XML holds the medium size; scale padding, icons and text from it. */
    private fun applySize(b: AppListItemBinding) {
        val s = ListSize.scale(sizeLevel)
        fun px(dp: Float) = (dp * density * s).toInt()
        b.root.setPadding(b.root.paddingLeft, px(8f), b.root.paddingRight, px(8f))
        b.imgIcon.layoutParams = b.imgIcon.layoutParams.apply { width = px(40f); height = px(40f) }
        b.imgCheck.layoutParams = b.imgCheck.layoutParams.apply { width = px(22f); height = px(22f) }
        b.tvAppname.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f * s)
        b.tvDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * s)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.iconJob?.cancel()
        holder.iconJob = null
    }

    /** Text next to the scroll thumb: a letter (by name), a month (by date) or a size (by size). */
    fun popupText(position: Int): String {
        if (position < 0 || position >= itemCount) return ""
        return AppListLogic.popupLabel(
            getItem(position).app, sortMode,
            monthLabel = { monthFormat.format(Date(it)) },
            sizeLabel = { Formatter.formatShortFileSize(context, it) }
        )
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.app.packageName == newItem.app.packageName
            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}
