package com.thesisapp.presentation.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class SimpleBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#43A047")
        style = Paint.Style.FILL
    }

    private val paddingLeftPx = 36f
    private val paddingRightPx = 16f
    private val paddingTopPx = 16f
    private val paddingBottomPx = 28f

    // Hardcoded sample data (e.g., stroke rate per lap)
    private val data = floatArrayOf(0.9f, 1.1f, 1.0f, 1.2f, 1.15f, 1.25f, 1.05f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val chartLeft = paddingLeftPx
        val chartRight = w - paddingRightPx
        val chartTop = paddingTopPx
        val chartBottom = h - paddingBottomPx

        canvas.drawLine(chartLeft, chartTop, chartLeft, chartBottom, axisPaint)
        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, axisPaint)

        val gridLines = 4
        val stepY = (chartBottom - chartTop) / gridLines
        for (i in 1..gridLines) {
            val y = chartBottom - i * stepY
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
        }

        if (data.isEmpty()) return

        val maxVal = max(1f, data.maxOrNull() ?: 1f)
        val span = max(0.0001f, maxVal)

        val count = data.size
        val totalWidth = chartRight - chartLeft
        val barSpace = 8f
        val barWidth = (totalWidth - barSpace * (count + 1)) / count

        for (i in 0 until count) {
            val left = chartLeft + barSpace + i * (barWidth + barSpace)
            val right = left + barWidth
            val norm = data[i] / span
            val top = chartBottom - norm * (chartBottom - chartTop)
            canvas.drawRect(left, top, right, chartBottom, barPaint)
        }
    }
}
