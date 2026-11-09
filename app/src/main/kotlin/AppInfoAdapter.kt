// Appurrge, GPL-3.0-or-later. Based on Batch Uninstaller by Sarbajit Saha.
package com.appurrge

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.appurrge.databinding.AppListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Adapter for the plain ListView. Also supplies the scroll-bar popup text. */
class AppInfoAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconLoader
) : BaseAdapter() {

    /** What the scroll popup describes; follows the sort order. */
    var sortMode = SortMode.DATE_DESC

    private var rows: List<Row> = emptyList()
    private val inflater = LayoutInflater.from(context)
    private val dayFormat = DateFormat.getMediumDateFormat(context)
    private val monthFormat = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMyyyy"), Locale.getDefault()
    )

    private class Holder(val binding: AppListItemBinding) {
        var iconJob: Job? = null
    }

    fun submit(newRows: List<Row>) {
        if (newRows == rows) return
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int): Row = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = convertView?.tag as? Holder
            ?: Holder(AppListItemBinding.inflate(inflater, parent, false)).also { it.binding.root.tag = it }
        val row = rows[position]
        val app = row.app
        val b = holder.binding
        b.tvAppname.text = app.appName
        b.tvDetails.text = dayFormat.format(Date(app.installTime)) + " · " + Formatter.formatShortFileSize(context, app.size)
        b.root.isActivated = row.selected

        holder.iconJob?.cancel()
        val cached = icons.cached(app.packageName)
        if (cached != null) {
            holder.iconJob = null
            b.imgIcon.setImageBitmap(cached)
        } else {
            b.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.iconJob = scope.launch { icons.load(app.packageName)?.let { b.imgIcon.setImageBitmap(it) } }
        }
        return b.root
    }

    /** Text next to the scroll thumb: a letter (by name), a month (by date) or a size (by size). */
    fun popupText(position: Int): String {
        if (position < 0 || position >= rows.size) return ""
        return AppListLogic.popupLabel(
            rows[position].app, sortMode,
            monthLabel = { monthFormat.format(Date(it)) },
            sizeLabel = { Formatter.formatShortFileSize(context, it) }
        )
    }
}
