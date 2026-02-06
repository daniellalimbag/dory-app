package com.thesisapp.data

import androidx.room.TypeConverter
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.data.non_dao.StrokeType
import com.thesisapp.data.non_dao.UserRole

class RoomConverters {
    @TypeConverter
    fun fromUserRole(role: UserRole): String = role.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromExerciseCategory(category: ExerciseCategory): String = category.name

    @TypeConverter
    fun toExerciseCategory(value: String): ExerciseCategory = ExerciseCategory.valueOf(value)

    @TypeConverter
    fun fromStrokeType(strokeType: StrokeType): String = strokeType.name

    @TypeConverter
    fun toStrokeType(value: String): StrokeType = StrokeType.valueOf(value)
}
