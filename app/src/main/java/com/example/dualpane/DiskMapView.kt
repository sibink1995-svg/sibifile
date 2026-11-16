package com.example.dualpane

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.File

/** Simple treemap: every block's area is proportional to the size of the file/folder. */
class DiskMapView(ctx: Context) : View(ctx) {

    class Item(val name: String, val size: Long, val file: File?)

    var onTap: ((Item) -> Unit)? = null

    private var items: List<Item> = emptyList()
    private val rects = ArrayList<Pair<RectF, Item>>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * resources.displayMetrics.scaledDensity
    }

    fun setItems(list: List<Item>) {
        items = list.filter { it.size > 0 }.sortedByDescending { it.size }
        relayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        relayout()
    }

    private fun relayout() {
        rects.clear()
        if (width > 0 && height > 0) layoutRec(items, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }

    private fun layoutRec(list: List<Item>, r: RectF) {
        if (list.isEmpty()) return
        if (list.size == 1) {
            rects.add(r to list[0]); return
        }
        val total = list.sumOf { it.size }.toDouble()
        var acc = 0L
        var split = 0
        for (i in list.indices) {
            acc += list[i].size
            split = i + 1
            if (acc * 2 >= total) break
        }
        if (split >= list.size) split = list.size - 1
        val a = list.subList(0, split)
        val b = list.subList(split, list.size)
        val frac = (a.sumOf { it.size } / total).toFloat()
        if (r.width() >= r.height()) {
            val x = r.left + r.width() * frac
            layoutRec(a, RectF(r.left, r.top, x, r.bottom))
            layoutRec(b, RectF(x, r.top, r.right, r.bottom))
        } else {
            val y = r.top + r.height() * frac
            layoutRec(a, RectF(r.left, r.top, r.right, y))
            layoutRec(b, RectF(r.left, y, r.right, r.bottom))
        }
    }

    override fun onDraw(c: Canvas) {
        for ((r, item) in rects) {
            val idx = items.indexOf(item)
            fill.color = if (item.file == null) Color.GRAY
            else Color.HSVToColor(floatArrayOf((idx * 47f) % 360f, if (item.file.isDirectory) 0.55f else 0.35f, 0.75f))
            c.drawRect(r, fill)
            c.drawRect(r, stroke)
            if (r.width() > 90f && r.height() > 50f) {
                c.save()
                c.clipRect(r)
                val pad = 10f
                c.drawText(item.name, r.left + pad, r.top + pad + text.textSize, text)
                c.drawText(FileOps.humanSize(item.size), r.left + pad, r.top + pad + text.textSize * 2.2f, text)
                c.restore()
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            rects.firstOrNull { it.first.contains(e.x, e.y) }?.let { onTap?.invoke(it.second) }
            return true
        }
        return super.onTouchEvent(e)
    }
}
