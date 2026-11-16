package com.example.dualpane

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var left: Pane
    private lateinit var right: Pane
    private lateinit var active: Pane
    private val other: Pane get() = if (active === left) right else left
    private var panesReady = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        left = Pane(findViewById(R.id.paneLeft), ::setActive, { FileOps.open(this, it) })
        right = Pane(findViewById(R.id.paneRight), ::setActive, { FileOps.open(this, it) })
        active = left
        setActive(left)

        buildToolbar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!active.up()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAccess()) {
            if (!panesReady) {
                panesReady = true
                left.navigate(left.storageRoot)
                right.navigate(File(left.storageRoot, "Download").takeIf { it.isDirectory } ?: right.storageRoot)
            } else {
                left.refresh(); right.refresh()
            }
        } else {
            requestStorageAccess()
        }
    }

    // ---------------------------------------------------------------- permissions

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            AlertDialog.Builder(this)
                .setTitle("Storage access needed")
                .setMessage("Allow \"All files access\" so the app can browse and manage your files.")
                .setCancelable(false)
                .setPositiveButton("Open settings") { _, _ ->
                    val i = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(i)
                }
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1
            )
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasStorageAccess()) onResume()
    }

    // ---------------------------------------------------------------- toolbar

    private class Btn(val icon: String, val label: String, val action: () -> Unit)

    private fun buildToolbar() {
        val buttons = listOf(
            Btn("⬆️", "Up dir") { active.up() },
            Btn("📊", "Disk map") {
                startActivity(Intent(this, DiskMapActivity::class.java).putExtra("path", active.dir.absolutePath))
            },
            Btn("📋", "Copy") { copyOrMove(move = false) },
            Btn("✂️", "Move") { copyOrMove(move = true) },
            Btn("✏️", "Rename") { rename() },
            Btn("🗑️", "Delete") { delete() },
            Btn("🗜", "Create ZIP") { createZip() },
            Btn("📁", "New folder") { newItem(folder = true) },
            Btn("📄", "New text file") { newItem(folder = false) },
            Btn("☑️", "Select all") { active.selectAll() },
            Btn("👁️", "Show hidden") { toggleHidden() },
            Btn("↕️", "Sort by") { sortDialog() },
            Btn("🔍", "Find") { findDialog() },
            Btn("🔄", "Refresh") { left.refresh(); right.refresh() }
        )
        val bar = findViewById<LinearLayout>(R.id.toolbar)
        val inflater = LayoutInflater.from(this)
        for (b in buttons) {
            val v = inflater.inflate(R.layout.toolbar_button, bar, false)
            v.findViewById<TextView>(R.id.btnIcon).text = b.icon
            v.findViewById<TextView>(R.id.btnLabel).text = b.label
            v.setOnClickListener { b.action() }
            bar.addView(v)
        }
    }

    private fun setActive(p: Pane) {
        active = p
        left.setActive(p === left)
        right.setActive(p === right)
    }

    // ---------------------------------------------------------------- actions

    private fun selection(): List<File> = active.selected.toList()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun requireSelection(): List<File>? {
        val s = selection()
        if (s.isEmpty()) toast("Long-press files to select them first")
        return s.ifEmpty { null }
    }

    /** Runs [work] off the main thread, then refreshes both panes. */
    private fun background(msg: String, work: () -> String) {
        toast(msg)
        thread {
            val result = work()
            runOnUiThread {
                active.clearSelection()
                left.refresh(); right.refresh()
                toast(result)
            }
        }
    }

    private fun copyOrMove(move: Boolean) {
        val files = requireSelection() ?: return
        val dest = other.dir
        background(if (move) "Moving…" else "Copying…") {
            val ok = files.count { if (move) FileOps.move(it, dest) else FileOps.copy(it, dest) }
            "${if (move) "Moved" else "Copied"} $ok/${files.size} to ${dest.name}"
        }
    }

    private fun delete() {
        val files = requireSelection() ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete ${files.size} item(s)?")
            .setMessage(files.joinToString("\n") { it.name }.take(400))
            .setPositiveButton("Delete") { _, _ ->
                background("Deleting…") {
                    val ok = files.count { FileOps.delete(it) }
                    "Deleted $ok/${files.size}"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rename() {
        val files = requireSelection() ?: return
        if (files.size != 1) return toast("Select exactly one item")
        val f = files[0]
        prompt("Rename", f.name) { name ->
            toast(if (FileOps.rename(f, name)) "Renamed" else "Rename failed")
            active.clearSelection()
            active.refresh()
        }
    }

    private fun newItem(folder: Boolean) {
        val dir = active.dir
        prompt(if (folder) "New folder" else "New text file", if (folder) "New folder" else "note.txt") { name ->
            val f = File(dir, name)
            val ok = try {
                if (name.isBlank() || f.exists()) false
                else if (folder) f.mkdirs() else f.createNewFile()
            } catch (e: Exception) {
                false
            }
            toast(if (ok) "Created" else "Could not create")
            active.refresh()
        }
    }

    private fun createZip() {
        val files = requireSelection() ?: return
        val dir = active.dir
        val base = if (files.size == 1) files[0].nameWithoutExtension else "archive"
        prompt("Create ZIP", "$base.zip") { name ->
            val out = FileOps.uniqueTarget(dir, if (name.endsWith(".zip")) name else "$name.zip")
            background("Zipping…") {
                if (FileOps.zip(files, out)) "Created ${out.name}" else "ZIP failed"
            }
        }
    }

    private fun toggleHidden() {
        val v = !active.showHidden
        left.showHidden = v; right.showHidden = v
        left.refresh(); right.refresh()
        toast(if (v) "Hidden files shown" else "Hidden files hidden")
    }

    private fun sortDialog() {
        val names = arrayOf("Name", "Size (largest first)", "Date (newest first)")
        AlertDialog.Builder(this)
            .setTitle("Sort by")
            .setSingleChoiceItems(names, active.sortBy.ordinal) { d, which ->
                val s = SortBy.values()[which]
                left.sortBy = s; right.sortBy = s
                left.refresh(); right.refresh()
                d.dismiss()
            }
            .show()
    }

    private fun findDialog() {
        val dir = active.dir
        prompt("Find in ${dir.name}", "") { q ->
            if (q.isBlank()) return@prompt
            val pane = active
            toast("Searching…")
            thread {
                val res = FileOps.search(dir, q)
                runOnUiThread { pane.showSearch(res, q) }
            }
        }
    }

    private fun prompt(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
            setSelection(text.length)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
