package com.thesisapp.presentation

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.MlResult
import com.thesisapp.data.SwimData
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

class SettingsImportActivity : AppCompatActivity() {

    private lateinit var btnSelectFile: Button
    private lateinit var btnImport: Button
    private lateinit var fileNameTextView: TextView

    private var selectedFileUri: Uri? = null
    private var preselectedSwimmerId: Int = -1

    companion object {
        const val EXTRA_SWIMMER_ID = "EXTRA_SWIMMER_ID"
    }

    // Hold parsed data until user assigns swimmer/exercise
    private var pendingSwimData: List<SwimData> = emptyList()
    private var pendingLinesProcessed: Int = 0
    private var pendingSkipped: Int = 0

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            fileNameTextView.text = getFileName(it)
            btnImport.isEnabled = true
        }
    }

    private val assignLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        val swimmerId = result.data?.getIntExtra(ImportAssignActivity.EXTRA_SWIMMER_ID, -1) ?: -1
        val exerciseId = result.data?.getIntExtra(ImportAssignActivity.EXTRA_EXERCISE_ID, -1) ?: -1

        if (swimmerId <= 0 || exerciseId <= 0 || pendingSwimData.isEmpty()) {
            Toast.makeText(this, "Invalid selection or no data to import", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        // Perform final insert with assignment
        val linesProcessed = pendingLinesProcessed
        val skipped = pendingSkipped
        val dataToInsert = pendingSwimData

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)

            // We need unique internal sessionIds that won't collide with existing MlResult rows.
            // Also, SwimData.sessionId must match MlResult.sessionId so HistorySessionActivity can load data.
            val existingMaxId = db.mlResultDao().getMaxSessionId() ?: 0

            // Group by original (external) sessionId from the imported file.
            val bySession = dataToInsert.groupBy { it.sessionId }

            // Build a mapping from external sessionId -> new internal sessionId.
            val idMap = mutableMapOf<Int, Int>()
            var nextId = existingMaxId + 1
            for (originalId in bySession.keys.sorted()) {
                idMap[originalId] = nextId
                nextId++
            }

            // Apply the mapping to all SwimData rows so they use the new internal IDs.
            val remappedSwimData = dataToInsert.map { sample ->
                val mappedId = idMap[sample.sessionId] ?: sample.sessionId
                sample.copy(sessionId = mappedId)
            }

            // Insert all swim samples with remapped sessionIds
            db.swimDataDao().insertAll(remappedSwimData)

            // Create MlResult per (new) sessionId with basic metadata
            val tz = java.util.TimeZone.getTimeZone("Asia/Manila")
            val formatterDate = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).apply { timeZone = tz }
            val formatterTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).apply { timeZone = tz }

            val byNewSession = remappedSwimData.groupBy { it.sessionId }
            for ((sid, samples) in byNewSession) {
                val timestamps = samples.map { it.timestamp }
                val firstMs = timestamps.minOrNull() ?: continue
                val lastMs = timestamps.maxOrNull() ?: firstMs

                val date = formatterDate.format(java.util.Date(firstMs))
                val timeStart = formatterTime.format(java.util.Date(firstMs))
                val timeEnd = formatterTime.format(java.util.Date(lastMs))

                val ml = MlResult(
                    sessionId = sid,
                    swimmerId = swimmerId,
                    exerciseId = exerciseId,
                    date = date,
                    timeStart = timeStart,
                    timeEnd = timeEnd,
                    backstroke = 0f,
                    breaststroke = 0f,
                    butterfly = 0f,
                    freestyle = 0f,
                    notes = "Imported session"
                )

                db.mlResultDao().insert(ml)
            }

            withContext(Dispatchers.Main) {
                val msg = if (skipped > 0) "$linesProcessed records imported. Skipped $skipped invalid rows." else "$linesProcessed records imported successfully."
                Toast.makeText(this@SettingsImportActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun importDataFromDb(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val swimDataList = mutableListOf<SwimData>()
            var linesProcessed = 0
            var skipped = 0

            val sessionRanges = mutableMapOf<Int, Pair<Long, Long>>()

            try {
                val tmp = File.createTempFile("import_", ".db", cacheDir)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }

                val dbFile = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                dbFile.use { importedDb ->
                    val table = when {
                        tableExists(importedDb, "sensor_data") -> "sensor_data"
                        tableExists(importedDb, "swim_data") -> "swim_data"
                        else -> null
                    }

                    if (table == null) throw IllegalArgumentException("No suitable sensor table found in DB")

                    val cursor = importedDb.rawQuery("SELECT * FROM $table", null)
                    cursor.use { c ->
                        val cols = c.columnNames.map { it.lowercase(Locale.getDefault()) }

                        fun findIndex(vararg names: String): Int? {
                            for (n in names) {
                                val i = cols.indexOf(n.lowercase(Locale.getDefault()))
                                if (i >= 0) return i
                            }
                            return null
                        }

                        val iSessionId = findIndex("sessionid", "session_id", "session", "sid")
                        val iTimestamp = findIndex("timestamp", "time", "unix_ts")
                        if (iTimestamp == null) {
                            throw IllegalArgumentException("Required column 'timestamp' not found")
                        }

                        val iAx = findIndex("accel_x")
                        val iAy = findIndex("accel_y")
                        val iAz = findIndex("accel_z")
                        val iGx = findIndex("gyro_x")
                        val iGy = findIndex("gyro_y")
                        val iGz = findIndex("gyro_z")
                        val iHr = findIndex("heart_rate")
                        val iPpg = findIndex("ppg")
                        val iEcg = findIndex("ecg")

                        while (c.moveToNext()) {
                            try {
                                val sid = iSessionId?.let { c.getLong(it).toInt() } ?: 1
                                var ts = c.getLong(iTimestamp)
                                if (ts < 100_000_000_000L) ts *= 1000

                                val ax = iAx?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val ay = iAy?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val az = iAz?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val gx = iGx?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val gy = iGy?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val gz = iGz?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val hr = iHr?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val ppg = iPpg?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }
                                val ecg = iEcg?.let { if (!c.isNull(it)) c.getDouble(it).toFloat() else null }

                                swimDataList.add(
                                    SwimData(
                                        sessionId = sid,
                                        timestamp = ts,
                                        accel_x = ax,
                                        accel_y = ay,
                                        accel_z = az,
                                        gyro_x = gx,
                                        gyro_y = gy,
                                        gyro_z = gz,
                                        heart_rate = hr,
                                        ppg = ppg,
                                        ecg = ecg
                                    )
                                )
                                linesProcessed++

                                val prev = sessionRanges[sid]
                                if (prev == null) sessionRanges[sid] = ts to ts else sessionRanges[sid] = kotlin.math.min(prev.first, ts) to kotlin.math.max(prev.second, ts)
                            } catch (_: Exception) {
                                skipped++
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "DB import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            if (swimDataList.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    pendingSwimData = swimDataList
                    pendingLinesProcessed = linesProcessed
                    pendingSkipped = skipped

                    val intent = android.content.Intent(this@SettingsImportActivity, ImportAssignActivity::class.java).apply {
                        if (preselectedSwimmerId > 0) {
                            putExtra(ImportAssignActivity.EXTRA_SWIMMER_ID, preselectedSwimmerId)
                        }
                    }
                    assignLauncher.launch(intent)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "No valid data found in the database.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        val c = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND lower(name)=?",
            arrayOf(table.lowercase(Locale.getDefault()))
        )
        c.use { return it.moveToFirst() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_import)

        preselectedSwimmerId = intent.getIntExtra(EXTRA_SWIMMER_ID, -1)

        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnImport = findViewById(R.id.btnImport)
        fileNameTextView = findViewById(R.id.fileNameTextView)
        val btnReturn = findViewById<Button>(R.id.btnReturn)

        btnSelectFile.setOnClickListener {
            it.animateClick()
            filePickerLauncher.launch("*/*")
        }

        btnImport.setOnClickListener {
            it.animateClick()
            selectedFileUri?.let { uri ->
                val name = getFileName(uri).lowercase(Locale.getDefault())
                if (name.endsWith(".db")) {
                    importDataFromDb(uri)
                } else {
                    importDataFromCSV(uri)
                }
            } ?: Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun importDataFromCSV(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val swimDataList = mutableListOf<SwimData>()
            var linesProcessed = 0
            var errorOccurred = false
            var skipped = 0

            try {
                contentResolver.openInputStream(uri)?.use { raw ->
                    val bis = java.io.BufferedInputStream(raw)
                    bis.mark(4)
                    val bom = ByteArray(4)
                    val read = bis.read(bom, 0, 4)
                    bis.reset()
                    val charset = when {
                        read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                        read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                        read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte() -> Charsets.UTF_8
                        else -> Charsets.UTF_8
                    }
                    BufferedReader(InputStreamReader(bis, charset)).use { reader ->
                        var firstNonEmpty: String? = null
                        val allLines = mutableListOf<String>()
                        var l: String?
                        while (reader.readLine().also { l = it } != null) {
                            val s = l!!.trim()
                            if (s.isNotEmpty()) {
                                if (firstNonEmpty == null) firstNonEmpty = s
                                allLines.add(s)
                            }
                        }

                        if (firstNonEmpty != null) {
                            val delimiter = detectDelimiter(firstNonEmpty!!)
                            var headerTokens = splitFlexible(firstNonEmpty!!, delimiter).map { it.trim() }
                            if (headerTokens.isNotEmpty()) {
                                headerTokens = headerTokens.toMutableList().also { list ->
                                    list[0] = list[0].removePrefix("\uFEFF")
                                }
                            }
                            var startIndex = 0
                            val headerMap = mutableMapOf<String, Int>()

                            val looksLikeHeader = headerTokens.any { it.any { ch -> ch.isLetter() } }
                            if (looksLikeHeader) {
                                headerTokens.forEachIndexed { idx, name -> headerMap[name.lowercase(Locale.getDefault())] = idx }
                                startIndex = 1
                            }

                            for (i in startIndex until allLines.size) {
                                var tokens = splitFlexible(allLines[i], delimiter).map { it.trim() }
                                if (tokens.isNotEmpty()) {
                                    val first = tokens[0]
                                    if (first.startsWith("\uFEFF")) {
                                        val mut = tokens.toMutableList()
                                        mut[0] = first.removePrefix("\uFEFF")
                                        tokens = mut
                                    }
                                }
                                val parsed = parseRow(tokens, headerMap)
                                if (parsed != null) {
                                    swimDataList.add(parsed)
                                    linesProcessed++
                                } else {
                                    skipped++
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                errorOccurred = true
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            if (errorOccurred) return@launch

            if (swimDataList.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    pendingSwimData = swimDataList
                    pendingLinesProcessed = linesProcessed
                    pendingSkipped = skipped

                    val intent = android.content.Intent(this@SettingsImportActivity, ImportAssignActivity::class.java)
                    assignLauncher.launch(intent)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsImportActivity, "No valid data found in the file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "File"
    }

    private fun cleanNumber(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1)
        }
        s = s.replace("\u00A0", "")
        s = s.replace(",", "")
        return s
    }

    private fun parseLongFlexible(raw: String?): Long? {
        val s = cleanNumber(raw) ?: return null
        return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong()
    }

    private fun parseIntFlexible(raw: String?): Int? {
        val s = cleanNumber(raw) ?: return null
        return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt()
    }

    private fun parseFloatFlexible(raw: String?): Float? {
        val s = cleanNumber(raw) ?: return null
        return s.toFloatOrNull() ?: s.toDoubleOrNull()?.toFloat()
    }

    private fun detectDelimiter(sample: String): Char {
        val candidates = charArrayOf(',', ';', '\t')
        var best = ','
        var bestCount = -1
        for (c in candidates) {
            val count = sample.count { it == c }
            if (count > bestCount) {
                best = c
                bestCount = count
            }
        }
        return best
    }

    private fun splitFlexible(line: String, preferred: Char): List<String> {
        val first = line.split(preferred)
        if (first.size > 1) return first
        for (c in charArrayOf(',', ';', '\t')) {
            if (c == preferred) continue
            val alt = line.split(c)
            if (alt.size > 1) return alt
        }
        return line.trim().split(Regex("\\s+"))
    }

    private fun parseRow(tokens: List<String>, headerMap: Map<String, Int>): SwimData? {
        if (tokens.isEmpty()) return null

        fun idx(vararg names: String): Int? {
            for (n in names) {
                val k = n.lowercase(Locale.getDefault())
                if (headerMap.containsKey(k)) return headerMap[k]
            }
            return null
        }

        val hasHeader = headerMap.isNotEmpty()

        var sessionIdIdx = idx("sessionId", "session_id", "session", "sid") ?: if (!hasHeader) 0 else null
        var timestampIdx = idx("unix_ts", "timestamp", "time", "epoch", "epoch_ms", "ts") ?: if (!hasHeader) 1 else null

        var leadOffset = if (!hasHeader && tokens.size == 12) 1 else 0

        if (sessionIdIdx == null || timestampIdx == null) {
            if (tokens.size >= 3 && isNumeric(tokens[0]) && isNumeric(tokens[1]) && isNumeric(tokens[2])) {
                leadOffset = 1
                sessionIdIdx = 0
                timestampIdx = 1
            } else if (tokens.size >= 2 && isNumeric(tokens[0]) && isNumeric(tokens[1])) {
                leadOffset = 0
                sessionIdIdx = 0
                timestampIdx = 1
            }
        }

        if (sessionIdIdx == null || timestampIdx == null) return null

        fun get(i: Int?): String? = i?.let { j -> tokens.getOrNull(j + leadOffset) }

        val sessionId = parseIntFlexible(get(sessionIdIdx)) ?: return null

        var timestamp = parseLongFlexible(get(timestampIdx)) ?: return null
        if (timestamp < 100_000_000_000L) {
            timestamp *= 1000
        }

        val minYear2000 = 946_684_800_000L
        if (timestamp < minYear2000) {
            val bigEpoch = tokens.firstOrNull { t ->
                val n = parseLongFlexible(t)
                n != null && n >= 1_000_000_000_000L
            }
            if (bigEpoch != null) {
                timestamp = parseLongFlexible(bigEpoch) ?: timestamp
            }
        }

        val accelX = parseFloatFlexible((idx("accel_x")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(2 + leadOffset) else null))
        val accelY = parseFloatFlexible((idx("accel_y")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(3 + leadOffset) else null))
        val accelZ = parseFloatFlexible((idx("accel_z")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(4 + leadOffset) else null))
        val gyroX = parseFloatFlexible((idx("gyro_x")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(5 + leadOffset) else null))
        val gyroY = parseFloatFlexible((idx("gyro_y")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(6 + leadOffset) else null))
        val gyroZ = parseFloatFlexible((idx("gyro_z")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(7 + leadOffset) else null))
        val heartRate = parseFloatFlexible((idx("heart_rate")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(8 + leadOffset) else null))
        val ppg = parseFloatFlexible((idx("ppg")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(9 + leadOffset) else null))
        val ecg = parseFloatFlexible((idx("ecg")?.let { get(it) } ?: if (!hasHeader) tokens.getOrNull(10 + leadOffset) else null))

        return SwimData(
            sessionId = sessionId,
            timestamp = timestamp,
            accel_x = accelX,
            accel_y = accelY,
            accel_z = accelZ,
            gyro_x = gyroX,
            gyro_y = gyroY,
            gyro_z = gyroZ,
            heart_rate = heartRate,
            ppg = ppg,
            ecg = ecg
        )
    }

    private fun isNumeric(s: String?): Boolean {
        if (s == null) return false
        val c = cleanNumber(s) ?: return false
        return c.toLongOrNull() != null || c.toDoubleOrNull() != null
    }
}
