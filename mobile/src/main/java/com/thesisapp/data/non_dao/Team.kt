package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @SerialName("join_code") val joinCode: String,
    @SerialName("logo_path") val logoPath: String? = null
)

