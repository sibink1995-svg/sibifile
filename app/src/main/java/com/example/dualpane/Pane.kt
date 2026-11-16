package com.example.dualpane

import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

enum class SortBy { NAME, SIZE, DATE }

/** One of the two file panels. */
class Pane(
    root: View,
    private val onFocus: (Pane) -> Unit,
    private val onOpenFile: (File) -> Unit
) {
    private val pathView: TextView = root.findViewById(R.id.path)
    private val list: RecyclerView = root.findViewById(R.id.list)

    val storageRoot: File = Environment.getExternalStorageDirectory()
    var dir: File = storageRoot
        private set
    val selected = linkedSetOf<File>()

    var showHidden = false
    var sortBy = SortBy.NAME

    /** Non-null while showing search results instead of a directory. */
    private var searchResults: List<File>? = null

    private val adapter = FileAdapter(selected, ::onItemClick, ::onItemLongClick)

    init {
        list.layoutManager = LinearLayoutManager(root.context)
        list.adapter = adapter
        pathView.setOnClickListener { onFocus(this) }
        list.setOnTouchListener { _, _ -> onFocus(this); false }
    }

    fun setActive(active: Boolean) {
        pathView.setBackgroundColor(
            pathView.context.getColor(if (active) R.color.pane_bar_active else R.color.pane_bar)
        )
    }

    fun navigate(target: File) {
        if (!target.isDirectory) return
        dir = target
        searchResults = null
        selected.clear()
        refresh()
    }

    /** @return false if already at the top and nothing happened. */
    fun up(): Boolean {
        if (searchResults != null) {
            searchResults = null
            selected.clear()
            refresh()
            return true
        }
        val parent = dir.parentFile ?: return false
        if (dir.absolutePath == storageRoot.absolutePath || !parent.canRead()) return false
        navigate(parent)
        return true
    }

    fun showSearch(results: List<File>, query: String) {
        searchResults = results
        selected.clear()
        refresh(title = "🔍 \"$query\" (${results.size})")
    }

    fun refresh(title: String? = null) {
        val sr = searchResults
        val files: List<File> = sr ?: (dir.listFiles()?.toList() ?: emptyList())
        val visible = files.filter { showHidden || !it.name.startsWith(".") }
        val cmp: Comparator<File> = when (sortBy) {
            SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortBy.SIZE -> compareByDescending { it.length() }
            SortBy.DATE -> compareByDescending { it.lastModified() }
        }
        val sorted = visible.sortedWith(compareByDescending<File> { it.isDirectory }.then(cmp))
        adapter.showFullPath = sr != null
        adapter.submit(sorted)
        pathView.text = title ?: dir.absolutePath
    }

    fun selectAll() {
        selected.clear()
        selected.addAll(currentFiles())
        adapter.notifyDataSetChanged()
    }

    fun clearSelection() {
        selected.clear()
        adapter.notifyDataSetChanged()
    }

    private fun currentFiles(): List<File> {
        val files = searchResults ?: (dir.listFiles()?.toList() ?: emptyList())
        return files.filter { showHidden || !it.name.startsWith(".") }
    }

    private fun onItemClick(f: File) {
        onFocus(this)
        if (selected.isNotEmpty()) {
            toggle(f)
        } else if (f.isDirectory) {
            navigate(f)
        } else {
            onOpenFile(f)
        }
    }

    private fun onItemLongClick(f: File) {
        onFocus(this)
        toggle(f)
    }

    private fun toggle(f: File) {
        if (!selected.add(f)) selected.remove(f)
        adapter.notifyDataSetChanged()
    }
}
