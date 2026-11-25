package dev.bbkb.ime.mlkit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.recognition.Ink

/** True-black ink pad. Each stroke can be undone. Recognition reads [currentInk]. */
class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val finished = ArrayList<Ink.Stroke>()
    private val drawn = ArrayList<List<Pair<Float, Float>>>()
    private val live = ArrayList<Pair<Float, Float>>()
    private var stroke = Ink.Stroke.builder()
    private var padColor = Color.BLACK
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        setBackgroundColor(padColor)
    }

    /** Ink and pad follow the keyboard theme. */
    fun setColors(background: Int, inkColor: Int) {
        padColor = background
        setBackgroundColor(background)
        paint.color = inkColor
        invalidate()
    }

    fun currentInk(): Ink {
        val builder = Ink.builder()
        finished.forEach { builder.addStroke(it) }
        return builder.build()
    }

    fun undoStroke() {
        if (finished.isEmpty()) return
        finished.removeAt(finished.lastIndex)
        if (drawn.isNotEmpty()) drawn.removeAt(drawn.lastIndex)
        invalidate()
    }

    fun clear() {
        finished.clear()
        drawn.clear()
        live.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val t = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stroke = Ink.Stroke.builder()
                live.clear()
                stroke.addPoint(Ink.Point.create(event.x, event.y, t))
                live.add(event.x to event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val history = event.historySize
                for (index in 0 until history) {
                    val x = event.getHistoricalX(index)
                    val y = event.getHistoricalY(index)
                    stroke.addPoint(Ink.Point.create(x, y, event.getHistoricalEventTime(index)))
                    live.add(x to y)
                }
                stroke.addPoint(Ink.Point.create(event.x, event.y, t))
                live.add(event.x to event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                stroke.addPoint(Ink.Point.create(event.x, event.y, t))
                live.add(event.x to event.y)
                finished.add(stroke.build())
                drawn.add(ArrayList(live))
                live.clear()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(padColor)
        for (strokePoints in drawn) drawStroke(canvas, strokePoints)
        drawStroke(canvas, live)
    }

    private fun drawStroke(canvas: Canvas, strokePoints: List<Pair<Float, Float>>) {
        if (strokePoints.size < 2) return
        val path = Path()
        path.moveTo(strokePoints[0].first, strokePoints[0].second)
        for (i in 1 until strokePoints.size) {
            val previous = strokePoints[i - 1]
            val current = strokePoints[i]
            path.quadTo(
                previous.first,
                previous.second,
                (previous.first + current.first) / 2f,
                (previous.second + current.second) / 2f,
            )
        }
        val last = strokePoints.last()
        path.lineTo(last.first, last.second)
        canvas.drawPath(path, paint)
    }
}
