package com.jcooper.tracker.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Minimal dependency-free line chart: plots (x, y) points on a Canvas. Deliberately
 * small — this avoids pulling in a third-party charting library the build might not
 * be able to fetch, and the trend lines the spec asks for don't need more than this.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Point(val x: Float, val y: Float, val label: String? = null)

    var points: List<Point> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var lineColor: Int = Color.BLUE
    var emptyText: String = "No data yet"

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 28f
    }

    private val padding = 60f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        linePaint.color = lineColor
        dotPaint.color = lineColor

        if (points.size < 2) {
            canvas.drawText(emptyText, padding, height / 2f, textPaint)
            return
        }

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
        val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f

        val chartLeft = padding
        val chartRight = width - padding
        val chartTop = padding
        val chartBottom = height - padding

        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)

        fun screenX(x: Float) = chartLeft + (x - minX) / spanX * (chartRight - chartLeft)
        fun screenY(y: Float) = chartBottom - (y - minY) / spanY * (chartBottom - chartTop)

        var previous: Point? = null
        for (p in points) {
            val sx = screenX(p.x)
            val sy = screenY(p.y)
            previous?.let { prev ->
                canvas.drawLine(screenX(prev.x), screenY(prev.y), sx, sy, linePaint)
            }
            canvas.drawCircle(sx, sy, 6f, dotPaint)
            previous = p
        }

        canvas.drawText(formatValue(maxY), chartLeft, chartTop, textPaint)
        canvas.drawText(formatValue(minY), chartLeft, chartBottom + 40f, textPaint)
    }

    private fun formatValue(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v)
}
