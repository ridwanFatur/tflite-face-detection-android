package com.ridwanfatur.faceverification.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "face_items")
data class FaceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val created: String,
    val array: List<ByteArray>
) : Serializable