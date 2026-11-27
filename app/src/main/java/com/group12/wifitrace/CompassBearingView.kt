package com.group12.wifitrace

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.cos
import kotlin.math.sin

class CompassBearingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colorOnSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val colorPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    private val colorOutline = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.GRAY)
    private val colorError = MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, Color.RED)

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOutline
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val paintCardinal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val paintPointer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.FILL
    }

    private val paintPeak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50") // Green
        style = Paint.Style.FILL
    }

    private val paintMin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorError
        style = Paint.Style.FILL
    }

    private val paintIndicatorLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private var azimuth = 0f
    private var peakAzimuth: Float? = null
    private var minAzimuth: Float? = null

    fun setAzimuth(angle: Float) {
        azimuth = angle
        invalidate()
    }

    fun setPeakAzimuth(angle: Float?) {
        peakAzimuth = angle
        invalidate()
    }

    fun setMinAzimuth(angle: Float?) {
        minAzimuth = angle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.8f

        // Draw compass circle
        canvas.drawCircle(centerX, centerY, radius, paintCircle)

        // Draw top pointer (fixed) pointing up
        canvas.drawCircle(centerX, centerY - radius - 20f, 15f, paintPointer)

        canvas.save()
        // Rotate the dial according to azimuth
        canvas.rotate(-azimuth, centerX, centerY)

        // Draw cardinal points
        drawCardinal(canvas, centerX, centerY, radius, "N", 0f)
        drawCardinal(canvas, centerX, centerY, radius, "E", 90f)
        drawCardinal(canvas, centerX, centerY, radius, "S", 180f)
        drawCardinal(canvas, centerX, centerY, radius, "W", 270f)

        // Draw Peak indicator
        peakAzimuth?.let {
            drawIndicator(canvas, centerX, centerY, radius, it, paintPeak, "PEAK")
        }

        // Draw Min indicator
        minAzimuth?.let {
            drawIndicator(canvas, centerX, centerY, radius, it, paintMin, "MIN")
        }

        canvas.restore()
    }

    private fun drawCardinal(canvas: Canvas, cx: Float, cy: Float, radius: Float, label: String, angle: Float) {
        val rad = Math.toRadians(angle.toDouble() - 90.0)
        val x = cx + radius * 0.85f * cos(rad).toFloat()
        val y = cy + radius * 0.85f * sin(rad).toFloat()
        
        // Adjust for text height to center vertically
        val textHeight = paintCardinal.descent() - paintCardinal.ascent()
        canvas.drawText(label, x, y + textHeight/3, paintCardinal)
    }

    private fun drawIndicator(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float, paint: Paint, label: String) {
        val rad = Math.toRadians(angle.toDouble() - 90.0)
        val x = cx + radius * cos(rad).toFloat()
        val y = cy + radius * sin(rad).toFloat()
        
        // Draw line from center to indicator
        paintIndicatorLine.color = paint.color
        paintIndicatorLine.alpha = 100
        canvas.drawLine(cx, cy, x, y, paintIndicatorLine)
        
        // Draw the dot
        canvas.drawCircle(x, y, 15f, paint)
        
        // Draw label outside the circle
        val labelX = cx + (radius + 40f) * cos(rad).toFloat()
        val labelY = cy + (radius + 40f) * sin(rad).toFloat()
        val textHeight = paintLabel.descent() - paintLabel.ascent()
        canvas.drawText(label, labelX, labelY + textHeight/3, paintLabel)
    }
}
