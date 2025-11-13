# Swimmer Profile Architecture Refactor

## Problem Statement

The previous implementation had two major issues:

### 1. **Backward Enrollment Flow**
- **Old Way**: Coach fills in swimmer's measurements → generates code → swimmer "claims" existing profile
- **Problem**: Coach doesn't know swimmer's exact height, weight, wingspan, birthday
- **Bad UX**: Coach has to guess or ask swimmer for measurements beforehand

### 2. **Team-Bound Profiles**
- **Old Schema**: `Swimmer` had `teamId` field embedding team membership directly in profile
- **Problem**: A swimmer's physical attributes (height, weight, etc.) don't change between teams
- **Issue**: If swimmer joins multiple teams, they'd have duplicate profiles with same measurements

## Solution: Junction Table Architecture

### New Data Model

```kotlin
// Swimmer - Team-independent profile (physical attributes)
data class Swimmer(
    id: Int,
    name: String,
    birthday: String,  // Physical attributes are universal
    height: Float,
    weight: Float,
    sex: String,
    wingspan: Float
    // NO teamId field
)

// TeamMembership - Junction table linking swimmers to teams
data class TeamMembership(
    id: Int,
    teamId: Int,       // Foreign key to Team
    swimmerId: Int,    // Foreign key to Swimmer
    joinedAt: Long     // Timestamp of when they joined
)
```

### Database Changes

**Version: 9 → 10**

**Entities Added:**
- `TeamMembership` junction table with foreign keys and unique constraint on (teamId, swimmerId)

**Schema Changes:**
- Removed `Swimmer.teamId` field
- Added `TeamMembershipDao` with queries:
  - `getSwimmersForTeam(teamId)` - Get all swimmers on a team
  - `getTeamsForSwimmer(swimmerId)` - Get all teams a swimmer belongs to
  - `isMember(teamId, swimmerId)` - Check membership
  - `getSwimmerCountForTeam(teamId)` - Count swimmers on team

## New Flow

### Coach Invites Swimmer

```
1. Coach clicks "Add Swimmer" → Opens InviteSwimmerActivity
2. Coach enters swimmer's name and email (optional)
3. System generates 6-character invitation code
4. Coach shares code via SMS/Email/WhatsApp
```

**Note:** Coach does NOT fill in measurements - they don't have this information!

### Swimmer Creates Profile

```
1. Swimmer enters invitation code → EnrollViaCodeActivity
2. Swimmer sees confirmation: "Join [Team Name]?"
3. Swimmer accepts → Redirected to CreateSwimmerProfileActivity
4. Swimmer fills in THEIR OWN measurements:
   - Birthday (NumberPicker)
   - Height, Weight, Wingspan (TextInput)
   - Sex (RadioButton)
5. Save → Creates:
   - Swimmer record (team-independent)
   - TeamMembership record (links swimmer to team)
   - Links swimmer ID to user account
```

### Multi-Team Support

**Scenario:** Swimmer joins a second team

```
1. Swimmer gets new invitation code from second coach
2. Swimmer enters code → System checks if swimmer profile exists
3. If exists: Reuse existing profile + Create new TeamMembership
4. If not exists: Create new profile + TeamMembership
```

**Result:** Same swimmer profile (height, weight, etc.) shared across multiple teams!

## Files Modified

### Data Layer (5 files)

1. **Swimmer.kt**
   - Removed `teamId: Int` field
   - Now team-independent

2. **TeamMembership.kt** ✨ NEW
   - Junction table entity
   - Foreign keys to Team and Swimmer
   - Unique constraint on (teamId, swimmerId)

3. **TeamMembershipDao.kt** ✨ NEW
   - CRUD operations for memberships
   - JOIN queries to get swimmers for team and teams for swimmer

4. **SwimmerDao.kt**
   - Removed `getSwimmersForTeam(teamId)` query
   - Moved to TeamMembershipDao

5. **AppDatabase.kt**
   - Added TeamMembership entity
   - Added teamMembershipDao()
   - Version bumped to 10

### Presentation Layer (5 files)

1. **CreateSwimmerProfileActivity.kt**
   - Updated to create TeamMembership after creating Swimmer
   - Properly separates profile creation from team enrollment

