package com.example.dualpane

import android.os.Bundle
import android.os.StatFs
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

class DiskMapActivity : AppCompatActivity() {

    private lateinit var header: TextView
    private lateinit var map: DiskMapView
    private lateinit var startDir: File
    private lateinit var current: File
    @Volatile private var token = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startDir = File(intent.getStringExtra("path") ?: "/storage/emulated/0")
        current = startDir

        header = TextView(this).apply {
            setPadding(24, 20, 24, 20)
            setBackgroundColor(getColor(R.color.pane_bar_active))
            textSize = 14f
        }
        map = DiskMapView(this).apply { onTap = ::onTapItem }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val parent = current.parentFile
                if (current.absolutePath != startDir.absolutePath && parent != null) {
                    load(parent)
                } else {
                    finish()
                }
            }
        })
        load(current)
    }

    private fun onTapItem(item: DiskMapView.Item) {
        val f = item.file
        if (f != null && f.isDirectory) load(f)
        else Toast.makeText(this, "${item.name}  •  ${FileOps.humanSize(item.size)}", Toast.LENGTH_SHORT).show()
    }

    private fun load(dir: File) {
        current = dir
        val my = ++token
        header.text = "${dir.absolutePath}\nCalculating…"
        thread {
            val children = dir.listFiles()?.toList() ?: emptyList()
            val items = children.map { DiskMapView.Item(it.name, sizeOf(it, 0), it) }
                .sortedByDescending { it.size }
            val shown = items.take(60)
            val rest = items.drop(60).sumOf { it.size }
            val all = if (rest > 0) shown + DiskMapView.Item("… other (${items.size - 60})", rest, null) else shown
            val total = items.sumOf { it.size }
            runOnUiThread {
                if (my != token) return@runOnUiThread
                map.setItems(all)
                header.text = "${dir.absolutePath}\nTotal: ${FileOps.humanSize(total)}   •   ${storageLine(dir)}"
            }
        }
    }

    private fun storageLine(dir: File): String = try {
        val st = StatFs(dir.absolutePath)
        "Free ${FileOps.humanSize(st.availableBytes)} of ${FileOps.humanSize(st.totalBytes)}"
    } catch (e: Exception) {
        ""
    }

    private fun sizeOf(f: File, depth: Int): Long {
        if (f.isFile) return f.length()
        if (depth > 25) return 0L
        var sum = 0L
        f.listFiles()?.forEach { sum += sizeOf(it, depth + 1) }
        return sum
    }
}
