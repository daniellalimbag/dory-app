package com.thesisapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "swimmers")
data class Swimmer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val age: Int,
    val wingspan: Float,
    val category: String
)