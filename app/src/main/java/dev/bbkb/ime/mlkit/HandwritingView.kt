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

/** Ink pad. Each stroke can be undone. Recognition reads [currentInk]. */
class HandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeFinished: (() -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val min = (96f * density).toInt()
        val height = when (mode) {
            MeasureSpec.EXACTLY -> if (size > 0) size else min
            else -> min
        }
        setMeasuredDimension(width, height)
    }

    private val finished = ArrayList<Ink.Stroke>()
    private val drawn = ArrayList<List<Pair<Float, Float>>>()
    private val live = ArrayList<Pair<Float, Float>>()
    private var stroke = Ink.Stroke.builder()
    private var padColor = Color.BLACK
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 7f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
    }

    init {
        setBackgroundColor(padColor)
    }

    /** Ink and pad follow the keyboard theme. */
    fun setColors(background: Int, inkColor: Int) {
        padColor = background
        setBackgroundColor(background)
        paint.color = inkColor
        dot.color = inkColor
        guide.color = inkColor
        invalidate()
    }

    fun hasInk(): Boolean = finished.isNotEmpty()

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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onStrokeStarted?.invoke()
                stroke = Ink.Stroke.builder()
                live.clear()
                addInkPoint(event.x, event.y, event.eventTime)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val history = event.historySize
                for (index in 0 until history) {
                    addInkPoint(
                        event.getHistoricalX(index),
                        event.getHistoricalY(index),
                        event.getHistoricalEventTime(index),
                    )
                }
                addInkPoint(event.x, event.y, event.eventTime)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                addInkPoint(event.x, event.y, event.eventTime)
                finished.add(stroke.build())
                drawn.add(ArrayList(live))
                live.clear()
                invalidate()
                onStrokeFinished?.invoke()
            }
        }
        return true
    }

    /** One clock for every point. Mixing wall time with event time makes the word unreadable. */
    private fun addInkPoint(x: Float, y: Float, time: Long) {
        stroke.addPoint(Ink.Point.create(x, y, time))
        live.add(x to y)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(padColor)
        val inset = 16f * density
        val baseline = height * 0.72f
        guide.alpha = 48
        canvas.drawLine(inset, baseline, width - inset, baseline, guide)
        guide.alpha = 28
        canvas.drawLine(inset, height * 0.46f, width - inset, height * 0.46f, guide)
        for (strokePoints in drawn) drawStroke(canvas, strokePoints)
        drawStroke(canvas, live)
    }

    private fun drawStroke(canvas: Canvas, strokePoints: List<Pair<Float, Float>>) {
        if (strokePoints.isEmpty()) return
        if (strokePoints.size == 1) {
            val point = strokePoints[0]
            canvas.drawCircle(point.first, point.second, paint.strokeWidth / 2f, dot)
            return
        }
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
