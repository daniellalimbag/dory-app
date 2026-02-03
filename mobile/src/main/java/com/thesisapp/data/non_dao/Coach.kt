package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName

@Entity(
    tableName = "coaches",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId"], unique = true),
        Index(value = ["teamId"])
    ]
)
data class Coach(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("team_id") val teamId: Int? = null
)
