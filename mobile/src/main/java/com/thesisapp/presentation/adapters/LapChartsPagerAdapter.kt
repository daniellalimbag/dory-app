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
) : RecyclerView.Adapter<LapChartsPagerAdapter.Vh>() {

    enum class MetricPage(
        val tabTitle: String,
        val chartTitle: String
    ) {
        STROKES(
            tabTitle = "Strokes",
            chartTitle = "Strokes (count)"
        ),
        STROKE_RATE(
            tabTitle = "Stroke Rate",
            chartTitle = "Stroke Rate (spm)"
        ),
        VELOCITY(
            tabTitle = "Velocity",
            chartTitle = "Velocity (m/s)"
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
        holder.title.text = page.chartTitle

        val entries = lapMetrics.mapIndexed { index, m ->
            val x = (index + 1).toFloat()
            val y = when (page) {
                MetricPage.STROKES -> m.strokeCount.toFloat()
                MetricPage.STROKE_RATE -> m.strokeRateSpm.toFloat()
                MetricPage.VELOCITY -> m.velocityMetersPerSecond.toFloat()
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

        val dataSet = BarDataSet(entries, page.chartTitle).apply {
            this.color = color
            valueTextSize = 12f
            valueTextColor = axisTextColor
            setDrawValues(true)
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
            axisMinimum = 0f
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
