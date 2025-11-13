package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TeamInvitationDao {
    @Insert
    suspend fun insert(invitation: TeamInvitation): Long

    @Update
    suspend fun update(invitation: TeamInvitation)

    @Query("SELECT * FROM team_invitations WHERE inviteCode = :code AND status = 'PENDING' LIMIT 1")
    suspend fun getByCode(code: String): TeamInvitation?

    @Query("SELECT * FROM team_invitations WHERE teamId = :teamId AND status = 'PENDING'")
    suspend fun getPendingInvitationsForTeam(teamId: Int): List<TeamInvitation>

    @Query("SELECT * FROM team_invitations WHERE teamId = :teamId AND role = :role AND status = 'PENDING'")
    suspend fun getPendingInvitationsByRole(teamId: Int, role: InvitationRole): List<TeamInvitation>

    @Query("UPDATE team_invitations SET status = :status WHERE id = :invitationId")
    suspend fun updateStatus(invitationId: Int, status: InvitationStatus)

    @Query("UPDATE team_invitations SET status = 'EXPIRED' WHERE expiresAt < :currentTime AND status = 'PENDING'")
    suspend fun expireOldInvitations(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM team_invitations WHERE teamId = :teamId")
    suspend fun deleteAllForTeam(teamId: Int)
}
