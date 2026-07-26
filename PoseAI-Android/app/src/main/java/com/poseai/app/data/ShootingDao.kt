package com.poseai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShootingDao {
    @Insert
    suspend fun insert(record: ShootingRecord): Long

    @Update
    suspend fun update(record: ShootingRecord)

    @Query("SELECT * FROM shooting_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ShootingRecord>>

    @Query("SELECT * FROM shooting_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsOnce(): List<ShootingRecord>

    @Query("SELECT * FROM shooting_records ORDER BY timestamp ASC")
    suspend fun getAllAscending(): List<ShootingRecord>

    @Query("SELECT * FROM shooting_records WHERE id = :id")
    suspend fun getById(id: Long): ShootingRecord?

    @Query("SELECT scene, COUNT(*) as count FROM shooting_records GROUP BY scene ORDER BY count DESC")
    suspend fun getSceneDistribution(): List<SceneCount>

    @Query("DELETE FROM shooting_records WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM shooting_records WHERE imagePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM shooting_records")
    suspend fun clearAll()
}
