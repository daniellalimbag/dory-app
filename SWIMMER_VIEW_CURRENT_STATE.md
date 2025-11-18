# Swimmer View - Current State Review
**Date:** November 17, 2025
**Branch:** coach-changes2

## Overview
This document reviews the current implementation of the Swimmer View against the requirements outlined in the "big plan."

---

## ✅ What's Currently Implemented

### 1. **Navigation Structure**
- ✅ Bottom Navigation with 4 tabs:
  - **Home** (`SwimmerHomeFragment`)
  - **Goals** (`SwimmerGoalsFragment`)
  - **Activity** (`SwimmerActivityFragment`)
  - **Profile** (`SwimmerProfileFragment`)

### 2. **Data Models**
- ✅ **Goal Entity** (`Goal.kt`)
  - Fields: id, swimmerUserId, event, goalTime, deadline, status, createdAt
  - Status tracking: ACTIVE, COMPLETED, CANCELLED
  - ❌ **MISSING:** `goalType` field (Sprint, Endurance, etc.) - Critical for filtering exercises
  - ❌ **MISSING:** `teamId` field - Needed for context-based goals

- ✅ **SwimActivity Entity** (`SwimActivity.kt`)
  - Basic swim tracking with distance, duration, pace
  - ❌ **MISSING:** Context/team association
  - ❌ **MISSING:** Exercise assignment (which exercise from library)
  - ❌ **MISSING:** Calculated metrics (Projected Race Time, etc.)

### 3. **Profile Management**
- ✅ **SwimmerProfile Entity** exists with:
  - Basic info: name, email, age, gender, height, weight
  - Swimming-specific: yearsOfExperience, specialization, skillLevel
  - ❌ **MISSING:** Team memberships/contexts

---

## 🏊 Required Features vs Current State

### **1. Home Dashboard & Contexts**
**Required:**
- Simple view of "My Goals"
- Big "Log Workout" button
- "My Profile" section to manage team contexts

**Current State:**
- ✅ Home fragment exists (`SwimmerHomeFragment`)
- ❓ Need to verify: Goals display
- ❓ Need to verify: Log workout button
- ❌ **MISSING:** Team context management UI

---

### **2. Log Activity (The Core Loop)**
**Required:**
```
Step 1: Record (Watch) → Step 2: Sync → Step 3: Categorize
```

**Current State:**
- ✅ Activity fragment exists (`SwimmerActivityFragment`)
- ❌ **MISSING:** Context selection ("Who is this log for?")
  - Options: [My Personal Log] [Varsity Team] [Club Team]
- ❌ **MISSING:** Exercise assignment ("What exercise was this?")
  - Should show team's exercise library
- ❌ **MISSING:** Automatic calculations:
  - Projected Goal Time formula
  - Adding points to Goal Progress Graph
  - Sharing to team context

**Critical Gap:** The entire categorization workflow is not implemented yet.

---

### **3. My Goals & Progress**
**Required:**
- Dashboard showing all goals grouped by context:
  - Personal Goals
  - Team-specific Goals (e.g., Varsity Team, Club Team)
- Tapping a goal shows Goal Progress Graph
- Graph plots Projected Race Time vs Date

**Current State:**
- ✅ Goals fragment exists (`SwimmerGoalsFragment`)
- ✅ Goal entity exists with basic fields
- ❌ **MISSING:** Context/team grouping
- ❌ **MISSING:** Goal Progress Graph
- ❌ **MISSING:** Projected Race Time calculations
- ❌ **MISSING:** Visual progress tracking

**Critical Gaps:**
1. No `teamId` or context field in Goal entity
2. No graph visualization
3. No formula implementation for projected times

---

### **4. My Personal Records (PRs)**
**Required:**
- Private, manual list of best-ever times
- "Trophy case" - separate from goals
- NOT used for calculations

**Current State:**
- ❌ **COMPLETELY MISSING**
- No PR entity
- No PR UI
- Consider adding as a new tab or section in Profile

---

## 🔧 Database Schema Gaps

