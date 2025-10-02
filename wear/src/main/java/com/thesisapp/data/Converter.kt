package com.thesisapp.data

import androidx.room.TypeConverter
import java.text.SimpleDateFormat
import java.util.*

class Converters {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) // Added milliseconds

    @TypeConverter
    fun fromTimestamp(value: Long?): String? {
        return value?.let { dateFormat.format(Date(it)) }
    }

    @TypeConverter
    fun toTimestamp(value: String?): Long? {
        return value?.let { dateFormat.parse(it)?.time }
    }
}