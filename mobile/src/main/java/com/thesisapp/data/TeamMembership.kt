package com.thesisapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
data class TeamMembership(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teamId: Int,
    val swimmerId: Int,
    val joinedAt: Long = System.currentTimeMillis()
)
