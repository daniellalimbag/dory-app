package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TeamMembershipDao {
    /**
     * Add a swimmer to a team
     */
    @Insert
    suspend fun insert(membership: TeamMembership): Long

    /**
     * Remove a swimmer from a team
     */
    @Delete
    suspend fun delete(membership: TeamMembership)

    /**
     * Get all swimmers for a specific team
     */
    @Transaction
    @Query("""
        SELECT swimmers.* FROM swimmers
        INNER JOIN team_memberships ON swimmers.id = team_memberships.swimmerId
        WHERE team_memberships.teamId = :teamId
        ORDER BY swimmers.name ASC
    """)
    suspend fun getSwimmersForTeam(teamId: Int): List<Swimmer>

    /**
     * Get all teams for a specific swimmer
     */
    @Transaction
    @Query("""
        SELECT teams.* FROM teams
        INNER JOIN team_memberships ON teams.id = team_memberships.teamId
        WHERE team_memberships.swimmerId = :swimmerId
        ORDER BY teams.name ASC
    """)
    suspend fun getTeamsForSwimmer(swimmerId: Int): List<Team>

    /**
     * Check if a swimmer is already a member of a team
     */
    @Query("SELECT COUNT(*) > 0 FROM team_memberships WHERE teamId = :teamId AND swimmerId = :swimmerId")
    suspend fun isMember(teamId: Int, swimmerId: Int): Boolean

    /**
     * Get membership record
     */
    @Query("SELECT * FROM team_memberships WHERE teamId = :teamId AND swimmerId = :swimmerId LIMIT 1")
    suspend fun getMembership(teamId: Int, swimmerId: Int): TeamMembership?

    /**
     * Remove swimmer from all teams (for cleanup)
     */
    @Query("DELETE FROM team_memberships WHERE swimmerId = :swimmerId")
    suspend fun removeSwimmerFromAllTeams(swimmerId: Int)

    /**
     * Remove all memberships for a team (for cleanup)
     */
    @Query("DELETE FROM team_memberships WHERE teamId = :teamId")
    suspend fun removeAllMembershipsForTeam(teamId: Int)

    /**
     * Get count of swimmers in a team
     */
    @Query("SELECT COUNT(*) FROM team_memberships WHERE teamId = :teamId")
    suspend fun getSwimmerCountForTeam(teamId: Int): Int

    /**
     * Clear all memberships (for testing)
     */
    @Query("DELETE FROM team_memberships")
    suspend fun clearAll()
}
