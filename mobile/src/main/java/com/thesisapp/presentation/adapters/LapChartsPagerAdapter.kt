package com.thesisapp.presentation.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.thesisapp.R
import com.thesisapp.utils.StrokeMetrics

class LapChartsPagerAdapter(
    private val context: Context,
    private val lapMetrics: List<StrokeMetrics.LapMetrics>
    ,
    private val chartMode: ChartMode
) : RecyclerView.Adapter<LapChartsPagerAdapter.Vh>() {

    enum class ChartMode {
        REGULAR,
        DELTA_VS_LAP1
    }

    enum class MetricPage(
        val tabTitle: String,
        val regularTitle: String,
        val deltaTitle: String
    ) {
        STROKES(
            tabTitle = "Strokes",
            regularTitle = "Strokes (count)",
            deltaTitle = "Δ Strokes vs Lap 1 (count)"
        ),
        STROKE_RATE(
            tabTitle = "Stroke Rate",
            regularTitle = "Stroke Rate (spm)",
            deltaTitle = "Δ Stroke Rate vs Lap 1 (spm)"
        ),
        VELOCITY(
            tabTitle = "Velocity",
            regularTitle = "Velocity (m/s)",
            deltaTitle = "Δ Velocity vs Lap 1 (m/s)"
        )
    }

    private val pages = MetricPage.entries

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lap_chart_page, parent, false)
        return Vh(view)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: Vh, pageIndex: Int) {
        val page = pages[pageIndex]
        val chartTitle = when (chartMode) {
            ChartMode.REGULAR -> page.regularTitle
            ChartMode.DELTA_VS_LAP1 -> page.deltaTitle
        }
        holder.title.text = chartTitle

        val baseline = lapMetrics.firstOrNull()

        val baselineValue = when (page) {
            MetricPage.STROKES -> baseline?.strokeCount?.toFloat() ?: 0f
            MetricPage.STROKE_RATE -> baseline?.strokeRateSpm?.toFloat() ?: 0f
            MetricPage.VELOCITY -> baseline?.velocityMetersPerSecond?.toFloat() ?: 0f
        }

        val entries = lapMetrics.mapIndexed { index, m ->
            val x = (index + 1).toFloat()
            val raw = when (page) {
                MetricPage.STROKES -> m.strokeCount.toFloat()
                MetricPage.STROKE_RATE -> m.strokeRateSpm.toFloat()
                MetricPage.VELOCITY -> m.velocityMetersPerSecond.toFloat()
            }
            val y = when (chartMode) {
                ChartMode.REGULAR -> raw
                ChartMode.DELTA_VS_LAP1 -> raw - baselineValue
            }
            BarEntry(x, y)
        }

        val color = when (page) {
            MetricPage.STROKES -> context.getColor(R.color.primary)
            MetricPage.STROKE_RATE -> context.getColor(R.color.accent)
            MetricPage.VELOCITY -> context.getColor(R.color.error)
        }

        val axisTextColor = context.getColor(R.color.text)
        val secondaryTextColor = context.getColor(R.color.text_secondary)

        val dataSet = BarDataSet(entries, chartTitle).apply {
            this.color = color
            valueTextSize = 12f
            valueTextColor = axisTextColor
            setDrawValues(true)
            valueFormatter = if (chartMode == ChartMode.DELTA_VS_LAP1) {
                object : ValueFormatter() {
                    override fun getBarLabel(barEntry: BarEntry?): String {
                        val v = barEntry?.y ?: 0f
                        return if (kotlin.math.abs(v) < 0.0005f) {
                            "0"
                        } else {
                            String.format("%+.2f", v)
                        }
                    }
                }
            } else {
                null
            }
        }

        holder.chart.data = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        holder.chart.description.isEnabled = false
        holder.chart.setTouchEnabled(true)
        holder.chart.setScaleEnabled(false)
        holder.chart.setPinchZoom(false)

        holder.chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            textSize = 11f
            textColor = secondaryTextColor
            axisMinimum = 0.5f
            axisMaximum = (lapMetrics.size + 0.5f)
            labelCount = lapMetrics.size.coerceAtMost(6)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val lapIndex = value.toInt()
                    return if (lapIndex in 1..lapMetrics.size) "Lap $lapIndex" else ""
                }
            }
        }

        holder.chart.axisRight.isEnabled = false
        holder.chart.axisLeft.apply {
            val ys = entries.map { it.y }
            val minY = ys.minOrNull() ?: 0f
            val maxY = ys.maxOrNull() ?: 0f
            val pad = ((maxY - minY) * 0.1f).coerceAtLeast(0.1f)
            if (chartMode == ChartMode.DELTA_VS_LAP1) {
                axisMinimum = minY - pad
                axisMaximum = maxY + pad
            } else {
                axisMinimum = 0f
            }
            textSize = 11f
            textColor = secondaryTextColor
            setDrawGridLines(true)
        }

        holder.chart.legend.isEnabled = false
        holder.chart.setNoDataTextColor(axisTextColor)
        holder.chart.animateY(600)
        holder.chart.invalidate()
    }

    fun getTabTitle(position: Int): String = pages[position].tabTitle

    class Vh(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvChartTitle)
        val chart: BarChart = itemView.findViewById(R.id.barChart)
    }
}