### **Goal Entity - Needs:**
```kotlin
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val swimmerUserId: String,
    val event: String,                    // e.g., "100m Freestyle"
    val goalTime: Long,                   // in milliseconds
    val deadline: Long,                   // timestamp
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    
    // 🚨 ADD THESE:
    val goalType: GoalType,              // Sprint, Endurance, Threshold
    val teamId: String?,                 // null = personal goal
    val setByCoachId: String?            // null = self-set
)

enum class GoalType {
    SPRINT, ENDURANCE, THRESHOLD, TECHNIQUE
}
```

### **SwimActivity Entity - Needs:**
```kotlin
@Entity(tableName = "swim_activities")
data class SwimActivity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val swimmerUserId: String,
    val timestamp: Long,
    val distance: Int,
    val duration: Long,
    val pace: Float,
    
    // 🚨 ADD THESE:
    val teamId: String?,                 // null = personal log
    val exerciseId: String?,             // from team's exercise library
    val intendedEffort: Int?,            // 1-100 (from exercise)
    val exerciseType: GoalType?,         // Sprint, Endurance, etc.
    
    // Thesis Metrics:
    val heartRateBefore: Int?,
    val heartRateAfter: Int?,
    val strokeCount: Int?,
    val strokeLength: Float?,
    
    // Calculated:
    val projectedRaceTime: Long?,        // using the formula
    val isCategorized: Boolean = false   // false until categorized
)
```

### **New Entity Needed: TeamMembership**
```kotlin
@Entity(tableName = "team_memberships")
data class TeamMembership(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val swimmerUserId: String,
    val teamId: String,
    val teamName: String,
    val role: String = "SWIMMER",
    val joinedAt: Long = System.currentTimeMillis()
)
```

### **New Entity Needed: PersonalRecord**
```kotlin
@Entity(tableName = "personal_records")
data class PersonalRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val swimmerUserId: String,
    val event: String,                   // e.g., "100m Freestyle"
    val time: Long,                      // in milliseconds
    val date: Long,                      // timestamp
    val location: String?,
    val notes: String?
)
```

---

## 📊 Missing Components

### **UI Components Needed:**

1. **Goal Progress Graph**
   - Library: MPAndroidChart or Jetpack Compose Charts
   - X-axis: Date
   - Y-axis: Projected Race Time
   - Goal line: Horizontal line at target time
   - Data points: Each categorized workout

2. **Context Selector Dialog**
   - Radio buttons: [Personal] [Team A] [Team B]
   - Shows when categorizing uncategorized swims

3. **Exercise Selector Dialog**
   - Shows after team context is selected
   - Lists exercises from team's library
   - Filtered by context

4. **Personal Records List**
   - Simple RecyclerView
   - Grouped by event
   - Add/Edit/Delete functionality

5. **Team Context Manager**
   - In Profile tab
   - List of teams swimmer belongs to
   - Accept/Reject invites

---

## 🧮 Formula Implementation Needed

**Projected Race Time Formula** (from your plan):
```
Projected Time = (Actual Time per Rep / Intended Effort %) × 100
```

This needs to be:
1. Calculated when a swim is categorized with an exercise
2. Stored in `SwimActivity.projectedRaceTime`
3. Plotted on the Goal Progress Graph

**Example:**
- Exercise: "Main Sprint Set" (10 × 50m @ 90% effort)
- Swimmer logs: 50m in 32 seconds
- Projected 50m Sprint Time = (32 / 0.90) × 1 = 35.56 seconds

---

## 🎯 Priority Implementation Order

### **Phase 1: Foundation (Critical)**
1. ✅ Add migration to update Goal entity (add `goalType`, `teamId`, `setByCoachId`)
2. ✅ Add migration to update SwimActivity entity (add all missing fields)
3. ✅ Create TeamMembership entity and DAO
4. ✅ Create PersonalRecord entity and DAO

