package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName

enum class UserRole {
    COACH,
    SWIMMER
}

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true)
    ]
)
data class User(
    @PrimaryKey val id: String,
    val email: String,
    val role: UserRole,
    @SerialName("created_at_ms") val createdAt: Long = System.currentTimeMillis()
)
