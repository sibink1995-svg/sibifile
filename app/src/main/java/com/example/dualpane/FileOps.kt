package com.example.dualpane

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileOps {

    /** Returns a file in [dir] named [name] that does not exist yet ("a.txt" -> "a (1).txt"). */
    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (target.exists()) {
            target = File(dir, "$base ($i)$ext")
            i++
        }
        return target
    }

    fun copy(src: File, dstDir: File): Boolean {
        if (src.isDirectory && dstDir.canonicalPath.startsWith(src.canonicalPath)) return false
        val target = uniqueTarget(dstDir, src.name)
        return try {
            src.copyRecursively(target, overwrite = false)
        } catch (e: Exception) {
            false
        }
    }

    fun move(src: File, dstDir: File): Boolean {
        if (src.isDirectory && dstDir.canonicalPath.startsWith(src.canonicalPath)) return false
        val target = uniqueTarget(dstDir, src.name)
        if (src.renameTo(target)) return true
        return if (copy(src, dstDir)) src.deleteRecursively() else false
    }

    fun delete(f: File): Boolean = f.deleteRecursively()

    fun rename(f: File, newName: String): Boolean {
        if (newName.isBlank() || newName.contains('/')) return false
        val target = File(f.parentFile, newName)
        if (target.exists()) return false
        return f.renameTo(target)
    }

    fun zip(files: List<File>, out: File): Boolean {
        return try {
            ZipOutputStream(out.outputStream().buffered()).use { zos ->
                for (f in files) addToZip(zos, f, f.parentFile ?: f)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun addToZip(zos: ZipOutputStream, f: File, root: File) {
        val name = f.relativeTo(root).path.replace('\\', '/')
        if (f.isDirectory) {
            zos.putNextEntry(ZipEntry("$name/"))
            zos.closeEntry()
            f.listFiles()?.forEach { addToZip(zos, it, root) }
        } else {
            zos.putNextEntry(ZipEntry(name))
            FileInputStream(f).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    /** Recursive, case-insensitive name search. */
    fun search(root: File, query: String, limit: Int = 500): List<File> {
        val out = ArrayList<File>()
        val q = query.lowercase()
        root.walkTopDown()
            .onEnter { true }
            .forEach {
                if (out.size >= limit) return out
                if (it != root && it.name.lowercase().contains(q)) out.add(it)
            }
        return out
    }

    fun open(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, f.name))
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var u = -1
        while (v >= 1024 && u < units.lastIndex) {
            v /= 1024; u++
        }
        return String.format("%.1f %s", v, units[u])
    }
}