2. **SwimmersActivity.kt**
   - Removed "Add Manually" option for coaches
   - Now only "Invite Swimmer" button
   - Updated loadSwimmers() to use `teamMembershipDao.getSwimmersForTeam()`

3. **MainActivity.kt**
   - Updated loadSwimmerCount() to use `teamMembershipDao.getSwimmerCountForTeam()`

4. **ConnectActivity.kt**
   - Updated swimmer list query to use `teamMembershipDao.getSwimmersForTeam()`

5. **TrackAddSwimmerActivity.kt**
   - Updated to create TeamMembership after creating Swimmer
   - Note: This activity still exists but is no longer accessible from coach flow

## Benefits

### ✅ Better UX
- Swimmers fill in their own accurate measurements
- Coaches don't need to know physical attributes beforehand
- More intuitive "invitation" metaphor

### ✅ Better Data Model
- Swimmer profile is team-independent
- Single source of truth for physical attributes
- Supports multi-team membership naturally

### ✅ Data Integrity
- Foreign key constraints enforce referential integrity
- Unique constraint prevents duplicate memberships
- Cascade deletes clean up orphaned records

## Testing Checklist

- [ ] Coach can generate invitation code
- [ ] Swimmer can accept invitation via code
- [ ] Swimmer can create profile with all fields
- [ ] Profile saves correctly to database
- [ ] TeamMembership record created
- [ ] Swimmer appears in coach's swimmer list
- [ ] Swimmer can join multiple teams with same profile
- [ ] Switching teams shows correct swimmers
- [ ] Deleting team removes memberships (cascade)
- [ ] Deleting swimmer removes memberships (cascade)

## Migration Notes

**Database Migration:** Destructive (fallbackToDestructiveMigration)

**Data Loss:** All existing swimmers will be deleted when database upgrades from v9 to v10.

**Production Consideration:** For production, implement proper migration:
```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Create team_memberships table
        database.execSQL("""
            CREATE TABLE team_memberships (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                teamId INTEGER NOT NULL,
                swimmerId INTEGER NOT NULL,
                joinedAt INTEGER NOT NULL,
                FOREIGN KEY(teamId) REFERENCES teams(id) ON DELETE CASCADE,
                FOREIGN KEY(swimmerId) REFERENCES swimmers(id) ON DELETE CASCADE
            )
        """)
        
        // 2. Migrate existing swimmers to junction table
        database.execSQL("""
            INSERT INTO team_memberships (teamId, swimmerId, joinedAt)
            SELECT teamId, id, ${System.currentTimeMillis()}
            FROM swimmers
        """)
        
        // 3. Create new swimmers table without teamId
        database.execSQL("""
            CREATE TABLE swimmers_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                birthday TEXT NOT NULL,
                height REAL NOT NULL,
                weight REAL NOT NULL,
                sex TEXT NOT NULL,
                wingspan REAL NOT NULL
            )
        """)
        
        // 4. Copy data
        database.execSQL("""
            INSERT INTO swimmers_new (id, name, birthday, height, weight, sex, wingspan)
            SELECT id, name, birthday, height, weight, sex, wingspan
            FROM swimmers
        """)
        
        // 5. Drop old table and rename
        database.execSQL("DROP TABLE swimmers")
        database.execSQL("ALTER TABLE swimmers_new RENAME TO swimmers")
        
        // 6. Create indices
        database.execSQL("CREATE INDEX index_team_memberships_teamId ON team_memberships(teamId)")
        database.execSQL("CREATE INDEX index_team_memberships_swimmerId ON team_memberships(swimmerId)")
        database.execSQL("CREATE UNIQUE INDEX index_team_memberships_teamId_swimmerId ON team_memberships(teamId, swimmerId)")
    }
}
```

## Next Steps

1. **Test the Flow:** Run app and test coach invite → swimmer accept → profile creation
2. **Test Multi-Team:** Have one swimmer join two different teams
3. **Verify Data:** Check database to ensure TeamMembership records are correct
4. **Remove TrackAddSwimmerActivity:** Consider deprecating this since it's no longer used in coach flow
5. **Update Documentation:** Update user guides and onboarding materials
