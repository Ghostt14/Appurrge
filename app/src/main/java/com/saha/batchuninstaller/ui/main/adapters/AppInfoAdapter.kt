/*
 * This file is part of Batch Uninstaller.
 *
 * Batch Uninstaller is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Batch Uninstaller is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Batch Uninstaller.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package com.saha.batchuninstaller.ui.main.adapters

import android.content.Context
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saha.batchuninstaller.AppInfo
import com.saha.batchuninstaller.R
import com.saha.batchuninstaller.databinding.AppListItemBinding
import com.saha.batchuninstaller.ui.main.Row
import com.saha.batchuninstaller.utils.AppListLogic
import com.saha.batchuninstaller.utils.IconLoader
import com.saha.batchuninstaller.utils.SortMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diff-based list adapter (only rows that really changed are redrawn) that also tells the
 * fast-scroll thumb what to show in its popup.
 */
class AppInfoAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconLoader,
    private val onRowClick: (AppInfo) -> Unit
) : ListAdapter<Row, AppInfoAdapter.ViewHolder>(DIFF), PopupTextProvider {

    /** What the fast-scroll popup describes; follows the current sort order. */
    var sortMode: SortMode = SortMode.DATE_DESC

    private val dayFormat = DateFormat.getMediumDateFormat(context)
    private val monthFormat = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMyyyy"),
        Locale.getDefault()
    )

    class ViewHolder(val binding: AppListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var iconJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AppListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onRowClick(getItem(position).app)
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val app = row.app
        val b = holder.binding
        b.tvAppname.text = app.appName
        b.tvDetails.text = context.getString(
            R.string.row_details,
            dayFormat.format(Date(app.installTime)),
            Formatter.formatShortFileSize(context, app.size)
        )
        b.root.isActivated = row.selected
        b.imgCheck.visibility = if (row.selected) View.VISIBLE else View.GONE

        holder.iconJob?.cancel()
        holder.iconJob = null
        val cached = icons.cached(app.packageName)
        if (cached != null) {
            b.imgIcon.setImageBitmap(cached)
        } else {
            b.imgIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            holder.iconJob = scope.launch {
                icons.load(app.packageName)?.let { b.imgIcon.setImageBitmap(it) }
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.iconJob?.cancel()
        holder.iconJob = null
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        if (position < 0 || position >= itemCount) return ""
        return AppListLogic.popupLabel(
            getItem(position).app,
            sortMode,
            monthLabel = { monthFormat.format(Date(it)) },
            sizeLabel = { Formatter.formatShortFileSize(context, it) }
        )
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) =
                oldItem.app.packageName == newItem.app.packageName

            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}
