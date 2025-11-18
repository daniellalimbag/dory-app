# Swimmer View - Current State Review
**Date:** November 17, 2025  
**Branch:** coach-changes2  
**Build:** ✅ Debug APK Generated (71MB)

---

## 📱 Current Implementation Status

### ✅ IMPLEMENTED

#### 1. **Home Dashboard**
- **Location:** `SwimmerHomeFragment.kt`
- **Features:**
  - Welcome message with swimmer name
  - "My Goals" section header (UI ready)
  - "Log Workout" button (prominent)
  - "My Profile" section link
  - Material Design 3 card-based layout

#### 2. **Navigation Structure**
- **Bottom Navigation:** Home, Stats, Profile
- **Fragments:**
  - `SwimmerHomeFragment` - Main dashboard
  - `SwimmerStatsFragment` - Workout history/stats
  - `SwimmerProfileFragment` - Profile management

#### 3. **Data Model (Partial)**
- **Entities:**
  - `Swimmer` - Core swimmer entity
  - `Session` - Workout sessions
  - `Lap` - Lap data with metrics
  - `Team` - Team entity
  - `Coach` - Coach entity
- **Metrics Captured:**
  - Heart rate (before/after)
  - Stroke count
  - Time
  - Distance

#### 4. **Watch Integration**
- **WearOS App:** Separate module (`wear/`)
- **Data Sync:** Health Services integration
- **Capabilities:**
  - Start/stop swim tracking
  - Real-time metrics capture
  - Lap detection

---

## ❌ MISSING (According to Big Plan)

### 🚨 **Critical Missing Features**

#### 1. **Goals System (HIGH PRIORITY)**
**Status:** ❌ Not Implemented

**Required:**
- `Goal` entity with:
  - Event (e.g., "100m Freestyle")
  - Goal time (e.g., "0:58.00")
  - Deadline
  - Goal type (Sprint, Endurance, etc.)
  - Context (Personal, Team A, Team B)
  - Status (Active, Achieved, Expired)
  
**UI Needed:**
- Goals list on home dashboard
- "Set New Goal" form
- Goal progress cards
- Context-based filtering

**Database:**
```kotlin
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val swimmerId: Long,
    val teamId: Long?, // null for personal goals
    val event: String, // "100m Freestyle"
    val goalTime: Long, // milliseconds
    val deadline: Date,
    val goalType: GoalType, // SPRINT, ENDURANCE, THRESHOLD
    val contextType: ContextType, // PERSONAL, TEAM
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Date = Date()
)

enum class GoalType { SPRINT, ENDURANCE, THRESHOLD }
enum class ContextType { PERSONAL, TEAM }
enum class GoalStatus { ACTIVE, ACHIEVED, EXPIRED }
```

---

#### 2. **Exercise Library Integration (HIGH PRIORITY)**
**Status:** ❌ Not Implemented

**Required:**
- Access to coach's exercise library per team
- Exercise entity with:
  - Title
  - Reps
  - Distance per rep
  - Intended effort %
  - Exercise type (Sprint, Endurance, Threshold)
  
**Missing Link:**
- Swimmers cannot see available exercises
- No way to categorize logged workouts to exercises

**Database:**
```kotlin
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val title: String,
    val reps: Int,
    val distancePerRep: Int, // meters
    val intendedEffortPercent: Int, // 1-100
    val exerciseType: ExerciseType,
    val createdBy: Long, // coach ID
    val createdAt: Date = Date()
)

enum class ExerciseType { SPRINT, ENDURANCE, THRESHOLD }
```

---

#### 3. **Log Categorization Flow (CRITICAL)**
**Status:** ❌ Not Implemented

**Current State:**
- Sessions are logged but not categorized
- No context assignment (Personal vs Team)
- No exercise matching

