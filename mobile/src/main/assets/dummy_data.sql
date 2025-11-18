-- Dummy Data for Testing Swimmer View
-- Run this after fresh app install

-- 1. Create a test user
INSERT OR REPLACE INTO User (id, email, firstName, lastName, birthday, isCoach) 
VALUES (999, 'test.swimmer@example.com', 'Test', 'Swimmer', '2000-01-01', 0);

-- 2. Create a test team
INSERT OR REPLACE INTO Team (id, name, coachId) 
VALUES (999, 'Test Swim Team', 1);

-- 3. Create test swimmer profile
INSERT OR REPLACE INTO Swimmer (id, userId, firstName, lastName, birthday, gender, teamId)
VALUES (999, 999, 'Test', 'Swimmer', '2000-01-01', 'Male', 999);

-- 4. Create a goal for the swimmer (100m Freestyle under 60 seconds, deadline in 30 days)
INSERT OR REPLACE INTO Goal (id, swimmerId, teamId, eventName, goalTime, startDate, endDate, isActive)
VALUES (999, 999, 999, '100m Freestyle', '0:59.50', 
        strftime('%s', 'now') * 1000,
        strftime('%s', 'now', '+30 days') * 1000,
        1);

-- 5. Add some goal progress points
INSERT OR REPLACE INTO GoalProgress (id, goalId, recordedDate, projectedRaceTime)
VALUES 
(9991, 999, strftime('%s', 'now', '-14 days') * 1000, '1:05.20'),
(9992, 999, strftime('%s', 'now', '-10 days') * 1000, '1:03.80'),
(9993, 999, strftime('%s', 'now', '-7 days') * 1000, '1:02.10'),
(9994, 999, strftime('%s', 'now', '-3 days') * 1000, '1:00.90'),
(9995, 999, strftime('%s', 'now', '-1 days') * 1000, '1:00.20');

-- 6. Create some exercises for the team
INSERT OR REPLACE INTO Exercise (id, teamId, name, sets, reps, distance, effortLevel, restTime, description)
VALUES 
(9991, 999, 'Sprint Intervals', 5, 4, 50, 85, 30, 'High intensity sprint work'),
(9992, 999, 'Endurance Base', 3, 8, 100, 70, 45, 'Aerobic base building'),
(9993, 999, 'Technique Drills', 4, 6, 25, 60, 20, 'Form and efficiency work'),
(9994, 999, 'Race Pace', 6, 2, 100, 90, 60, 'Competition pace simulation');

-- 7. Create CATEGORIZED sessions (with exerciseId)
INSERT OR REPLACE INTO MlResult (
    sessionId, swimmerId, exerciseId, exerciseName, sets, reps, distance,
    timeStart, timeEnd, heartRateBefore, heartRateAfter,
    strokeCount, avgStrokeLength, strokeIndex, avgLapTime, totalDistance
)
VALUES 
-- Session 1: Sprint Intervals (2 days ago)
('session_cat_001', 999, 9991, 'Sprint Intervals', 5, 4, 50,
 '09:00:00', '09:35:00', 78, 165,
 42, 1.19, 2.45, 28.5, 1000),

-- Session 2: Endurance Base (5 days ago)
('session_cat_002', 999, 9992, 'Endurance Base', 3, 8, 100,
 '08:30:00', '09:45:00', 72, 142,
 65, 1.54, 2.35, 62.3, 2400),

-- Session 3: Race Pace (7 days ago)
('session_cat_003', 999, 9994, 'Race Pace', 6, 2, 100,
 '10:00:00', '11:10:00', 80, 168,
 58, 1.72, 2.89, 58.7, 1200);

-- 8. Create UNCATEGORIZED sessions (exerciseId is NULL) - these need categorization
INSERT OR REPLACE INTO MlResult (
    sessionId, swimmerId, exerciseId, exerciseName, sets, reps, distance,
    timeStart, timeEnd, heartRateBefore, heartRateAfter,
    strokeCount, avgStrokeLength, strokeIndex, avgLapTime, totalDistance
)
VALUES 
-- Uncategorized Session 1 (today)
('session_uncat_001', 999, NULL, NULL, NULL, NULL, NULL,
 '07:00:00', '07:42:00', 75, 158,
 48, 1.35, 2.52, 35.2, 800),

-- Uncategorized Session 2 (yesterday)
('session_uncat_002', 999, NULL, NULL, NULL, NULL, NULL,
 '16:30:00', '17:25:00', 82, 172,
 72, 1.48, 2.68, 45.8, 1600),

-- Uncategorized Session 3 (3 days ago)
('session_uncat_003', 999, NULL, NULL, NULL, NULL, NULL,
 '09:15:00', '10:05:00', 70, 150,
 55, 1.42, 2.40, 40.5, 1200);

-- Summary:
-- - 1 swimmer account (test.swimmer@example.com)
-- - 1 active goal (100m Freestyle under 60 seconds)
-- - 5 progress points showing improvement
-- - 4 available exercises
-- - 3 categorized sessions (assigned to exercises)
-- - 3 uncategorized sessions (need categorization - should show "3 pending" badge)
