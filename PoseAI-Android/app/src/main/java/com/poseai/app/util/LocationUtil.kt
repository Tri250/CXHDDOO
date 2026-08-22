package com.poseai.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 位置信息工具——无 Google Play Services 依赖的纯 Android 实现。
 *
 * 设计目标：
 *  - 优先使用 GPS_PROVIDER（室外高精度），降级到 NETWORK_PROVIDER
 *  - 无权限 / 无 Provider 时返回 null，不抛出
 *  - 地理逆编码同步调用（在 IO 线程执行），返回城市/地点名
 *  - 所有调用均为挂起函数，安全在协程中使用
 */
object LocationUtil {

    /** 判断是否有定位权限 */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 非阻塞地尝试获取最后一次已知位置。
     * 先 GPS 再 NETWORK，返回第一个可用结果。
     */
    suspend fun getLastKnownLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }
        for (provider in providers) {
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    @Suppress("MissingPermission")
                    lm.getLastKnownLocation(provider)
                } else null
            }.getOrNull()?.let { loc ->
                if (loc.latitude != 0.0 || loc.longitude != 0.0) return@withContext loc
            }
        }
        null
    }

    /**
     * 地理逆编码：将经纬度转换为 (地点名, 城市名)。
     * 失败或无结果返回 Pair(null, null)。
     */
    suspend fun reverseGeocode(
        context: Context,
        lat: Double,
        lng: Double
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) return@withContext Pair(null, null)
            val addr = addresses.first()
            // 地点名：优先 subLocality / thoroughfare / featureName
            val place = addr.subLocality
                ?: addr.thoroughfare
                ?: addr.featureName
                ?: addr.locality
            // 城市：locality 或 subAdminArea
            val city = addr.locality
                ?: addr.subAdminArea
                ?: addr.adminArea
            Pair(place, city)
        }.getOrElse { Pair(null, null) }
    }

    /**
     * 便捷方法：一次性返回 (latitude, longitude, place, city)，
     * 任一字段失败则为 null。
     */
    suspend fun captureLocation(context: Context): LocationSnapshot {
        val loc = getLastKnownLocation(context)
            ?: return LocationSnapshot(null, null, null, null)
        val (place, city) = reverseGeocode(context, loc.latitude, loc.longitude)
        return LocationSnapshot(
            latitude = loc.latitude,
            longitude = loc.longitude,
            placeName = place,
            cityName = city
        )
    }
}

data class LocationSnapshot(
    val latitude: Double?,
    val longitude: Double?,
    val placeName: String?,
    val cityName: String?
)
