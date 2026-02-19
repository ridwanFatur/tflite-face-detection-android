package com.ridwanfatur.faceverification.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ridwanfatur.faceverification.models.FaceItem
import android.util.Base64
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromList(list: List<ByteArray>): String {
        return list.joinToString(";") { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    @TypeConverter
    fun toList(data: String): List<ByteArray> {
        if (data.isEmpty()) return emptyList()
        return data.split(";").map { Base64.decode(it, Base64.DEFAULT) }
    }
}

@Database(entities = [FaceItem::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun faceItemDao(): FaceItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "face_database"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}