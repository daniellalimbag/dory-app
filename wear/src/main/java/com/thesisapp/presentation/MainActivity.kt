package com.thesisapp.presentation

import android.Manifest
import android.R
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import com.thesisapp.communication.WearReceiver
import com.thesisapp.communication.WearSender
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.PendingSessionStore
import com.thesisapp.data.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {
    // initialize sensors
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var heartRate: Sensor? = null
    private var ppg: Sensor? = null
    private var ecg: Sensor? = null

    // initialize sensor values
    private var accelValues = FloatArray(3) { 0f }
    private var gyroValues = FloatArray(3) { 0f }
    private var heartRateValue = 0f
    private var ppgValue = 0f
    private var ecgValue = 0f

    // initialize communication
    private lateinit var sender: WearSender
    private lateinit var receiver: WearReceiver

    // initialize database
    private lateinit var database: AppDatabase
    private lateinit var pendingSessionStore: PendingSessionStore
    private val syncMutex = Mutex()
    private var periodicSyncJob: Job? = null

    // allows live changes
    var isRecording by mutableStateOf(false)
        private set

    // launch all coroutines within this scope
    private var sensorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // permissions needed to run app
    private val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
        Manifest.permission.WAKE_LOCK
    )

    private var lastSendTime = 0L
    private val sendIntervalMs = 300L // send at most every 300 ms
    private val syncRetryIntervalMs = 30_000L

    private var currentSessionId: Int? = null
    private var localSessionCounter = 1
    private var buttonPressCount = 0
    private var lastButtonPressTime = 0L

    private val buttonPressWindowMs = 2_000L
    private val requiredButtonPressCount = 4

    private var rotaryAccumulatedScroll = 0f
    private var lastRotaryEventTime = 0L
    private val rotaryTriggerThreshold = 10f
    private val rotaryWindowMs = 600L

    // main function
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_DeviceDefault)

        // keeps screen from idling while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // check permissions
        checkAndRequestPermissions()

        // instantiate db
        database = AppDatabase.getInstance(this)
        pendingSessionStore = PendingSessionStore(this)

        // instantiate communication
        sender = WearSender(this)
        receiver = WearReceiver(
            context = this,
            onStartRecording = { startRecording() },
            onStopRecording = { stopRecording() },
            onSessionIdReceived = { sessionId ->
                currentSessionId = sessionId
                Log.d("WearReceiver", "Session id synced from phone: $sessionId")
            },
            isRecordingProvider = { isRecording },
            hasPendingUploads = { pendingSessionStore.hasPendingSessions() },
            onConnectionActive = { syncPendingSessions() }
        )
        receiver.register()
        syncPendingSessions()
        periodicSyncJob = lifecycleScope.launch {
            while (isActive) {
                delay(syncRetryIntervalMs)
                syncPendingSessions()
            }
        }

        // instantiate sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        Log.i("Sensors", "Sensor Manager instantiated.")
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        Log.i("Sensors", "Accelerometer sensor instantiated.")
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        Log.i("Sensors", "Gyroscope sensor instantiated.")
        heartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        Log.i("Sensors", "Heart Rate sensor instantiated.")
        // Get PPG and ECG sensors from list
        for (sensor in sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            if (sensor.name.equals("PPG Sensor", ignoreCase = true)) {
                ppg = sensor
                Log.i("Sensors", "PPG sensor instantiated.")
            } else if (sensor.name.equals("ECG Sensor", ignoreCase = true)) {
                ecg = sensor
                Log.i("Sensors", "ECG sensor instantiated.")
            }
        }

        setContent {
            RecordingStatusScreen(isRecording = isRecording)
        }
    }

    // get permission from user to run app
    private fun checkAndRequestPermissions() {
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    private fun registerSensors() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 100000) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 100000) }
        heartRate?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 100000) }
        ppg?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 100000) }
        ecg?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 100000) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (!isRecording) return
        val now = System.currentTimeMillis()
        if (now - lastSendTime < sendIntervalMs) return // too soon, skip sending
        lastSendTime = now

        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> accelValues = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> gyroValues = event.values.clone()
            Sensor.TYPE_HEART_RATE -> heartRateValue = event.values[0]
        }

        when (event.sensor.name) {
            "PPG Sensor" -> ppgValue = event.values[0]
            "ECG Sensor" -> ecgValue = event.values[0]
        }

        val sessionId = currentSessionId ?: localSessionCounter
        val currentData = SensorData(
            sessionId = sessionId,
            accel_x = accelValues[0],
            accel_y = accelValues[1],
            accel_z = accelValues[2],
            gyro_x = gyroValues[0],
            gyro_y = gyroValues[1],
            gyro_z = gyroValues[2],
            heart_rate = heartRateValue,
            ppg = ppgValue,
            ecg = ecgValue
        )

        sensorScope.launch {
            database.sensorDataDao().insertSensorData(currentData)
            Log.d("SensorService", "Saved combined data: $currentData")
            sender.sendSensorData(currentData)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        periodicSyncJob?.cancel()
        stopRecording()
        receiver.unregister()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        syncPendingSessions()
    }

    fun startRecording() {
        if (isRecording) {
            return
        }
        if (pendingSessionStore.hasPendingSessions()) {
            Toast.makeText(this, "Sync in progress. Stop pending session first.", Toast.LENGTH_SHORT).show()
            syncPendingSessions()
            return
        }
        isRecording = true
        if (!sensorScope.coroutineContext.isActive) {
            sensorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        val sessionId = currentSessionId ?: localSessionCounter++
        currentSessionId = sessionId
        if (sessionId >= localSessionCounter) {
            localSessionCounter = sessionId + 1
        }
        if (!pendingSessionStore.containsSession(sessionId)) {
            pendingSessionStore.addSession(sessionId)
        }
        buttonPressCount = 0
        lastButtonPressTime = 0L
        rotaryAccumulatedScroll = 0f
        lastRotaryEventTime = 0L
        lastSendTime = 0L
        registerSensors()
        Log.d("Sensors", "Recording started. Sensors registered.")
    }

    fun stopRecording() {
        val sessionToSync = currentSessionId
        if (!isRecording && sessionToSync == null) {
            return
        }
        isRecording = false
        sensorManager.unregisterListener(this)
        sensorScope.cancel()
        buttonPressCount = 0
        lastButtonPressTime = 0L
        rotaryAccumulatedScroll = 0f
        lastRotaryEventTime = 0L
        Log.d("Sensors", "Recording stopped. Sensors unregistered.")
        currentSessionId = null
        sessionToSync?.let { syncPendingSessions(it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_STEM_PRIMARY || keyCode == KeyEvent.KEYCODE_STEM_1) && event?.repeatCount == 0) {
            handleSideButtonPress()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleSideButtonPress() {
        if (!isRecording) {
            buttonPressCount = 0
            lastButtonPressTime = 0L
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastButtonPressTime > buttonPressWindowMs) {
            buttonPressCount = 0
        }
        buttonPressCount += 1
        lastButtonPressTime = now

        if (buttonPressCount >= requiredButtonPressCount) {
            Toast.makeText(this, "Stopping recording", Toast.LENGTH_SHORT).show()
            stopRecording()
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onGenericMotionEvent(null)
        }

        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            if (!isRecording) {
                rotaryAccumulatedScroll = 0f
                lastRotaryEventTime = 0L
                return super.onGenericMotionEvent(event)
            }

            val now = System.currentTimeMillis()
            if (now - lastRotaryEventTime > rotaryWindowMs) {
                rotaryAccumulatedScroll = 0f
            }

            val scrollDelta = abs(event.getAxisValue(MotionEvent.AXIS_SCROLL))
            rotaryAccumulatedScroll += scrollDelta
            lastRotaryEventTime = now

            if (rotaryAccumulatedScroll >= rotaryTriggerThreshold) {
                // Provide immediate feedback so the user feels the stop was registered.
                window.decorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                Toast.makeText(this, "Stopping recording", Toast.LENGTH_SHORT).show()
                stopRecording()
            }

            return true
        }

        return super.onGenericMotionEvent(event)
    }

    private fun syncPendingSessions(sessionId: Int? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                val pendingSessions = sessionId?.let { setOf(it) } ?: pendingSessionStore.getPendingSessions()
                if (pendingSessions.isEmpty()) {
                    return@withLock
                }

                for (id in pendingSessions.sorted()) {
                    if (isRecording && currentSessionId == id) {
                        Log.d("WearSync", "Skipping active session $id during sync")
                        continue
                    }

                    val sessionData = database.sensorDataDao().getSensorDataForSession(id)
                    if (sessionData.isEmpty()) {
                        pendingSessionStore.removeSession(id)
                        Log.d("WearSync", "No data found for session $id; cleared pending flag")
                        continue
                    }

                    val success = runCatching {
                        sender.sendSessionData(id, sessionData)
                    }.onFailure { error ->
                        Log.e("WearSync", "Failed to send session $id", error)
                    }.getOrElse { false }

                    if (success) {
                        database.sensorDataDao().deleteSensorDataForSession(id)
                        pendingSessionStore.removeSession(id)
                        Log.d("WearSync", "Session $id synced and removed from watch storage")
                    } else {
                        Log.w("WearSync", "Session $id sync pending; will retry when connected")
                        break
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingStatusScreen(isRecording: Boolean) {
    val statusText = if (isRecording) "RECORDING" else "STANDBY"
    val dotColor = if (isRecording) Color.Red else Color.DarkGray

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}