**Required Flow:**
```
Step 1: Record on Watch ✅ (Implemented)
   ↓
Step 2: Sync to Phone ✅ (Implemented)
   ↓
Step 3: Categorize (❌ MISSING)
   → Question 1: "Who is this log for?"
      • My Personal Log (Private)
      • Varsity Team
      • Club Team
   → Question 2: "What exercise was this?"
      • Show team's exercise library
      • Match to exercise
   → Save with context
   → Calculate projected time
   → Update goal progress
```

**UI Needed:**
- "Uncategorized Workouts" section on home
- Categorization dialog/flow
- Team selector
- Exercise selector (filtered by selected team)

---

#### 4. **Goal Progress Graph (HIGH PRIORITY)**
**Status:** ❌ Not Implemented

**Required:**
- X-axis: Date
- Y-axis: Projected Race Time
- Formula: `Projected Time = (Exercise Distance / Goal Distance) × Average Lap Time`
- Points added when:
  - Exercise type matches goal type
  - Workout is categorized to that exercise
  
**Dependencies:**
- Goals system
- Exercise categorization
- Calculation engine

---

#### 5. **Context Management (CRITICAL)**
**Status:** ❌ Not Implemented

**Required:**
- Multi-context support (Personal + Multiple Teams)
- Context switching
- Per-context data:
  - Goals
  - Exercise library
  - Workout logs
  - Progress graphs
  
**UI Needed:**
- "My Profile" → "My Teams" section
- Context switcher on home dashboard
- Visual indicators for which context is active

---

#### 6. **Personal Records (PRs) (LOW PRIORITY)**
**Status:** ❌ Not Implemented

**Required:**
- Manual PR entry
- Trophy case display
- Separate from goals
- Not used for calculations

---

### 📊 **Metrics Tab (Thesis Metrics)**
**Status:** ⚠️ Partially Implemented

**Current State:**
- Raw data is captured (HR, stroke count, time)
- No analysis dashboard
- No exercise-specific filtering

**Required:**
- Dropdown: "Show metrics for..." [Exercise Name]
- Graphs:
  - **Performance:** Actual times over time for that exercise
  - **Effort Validation:** HR vs Intended Effort %
  - **Technique:** Stroke count & length for that exercise

---

## 🏗️ Architecture Review

### Current Structure
```
mobile/src/main/java/com/thesisapp/
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── dao/
│   │   │   ├── SwimmerDao.kt
│   │   │   ├── SessionDao.kt
│   │   │   └── LapDao.kt
│   │   └── entities/
│   │       ├── Swimmer.kt
│   │       ├── Session.kt
│   │       ├── Lap.kt
│   │       ├── Team.kt
│   │       └── Coach.kt
│   └── repository/
│       └── SwimmerRepository.kt
├── domain/
│   └── model/ (empty - needs ViewModels)
└── presentation/
    ├── swimmer/
    │   ├── SwimmerHomeFragment.kt ✅
    │   ├── SwimmerStatsFragment.kt ✅
    │   └── SwimmerProfileFragment.kt ✅
    └── MainActivity.kt
```

### Missing Layers
- ❌ **ViewModels** - No MVVM implementation
- ❌ **Use Cases** - Business logic scattered
- ❌ **Calculation Engine** - No projected time calculator
- ❌ **Repository Layer** - Incomplete for new entities

---

## 🎯 Implementation Roadmap (Priority Order)

### Phase 1: Data Foundation (Week 1)
1. Create `Goal` entity and DAO
2. Create `Exercise` entity and DAO
3. Create `SessionExercise` junction table (links sessions to exercises)
4. Add context fields to `Session` entity
5. Update database version and migrations

### Phase 2: Core Flows (Week 2)
1. Implement categorization flow
   - Uncategorized sessions list
   - Context selector UI
   - Exercise selector UI
   - Save categorization logic
2. Implement goals management
   - Set new goal form
   - Goals list UI
   - Goal status tracking

### Phase 3: Calculations & Progress (Week 3)
1. Build calculation engine
   - Projected time formula
   - Average lap time calculator
   - Exercise type matching
