package com.group12.wifitrace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Small dependency-free line chart: average RSSI (dBm) vs distance (m).
 * Each series is drawn with its own colour AND line style (solid / dashed / dotted).
 * Text and grid colours follow the current light/dark theme.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** dash is given in dp units (on, off, on, off...) or null for a solid line. */
    data class Series(
        val name: String,
        val color: Int,
        val points: List<Pair<Float, Float>>,
        val dash: List<Float>? = null,
        val showDots: Boolean = true
    )

    private var series: List<Series> = emptyList()

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun setData(newSeries: List<Series>) {
        series = newSeries
        invalidate()
    }

    private fun applyDash(s: Series) {
        val d = s.dash
        linePaint.pathEffect =
            if (d == null) null else DashPathEffect(d.map { dp(it) }.toFloatArray(), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val onSurface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface, Color.GRAY
        )
        textPaint.color = onSurface
        gridPaint.color = Color.argb(
            50, Color.red(onSurface), Color.green(onSurface), Color.blue(onSurface)
        )

        if (width <= 0 || height <= 0) return
        val left = dp(48f)
        val right = width - dp(16f)
        val top = dp(40f)
        val bottom = height - dp(36f)
        if (right <= left || bottom <= top) return

        val all = series.flatMap { it.points }
        if (all.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = sp(14f)
            canvas.drawText("No data yet. Run some trials first.", width / 2f, height / 2f, textPaint)
            return
        }

        val minY = all.minOf { it.second }
        val maxY = all.maxOf { it.second }
        val yMin = floor((minY - 5f) / 10f) * 10f
        val yMaxRaw = ceil((maxY + 5f) / 10f) * 10f
        val yMax = if (yMaxRaw - yMin < 20f) yMin + 20f else yMaxRaw
        val xMin = 0f
        val xMax = 16f

        fun xPos(x: Float) = left + (x - xMin) / (xMax - xMin) * (right - left)
        fun yPos(y: Float) = top + (yMax - y) / (yMax - yMin) * (bottom - top)

        // Horizontal grid + y labels
        textPaint.textSize = sp(11f)
        textPaint.textAlign = Paint.Align.RIGHT
        var v = yMin
        while (v <= yMax + 0.01f) {
            val y = yPos(v)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(v.toInt().toString(), left - dp(6f), y + dp(4f), textPaint)
            v += 10f
        }

        // Vertical grid + x labels at the tested distances
        textPaint.textAlign = Paint.Align.CENTER
        for (d in Plan.LOS_DISTANCES) {
            val x = xPos(d.toFloat())
            canvas.drawLine(x, top, x, bottom, gridPaint)
            canvas.drawText("${d}m", x, bottom + dp(15f), textPaint)
        }
        canvas.drawText("Distance from router", (left + right) / 2f, bottom + dp(31f), textPaint)

        // Unit label
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("dBm", dp(6f), top - dp(8f), textPaint)

        // Legend (line sample + name)
        var lx = dp(12f)
        textPaint.textSize = sp(11f)
        for (s in series) {
            linePaint.color = s.color
            applyDash(s)
            canvas.drawLine(lx, dp(14f), lx + dp(18f), dp(14f), linePaint)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(s.name, lx + dp(22f), dp(18f), textPaint)
            lx += dp(22f) + textPaint.measureText(s.name) + dp(12f)
        }

        // Data lines + points
        for (s in series) {
            val pts = s.points.sortedBy { it.first }
            linePaint.color = s.color
            applyDash(s)
            if (pts.size > 1) {
                path.reset()
                pts.forEachIndexed { i, p ->
                    val px = xPos(p.first)
                    val py = yPos(p.second)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, linePaint)
            }
            if (s.showDots) {
                dotPaint.color = s.color
                for (p in pts) {
                    canvas.drawCircle(xPos(p.first), yPos(p.second), dp(4.5f), dotPaint)
                }
            }
        }
        linePaint.pathEffect = null
    }
}
