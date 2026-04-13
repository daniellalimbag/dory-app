package com.thesisapp.presentation

import android.Manifest
import android.R
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material.Text
import com.thesisapp.communication.WearReceiver
import com.thesisapp.communication.WearSender
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    private val sendIntervalMs = 50L // send at most every 50 ms (20 Hz)

    var id = 0

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

        // instantiate communication
        sender = WearSender(this)
        receiver = WearReceiver(
            context = this,
            onStartRecording = { startRecording() },
            onStopRecording = { stopRecording() }
        )
        receiver.register()

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
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        heartRate?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        ppg?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
        ecg?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (!isRecording) return

        // Always update sensor values immediately when they arrive
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> accelValues = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> gyroValues = event.values.clone()
            Sensor.TYPE_HEART_RATE -> heartRateValue = event.values[0]
        }

        when (event.sensor.name) {
            "PPG Sensor" -> ppgValue = event.values[0]
            "ECG Sensor" -> ecgValue = event.values[0]
        }

        // Only send/save data at throttled rate
        val now = System.currentTimeMillis()
        if (now - lastSendTime < sendIntervalMs) return
        lastSendTime = now

        val currentData = SensorData(
            sessionId = id,
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
        super.onDestroy()
        stopRecording()
        receiver.unregister()
    }

    fun startRecording() {
        isRecording = true
        if (!sensorScope.coroutineContext.isActive) {
            sensorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        registerSensors()
        Log.d("Sensors", "Recording started. Sensors registered.")
    }

    fun stopRecording() {
        isRecording = false
        sensorManager.unregisterListener(this)
        sensorScope.cancel()
        Log.d("Sensors", "Recording stopped. Sensors unregistered.")
    }
}

@Composable
fun RecordingStatusScreen(isRecording: Boolean) {
    val backgroundColor = if (isRecording) Color.Red else Color.Black
    val statusText = if (isRecording) "RECORDING" else "STANDBY"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}