2. Implement goal progress graph
   - Chart library integration (MPAndroidChart)
   - Data preparation
   - Real-time updates

### Phase 4: Context Management (Week 4)
1. Multi-team support
   - Team invitation flow (receive from coach)
   - Team list in profile
   - Context switcher
2. Per-context data filtering
   - Filter goals by context
   - Filter exercises by team
   - Filter workouts by context

### Phase 5: Metrics Dashboard (Week 5)
1. Exercise-specific metrics
   - Performance over time
   - Effort validation (HR vs Intended)
   - Technique graphs
2. Personal Records
   - Manual PR entry
   - Trophy case UI

---

## 🐛 Current Issues

1. **No ViewModel Pattern** - Direct database access from fragments
2. **No Navigation Graph** - Bottom nav only, no deep linking
3. **Hardcoded Strings** - Many UI strings not in resources
4. **No Error Handling** - Database operations not wrapped in try-catch
5. **Missing Permissions** - No runtime permission checks for health data
6. **Deprecated APIs** - Some warnings about Bluetooth adapter usage

---

## ✅ Debug Build Info

**APK Location:**  
`/Users/jabinguamos/Documents/School/thesis/UI/mobile/build/outputs/apk/debug/mobile-debug.apk`

**Size:** 71 MB  
**Build Date:** November 17, 2025 at 17:07  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 34 (Android 14)  

**Install Command:**
```bash
adb install /Users/jabinguamos/Documents/School/thesis/UI/mobile/build/outputs/apk/debug/mobile-debug.apk
```

---

## 📝 Recommendations

### Immediate Next Steps:
1. **Add Goal entity and basic CRUD** - Foundation for all progress tracking
2. **Implement categorization dialog** - Critical for connecting workouts to exercises
3. **Create ViewModels** - Proper MVVM architecture
4. **Add Exercise access** - Swimmers need to see team exercise libraries

### Long-term Improvements:
1. Implement offline-first architecture with WorkManager
2. Add proper error handling and loading states
3. Implement deep linking for team invitations
4. Add unit tests for calculation engine
5. Implement data sync strategy for multi-device support

---

## 🎨 UI/UX Notes

**Strengths:**
- Clean Material Design 3 implementation
- Good use of cards and spacing
- Consistent color scheme
- Bottom navigation is intuitive

**Needs Work:**
- Empty states (no goals, no workouts)
- Loading states
- Error states
- Onboarding flow for new swimmers
- Context switching UI (very important for multi-team support)

---

## 📚 References to Big Plan

| Feature | Big Plan Section | Status |
|---------|------------------|--------|
| Home Dashboard | 🏊 Swimmer View #1 | ✅ Partial |
| Log Workout Button | 🏊 Swimmer View #2 | ✅ Yes |
| Watch Recording | 🏊 Swimmer View #2 Step 1 | ✅ Yes |
| Sync to Phone | 🏊 Swimmer View #2 Step 2 | ✅ Yes |
| Categorization Flow | 🏊 Swimmer View #2 Step 3 | ❌ Missing |
| Context Selection | 🏊 Swimmer View #2 Step 3 Q1 | ❌ Missing |
| Exercise Selection | 🏊 Swimmer View #2 Step 3 Q2 | ❌ Missing |
| Goals Dashboard | 🏊 Swimmer View #3 | ❌ Missing |
| Goal Progress Graph | 🏊 Swimmer View #3 | ❌ Missing |
| Multi-Context Goals | 🏊 Swimmer View #3 | ❌ Missing |
| Personal Records | 🏊 Swimmer View #4 | ❌ Missing |
| Exercise Library Access | 🧑‍🏫 Coach View #2 | ❌ Missing |
| Team Management | 🏊 Swimmer View #1 Profile | ⚠️ Entity exists, no UI |

---

**Overall Completion:** ~30% of Big Plan  
**Critical Path:** Goals → Categorization → Progress Tracking  
**Estimated Work:** 5-6 weeks for full implementation
