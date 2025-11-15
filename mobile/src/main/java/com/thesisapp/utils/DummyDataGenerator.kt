package com.thesisapp.utils

import com.thesisapp.data.*
import java.text.SimpleDateFormat
import java.util.*

object DummyDataGenerator {
    
    private val random = Random()
    
    fun generateDummySwimmers(teamId: Int, count: Int = 5): List<Swimmer> {
        val firstNames = listOf("Emma", "Olivia", "Ava", "Sophia", "Isabella", 
                                "Liam", "Noah", "William", "James", "Oliver",
                                "Charlotte", "Amelia", "Mia", "Harper", "Evelyn",
                                "Benjamin", "Lucas", "Henry", "Alexander", "Sebastian")
        val lastNames = listOf("Smith", "Johnson", "Williams", "Brown", "Jones", 
                               "Garcia", "Miller", "Davis", "Rodriguez", "Martinez",
                               "Anderson", "Taylor", "Thomas", "Moore", "Jackson")
        
        val usedNames = mutableSetOf<String>()
        
        return (1..count).mapNotNull { i ->
            var attempts = 0
            var swimmer: Swimmer? = null
            
            while (attempts < 20 && swimmer == null) {
                val firstName = firstNames.random()
                val lastName = lastNames.random()
                val fullName = "$firstName $lastName"
                
                if (fullName !in usedNames) {
                    usedNames.add(fullName)
                    val isMale = firstName in listOf("Liam", "Noah", "William", "James", "Oliver", "Benjamin", "Lucas", "Henry", "Alexander", "Sebastian")
                    
                    // More varied age distribution
                    val ageCategory = random.nextInt(3)
                    val age = when (ageCategory) {
                        0 -> random.nextInt(4) + 14 // 14-17 (juniors)
                        1 -> random.nextInt(6) + 18 // 18-23 (college)
                        else -> random.nextInt(5) + 24 // 24-28 (masters)
                    }
                    
                    swimmer = Swimmer(
                        id = 0,
                        name = fullName,
                        birthday = generateRandomBirthday(age, age),
                        height = if (isMale) randomFloat(165f, 195f) else randomFloat(155f, 180f),
                        weight = if (isMale) randomFloat(60f, 90f) else randomFloat(50f, 75f),
                        sex = if (isMale) "Male" else "Female",
                        wingspan = if (isMale) randomFloat(170f, 200f) else randomFloat(160f, 185f),
                        category = if (i % 3 == 0) ExerciseCategory.DISTANCE else ExerciseCategory.SPRINT // 2/3 sprint, 1/3 distance
                    )
                }
                attempts++
            }
            
            swimmer
        }
    }
    
    fun generateDummyExercises(teamId: Int): List<Exercise> {
        val sprintExercises = listOf(
            Exercise(
                teamId = teamId,
                name = "50m Sprint Set",
                category = ExerciseCategory.SPRINT,
                description = "High-intensity 50m freestyle sprints with maximum effort",
                distance = 50,
                sets = 8,
                restTime = 60,
                effortLevel = 95 // 95% max effort
            ),
            Exercise(
                teamId = teamId,
                name = "25m Explosiveness",
                category = ExerciseCategory.SPRINT,
                description = "Short explosive bursts focusing on dive and breakout speed",
                distance = 25,
                sets = 12,
                restTime = 45,
                effortLevel = 98 // 98% max effort
            ),
            Exercise(
                teamId = teamId,
                name = "100m Fast Pace",
                category = ExerciseCategory.SPRINT,
                description = "100m freestyle maintaining race pace throughout",
                distance = 100,
                sets = 6,
                restTime = 90,
                effortLevel = 90 // 90% max effort
            ),
            Exercise(
                teamId = teamId,
                name = "Relay Starts",
                category = ExerciseCategory.SPRINT,
                description = "Practice relay exchanges and reaction time",
                distance = 50,
                sets = 10,
                restTime = 60,
                effortLevel = 92 // 92% max effort
            )
        )
        
        val distanceExercises = listOf(
            Exercise(
                teamId = teamId,
                name = "800m Endurance",
                category = ExerciseCategory.DISTANCE,
                description = "Steady-state 800m freestyle for aerobic capacity",
                distance = 800,
                sets = 4,
                restTime = 120,
                effortLevel = 70 // 70% - Zone 2
            ),
            Exercise(
                teamId = teamId,
                name = "1500m Time Trial",
                category = ExerciseCategory.DISTANCE,
                description = "Full 1500m freestyle at target race pace",
                distance = 1500,
                sets = 2,
                restTime = 300,
                effortLevel = 85 // 85% - race pace
            ),
            Exercise(
                teamId = teamId,
                name = "400m IM",
                category = ExerciseCategory.DISTANCE,
                description = "Individual medley focusing on stroke transitions",
                distance = 400,
                sets = 5,
                restTime = 150,
                effortLevel = 80 // 80% - threshold
            ),
            Exercise(
                teamId = teamId,
                name = "200m Threshold",
                category = ExerciseCategory.DISTANCE,
                description = "200m repeats at lactate threshold pace",
                distance = 200,
                sets = 10,
                restTime = 30,
                effortLevel = 82 // 82% - threshold
            )
        )
        
        return sprintExercises + distanceExercises
    }
    
