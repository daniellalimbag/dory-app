# Testing Swimmer View

## What's Changed

### UI Improvements
1. **Watch Connection Card**: Now shows status prominently with the record button (more intuitive than top bar indicator)
2. **Tab Structure**: Reduced from 4 tabs to 3 tabs:
   - **Home**: Core loop (goal → watch → record → sessions)
   - **Performance**: Combined Stats + Sessions (like coach view)
   - **Profile**: Personal info

### Home Tab Features
- **Goal Progress Graph**: Shows progress over time (reused from coach view)
- **Watch Status**: Clear connection indicator with record button
- **Recent Sessions**: Horizontal scrollable tabs
- **Pending Badge**: Shows count of uncategorized sessions (e.g., "3 pending")
- **Metrics Display**: Shows latest session metrics by default (click other sessions to switch)

## How to Test

### 1. Create Test Account
Register a new swimmer account or use existing one

### 2. Create a Goal
- Navigate to Profile tab
- Create a goal (e.g., "100m Freestyle under 60 seconds")

### 3. Add Goal Progress
You'll need to manually add progress points through the coach view or database:
- Use coach view to track swimmer progress
- Or directly insert into `GoalProgress` table

### 4. Create Exercises (Coach View)
- Login as coach
- Create 3-4 exercises for your team with different effort levels:
  - Sprint Intervals (85% effort)
  - Endurance Base (70% effort)
  - Technique Drills (60% effort)
  - Race Pace (90% effort)

### 5. Record Sessions
**Option A: Using Watch App**
- Connect your Wear OS watch
- Record actual swimming sessions
- Data syncs to phone

**Option B: Manual Database Insert**
For testing, you can insert sessions directly into the `MlResult` table:

```sql
-- Categorized session (has exerciseId)
INSERT INTO MlResult (
    sessionId, swimmerId, exerciseId, exerciseName, sets, distance,
    timeStart, timeEnd, heartRateBefore, heartRateAfter,
    strokeCount, avgStrokeLength, strokeIndex, avgLapTime, totalDistance,
    date, backstroke, breaststroke, butterfly, freestyle
) VALUES (
    0, YOUR_SWIMMER_ID, EXERCISE_ID, 'Sprint Intervals', 5, 50,
    '09:00:00', '09:35:00', 78, 165,
    42, 1.19, 2.45, 28.5, 1000,
    CURRENT_TIMESTAMP, 0.0, 0.0, 0.0, 1000.0
);

-- Uncategorized session (NULL exerciseId) - Will show in pending badge
INSERT INTO MlResult (
    sessionId, swimmerId, exerciseId, exerciseName, sets, distance,
    timeStart, timeEnd, heartRateBefore, heartRateAfter,
    strokeCount, avgStrokeLength, strokeIndex, avgLapTime, totalDistance,
    date, backstroke, breaststroke, butterfly, freestyle
) VALUES (
    0, YOUR_SWIMMER_ID, NULL, NULL, NULL, NULL,
    '07:00:00', '07:42:00', 75, 158,
    48, 1.35, 2.52, 35.2, 800,
    CURRENT_TIMESTAMP, 0.0, 0.0, 0.0, 800.0
);
```

### 6. What to Verify

**Home Tab:**
- ✅ Goal card shows current goal with deadline
- ✅ Progress graph displays historical performance
- ✅ Watch status shows "Not Connected" (red) or "Connected" (green)
- ✅ Record button is enabled when watch connected
- ✅ Recent sessions appear as horizontal scrollable tabs
- ✅ Pending badge shows count of uncategorized sessions
- ✅ Latest session metrics display by default
- ✅ Clicking different session tabs switches the metrics displayed
- ✅ Performance chart shows lap times
- ✅ Heart rate chart shows before/after comparison

**Performance Tab:**
- ✅ Shows all sessions (categorized + uncategorized)
- ✅ Displays detailed metrics and charts

**Profile Tab:**
- ✅ Personal information
- ✅ Account settings

## Key Testing Scenarios

### Scenario 1: New Swimmer (No Data)
- Goal card shows "No active goal"
- Sessions area is empty
- Pending badge is hidden
- Metrics card is hidden

### Scenario 2: Active Training (Categorized Sessions)
- Goal progress graph shows improvement trend
- Sessions appear in horizontal tabs
- Latest session metrics displayed
- Charts show lap times and heart rate
- Pending badge hidden (all sessions categorized)

### Scenario 3: Pending Categorization
- Pending badge visible with count (e.g., "3 pending")
- Uncategorized sessions appear in tabs but show minimal info
- Coach needs to assign exercises to these sessions

### Scenario 4: Watch Connection
- Disconnected: Red indicator, button disabled, text says "Watch Not Connected"
- Connected: Green indicator, button enabled, text says "Watch Connected"

## Database Schema Reference

**Key Tables:**
- `swimmers`: Athlete profiles
- `Goal`: Performance targets
- `GoalProgress`: Progress tracking points
- `Exercise`: Workout templates (created by coach)
- `MlResult`: Swimming session data
  - If `exerciseId` is NOT NULL → categorized session
  - If `exerciseId` is NULL → uncategorized (pending)

## Notes
- The app uses existing database and doesn't create dummy data automatically
- You must have sessions recorded via the watch app or inserted manually
- The "core loop" is: Check goal → Ensure watch connected → Record session → Review metrics
- Uncategorized sessions need coach to assign an exercise via coach view
