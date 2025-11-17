package com.thesisapp.utils

import com.thesisapp.data.SwimData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlin.math.PI

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

    // Moving Average Filter, window size of 1 as default
    private fun movingAverage(signal: FloatArray, fs: Double, windowSizeSeconds: Int = 1): FloatArray {
        val windowSizeSamples = (windowSizeSeconds * fs).toInt().coerceAtLeast(1)

        val n = signal.size
        // Initialize the output array with NaN, as pandas does for incomplete windows
        val smoothedSignal = FloatArray(n) { Float.NaN }

        // 2. Calculate the offsets for the centered window
        val offsetBefore = windowSizeSamples / 2 // Integer division (floor)
        val offsetAfter = ceil(windowSizeSamples / 2.0).toInt() - 1

        // 3. Iterate over the signal and apply the rolling mean
        // (This logic remains the same)
        for (i in signal.indices) {
            val windowStart = i - offsetBefore
            val windowEnd = i + offsetAfter // This is the inclusive end index

            // Check if the *full* window is within the array bounds
            if (windowStart < 0 || windowEnd >= n) {
                continue // Skip calculation, leave as NaN
            }

            // Calculate the mean for the valid window
            var sum = 0.0f
            for (j in windowStart..windowEnd) {
                sum += signal[j]
            }
            smoothedSignal[i] = sum / windowSizeSamples
        }

        return smoothedSignal
    }

    private fun fillNaNs(signal: FloatArray): FloatArray {
        val n = signal.size
        if (n == 0) return FloatArray(0)

        val cleaned = signal.copyOf()

        // 1. Back-fill (bfill)
        // Propagates the next valid non-NaN value backward.
        var nextValid = Float.NaN
        for (i in n - 1 downTo 0) {
            if (cleaned[i].isNaN()) {
                cleaned[i] = nextValid
            } else {
                nextValid = cleaned[i]
            }
        }

        // 2. Forward-fill (ffill)
        // Propagates the last valid non-NaN value forward.
        var prevValid = Float.NaN
        for (i in 0 until n) {
            if (cleaned[i].isNaN()) {
                cleaned[i] = prevValid
            } else {
                prevValid = cleaned[i]
            }
        }

        return cleaned
    }

    // Low Pass Filter, cut-off frequency of 3.0 hz by default
    private fun lowPassFilter(
        signal: FloatArray,
        samplingFrequencyHz: Double,
        cutoffFrequencyHz: Double = 3.0
    ): FloatArray {
        // 1. Handle NaN values by filling them
        val signalCleaned = fillNaNs(signal)

        if (signalCleaned.isEmpty()) {
            return FloatArray(0)
        }

        // 2. Calculate filter constants (using Double for precision)
        val dt = 1.0 / samplingFrequencyHz
        val rc = 1.0 / (2.0 * PI * cutoffFrequencyHz)
        val alpha = dt / (rc + dt)

        // Use a DoubleArray for calculations to maintain precision
        val filteredSignal = DoubleArray(signalCleaned.size)

        // 3. Initialize the first element
        // We check for NaN in case the *entire* array was NaN
        val firstVal = signalCleaned[0]
        filteredSignal[0] = if (firstVal.isNaN()) 0.0 else firstVal.toDouble()

        // 4. Apply the filter equation
        for (i in 1 until signalCleaned.size) {
            val currentValue = signalCleaned[i].toDouble()
            val previousFilteredValue = filteredSignal[i - 1]

            filteredSignal[i] = alpha * currentValue + (1.0 - alpha) * previousFilteredValue
        }

        // 5. Convert back to FloatArray for the final output
        return filteredSignal.map { it.toFloat() }.toFloatArray()
    }

    // Identifies the start of each period where the filtered signal drops below a threshold.
    // This detects the 'falling edge' (from >= threshold to < threshold).
    // Includes a debouncing mechanism and start/end trimming (35.0 seconds), and should be minimum 2.5 seconds between each lap turn.
    private fun detectLapTurns(
        filteredSignal: FloatArray,
        timestamps: List<Long>,
        threshold: Float,
        debounceSeconds: Double = 2.5,
        trimSeconds: Double = 35.0
    ): List<Long> {

        val lapTurnTimes = mutableListOf<Long>()
        var lastDetectedLapTurnTime: Long? = null
        val debounceMilliseconds = (debounceSeconds * 1000).toLong()

        // We must have at least 2 points to compare.
        if (filteredSignal.size < 2 || timestamps.size != filteredSignal.size) {
            return emptyList()
        }

        // Get the absolute start and end times from the list
        val segmentStartTime = timestamps.first()
        val segmentEndTime = timestamps.last()

        // Calculate the valid time range, applying the trim margin
        val marginMilliseconds = (trimSeconds * 1000).toLong()
        val validStartTime = segmentStartTime + marginMilliseconds
        val validEndTime = segmentEndTime - marginMilliseconds

        // Iterate from the second element (index 1) to compare with the previous one
        for (i in 1 until filteredSignal.size) {
            val currentGyro = filteredSignal[i]
            val previousGyro = filteredSignal[i - 1]
            val currentTimestamp = timestamps[i]

            // 1. Condition for detecting a 'falling edge'
            // (Signal crossed from *at or above* threshold to *below* threshold)
            if (currentGyro < threshold && previousGyro >= threshold) {

                // 2. Is the turn within the valid time range?
                // If it's too early or too late, skip it.
                if (currentTimestamp < validStartTime || currentTimestamp > validEndTime) {
                    continue // Ignore this turn; it's in the trimmed area
                }

                // 3. Apply debouncing mechanism (only for valid turns)
                val lastTime = lastDetectedLapTurnTime // Local val for smart-casting

                if (lastTime == null || (currentTimestamp - lastTime >= debounceMilliseconds)) {
                    // If this is the first valid turn OR enough time has passed, record it.
                    lapTurnTimes.add(currentTimestamp)
                    lastDetectedLapTurnTime = currentTimestamp
                }
            }
        }

        return lapTurnTimes
    }

    private fun calculateLapTimes(turnTimestamps: List<Long>): List<Long> {
        // If there are 0 or 1 timestamps, no lap times can be calculated.
        if (turnTimestamps.size < 2) {
            return emptyList()
        }

        val lapTimes = mutableListOf<Long>()

        // Iterate from the second timestamp to calculate the difference from the previous one
        for (i in 1 until turnTimestamps.size) {
            val currentLapTime = turnTimestamps[i]
            val previousLapTime = turnTimestamps[i - 1]
            val lapDuration = currentLapTime - previousLapTime

            lapTimes.add(lapDuration)
        }

        return lapTimes
    }

    // Assumed that only swimming bout data is passed to this function
    private fun computeSingleBoutLapTimes(data: List<SwimData>, fs: Double): List<Long> {
        if (data.isEmpty()) return emptyList()
        val gx = FloatArray(data.size) { i -> data[i].gyro_x ?: 0f }
        val gy = FloatArray(data.size) { i -> data[i].gyro_y ?: 0f }
        val gz = FloatArray(data.size) { i -> data[i].gyro_z ?: 0f }
        val t = List(data.size) { i -> data[i].timestamp }
        val sum = FloatArray(data.size) { i ->
            val x = gx[i]
            val y = gy[i]
            val z = gz[i]

            // Calculate the norm and convert the Double result to Float
            sqrt(x * x + y * y + z * z).toFloat()
        }
        val smoothed = movingAverage(sum, fs)
        val filtered = lowPassFilter(smoothed, fs)
        val turnTimestamps = detectLapTurns(filtered, t, 12.0f)
        return calculateLapTimes(turnTimestamps)
    }

    private fun detectSwimming(accelCombined: FloatArray, threshold: Float = 12.0f): IntArray {
        // Create a new IntArray of the same size.
        // For each index 'i', check the condition.
        // The 'if' expression returns 1 or 0, which is then placed in the new array.
        return IntArray(accelCombined.size) { i ->
            if (accelCombined[i] > threshold) 1 else 0
        }
    }

    private fun binaryDilation(signal: IntArray, windowSize: Int): IntArray {
        val n = signal.size
        if (n == 0 || windowSize <= 1) return signal.copyOf()

        val output = IntArray(n) { 0 } // Start with all 0s

        // Calculate centered window offsets
        // This matches the standard 'origin' for morphological operations
        val offsetBefore = (windowSize - 1) / 2
        val offsetAfter = windowSize - 1 - offsetBefore

        for (i in 0 until n) {
            // Check for *any* 1 in the window
            var foundOne = false
            // Iterate over the window defined by the offsets
            for (j in -offsetBefore..offsetAfter) {
                val index = i + j
                // Check if index is in bounds and is 1
                if (index in 0 until n && signal[index] == 1) {
                    foundOne = true
                    break
                }
            }

            if (foundOne) {
                output[i] = 1
            }
        }
        return output
    }

    private fun binaryErosion(signal: IntArray, windowSize: Int): IntArray {
        val n = signal.size
        if (n == 0 || windowSize <= 1) return signal.copyOf()

        val output = IntArray(n) { 0 } // Start with all 0s

        val offsetBefore = (windowSize - 1) / 2
        val offsetAfter = windowSize - 1 - offsetBefore

        for (i in 0 until n) {
            // Check if *all* are 1s in the window
            var allOnes = true
            for (j in -offsetBefore..offsetAfter) {
                val index = i + j

                // Get the value, treating out-of-bounds as 0 (the "border value")
                val value = if (index in 0 until n) {
                    signal[index]
                } else {
                    0 // Out-of-bounds is treated as 0
                }

                if (value == 0) {
                    allOnes = false
                    break
                }
            }

            if (allOnes) {
                output[i] = 1
            }
        }
        return output
    }

    private fun cleanSwimmingBouts(isSwimming: IntArray, gapFillSamples: Int, boutFilterSamples: Int): IntArray {

        // --- 1. Apply Gap Filling (binary_closing) ---
        // "binary_closing fills small holes (0s) within 1s."
        // This is a Dilation (widens 1s to fill holes) followed by an Erosion (shrinks 1s back).
        val dilated = binaryDilation(isSwimming, gapFillSamples)
        val gapFilled = binaryErosion(dilated, gapFillSamples)

        // --- 2. Apply Bout Filtering (binary_opening) ---
        // "binary_opening removes small objects (1s)."
        // This is an Erosion (shrinks 1s, removing small ones) followed by a Dilation (widens remaining 1s back).
        val eroded = binaryErosion(gapFilled, boutFilterSamples)
        val cleaned = binaryDilation(eroded, boutFilterSamples)

        // 3. Return the final cleaned array
        return cleaned
    }

    data class LapMetrics(
        val lapTimeSeconds: Double,
        val strokeCount: Int,
        val strokeRateSpm: Double,
        val strokeLengthMeters: Double,
        val velocityMetersPerSecond: Double,
        val strokeRatePerSecond: Double,
        val strokeIndex: Double
    )

    data class SessionAverages(
        val avgLapTimeSeconds: Double,
        val avgStrokeCount: Double,
        val avgVelocityMetersPerSecond: Double,
        val avgStrokeRatePerSecond: Double,
        val avgStrokeLengthMeters: Double,
        val avgStrokeIndex: Double
    )

    fun computeLapMetrics(data: List<SwimData>, poolLengthMeters: Double = 50.0): List<LapMetrics> {
        if (data.isEmpty()) return emptyList()

        val ax = FloatArray(data.size) { i -> data[i].accel_x ?: 0f }
        val ay = FloatArray(data.size) { i -> data[i].accel_y ?: 0f }
        val az = FloatArray(data.size) { i -> data[i].accel_z ?: 0f }
        val t = List(data.size) { i -> data[i].timestamp }
        val fs = estimateSamplingRate(t)
        val accelCombined = FloatArray(data.size) { i -> abs(ax[i]) + abs(ay[i]) + abs(az[i]) }
        val isSwimming = detectSwimming(accelCombined, 12.0f)
        val gapFillSamples = (7 * fs).toInt()
        val boutFilterSamples = (30 * fs).toInt()
        val cleaned = cleanSwimmingBouts(isSwimming, gapFillSamples, boutFilterSamples)

        val lapMetrics = mutableListOf<LapMetrics>()
        var boutStartIndex: Int? = null

        for (i in cleaned.indices) {
            val swimmingNow = cleaned[i] == 1
            if (boutStartIndex == null) {
                if (swimmingNow) {
                    boutStartIndex = i
                }
            } else {
                if (!swimmingNow) {
                    val start = boutStartIndex!!
                    val end = i
                    val boutData = data.subList(start, end)
                    val boutTimestamps = t.subList(start, end)

                    if (boutData.isNotEmpty()) {
                        val gx = FloatArray(boutData.size) { j -> boutData[j].gyro_x ?: 0f }
                        val gy = FloatArray(boutData.size) { j -> boutData[j].gyro_y ?: 0f }
                        val gz = FloatArray(boutData.size) { j -> boutData[j].gyro_z ?: 0f }
                        val mag = FloatArray(boutData.size) { j ->
                            val x = gx[j]
                            val y = gy[j]
                            val z = gz[j]
                            sqrt(x * x + y * y + z * z).toFloat()
                        }
                        val smoothed = movingAverage(mag, fs)
                        val filtered = lowPassFilter(smoothed, fs)
                        val lapTurnTimestamps = detectLapTurns(filtered, boutTimestamps, 12.0f)

                        if (lapTurnTimestamps.size >= 2) {
                            for (k in 1 until lapTurnTimestamps.size) {
                                val prevTs = lapTurnTimestamps[k - 1]
                                val currTs = lapTurnTimestamps[k]
                                val lapTimeSec = (currTs - prevTs) / 1000.0
                                if (lapTimeSec <= 0.0) continue

                                var lapStartIdx = start
                                var lapEndIdx = end - 1
                                for (idx in start until end) {
                                    if (t[idx] >= prevTs) {
                                        lapStartIdx = idx
                                        break
                                    }
                                }
                                for (idx in end - 1 downTo start) {
                                    if (t[idx] <= currTs) {
                                        lapEndIdx = idx
                                        break
                                    }
                                }

                                if (lapEndIdx <= lapStartIdx) continue

                                val lapData = data.subList(lapStartIdx, lapEndIdx + 1)
                                val lapStrokeCount = computeStrokeCount(lapData)
                                if (lapStrokeCount <= 0) continue

                                val velocity = poolLengthMeters / lapTimeSec
                                val strokeRatePerSecond = lapStrokeCount / lapTimeSec
                                val lapStrokeRateSpm = strokeRatePerSecond * 60.0
                                val lapStrokeLength = if (strokeRatePerSecond > 0.0) velocity / strokeRatePerSecond else 0.0
                                val strokeIndex = velocity * lapStrokeLength

                                lapMetrics.add(
                                    LapMetrics(
                                        lapTimeSeconds = lapTimeSec,
                                        strokeCount = lapStrokeCount,
                                        strokeRateSpm = lapStrokeRateSpm,
                                        strokeLengthMeters = lapStrokeLength,
                                        velocityMetersPerSecond = velocity,
                                        strokeRatePerSecond = strokeRatePerSecond,
                                        strokeIndex = strokeIndex
                                    )
                                )
                            }
                        }
                    }

                    boutStartIndex = null
                }
            }
        }

        if (boutStartIndex != null) {
            val start = boutStartIndex!!
            val end = data.size
            val boutData = data.subList(start, end)
            val boutTimestamps = t.subList(start, end)

            if (boutData.isNotEmpty()) {
                val gx = FloatArray(boutData.size) { j -> boutData[j].gyro_x ?: 0f }
                val gy = FloatArray(boutData.size) { j -> boutData[j].gyro_y ?: 0f }
                val gz = FloatArray(boutData.size) { j -> boutData[j].gyro_z ?: 0f }
                val mag = FloatArray(boutData.size) { j ->
                    val x = gx[j]
                    val y = gy[j]
                    val z = gz[j]
                    sqrt(x * x + y * y + z * z).toFloat()
                }
                val smoothed = movingAverage(mag, fs)
                val filtered = lowPassFilter(smoothed, fs)
                val lapTurnTimestamps = detectLapTurns(filtered, boutTimestamps, 12.0f)

                if (lapTurnTimestamps.size >= 2) {
                    for (k in 1 until lapTurnTimestamps.size) {
                        val prevTs = lapTurnTimestamps[k - 1]
                        val currTs = lapTurnTimestamps[k]
                        val lapTimeSec = (currTs - prevTs) / 1000.0
                        if (lapTimeSec <= 0.0) continue

                        var lapStartIdx = start
                        var lapEndIdx = end - 1
                        for (idx in start until end) {
                            if (t[idx] >= prevTs) {
                                lapStartIdx = idx
                                break
                            }
                        }
                        for (idx in end - 1 downTo start) {
                            if (t[idx] <= currTs) {
                                lapEndIdx = idx
                                break
                            }
                        }

                        if (lapEndIdx <= lapStartIdx) continue

                        val lapData = data.subList(lapStartIdx, lapEndIdx + 1)
                        val lapStrokeCount = computeStrokeCount(lapData)
                        if (lapStrokeCount <= 0) continue

                        val velocity = poolLengthMeters / lapTimeSec
                        val strokeRatePerSecond = lapStrokeCount / lapTimeSec
                        val lapStrokeRateSpm = strokeRatePerSecond * 60.0
                        val lapStrokeLength = if (strokeRatePerSecond > 0.0) velocity / strokeRatePerSecond else 0.0
                        val strokeIndex = velocity * lapStrokeLength

                        lapMetrics.add(
                            LapMetrics(
                                lapTimeSeconds = lapTimeSec,
                                strokeCount = lapStrokeCount,
                                strokeRateSpm = lapStrokeRateSpm,
                                strokeLengthMeters = lapStrokeLength,
                                velocityMetersPerSecond = velocity,
                                strokeRatePerSecond = strokeRatePerSecond,
                                strokeIndex = strokeIndex
                            )
                        )
                    }
                }
            }
        }

        return lapMetrics
    }

    fun computeSessionAverages(lapMetrics: List<LapMetrics>): SessionAverages {
        if (lapMetrics.isEmpty()) {
            return SessionAverages(
                avgLapTimeSeconds = 0.0,
                avgStrokeCount = 0.0,
                avgVelocityMetersPerSecond = 0.0,
                avgStrokeRatePerSecond = 0.0,
                avgStrokeLengthMeters = 0.0,
                avgStrokeIndex = 0.0
            )
        }

        var sumLapTime = 0.0
        var sumStrokeCount = 0.0
        var sumVelocity = 0.0
        var sumStrokeRatePerSecond = 0.0
        var sumStrokeLength = 0.0
        var sumStrokeIndex = 0.0

        for (m in lapMetrics) {
            sumLapTime += m.lapTimeSeconds
            sumStrokeCount += m.strokeCount.toDouble()
            sumVelocity += m.velocityMetersPerSecond
            sumStrokeRatePerSecond += m.strokeRatePerSecond
            sumStrokeLength += m.strokeLengthMeters
            sumStrokeIndex += m.strokeIndex
        }

        val n = lapMetrics.size.toDouble()

        return SessionAverages(
            avgLapTimeSeconds = sumLapTime / n,
            avgStrokeCount = sumStrokeCount / n,
            avgVelocityMetersPerSecond = sumVelocity / n,
            avgStrokeRatePerSecond = sumStrokeRatePerSecond / n,
            avgStrokeLengthMeters = sumStrokeLength / n,
            avgStrokeIndex = sumStrokeIndex / n
        )
    }

    fun computeLapTimes(data: List<SwimData>): List<List<Long>> {
        if (data.isEmpty()) return emptyList()
        val ax = FloatArray(data.size) { i -> data[i].accel_x ?: 0f }
        val ay = FloatArray(data.size) { i -> data[i].accel_y ?: 0f }
        val az = FloatArray(data.size) { i -> data[i].accel_z ?: 0f }
        val t = List(data.size) { i -> data[i].timestamp }
        val fs = estimateSamplingRate(t)
        val sum = FloatArray(data.size) { i -> abs(ax[i]) + abs(ay[i]) + abs(az[i]) }
        val isSwimming = detectSwimming(sum, 12.0f)
        val gapFillSamples = (7 * fs).toInt()
        val boutFilterSamples = (30 * fs).toInt()
        val cleaned = cleanSwimmingBouts(isSwimming, gapFillSamples, boutFilterSamples)

        val allBoutLapTimes = mutableListOf<List<Long>>()
        var boutStartIndex: Int? = null

        // Iterate over the cleaned array to find continuous segments of 1s
        for (i in cleaned.indices) {
            val isCurrentlySwimming = (cleaned[i] == 1)

            if (boutStartIndex == null) {
                // --- STATE: Not in a bout ---
                if (isCurrentlySwimming) {
                    // A new bout just started! Record its start index.
                    boutStartIndex = i
                }
            } else {
                // --- STATE: In a bout ---
                if (!isCurrentlySwimming) {
                    // The bout just ended! (at index i-1)

                    // Get the data segment for this bout (from startIndex up to, but not including, i)
                    val boutData = data.subList(boutStartIndex, i)

                    // Process that single bout to get its lap times
                    val lapTimes = computeSingleBoutLapTimes(boutData, fs)

                    // Add the resulting lap list to our main list
                    allBoutLapTimes.add(lapTimes)

                    // Reset the state, since we are no longer in a bout
                    boutStartIndex = null
                }
            }
        }

        // What if the loop finishes while still in a bout? (e.g., data ends with 1, 1, 1)
        if (boutStartIndex != null) {
            // The last bout goes from the start index to the very end of the data
            val lastBoutData = data.subList(boutStartIndex, data.size)

            // Process this final bout
            val lapTimes = computeSingleBoutLapTimes(lastBoutData, fs)
            allBoutLapTimes.add(lapTimes)
        }

        return allBoutLapTimes
    }
}
