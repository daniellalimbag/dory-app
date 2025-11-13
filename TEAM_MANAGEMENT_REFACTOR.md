# Team Management System Refactor - Summary

## Date: November 12, 2025

---

## 🎯 Objective

Revamp the team management system to be more intuitive and remove remnants of the old code-based swimmer enrollment system.

---

## 🔍 Analysis of Old System

### **Problems Identified:**

1. **Conflicting Code Systems:**
   - `Team.joinCode` - Used by coaches to join teams ✅ (This was good)
   - `Swimmer.code` - Used by swimmers to "claim" pre-created profiles ❌ (This was problematic)

2. **Backward Swimmer Enrollment Flow:**
   ```
   OLD FLOW (BAD):
   Coach → Creates full swimmer profile → Generates code → Swimmer claims profile
   
   PROBLEM: Profile exists before swimmer accepts invitation!
   ```

3. **Manual Coach Invitation:**
   - Required typing exact email addresses
   - No invite notifications
   - Confusing UX

---

## ✅ Changes Made

### **1. Database Changes**

#### **Removed from `Swimmer` entity:**
```kotlin
// REMOVED
val code: String // per-swimmer unique code
```

#### **Created new `TeamInvitation` entity:**
```kotlin
@Entity(tableName = "team_invitations")
data class TeamInvitation(
    val id: Int = 0,
    val teamId: Int,
    val inviteCode: String,        // 6-char code
    val invitedEmail: String? = null,
    val role: InvitationRole,      // COACH or SWIMMER
    val status: InvitationStatus,  // PENDING, ACCEPTED, EXPIRED, CANCELLED
    val createdAt: Long,
    val expiresAt: Long,           // 7 days expiration
    val createdBy: String
)
```

#### **Updated AppDatabase:**
- Version bumped from 8 → 9
- Added `teamInvitationDao()` accessor
- Includes automatic migration (destructive for dev)

---

### **2. New Activities Created**

#### **a) InviteSwimmerActivity** ✨
**Purpose:** Generate invitation codes for swimmers

**Features:**
- Input swimmer name (required)
- Input email (optional)
- Generate 6-character invitation code
- Share invitation via system share sheet (SMS, Email, etc.)
- Clean, card-based UI

**Layout:** `activity_invite_swimmer.xml`

**Flow:**
```
Coach → InviteSwimmerActivity → Generates code → Share invitation
                                                → Swimmer receives code
```

---

#### **b) CreateSwimmerProfileActivity** ✨
**Purpose:** Swimmers fill their own profile when accepting invitation

**Features:**
- Full profile form (name, birthday, measurements, sex)
- Links swimmer account to team
- Marks invitation as ACCEPTED
- Redirects to MainActivity

**Layout:** `activity_create_swimmer_profile.xml`

**Flow:**
```
Swimmer → EnrollViaCodeActivity → Shows team info → CreateSwimmerProfileActivity
                                 → Accepts invitation → Profile created → Join team
```

---

### **3. Updated Existing Files**

#### **EnrollViaCodeActivity** (Major Refactor)
**Changes:**
- Now looks up `TeamInvitation` instead of `Swimmer`
- Validates invitation (checks expiration, role)
- Shows confirmation dialog with team name
- Redirects to `CreateSwimmerProfileActivity`

**Old Code (Removed):**
```kotlin
val swimmer = db.swimmerDao().getByCode(code) // ❌ OLD
```

**New Code:**
```kotlin
val invitation = db.teamInvitationDao().getByCode(code) // ✅ NEW
// Validate role, expiration, team exists
// Show confirmation dialog
// Redirect to profile creation
```

---

#### **TrackAddSwimmerActivity**
**Changes:**
- Removed code generation (`CodeGenerator.code(6)`)
- Removed redirect to `TrackSwimmerSuccessActivity`
- Now only used for manual swimmer addition (coach only)
- Simplified success message

**Purpose NOW:** Backup option for coaches to add swimmers who don't have the app

---

#### **SwimmersAdapter**
**Changes:**
- Removed `code` TextView from ViewHolder
- Removed `btnCopyCode` button
- Removed code display logic
- Cleaner swimmer list UI

---

#### **SwimmersActivity**
**Changes:**
- FAB button now shows dialog for coaches:
  - "Invite Swimmer (Recommended)" → `InviteSwimmerActivity`
  - "Add Manually (Coach Only)" → `TrackAddSwimmerActivity`

---

#### **SwimmerDao**
**Changes:**
- Removed `getByCode(code: String)` query
- Cleaner DAO interface

---

### **4. New DAO: TeamInvitationDao**

**Key Methods:**
```kotlin
- insert(invitation): Long
- getByCode(code): TeamInvitation?
- getPendingInvitationsForTeam(teamId): List<TeamInvitation>
- updateStatus(invitationId, status)
- expireOldInvitations(currentTime)
```

---

## 🚀 New Invitation Flow

