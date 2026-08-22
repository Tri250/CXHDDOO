package com.poseai.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库。
 *
 * v1 → v2 迁移：
 *  - shooting_records 新增地理位置字段（latitude/longitude/placeName/cityName）
 *  - 新增光线环境字段（lightLevel/colorTemperature/exposureTimeMs/isLowLight）
 *  - 新增设备字段（deviceModel/lensFacing）
 *  - 新增 4 个索引：createdAt / sceneRawValue / planId / matchScore
 */
@Database(
    entities = [ShootingRecordEntity::class, CustomPlanEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shootingRecordDao(): ShootingRecordDao
    abstract fun customPlanDao(): CustomPlanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) shooting_records 表新增字段
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN latitude REAL NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN longitude REAL NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN placeName TEXT NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN cityName TEXT NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN lightLevel REAL NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN colorTemperature REAL NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN exposureTimeMs INTEGER NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN isLowLight INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN deviceModel TEXT NULL")
                db.execSQL("ALTER TABLE shooting_records ADD COLUMN lensFacing TEXT NULL")

                // 2) 索引（Room 的 @Entity indices 不会自动通过 migration 创建，需要手写 DDL）
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shooting_records_createdAt ON shooting_records(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shooting_records_sceneRawValue ON shooting_records(sceneRawValue)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shooting_records_planId ON shooting_records(planId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shooting_records_matchScore ON shooting_records(matchScore)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poseai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // 开发期间允许降级（数据损失可接受），正式发行时可关闭
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
