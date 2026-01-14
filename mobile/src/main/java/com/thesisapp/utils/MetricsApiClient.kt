package com.thesisapp.utils

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// NOTE: For Android emulator, 10.0.2.2 points to the host machine's localhost.
private const val DEFAULT_BASE_URL = "http://ccscloud.dlsu.edu.ph:11526/"

// Data classes mirroring the FastAPI models

data class MetricsSample(
    val timestamp_ms: Long,
    val accel_x: Double,
    val accel_y: Double,
    val accel_z: Double,
    val gyro_x: Double,
    val gyro_y: Double,
    val gyro_z: Double,
    val stroke_type: String? = null,
)

data class MetricsSessionRequest(
    val session_id: Int?,
    val swimmer_id: Int?,
    val exercise_id: Int?,
    val pool_length_m: Double = 50.0,
    val samples: List<MetricsSample>,
)

data class MetricsLapOut(
    val lap_number: Int,
    val lap_time_s: Double,
    val stroke_count: Int,
    val velocity_m_per_s: Double,
    val stroke_rate_hz: Double,
    val stroke_rate_spm: Double,
    val stroke_length_m: Double,
    val stroke_index: Double,
    val stroke_type: String?,
)

data class MetricsSessionAveragesOut(
    val lap_count: Int,
    val stroke_count: Double,
    val avg_lap_time_s: Double,
    val avg_velocity_m_per_s: Double,
    val avg_stroke_rate_hz: Double,
    val avg_stroke_length_m: Double,
    val avg_stroke_index: Double,
)

data class MetricsResponse(
    val session_id: Int?,
    val swimmer_id: Int?,
    val exercise_id: Int?,
    val session_averages: MetricsSessionAveragesOut,
    val laps: List<MetricsLapOut>,
)

interface MetricsApiService {
    @POST("metrics/session")
    suspend fun computeMetrics(@Body request: MetricsSessionRequest): MetricsResponse
}

object MetricsApiClient {
    // You can later make this configurable if you want different base URLs per build type.
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(DEFAULT_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: MetricsApiService = retrofit.create(MetricsApiService::class.java)
}