### **For Swimmers:**
```
1. Coach generates invitation code in InviteSwimmerActivity
2. Coach shares code via SMS/Email/etc.
3. Swimmer opens app → EnrollViaCodeActivity
4. Swimmer enters code
5. App shows: "Join [Team Name]?" confirmation
6. Swimmer accepts → CreateSwimmerProfileActivity
7. Swimmer fills profile (name, birthday, measurements)
8. Profile saved → Invitation marked ACCEPTED
9. Swimmer linked to team → Redirected to MainActivity
```

### **For Coaches:**
```
(Still needs implementation in next phase)
1. Team admin generates coach invitation code
2. Code shared with new coach
3. New coach enters code
4. Added to team as coach
```

---

## 📁 Files Modified

### **Data Layer:**
1. `/data/Swimmer.kt` - Removed `code` field
2. `/data/SwimmerDao.kt` - Removed `getByCode()` method
3. `/data/TeamInvitation.kt` - **NEW**
4. `/data/TeamInvitationDao.kt` - **NEW**
5. `/data/AppDatabase.kt` - Added TeamInvitation entity, bumped version

### **Presentation Layer:**
6. `/presentation/InviteSwimmerActivity.kt` - **NEW**
7. `/presentation/CreateSwimmerProfileActivity.kt` - **NEW**
8. `/presentation/EnrollViaCodeActivity.kt` - Complete refactor
9. `/presentation/TrackAddSwimmerActivity.kt` - Simplified
10. `/presentation/SwimmersActivity.kt` - Updated FAB logic
11. `/presentation/SwimmersAdapter.kt` - Removed code display

### **Layout Files:**
12. `/res/layout/activity_invite_swimmer.xml` - **NEW**
13. `/res/layout/activity_create_swimmer_profile.xml` - **NEW**

---

## 🔄 Migration Notes

⚠️ **Database Migration:**
- Version 8 → 9
- Uses `fallbackToDestructiveMigration()`
- **All existing data will be wiped** (acceptable for dev)
- For production, you'd need proper migration scripts

⚠️ **Breaking Changes:**
- Old swimmer codes are no longer valid
- Existing swimmer profiles remain but can't be "claimed" anymore
- Coaches should use new invitation system going forward

---

## ✨ Benefits of New System

### **1. Better User Experience**
- ✅ Clear invitation flow
- ✅ Swimmers create their own profiles (more accurate data)
- ✅ System share sheet integration (easier sharing)
- ✅ Invitation expiration (security)

### **2. More Intuitive**
- ✅ No pre-created "ghost" profiles
- ✅ Confirmation dialogs with team names
- ✅ Clear role separation (coach vs swimmer invitations)

### **3. Scalable**
- ✅ Can track pending invitations
- ✅ Can revoke invitations
- ✅ Can see invitation history
- ✅ Ready for coach invitations too

---

## 🚧 Still TODO (Next Phase)

1. **EditTeamActivity** - Edit team name, regenerate join code
2. **Redesign ManageCoachesActivity** - Add invitation system for coaches
3. **Pending Invitations View** - Show list of pending invitations
4. **MainActivity Menu Updates** - Better organization of team management options
5. **Invitation Notifications** - (Future) Push notifications for invitations

---

## 🧪 Testing Checklist

### **Swimmer Invitation Flow:**
- [ ] Coach can generate invitation code
- [ ] Invitation code is shareable
- [ ] Swimmer can enter code in EnrollViaCodeActivity
- [ ] Invalid/expired codes are rejected
- [ ] Team confirmation dialog shows correct team name
- [ ] Swimmer profile form validates inputs
- [ ] Profile is created successfully
- [ ] Swimmer is linked to correct team
- [ ] Invitation is marked as ACCEPTED

### **Coach Manual Addition:**
- [ ] Coach can still manually add swimmers via TrackAddSwimmerActivity
- [ ] Manually added swimmers appear in list
- [ ] No code is generated for manual additions

### **Data Integrity:**
- [ ] Database migration works without crashes
- [ ] TeamInvitation table is created
- [ ] Swimmer table no longer has `code` column
- [ ] All existing features still work

---

## 📝 Notes

- The system now separates "invitation" from "profile creation"
- Invitations are temporary (expire after 7 days)
- Coaches can choose between inviting or manually adding
- Manual addition is kept for edge cases (swimmers without app access)
- The new system is more aligned with modern app onboarding patterns

---

## 🎓 Key Architectural Decisions

1. **Why separate TeamInvitation from Swimmer?**
   - Invitations are temporary, profiles are permanent
   - Allows tracking of invitation lifecycle
   - Enables invitation revocation

2. **Why keep TrackAddSwimmerActivity?**
   - Edge case: swimmers who don't have app yet
   - Allows offline data collection
   - Provides fallback option

3. **Why 7-day expiration?**
   - Balances convenience vs security
   - Prevents old codes from being misused
   - Can be adjusted based on user feedback

---

**End of Summary**
