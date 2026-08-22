package com.poseai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShootingRecordDao {

    // MARK: - 写入

    @Insert
    suspend fun insert(record: ShootingRecordEntity)

    @Insert
    suspend fun insertAll(records: List<ShootingRecordEntity>)

    // MARK: - 流式读取（供 UI 订阅）

    @Query("SELECT * FROM shooting_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ShootingRecordEntity>>

    @Query("SELECT COUNT(*) FROM shooting_records")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM shooting_records WHERE sceneRawValue = :scene ORDER BY createdAt DESC")
    fun observeByScene(scene: String): Flow<List<ShootingRecordEntity>>

    @Query("SELECT * FROM shooting_records WHERE planId = :planId ORDER BY createdAt DESC")
    fun observeByPlan(planId: String): Flow<List<ShootingRecordEntity>>

    @Query("SELECT * FROM shooting_records WHERE isLowLight = 1 ORDER BY createdAt DESC")
    fun observeLowLight(): Flow<List<ShootingRecordEntity>>

    @Query("SELECT * FROM shooting_records WHERE cityName IS NOT NULL ORDER BY createdAt DESC")
    fun observeWithLocation(): Flow<List<ShootingRecordEntity>>

    // MARK: - 一次性读取（同步用）

    @Query("SELECT * FROM shooting_records WHERE id = :id")
    suspend fun byId(id: String): ShootingRecordEntity?

    @Query("SELECT * FROM shooting_records ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ShootingRecordEntity>

    @Query("SELECT * FROM shooting_records WHERE matchScore >= :minScore ORDER BY matchScore DESC LIMIT :limit")
    suspend fun topByScore(minScore: Int, limit: Int): List<ShootingRecordEntity>

    /** 最近 N 天内的记录 */
    @Query("SELECT * FROM shooting_records WHERE createdAt >= :sinceTs ORDER BY createdAt DESC")
    suspend fun since(sinceTs: Long): List<ShootingRecordEntity>

    /** 按城市分组聚合查询（用于"在哪拍过"统计） */
    @Query("""
        SELECT cityName AS sceneRawValue, COUNT(*) AS count
        FROM shooting_records
        WHERE cityName IS NOT NULL AND cityName != ''
        GROUP BY cityName
        ORDER BY count DESC
    """)
    suspend fun statsByCity(): List<SceneStat>

    /** 按场景分组聚合查询 */
    @Query("""
        SELECT sceneRawValue, COUNT(*) AS count
        FROM shooting_records
        GROUP BY sceneRawValue
        ORDER BY count DESC
    """)
    suspend fun statsByScene(): List<SceneStat>

    /** 总体分数统计 */
    @Query("""
        SELECT AVG(matchScore) AS avgScore, MAX(matchScore) AS maxScore, COUNT(*) AS count
        FROM shooting_records
    """)
    suspend fun scoreSummary(): ScoreStat?

    /** 指定时间窗口内的分数统计 */
    @Query("""
        SELECT AVG(matchScore) AS avgScore, MAX(matchScore) AS maxScore, COUNT(*) AS count
        FROM shooting_records
        WHERE createdAt >= :sinceTs
    """)
    suspend fun scoreSummarySince(sinceTs: Long): ScoreStat?

    // MARK: - 更新

    @Query("UPDATE shooting_records SET appliedFilterRawValue = :filter WHERE id = :id")
    suspend fun updateFilter(id: String, filter: String?)

    @Query("""
        UPDATE shooting_records
        SET latitude = :lat, longitude = :lng, placeName = :place, cityName = :city
        WHERE id = :id
    """)
    suspend fun updateLocation(id: String, lat: Double, lng: Double, place: String?, city: String?)

    @Query("""
        UPDATE shooting_records
        SET lightLevel = :light, colorTemperature = :ct, exposureTimeMs = :exp, isLowLight = :low
        WHERE id = :id
    """)
    suspend fun updateLightInfo(id: String, light: Float?, ct: Float?, exp: Long?, low: Boolean)

    @Query("""
        UPDATE shooting_records
        SET deviceModel = :model, lensFacing = :lens
        WHERE id = :id
    """)
    suspend fun updateDeviceInfo(id: String, model: String?, lens: String?)

    // MARK: - 删除

    @Query("DELETE FROM shooting_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM shooting_records WHERE createdAt < :beforeTs")
    suspend fun deleteOlderThan(beforeTs: Long)

    @Query("DELETE FROM shooting_records")
    suspend fun clearAll()
}

@Dao
interface CustomPlanDao {
    @Insert
    suspend fun insert(plan: CustomPlanEntity)

    @Insert
    suspend fun insertAll(plans: List<CustomPlanEntity>)

    @Query("SELECT * FROM custom_plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomPlanEntity>>

    @Query("SELECT * FROM custom_plans WHERE id = :id")
    suspend fun byId(id: String): CustomPlanEntity?

    @Query("DELETE FROM custom_plans WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM custom_plans")
    suspend fun clearAll()
}
