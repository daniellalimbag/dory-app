package com.thesisapp.data

import androidx.room.TypeConverter
import com.thesisapp.data.non_dao.UserRole

class RoomConverters {
    @TypeConverter
    fun fromUserRole(role: UserRole): String = role.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)
}
