package com.thesisapp.communication

import com.thesisapp.data.SensorData
import kotlinx.serialization.Serializable

@Serializable
data class SessionChunkPayload(
    val sessionId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val records: List<SensorData>
)
