package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Junction table linking swimmers to teams.
 * Separates team membership from swimmer's physical profile,
 * allowing swimmers to belong to multiple teams with the same profile.
 */
@Entity(
    tableName = "team_memberships",
    foreignKeys = [
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Swimmer::class,
            parentColumns = ["id"],
            childColumns = ["swimmerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("teamId"),
        Index("swimmerId"),
        Index(value = ["teamId", "swimmerId"], unique = true)
    ]
)
@Serializable
data class TeamMembership(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerialName("team_id") val teamId: Int,
    @SerialName("swimmer_id") val swimmerId: Int,
    @SerialName("joined_at") val joinedAt: Long = System.currentTimeMillis()
)
