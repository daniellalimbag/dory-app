package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val createdAt: Long = System.currentTimeMillis()
)
