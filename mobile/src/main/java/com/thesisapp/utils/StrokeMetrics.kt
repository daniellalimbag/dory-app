package com.thesisapp.utils

import com.thesisapp.data.SwimData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object StrokeMetrics {
    // Estimate sampling rate (Hz) using median delta between timestamps (ms)
    fun estimateSamplingRate(timestampsMs: List<Long>): Double {
        if (timestampsMs.size < 2) return 50.0
        val diffs = mutableListOf<Long>()
        for (i in 1 until timestampsMs.size) {
            val d = timestampsMs[i] - timestampsMs[i - 1]
            if (d > 0) diffs.add(d)
        }
        if (diffs.isEmpty()) return 50.0
        diffs.sort()
        val median = if (diffs.size % 2 == 1) {
            diffs[diffs.size / 2].toDouble()
        } else {
            (diffs[diffs.size / 2 - 1] + diffs[diffs.size / 2]).toDouble() / 2.0
        }
        return 1000.0 / median
    }

    // Lightweight band-pass using first-order high-pass then first-order low-pass
    // This approximates the notebook's Butterworth(0.25-0.5Hz) without external deps
    private fun bandpass(signal: FloatArray, fs: Double, lowCut: Double = 0.25, highCut: Double = 0.5): FloatArray {
        if (signal.isEmpty()) return signal
        val dt = 1.0 / fs
        // 1st-order high-pass
        val rcHigh = 1.0 / (2.0 * Math.PI * lowCut)
        val alphaHigh = rcHigh / (rcHigh + dt)
        val hp = FloatArray(signal.size)
        hp[0] = 0f
        for (i in 1 until signal.size) {
            hp[i] = (alphaHigh * (hp[i - 1] + signal[i] - signal[i - 1])).toFloat()
        }
        // 1st-order low-pass
        val rcLow = 1.0 / (2.0 * Math.PI * highCut)
        val alphaLow = dt / (rcLow + dt)
        val lp = FloatArray(signal.size)
        lp[0] = hp[0]
        for (i in 1 until signal.size) {
            lp[i] = (lp[i - 1] + alphaLow * (hp[i] - lp[i - 1])).toFloat()
        }
        return lp
    }

    // Simple peak detection with minimum distance and dynamic threshold
    private fun findPeaks(signal: FloatArray, fs: Double, minHz: Double = 0.2): List<Int> {
        if (signal.size < 3) return emptyList()
        val minDist = max(1, (fs / minHz).toInt()) // enforce at least ~1 cycle per minHz
        // Compute robust threshold using percentile-like approach
        var minVal = Float.POSITIVE_INFINITY
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in signal) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val thresh = minVal + (maxVal - minVal) * 0.35f

        val peaks = ArrayList<Int>()
        var lastIdx = -minDist
        for (i in 1 until signal.size - 1) {
            val prev = signal[i - 1]
            val curr = signal[i]
            val next = signal[i + 1]
            if (curr > prev && curr >= next && curr > thresh) {
                if (i - lastIdx >= minDist) {
                    peaks.add(i)
                    lastIdx = i
                } else if (curr > signal[lastIdx]) {
                    // replace previous peak within window if current is higher
                    if (peaks.isNotEmpty()) peaks[peaks.size - 1] = i
                    lastIdx = i
                }
            }
        }
        return peaks
    }

    // Main API: compute stroke count using ay + az channel
    fun computeStrokeCount(data: List<SwimData>): Int {
        if (data.isEmpty()) return 0
        val ay = FloatArray(data.size) { i -> data[i].accel_y ?: 0f }
        val az = FloatArray(data.size) { i -> data[i].accel_z ?: 0f }
        val t = List(data.size) { i -> data[i].timestamp }
        val fs = estimateSamplingRate(t)
        val sum = FloatArray(data.size) { i -> ay[i] + az[i] }
        val bp = bandpass(sum, fs)
        val peaks = findPeaks(bp, fs)
        return peaks.size
    }
}