    fun generateDummySessions(swimmerId: Int, swimmerCategory: ExerciseCategory, exercises: List<Exercise>, count: Int = 10): List<MlResult> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sessions = mutableListOf<MlResult>()
        
        // Filter exercises by swimmer category
        val relevantExercises = exercises.filter { it.category == swimmerCategory }
        if (relevantExercises.isEmpty()) return emptyList()
        
        val calendar = Calendar.getInstance()
        
        for (i in 0 until count) {
            // Go back in time for each session
            calendar.add(Calendar.DAY_OF_YEAR, -random.nextInt(4) - 1) // 1-4 days apart
            
            val date = dateFormat.format(calendar.time)
            val startHour = random.nextInt(8) + 5 // Between 5 AM and 12 PM
            val startMinute = listOf(0, 15, 30, 45).random()
            val durationMinutes = random.nextInt(60) + 45 // 45-105 minutes
            
            val timeStart = String.format("%02d:%02d", startHour, startMinute)
            
            val endCalendar = calendar.clone() as Calendar
            endCalendar.add(Calendar.MINUTE, durationMinutes)
            val timeEnd = String.format("%02d:%02d", 
                endCalendar.get(Calendar.HOUR_OF_DAY), 
                endCalendar.get(Calendar.MINUTE))
            
            // Pick a random exercise for this session
            val exercise = relevantExercises.random()
            
            // Calculate session metrics based on exercise
            val setsCompleted = exercise.sets ?: random.nextInt(5) + 3
            val repsPerSet = random.nextInt(4) + 1
            val distancePerRep = exercise.distance ?: (if (swimmerCategory == ExerciseCategory.SPRINT) 50 else 200)
            val totalDistance = setsCompleted * repsPerSet * distancePerRep
            
            // Effort level based on exercise type and category
            val effortLevel = when {
                swimmerCategory == ExerciseCategory.SPRINT && distancePerRep <= 50 -> listOf("Hard", "Max Effort").random()
                swimmerCategory == ExerciseCategory.SPRINT -> listOf("Moderate", "Hard").random()
                distancePerRep >= 400 -> listOf("Easy", "Moderate").random()
                else -> listOf("Moderate", "Hard").random()
            }
            
            // Stroke metrics (more variation)
            val avgStrokeLength = when (swimmerCategory) {
                ExerciseCategory.SPRINT -> randomFloat(1.8f, 2.5f) // Shorter, faster strokes
                ExerciseCategory.DISTANCE -> randomFloat(2.2f, 3.0f) // Longer, efficient strokes
            }
            val strokesPerMeter = 1.0f / avgStrokeLength
            val totalStrokes = (totalDistance * strokesPerMeter).toInt()
            
            // Average lap time (for standard 50m pool)
            val avgLapTime = when (swimmerCategory) {
                ExerciseCategory.SPRINT -> when {
                    distancePerRep <= 50 -> randomFloat(26f, 35f) // Fast laps
                    distancePerRep == 100 -> randomFloat(58f, 68f)
                    else -> randomFloat(120f, 140f)
                }
                ExerciseCategory.DISTANCE -> when {
                    distancePerRep <= 100 -> randomFloat(65f, 80f)
                    distancePerRep <= 200 -> randomFloat(135f, 155f)
                    else -> randomFloat(290f, 330f)
                }
            }
            
            // Stroke Index: speed (m/s) * stroke length (m)
            // Speed = distance / time
            val avgSpeed = distancePerRep / avgLapTime // m/s
            val strokeIndex = avgSpeed * avgStrokeLength
            
            // Heart rate data
            val restingHR = random.nextInt(20) + 60 // 60-80 BPM
            val heartRateBefore = restingHR + random.nextInt(30) // Warm-up elevated
            
            val maxHRIncrease = when (effortLevel) {
                "Easy" -> random.nextInt(30) + 40 // +40-70
                "Moderate" -> random.nextInt(30) + 60 // +60-90
                "Hard" -> random.nextInt(30) + 80 // +80-110
                "Max Effort" -> random.nextInt(30) + 100 // +100-130
                else -> random.nextInt(40) + 60
            }
            
            val maxHeartRate = (restingHR + maxHRIncrease).coerceIn(120, 195)
            val avgHeartRate = ((heartRateBefore + maxHeartRate) / 2.0f + random.nextInt(20) - 10).toInt()
            val heartRateAfter = (maxHeartRate - random.nextInt(30) - 20).coerceIn(100, 170)
            
            // Generate realistic stroke distribution
            val dominantStroke = random.nextInt(4)
            val strokes = generateStrokeDistribution(dominantStroke, swimmerCategory)
            
            sessions.add(
                MlResult(
                    sessionId = 0,
                    swimmerId = swimmerId,
                    exerciseId = exercise.id,
                    date = date,
                    timeStart = timeStart,
                    timeEnd = timeEnd,
                    exerciseName = exercise.name,
                    distance = distancePerRep,
                    sets = setsCompleted,
                    reps = repsPerSet,
                    effortLevel = effortLevel,
                    strokeCount = totalStrokes,
                    avgStrokeLength = avgStrokeLength,
                    strokeIndex = strokeIndex,
                    avgLapTime = avgLapTime,
                    totalDistance = totalDistance,
                    heartRateBefore = heartRateBefore,
                    heartRateAfter = heartRateAfter,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate,
                    backstroke = strokes[0],
                    breaststroke = strokes[1],
                    butterfly = strokes[2],
                    freestyle = strokes[3],
                    notes = generateSessionNotes(swimmerCategory)
                )
            )
        }
        
