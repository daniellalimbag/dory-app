package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class InvitationRole {
    COACH,
    SWIMMER
}

enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    EXPIRED,
    CANCELLED
}

@Entity(tableName = "team_invitations")
data class TeamInvitation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teamId: Int,
    val inviteCode: String, // 6-char code like "ABC123"
    val invitedEmail: String? = null, // Optional - can invite without knowing email
    val role: InvitationRole, // COACH or SWIMMER
    val status: InvitationStatus = InvitationStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 days default
    val createdBy: String // Email of coach who created the invitation
)
