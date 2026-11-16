package com.example.dualpane

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.DateFormat
import java.util.Date

class FileAdapter(
    private val selected: Set<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    private var items: List<File> = emptyList()
    var showFullPath = false
    private val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun submit(list: List<File>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val info: TextView = v.findViewById(R.id.info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = items[position]
        h.icon.text = iconFor(f)
        h.name.text = f.name
        val date = df.format(Date(f.lastModified()))
        h.info.text = when {
            showFullPath -> f.parent ?: ""
            f.isDirectory -> "${f.list()?.size ?: 0} items  •  $date"
            else -> "${FileOps.humanSize(f.length())}  •  $date"
        }
        h.itemView.setBackgroundColor(
            if (f in selected) h.itemView.context.getColor(R.color.selected) else Color.TRANSPARENT
        )
        h.itemView.setOnClickListener { onClick(f) }
        h.itemView.setOnLongClickListener { onLongClick(f); true }
    }

    private fun iconFor(f: File): String {
        if (f.isDirectory) return "📁"
        return when (f.extension.lowercase()) {
            "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "🎬"
            "mp3", "wav", "flac", "m4a", "ogg", "aac" -> "🎵"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "🖼"
            "zip", "rar", "7z", "tar", "gz" -> "🗜"
            "pdf" -> "📕"
            "apk" -> "📦"
            else -> "📄"
        }
    }
}
