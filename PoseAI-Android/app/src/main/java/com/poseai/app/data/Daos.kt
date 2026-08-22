package com.poseai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ShootingRecordDao {
    @Insert
    suspend fun insert(record: ShootingRecordEntity)

    @Query("SELECT * FROM shooting_records ORDER BY createdAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ShootingRecordEntity>>

    @Query("SELECT COUNT(*) FROM shooting_records")
    fun observeCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("UPDATE shooting_records SET appliedFilterRawValue = :filter WHERE id = :id")
    suspend fun updateFilter(id: String, filter: String?)

    @Query("SELECT * FROM shooting_records WHERE id = :id")
    suspend fun byId(id: String): ShootingRecordEntity?
}

@Dao
interface CustomPlanDao {
    @Insert
    suspend fun insert(plan: CustomPlanEntity)

    @Query("SELECT * FROM custom_plans ORDER BY createdAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CustomPlanEntity>>

    @Query("DELETE FROM custom_plans WHERE id = :id")
    suspend fun delete(id: String)
}