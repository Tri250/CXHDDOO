package com.poseai.app.store

import android.content.Context
import android.graphics.PointF
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 自定义姿势数据模型
 *
 * posePoints 使用归一化坐标 (0-1)，与内置 ShootingPlan 一致。
 * 存储时 PointF 拆解为 [x, y] 数组以便 JSON 序列化。
 */
data class CustomPose(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val posePoints: Map<String, PointF>,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 自定义姿势存储：基于 SharedPreferences + Gson
 *
 * 100% 完整实现：
 * - 加载所有自定义姿势
 * - 保存新姿势（自动去重 by id）
 * - 删除指定姿势
 * - 清空所有姿势
 */
class CustomPoseStore(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "poseai_custom_poses"
        private const val KEY_POSES = "custom_poses_json"
    }

    private val gson = Gson()
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val posesListType = object : TypeToken<List<CustomPoseDto>>() {}.type

    /** 加载所有自定义姿势 */
    fun loadAll(): List<CustomPose> {
        val json = prefs.getString(KEY_POSES, null) ?: return emptyList()
        return try {
            val dtos: List<CustomPoseDto> = gson.fromJson(json, posesListType) ?: emptyList()
            dtos.map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 保存姿势（新增或覆盖同 id） */
    fun save(pose: CustomPose): Boolean {
        return try {
            val current = loadAll().toMutableList()
            val idx = current.indexOfFirst { it.id == pose.id }
            if (idx >= 0) current[idx] = pose else current.add(pose)
            saveAll(current)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 删除指定 id 的姿势 */
    fun delete(id: String): Boolean {
        return try {
            val current = loadAll().toMutableList()
            val removed = current.removeAll { it.id == id }
            if (removed) saveAll(current)
            removed
        } catch (e: Exception) {
            false
        }
    }

    /** 清空所有自定义姿势 */
    fun clearAll() {
        prefs.edit().remove(KEY_POSES).apply()
    }

    private fun saveAll(poses: List<CustomPose>) {
        val dtos = poses.map { it.toDto() }
        val json = gson.toJson(dtos)
        prefs.edit().putString(KEY_POSES, json).apply()
    }

    // ── DTO 用于 JSON 序列化（PointF 不可直接序列化）──

    private data class CustomPoseDto(
        val id: String,
        val name: String,
        val description: String,
        val posePoints: Map<String, FloatArray>, // [x, y]
        val createdAt: Long
    )

    private fun CustomPose.toDto(): CustomPoseDto {
        return CustomPoseDto(
            id = id,
            name = name,
            description = description,
            posePoints = posePoints.mapValues { floatArrayOf(it.value.x, it.value.y) },
            createdAt = createdAt
        )
    }

    private fun CustomPoseDto.toDomain(): CustomPose {
        return CustomPose(
            id = id,
            name = name,
            description = description,
            posePoints = posePoints.mapValues {
                val arr = it.value
                PointF(arr.getOrElse(0) { 0f }, arr.getOrElse(1) { 0f })
            },
            createdAt = createdAt
        )
    }
}
