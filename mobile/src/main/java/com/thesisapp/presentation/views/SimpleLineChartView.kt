package com.thesisapp.presentation.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SimpleLineChartView @JvmOverloads constructor(
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

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        alpha = 40
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.FILL
    }

    private val paddingLeftPx = 36f
    private val paddingRightPx = 16f
    private val paddingTopPx = 16f
    private val paddingBottomPx = 28f

    private val data = floatArrayOf(
        1.2f, 1.4f, 1.35f, 1.6f, 1.55f, 1.7f, 1.65f, 1.8f
    )

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

        val minVal = max(0f, data.minOrNull() ?: 0f)
        val maxVal = max(1f, data.maxOrNull() ?: 1f)
        val span = max(0.0001f, maxVal - minVal)

        val count = data.size
        val stepX = (chartRight - chartLeft) / max(1, count - 1)

        val path = Path()
        for (i in 0 until count) {
            val x = chartLeft + i * stepX
            val norm = (data[i] - minVal) / span
            val y = chartBottom - norm * (chartBottom - chartTop)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val fillPath = Path(path)
        fillPath.lineTo(chartRight, chartBottom)
        fillPath.lineTo(chartLeft, chartBottom)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        val radius = 5f
        for (i in 0 until count) {
            val x = chartLeft + i * stepX
            val norm = (data[i] - minVal) / span
            val y = chartBottom - norm * (chartBottom - chartTop)
            canvas.drawCircle(x, y, radius, markerPaint)
        }
    }
}
