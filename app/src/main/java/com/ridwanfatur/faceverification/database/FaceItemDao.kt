package com.ridwanfatur.faceverification.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import com.ridwanfatur.faceverification.models.FaceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceItemDao {
    @Insert
    suspend fun insert(faceItem: FaceItem)

    @Delete
    suspend fun delete(faceItem: FaceItem)

    @Query("SELECT * FROM face_items")
    suspend fun getAllFaceItems(): List<FaceItem>

    @Query("SELECT * FROM face_items")
    fun getAllFaceItemsFlow(): Flow<List<FaceItem>>
}