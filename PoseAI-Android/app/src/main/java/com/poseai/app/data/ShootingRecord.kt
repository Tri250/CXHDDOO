package com.poseai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "shooting_records")
@TypeConverters(Converters::class)
data class ShootingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),
    val scene: String = "UNKNOWN",
    val poseName: String = "",
    val score: Float = 0f,
    val isFavorite: Boolean = false,
    val note: String = "",
    val imagePath: String = ""
)

data class SceneCount(
    val scene: String,
    val count: Int
)
