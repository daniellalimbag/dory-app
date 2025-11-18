# 🏊 Swimmer View Implementation Review

**Date:** November 17, 2025  
**Branch:** coach-changes2  
**Review Focus:** Current state vs. Big Plan requirements

---

## 📋 Big Plan Requirements Overview

### Swimmer View Should Have:

1. **Home Dashboard & Contexts**
   - Simple view of "My Goals"
   - Big "Log Workout" button
   - "My Profile" section to manage team contexts

2. **Log Activity (The Core Loop)**
   - Step 1: Record (use watch to "Start Swim")
   - Step 2: Sync (uncategorized swim appears in phone app)
   - Step 3: Categorize (The Context Switch)
     - Question 1: "Who is this log for?" [My Personal Log / Varsity Team / Club Team]
     - Question 2: "What exercise was this?" (shows team's exercise library)

3. **My Goals & Progress**
   - Dashboard showing all goals, grouped by context
   - Personal Goals (self-set)
   - Team Goals (set by coaches)
   - Goal Progress Graph (projected time getting closer to goal)

4. **My Personal Records (PRs)**
   - Private, manual list of best-ever times
   - "Trophy case" separate from goals

---

## ✅ What's Currently Implemented

### 1. **SwimmerProfileActivity** (Main Entry Point)
**Location:** `mobile/src/main/java/com/thesisapp/presentation/SwimmerProfileActivity.kt`

**Current Structure:**
- ✅ Top bar with Team Switcher and Account menu
- ✅ Three tabs:
  - **Tab 0: "Home"** → `SwimmerHomeFragment`
  - **Tab 1: "Performance"** → `SwimmerStatsFragment`
  - **Tab 2: "Profile"** → `SwimmerProfileFragment`

**Team Context Management:**
- ✅ Team switcher dropdown shows all enrolled teams
- ✅ Can switch between teams (reloads swimmer data for that team)
- ✅ Option to "+ Enroll in Another Team"

**Issues:**
- ⚠️ When swimmer has a team, MainActivity redirects to SwimmerProfileActivity automatically (good!)
- ⚠️ No direct "Log Workout" button on main dashboard

---

### 2. **SwimmerHomeFragment** (The Core Dashboard)
**Location:** `mobile/src/main/java/com/thesisapp/presentation/SwimmerHomeFragment.kt`

**Currently Shows:**

#### A. Goal Progress Card
- ✅ Shows current active goal (if coach has set one)
- ✅ Goal title: "100m Freestyle < 1:00.00"
- ✅ Deadline display
- ✅ Line chart showing progress over time (projected race time)
- ✅ "No Goal Assigned" state when coach hasn't set a goal

**Data Source:**
```kotlin
currentGoal = db.goalDao().getActiveGoalForSwimmer(swimmerLocal.id, teamId)
```

**Goal Entity Exists:**
- ✅ `Goal.kt` with: swimmerId, teamId, eventName, goalTime, deadlines, goalType (SPRINT/ENDURANCE), isActive
- ✅ `GoalProgress.kt` for tracking progress points
- ✅ DAOs ready for CRUD operations

#### B. Record Session Button
- ✅ "Record Session" button with watch icon
- ✅ Watch connection status indicator (red dot if disconnected)
- ✅ Calls `startRecording()` which navigates to `ConnectActivity`

**Recording Flow:**
```kotlin
private fun startRecording() {
    startActivity(Intent(requireContext(), ConnectActivity::class.java))
}
```

#### C. Recent Sessions (Horizontal Tabs)
- ✅ Horizontal scrolling RecyclerView of recent sessions
- ✅ Shows all sessions for the swimmer
- ✅ "Pending Categorization" badge with count (for uncategorized sessions)
- ✅ Tap a session → displays metrics below

**Data Source:**
```kotlin
sessions = db.mlResultDao().getResultsForSwimmer(swimmerLocal.id)
val uncategorizedCount = sessions.count { it.exerciseId == null }
```

#### D. Metrics Card
- ✅ Displays detailed metrics for selected session:
  - Stroke Count, Stroke Length
  - Distance, Duration
  - Stroke Index, Lap Time
  - Performance Chart (line graph)
  - Heart Rate Chart (bar graph)

**Metric Calculation:**
```kotlin
private fun displayMetricsForSession(session: MlResult) {
    // Calculates all thesis metrics
    // Shows performance and HR graphs
}
```

---

### 3. **Recording Flow** (Watch Integration)
**Location:** `mobile/src/main/java/com/thesisapp/presentation/TrackSwimmerActivity.kt`

**Current Implementation:**
- ✅ Uses Compose UI with real-time sensor visualization
- ✅ 3D hand model showing gyroscope data
- ✅ Records: accel (x,y,z), gyro (x,y,z), HR, PPG, ECG
- ✅ ML stroke classification (Freestyle, Backstroke, Breaststroke, Butterfly)
- ✅ Saves to `SwimData` table and creates `MlResult` session

**Session Storage:**
```kotlin
val mlResult = MlResult(
    sessionId = newSessionId,
    swimmerId = swimmerId,
    date = date,
    timeStart = timeStart,
    timeEnd = timeEnd,
    backstroke = percentages["backstroke"] ?: 0f,
    breaststroke = percentages["breaststroke"] ?: 0f,
    butterfly = percentages["butterfly"] ?: 0f,
    freestyle = percentages["freestyle"] ?: 0f,
    notes = "[Editable Text Field]"
)
db.mlResultDao().insert(mlResult)
```

---

### 4. **MainActivity** (Entry Point)
**Location:** `mobile/src/main/java/com/thesisapp/presentation/MainActivity.kt`

**Current Behavior for Swimmers:**
- ✅ If swimmer has team + linked swimmer profile → **Auto-redirects to SwimmerProfileActivity**
- ✅ Shows three cards when enrolled:
  - **Connect** button (circular, with watch icon)
  - **Swimmers** card (hidden for swimmers, only coaches see this)
  - **Sessions** card → navigates to `HistoryListActivity`
- ✅ Team switcher at top
- ✅ Account menu (Settings, Logout, Switch User)

**Empty State (No Team):**
- ✅ Shows: "No classes" with "Enroll in Team" button
- ✅ Redirects to `EnrollViaCodeActivity`

---

## ❌ What's Missing (Gaps vs. Big Plan)

### 1. **Categorization Flow (The Core Loop - Step 3)**
**Big Plan:**
> After recording, swimmer must categorize:
> - Question 1: "Who is this log for?" [My Personal Log / Varsity Team / Club Team]
> - Question 2: "What exercise was this?" (from team's exercise library)

**Current Reality:**
- ❌ No categorization dialog/screen
- ❌ Sessions are created with `exerciseId = null` (uncategorized)
- ❌ No way to assign a session to a specific team context
- ❌ No way to link session to an exercise from Exercise Library

**What Needs to Happen:**
1. After `TrackSwimmerActivity` saves `MlResult`, should redirect to **Categorization Screen**
2. Categorization screen should ask:
   - "Categorize this session" → Show dropdown with teams: [Personal, Team A, Team B]
   - If team selected → "What exercise was this?" → Show that team's exercise library
3. Once categorized → Update `MlResult.exerciseId` and `MlResult.teamId`
4. Calculate projected race time using formula
5. Add point to Goal Progress Graph

---

### 2. **Personal Goals (Self-Set Goals)**
**Big Plan:**
> Swimmers can set their own personal goals, separate from coach-assigned goals

**Current Reality:**
- ❌ No UI to create personal goals
- ✅ `Goal` entity supports it (just needs `teamId = null` or special flag)
- ❌ No "Set New Goal" button in Home tab

**What Needs to Happen:**
1. Add "+ Set Personal Goal" button in Home tab (when no goal exists OR in a separate section)
2. Dialog/Activity to create goal:
   - Event name (e.g., "50m Freestyle")
   - Goal time (e.g., "0:28.00")
   - Deadline
   - Goal type (Sprint/Endurance)
3. Mark as personal (maybe `teamId = -1` or `isPersonal = true`)

---

### 3. **My Goals & Progress Tab**
**Big Plan:**
> Dashboard showing ALL goals, grouped by context:
> - Personal Goals
> - Varsity Team Goals
> - Club Team Goals
> Each with its own progress graph

**Current Reality:**
- ✅ Shows ONE active goal in Home tab
- ❌ No dedicated "Goals" tab
- ❌ No grouping by team context
- ❌ Can't see all goals at once

**What Needs to Happen:**
1. Either:
   - **Option A:** Expand Home tab to show multiple goal cards (grouped by context)
   - **Option B:** Add a new "Goals" tab between Home and Performance
2. Show sections:
   - 📌 Personal Goals (with progress graphs)
   - 🏊 [Team Name] Goals (with progress graphs)
3. Each goal card tappable → full-screen progress graph view

---

### 4. **My Personal Records (PRs)**
**Big Plan:**
> A private, manual list of best-ever times. This is their "trophy case"

**Current Reality:**
- ❌ No PR tracking at all
- ❌ No entity for `PersonalRecord`

**What Needs to Happen:**
1. Create `PersonalRecord` entity:
   - swimmerId
   - eventName (e.g., "50m Freestyle")
   - bestTime (e.g., "0:27.85")
   - dateAchieved
   - location/meet (optional)
2. Add "My PRs" section to Profile tab OR new tab
3. Add/edit/delete PRs manually (not auto-calculated)

---

### 5. **Exercise Library Access (Read-Only for Swimmers)**
**Big Plan:**
> When categorizing, swimmer sees the team's exercise library

**Current Reality:**
- ✅ Exercise Library exists (`ExerciseLibraryActivity.kt`)
- ✅ Coaches can create/edit exercises
- ❌ Swimmers can't access it (only coaches can via MainActivity)
- ❌ No "browse exercises" option for swimmers

**What Needs to Happen:**
1. Allow swimmers to VIEW (read-only) their team's exercises
2. Either:
   - Make Exercise Library accessible from Sessions tab
   - Show exercise list in categorization flow
3. Exercise should show:
   - Title, Reps, Distance, Intended Effort %, Exercise Type

---

### 6. **Context Awareness in History**
**Big Plan:**
> Activity log should be filterable by team context

**Current Reality:**
- ✅ `HistoryListActivity` shows all sessions
- ❌ No team context filtering
- ❌ Shows ALL sessions from ALL teams mixed together

**What Needs to Happen:**
1. Add team filter dropdown in `HistoryListActivity`
2. Filter sessions by `MlResult.teamId`
3. Show personal logs separately

---

## 🎯 Priority Fixes (To Match Big Plan)

### Must Have (Core Loop):
1. ❌ **Categorization Flow** after recording
   - Create `CategorizeSessionActivity`
   - Question 1: Team dropdown
   - Question 2: Exercise library picker
   - Update `MlResult` with exerciseId + teamId
   - Calculate & save goal progress

### Should Have (Goals):
2. ❌ **Personal Goals UI**
   - Add "Set Personal Goal" button
   - Dialog to create personal goal
   - Show personal goals separately

3. ❌ **Multiple Goals Display**
   - Show all goals grouped by team
   - Expand Home tab OR add Goals tab

### Nice to Have:
4. ❌ **Personal Records Tracking**
   - Create PR entity
   - UI to add/edit PRs
   - Show in Profile tab

5. ❌ **Exercise Library Access**
   - Read-only view for swimmers
   - Available in categorization flow

6. ❌ **History Filtering**
   - Filter by team context
   - Separate personal logs

---

## 📊 Current Database Schema Status

### ✅ Ready:
- `Goal` entity (with `GoalType` enum)
- `GoalProgress` entity
- `GoalDao` and `GoalProgressDao`
- `Exercise` entity (in Exercise Library)
- `MlResult` entity (has `exerciseId` field, ready to link)

### ❌ Missing:
- `PersonalRecord` entity
- Team context field in `MlResult` (may need `teamId` if not already there)

---

## 🏗️ Architecture Review

### Strong Points:
- ✅ Clean separation: Coach vs Swimmer views
- ✅ Team context switching works well
- ✅ Goal progress graph reuses coach's implementation
- ✅ Watch integration is solid
- ✅ Metrics calculation is thorough

### Weak Points:
- ⚠️ No categorization step breaks the "Core Loop"
- ⚠️ Multiple goals not supported in UI
- ⚠️ Personal goals not implemented
- ⚠️ No PR tracking

---

## 🚀 Recommended Next Steps

1. **Implement Categorization Flow** (Highest Priority)
   - Create `CategorizeSessionActivity.kt`
   - Add team dropdown + exercise picker
   - Link to Exercise Library
   - Update MlResult after categorization
   - Calculate goal progress

2. **Add Personal Goals**
   - Create "Set Goal" dialog
   - Support personal + team goals
   - Group goals by context in UI

3. **Expand Goals Display**
   - Show multiple goals in Home tab
   - OR add dedicated Goals tab

4. **Add PR Tracking**
   - Create PersonalRecord entity
   - Add UI in Profile tab

5. **History Filtering**
   - Team context dropdown in HistoryListActivity

---

## 📱 Current UI Flow Map

```
┌─────────────────────┐
│   MainActivity      │  (Swimmer View)
│  ┌───────────────┐  │
│  │ If has team?  │──┼─YES→ SwimmerProfileActivity
│  └───────────────┘  │       ┌─────────────────┐
│         │            │       │ Tab 0: Home     │ ← You are here
│         NO           │       │ Tab 1: Perf     │
│         ↓            │       │ Tab 2: Profile  │
│  Empty State         │       └─────────────────┘
│  "Enroll in Team"    │                │
└─────────────────────┘                 │
                                        ↓
                        Home Tab (SwimmerHomeFragment)
                        ┌──────────────────────────┐
                        │ 🎯 Goal Progress Card    │
                        │ 📹 Record Session Button │
                        │ 📊 Recent Sessions       │
                        │ 📈 Metrics Card          │
                        └──────────────────────────┘
                                │
                                ↓ (Tap "Record Session")
                        ConnectActivity
                                │
                                ↓ (Connected)
                        TrackSwimmerActivity
                                │
                                ↓ (Stop Recording)
                        ❌ MISSING: CategorizeSessionActivity
                                │
                                ↓
                        Back to Home (session saved as uncategorized)
```

---

## 🔧 Technical Notes

### Data Flow:
1. **Recording:** Watch → TrackSwimmerActivity → MlResult (uncategorized)
2. **Categorization:** ❌ MISSING → Should update MlResult.exerciseId
3. **Goal Progress:** Exercise linked → Calculate projected time → GoalProgress entry
4. **Graph Update:** Home tab loads GoalProgress → Renders line chart

### Key Files to Modify:
- `SwimmerHomeFragment.kt` - Add personal goal button, multi-goal support
- NEW: `CategorizeSessionActivity.kt` - The missing categorization step
- `TrackSwimmerActivity.kt` - Redirect to categorization after recording
- `MlResult.kt` - Ensure teamId field exists
- NEW: `PersonalRecord.kt` - For PR tracking

---

## ✨ Summary

**What's Working:**
- ✅ Basic swimmer dashboard exists
- ✅ Goal progress visualization works
- ✅ Recording and watch integration solid
- ✅ Team context switching functional
- ✅ Metrics calculation comprehensive

**What's Broken/Missing:**
- ❌ **No categorization flow** (breaks core loop!)
- ❌ **No personal goals**
- ❌ **No multi-goal support**
- ❌ **No PR tracking**
- ❌ **No team filtering in history**

**Priority:** Implement categorization flow ASAP to complete the "Core Loop" as described in your big plan.

---

**Review Complete!** Ready to proceed with fixes or debug build.
