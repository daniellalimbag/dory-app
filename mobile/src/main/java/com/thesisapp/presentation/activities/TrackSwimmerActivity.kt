package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.communication.PhoneReceiver
import com.thesisapp.communication.PhoneSender
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.thesisapp.presentation.HandModelView
import com.thesisapp.presentation.StrokeClassifier
import com.thesisapp.utils.StrokeMetrics
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

class TrackSwimmerActivity : AppCompatActivity() {
    private lateinit var receiver: PhoneReceiver
    private lateinit var sender: PhoneSender
    private lateinit var classifier: StrokeClassifier
    private lateinit var db: AppDatabase
    private val liveSensorData = MutableStateFlow<SwimData?>(null)

    private var swimmerId: Int = -1

    init {
        try {
            System.loadLibrary("filament-jni")
            System.loadLibrary("gltfio-jni")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        receiver = PhoneReceiver(this, liveSensorData)
        sender = PhoneSender(this)
        classifier = StrokeClassifier(this)
        db = AppDatabase.getInstance(applicationContext)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.track_swimmer)

        swimmerId = intent.getIntExtra("SWIMMER_ID", -1)
        if (swimmerId <= 0) {
            Toast.makeText(this, "No swimmer selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        receiver.register()

        lifecycleScope.launch(Dispatchers.IO) {
            runOnUiThread {
                setContent {
                    RealtimeSensorScreen(
                        sensorDataFlow = liveSensorData,
                        phoneSender = sender,
                        predictedLabel = receiver.predictedLabel,
                        db = db,
                        swimmerId = swimmerId
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver.unregister()
    }
}

fun Float?.format(decimals: Int = 2): String {
    return if (this == null) "--" else String.format(Locale.US, "% .${decimals}f", this)
}

@Composable
fun RealtimeSensorScreen(
    sensorDataFlow: Flow<SwimData?>,
    phoneSender: PhoneSender,
    predictedLabel: State<String>,
    db: AppDatabase,
    swimmerId: Int
) {
    val sensorData by sensorDataFlow.collectAsState(initial = null)
    var isRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val strokeResults = remember { mutableStateListOf<String>() }

    var startTime by remember { mutableLongStateOf(0L) }
    var currentSessionId by remember { mutableIntStateOf(0) }

    LaunchedEffect(sensorData) {
        sensorData?.let { data ->
            if (!isRecording) return@LaunchedEffect
            if (currentSessionId <= 0) return@LaunchedEffect
            if (startTime <= 0L) return@LaunchedEffect
            if (data.timestamp < startTime) return@LaunchedEffect

            if (predictedLabel.value.isNotBlank() && predictedLabel.value != "Waiting...") {
                strokeResults.add(predictedLabel.value)
            }

            val swim = data.copy(sessionId = currentSessionId)
            (context as? AppCompatActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                db.swimDataDao().insert(swim)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ReturnButton()

            Spacer(modifier = Modifier.height(12.dp))

            Text("LATEST SENSOR DATA", color = Color.White)

            sensorData?.let { data ->
                sensorData?.let { data ->
                    HandViewerComposable(
                        gyroX = data.gyro_x ?: 0f,
                        gyroY = data.gyro_y ?: 0f,
                        gyroZ = data.gyro_z ?: 0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }

                StrokePrediction(predictedLabel)

                Text(
                    "Accel: x=${data.accel_x.format()}, y=${data.accel_y.format()}, z=${data.accel_z.format()}",
                    color = Color.White
                )
                Text(
                    "Gyro: x=${data.gyro_x.format()}, y=${data.gyro_y.format()}, z=${data.gyro_z.format()}",
                    color = Color.White
                )
                Text(
                    "HR: ${data.heart_rate.format()}, PPG: ${data.ppg.format()}, ECG: ${data.ecg.format()}",
                    color = Color.White
                )
            } ?: Text("Start recording to see swimmer data.", color = Color.White)

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = {
                val activity = context as? AppCompatActivity
                if (activity == null) return@Button

                if (!isRecording) {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        val maxSessionId = db.mlResultDao().getMaxSessionId() ?: 0
                        val newId = maxSessionId + 1
                        withContext(Dispatchers.Main) {
                            phoneSender.sendCommand(
                                start = true,
                                onSuccess = {
                                    phoneSender.sendId(
                                        id = newId,
                                        onSuccess = {
                                            currentSessionId = newId
                                            strokeResults.clear()
                                            startTime = System.currentTimeMillis()
                                            isRecording = true
                                        },
                                        onFailure = {
                                            Toast.makeText(
                                                context,
                                                "Watch not listening. Please open the app on your watch.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                },
                                onFailure = {
                                    Toast.makeText(
                                        context,
                                        "Watch not listening. Please open the app on your watch.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                } else {
                    phoneSender.sendCommand(
                        start = false,
                        onSuccess = {
                            val sessionIdToSave = currentSessionId
                            val startTimeToSave = startTime
                            isRecording = false

                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                val swimDataList = db.swimDataDao().getSwimDataForSession(sessionIdToSave)
                                if (swimDataList.isEmpty()) return@launch

                                val formatterDate = SimpleDateFormat(
                                    "MMMM dd, yyyy",
                                    Locale.getDefault()
                                )
                                val formatterTime =
                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault())

                                val firstTime = startTimeToSave
                                val lastTs = swimDataList.last().timestamp

                                val timeStart = formatterTime.format(firstTime)
                                val timeEnd = formatterTime.format(Date(lastTs))
                                val date = formatterDate.format(firstTime)

                                val percentages = calculateStrokePercentages(strokeResults)

                                val lapMetrics = StrokeMetrics.computeLapMetrics(swimDataList)

                                val totalStrokeCount: Int
                                val avgStrokeLength: Float
                                val strokeIndex: Float
                                val avgLapTime: Float
                                val totalDistanceMeters: Int

                                if (lapMetrics.isNotEmpty()) {
                                    val sessionAvgs = StrokeMetrics.computeSessionAverages(lapMetrics)
                                    totalStrokeCount = lapMetrics.sumOf { it.strokeCount }
                                    avgStrokeLength = sessionAvgs.avgStrokeLengthMeters.toFloat()
                                    strokeIndex = sessionAvgs.avgStrokeIndex.toFloat()
                                    avgLapTime = sessionAvgs.avgLapTimeSeconds.toFloat()
                                    totalDistanceMeters = (50.0 * lapMetrics.size).toInt()
                                } else {
                                    totalStrokeCount = StrokeMetrics.computeStrokeCount(swimDataList)
                                    val totalTimeSeconds = ((lastTs - firstTime).coerceAtLeast(1L) / 1000.0)
                                    val poolLengthMeters = 50.0
                                    val velocity = poolLengthMeters / totalTimeSeconds
                                    val strokeRatePerSecond = if (totalTimeSeconds > 0.0) totalStrokeCount / totalTimeSeconds else 0.0
                                    val strokeLengthFallback = if (strokeRatePerSecond > 0.0) velocity / strokeRatePerSecond else 0.0
                                    avgStrokeLength = strokeLengthFallback.toFloat()
                                    strokeIndex = (velocity * strokeLengthFallback).toFloat()
                                    avgLapTime = totalTimeSeconds.toFloat()
                                    totalDistanceMeters = poolLengthMeters.toInt()
                                }

                                val hrValues = swimDataList.mapNotNull { it.heart_rate?.roundToInt() }
                                val hrBefore = hrValues.firstOrNull()
                                val hrAfter = hrValues.lastOrNull()
                                val avgHr = if (hrValues.isNotEmpty()) (hrValues.sum().toDouble() / hrValues.size).roundToInt() else null
                                val maxHr = hrValues.maxOrNull()

                                val mlResult = MlResult(
                                    sessionId = sessionIdToSave,
                                    swimmerId = swimmerId,
                                    date = date,
                                    timeStart = timeStart,
                                    timeEnd = timeEnd,
                                    strokeCount = totalStrokeCount,
                                    avgStrokeLength = avgStrokeLength,
                                    strokeIndex = strokeIndex,
                                    avgLapTime = avgLapTime,
                                    totalDistance = totalDistanceMeters,
                                    heartRateBefore = hrBefore,
                                    heartRateAfter = hrAfter,
                                    avgHeartRate = avgHr,
                                    maxHeartRate = maxHr,
                                    backstroke = percentages["backstroke"] ?: 0f,
                                    breaststroke = percentages["breaststroke"] ?: 0f,
                                    butterfly = percentages["butterfly"] ?: 0f,
                                    freestyle = percentages["freestyle"] ?: 0f,
                                    notes = ""
                                )

                                db.mlResultDao().insert(mlResult)

                                withContext(Dispatchers.Main) {
                                    val intent = Intent(context, CategorizeSessionActivity::class.java)
                                    intent.putExtra("sessionId", sessionIdToSave)
                                    intent.putExtra("SWIMMER_ID", swimmerId)
                                    context.startActivity(intent)
                                    activity.finish()
                                }
                            }
                        },
                        onFailure = {
                            Toast.makeText(
                                context,
                                "Watch not listening. Please open the app on your watch.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }) {
                Text(if (isRecording) "Stop Recording" else "Start Recording")
            }
        }
    }
}

fun calculateStrokePercentages(strokeResults: List<String>): Map<String, Float> {
    val total = strokeResults.size.toFloat().takeIf { it > 0 } ?: return emptyMap()

    val counts = strokeResults.groupingBy { it.lowercase() }.eachCount()

    return mapOf(
        "freestyle" to (counts["freestyle"] ?: 0) / total * 100,
        "backstroke" to (counts["backstroke"] ?: 0) / total * 100,
        "breaststroke" to (counts["breaststroke"] ?: 0) / total * 100,
        "butterfly" to (counts["butterfly"] ?: 0) / total * 100
    )
}

@Composable
fun HandViewerComposable(
    gyroX: Float,
    gyroY: Float,
    gyroZ: Float,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context -> HandModelView(context) },
        modifier = modifier,
        update = { view -> view.setRotation(gyroX, gyroY, gyroZ) }
    )
}

@Composable
fun StrokePrediction(predictedLabel: State<String>) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Detected Stroke: ",
                fontSize = 16.sp,
                color = Color.White,
            )
            Text(
                text = predictedLabel.value,
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReturnButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Button(
        onClick = {
            (context as? AppCompatActivity)?.finish()
        },
        modifier = modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
    ) {
        Text("Return")
    }
}