        return sessions.reversed() // Oldest first
    }
    
    fun generateDummyHeartRateData(sessionId: Int, durationMinutes: Int): List<HeartRateData> {
        val data = mutableListOf<HeartRateData>()
        val dataPoints = durationMinutes / 5 // One reading every 5 minutes
        
        var currentHR = random.nextInt(20) + 110 // Start 110-130
        
        for (i in 0 until dataPoints) {
            // Simulate variation
            currentHR += random.nextInt(21) - 10 // -10 to +10 change
            currentHR = currentHR.coerceIn(100, 180)
            
            data.add(
                HeartRateData(
                    id = 0,
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis() + (i * 5 * 60 * 1000L),
                    heartRate = currentHR
                )
            )
        }
        
        return data
    }
    
    fun generateDummyLapTimes(sessionId: Int, laps: Int = 10): List<LapTime> {
        val baseLapTime = randomFloat(28f, 35f) // Base time in seconds
        
        return (1..laps).map { lap ->
            // Add variation to each lap
            val variation = randomFloat(-2f, 3f)
            val lapTime = baseLapTime + variation
            
            LapTime(
                id = 0,
                sessionId = sessionId,
                lapNumber = lap,
                lapTime = lapTime,
                timestamp = System.currentTimeMillis() + (lap * 35000L) // ~35 sec per lap
            )
        }
    }
    
    // Helper functions
    
    private fun generateRandomBirthday(minAge: Int, maxAge: Int): String {
        val calendar = Calendar.getInstance()
        val age = random.nextInt(maxAge - minAge + 1) + minAge
        calendar.add(Calendar.YEAR, -age)
        calendar.set(Calendar.MONTH, random.nextInt(12))
        calendar.set(Calendar.DAY_OF_MONTH, random.nextInt(28) + 1)
        
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
    
    private fun randomFloat(min: Float, max: Float): Float {
        return min + random.nextFloat() * (max - min)
    }
    
    private fun generateStrokeDistribution(dominantStroke: Int, category: ExerciseCategory = ExerciseCategory.SPRINT): FloatArray {
        val distribution = FloatArray(4)
        val total = 100f
        
        // Dominant stroke gets different percentages based on category
        val dominantPercent = when (category) {
            ExerciseCategory.SPRINT -> randomFloat(50f, 80f) // Sprinters focus more on one stroke
            ExerciseCategory.DISTANCE -> randomFloat(40f, 65f) // Distance swimmers mix more
        }
        
        distribution[dominantStroke] = dominantPercent
        
        val remaining = total - distribution[dominantStroke]
        
        // Distribute remaining percentage among other strokes
        var allocated = 0f
        for (i in 0 until 4) {
            if (i != dominantStroke) {
                if (i == 3 || allocated + 5f > remaining) {
                    distribution[i] = remaining - allocated
                } else {
                    val amount = randomFloat(3f, (remaining - allocated) / 2)
                    distribution[i] = amount
                    allocated += amount
                }
            }
        }
        
        return distribution
    }
    
    private fun generateSessionNotes(category: ExerciseCategory = ExerciseCategory.SPRINT): String {
        val sprintNotes = listOf(
            "Excellent explosive power today",
            "Great starts and turns",
            "Need to work on maintaining speed",
            "Fantastic breakout technique",
            "Focus on reaction time next session",
            "Strong finish on all sprints",
            "Good power but tiring in later sets",
            "Personal best on several attempts",
            ""
        )
        
        val distanceNotes = listOf(
            "Maintained steady pace throughout",
            "Good endurance, felt strong",
            "Need to work on pacing strategy",
            "Excellent breathing rhythm",
            "Struggled with fatigue in final sets",
            "Great aerobic capacity today",
            "Smooth stroke mechanics all session",
            "Worked on negative splitting",
            ""
        )
        
        return when (category) {
            ExerciseCategory.SPRINT -> sprintNotes.random()
            ExerciseCategory.DISTANCE -> distanceNotes.random()
        }
    }
}

// Additional data classes for session metrics

data class HeartRateData(
    val id: Int = 0,
    val sessionId: Int,
    val timestamp: Long,
    val heartRate: Int
)

data class LapTime(
    val id: Int = 0,
    val sessionId: Int,
    val lapNumber: Int,
    val lapTime: Float, // in seconds
    val timestamp: Long
)