### **Phase 2: Categorization Flow**
5. ✅ Implement "Uncategorized Swims" view in Activity tab
6. ✅ Create Context Selector dialog
7. ✅ Create Exercise Selector dialog (fetch from team's library)
8. ✅ Implement Projected Race Time calculation
9. ✅ Auto-share categorized logs to team

### **Phase 3: Goals & Progress**
10. ✅ Implement Goal Progress Graph component
11. ✅ Group goals by context in Goals tab
12. ✅ Add data points to graph when swim is categorized
13. ✅ Visual progress indicators

### **Phase 4: Personal Records**
14. ✅ Build PR CRUD UI
15. ✅ Add PR section to Profile or new tab

### **Phase 5: Polish**
16. ✅ Team context management in Profile
17. ✅ Invite acceptance flow
18. ✅ Notifications for new goals from coach

---

## 🐛 Current Issues to Fix

1. **No way to categorize logged swims** - This is the core loop!
2. **Goals are not context-aware** - Can't separate personal vs team goals
3. **No progress visualization** - Swimmers can't see if they're improving
4. **No team management** - Swimmers can't see which teams they belong to
5. **No exercise library integration** - Can't assign exercises to logs
6. **No formula calculations** - Can't predict race times
7. **No PR tracking** - Missing the "trophy case" feature

---

## 📁 Files That Need Work

### **Entities (Add fields):**
- `mobile/src/main/java/com/example/dory/data/entities/Goal.kt`
- `mobile/src/main/java/com/example/dory/data/entities/SwimActivity.kt`

### **New Entities to Create:**
- `mobile/src/main/java/com/example/dory/data/entities/TeamMembership.kt`
- `mobile/src/main/java/com/example/dory/data/entities/PersonalRecord.kt`

### **DAOs to Update/Create:**
- `mobile/src/main/java/com/example/dory/data/dao/GoalDao.kt` (add queries for teamId)
- `mobile/src/main/java/com/example/dory/data/dao/SwimActivityDao.kt` (add uncategorized query)
- `mobile/src/main/java/com/example/dory/data/dao/TeamMembershipDao.kt` (NEW)
- `mobile/src/main/java/com/example/dory/data/dao/PersonalRecordDao.kt` (NEW)

### **Fragments to Enhance:**
- `SwimmerHomeFragment.kt` - Add context switcher, upcoming goals summary
- `SwimmerGoalsFragment.kt` - Add context grouping, progress graphs
- `SwimmerActivityFragment.kt` - Add categorization flow, uncategorized section
- `SwimmerProfileFragment.kt` - Add team management, PR section

### **New Components Needed:**
- `GoalProgressGraphView.kt` - Custom view or composable for graphs
- `CategorizationDialog.kt` - For swim categorization
- `PersonalRecordsFragment.kt` - PR management
- `ProjectedTimeCalculator.kt` - Formula implementation

---

## 🎨 UI/UX Recommendations

1. **Home Tab:**
   - Top: Context selector chip (e.g., "Viewing: Varsity Team")
   - Middle: Card showing next upcoming goal deadline
   - Bottom: Large FAB "Log Workout"

2. **Goals Tab:**
   - Section headers: "Personal Goals", "Varsity Team", "Club Team"
   - Each goal card shows: Event, Target Time, Deadline, Progress %
   - Tap to see detailed graph

3. **Activity Tab:**
   - Top section: "Uncategorized Swims" (red badge if any)
   - Below: Categorized swims grouped by date
   - Each swim shows: Date, Exercise Name, Context Badge

4. **Profile Tab:**
   - Personal Info card
   - "My Teams" section with list
   - "My Personal Records" section
   - Settings

---

## 🚀 Next Steps

1. **Immediate:** Create database migrations for entity updates
2. **Then:** Implement categorization flow (highest priority)
3. **Then:** Build goal progress graph
4. **Finally:** Add PR tracking and polish

**Estimated Development Time:**
- Phase 1 (Foundation): 2-3 days
- Phase 2 (Categorization): 3-4 days
- Phase 3 (Goals & Progress): 3-4 days
- Phase 4 (Personal Records): 1-2 days
- Phase 5 (Polish): 2-3 days

**Total: ~2-3 weeks of focused development**

---

## 📝 Notes
- The core thesis concept (Projected Race Time formula) is NOT yet implemented
- The context-based logging (critical for multi-team swimmers) is NOT yet implemented
- The swimmer view has the basic structure but lacks the smart features that make it useful
- Focus should be on the categorization flow first - this unlocks everything else
