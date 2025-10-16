package com.thesisapp.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "swimmers")
data class Swimmer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teamId: Int, // team membership
    val name: String,
    val birthday: String, // Store as "YYYY-MM-DD" format
    val height: Float, // in cm
    val weight: Float, // in kg
    val sex: String, // "Male" or "Female"
    val wingspan: Float, // in cm
    val code: String // per-swimmer unique code
) : Parcelable
