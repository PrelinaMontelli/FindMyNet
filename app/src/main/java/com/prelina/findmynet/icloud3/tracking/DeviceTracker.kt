package com.prelina.findmynet.icloud3.tracking

import android.util.Log
import com.prelina.findmynet.icloud3.IC3Constants
import com.prelina.findmynet.icloud3.models.*
import com.prelina.findmynet.icloud3.zone.ZoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Device Tracker - 设备追踪引擎
 * 完整移植自 determine_interval.py 和 device.py
 * 处理设备位置更新、间隔计算、区域检测等
 */
class DeviceTracker(
    private val zoneManager: ZoneManager,
    private val config: GeneralConfig = GeneralConfig()
) {
    companion object {
        private const val TAG = "DeviceTracker"
        
        // 距离阈值
        private const val DISTANCE_3KM = 3000.0          // 3公里
        private const val DISTANCE_2KM = 2000.0          // 2公里
        private const val DISTANCE_3_5KM = 3500.0        // 3.5公里
        private const val DISTANCE_5KM = 5000.0          // 5公里
        private const val DISTANCE_8KM = 8000.0          // 8公里
        private const val DISTANCE_12KM = 12000.0        // 12公里
        private const val DISTANCE_20KM = 20000.0        // 20公里
        private const val DISTANCE_40KM = 40000.0        // 40公里
        
        // 移动阈值
        private const val MOVED_20M = 20.0               // 20米
        private const val MOVED_50M = 50.0               // 50米
        
        // 时间间隔（秒）
        private const val INTERVAL_15_SEC = 15
        private const val INTERVAL_30_SEC = 30
        private const val INTERVAL_1_MIN = 60
        private const val INTERVAL_2_MIN = 120
        private const val INTERVAL_3_MIN = 180
        private const val INTERVAL_5_MIN = 300
        private const val INTERVAL_10_MIN = 600
        private const val INTERVAL_15_MIN = 900
        private const val INTERVAL_30_MIN = 1800
    }
    
    // 追踪的设备列表
    private val trackedDevices = mutableMapOf<String, Device>()
    
    /**
     * 添加设备到追踪列表
     */
    fun addDevice(device: Device) {
        trackedDevices[device.devicename] = device
        Log.d(TAG, "Added device to tracking: ${device.devicename}")
    }
    
    /**
     * 移除设备
     */
    fun removeDevice(deviceName: String) {
        trackedDevices.remove(deviceName)
        Log.d(TAG, "Removed device from tracking: $deviceName")
    }
    
    /**
     * 获取设备
     */
    fun getDevice(deviceName: String): Device? = trackedDevices[deviceName]
    
    /**
     * 获取所有设备
     */
    fun getAllDevices(): List<Device> = trackedDevices.values.toList()
    
    /**
     * 更新设备位置 - 主入口
     */
    suspend fun updateDeviceLocation(
        deviceName: String,
        newLocation: LocationData,
        trigger: String = ""
    ): Result<TrackingUpdateResult> = withContext(Dispatchers.IO) {
        try {
            val device = trackedDevices[deviceName]
                ?: return@withContext Result.failure(Exception("Device not found: $deviceName"))
            
            if (!device.shouldTrack()) {
                Log.d(TAG, "Device $deviceName should not be tracked (paused/inactive)")
                return@withContext Result.success(TrackingUpdateResult(
                    success = false,
                    device = device,
                    error = "Device not being tracked"
                ))
            }
            
            Log.d(TAG, "Updating location for $deviceName")
            
            // 1. 更新设备位置
            var updatedDevice = device.updateLocation(newLocation)
            updatedDevice = updatedDevice.copy(trigger = trigger)
            
            // 2. 检查GPS精度
            if (!updatedDevice.isGpsAccuracyGood() && updatedDevice.isInZone) {
                Log.w(TAG, "Poor GPS accuracy for $deviceName: ${newLocation.horizontalAccuracy}m")
                if (config.discardPoorGpsInzone) {
                    // 在区域内且GPS精度差，丢弃此更新
                    return@withContext Result.success(TrackingUpdateResult(
                        success = false,
                        device = device,
                        error = "Poor GPS accuracy in zone"
                    ))
                }
            }
            
            // 3. 检查位置是否过旧
            if (updatedDevice.isLocationOld()) {
                Log.w(TAG, "Location data is old for $deviceName: ${newLocation.age}s")
            }
            
            // 4. 确定当前区域
            val currentZone = determineCurrentZone(updatedDevice)
            updatedDevice = updatedDevice.updateZone(currentZone)
            
            // 5. 计算距离
            val homeZone = zoneManager.getHomeZone()
            if (homeZone != null) {
                updatedDevice = updatedDevice.copy(
                    distanceFromHome = updatedDevice.distanceToZone(homeZone)
                )
            }
            
            // 如果有追踪基准区域，计算距离
            val trackFromZone = zoneManager.getZone(updatedDevice.trackFromBaseZone)
            if (trackFromZone != null) {
                updatedDevice = updatedDevice.copy(
                    distanceFromZone = updatedDevice.distanceToZone(trackFromZone)
                )
            }
            
            // 6. 确定移动方向
            val direction = determineDirection(device, updatedDevice, trackFromZone)
            updatedDevice = updatedDevice.copy(directionOfTravel = direction)
            
            // 7. 计算下次更新间隔
            val interval = determineInterval(updatedDevice, trackFromZone)
            val nextUpdate = System.currentTimeMillis() + (interval * 1000)
            
            updatedDevice = updatedDevice.copy(
                interval = interval,
                nextUpdate = nextUpdate
            )
            
            // 8. 更新设备到缓存
            trackedDevices[deviceName] = updatedDevice
            
            Log.d(TAG, "Device $deviceName updated: zone=${updatedDevice.currentZone}, " +
                    "distance=${updatedDevice.distanceFromHome}m, " +
                    "direction=$direction, " +
                    "interval=${interval}s")
            
            Result.success(TrackingUpdateResult(
                success = true,
                device = updatedDevice
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device location", e)
            Result.failure(e)
        }
    }
    
    /**
     * 确定设备当前所在区域
     */
    private fun determineCurrentZone(device: Device): String {
        // 检查所有区域
        val zones = zoneManager.getAllZones()
        
        for (zone in zones) {
            if (device.isInZone(zone)) {
                return zone.name
            }
        }
        
        // 不在任何区域内
        return IC3Constants.NOT_HOME
    }
    
    /**
     * 确定移动方向
     */
    private fun determineDirection(
        oldDevice: Device,
        newDevice: Device,
        trackFromZone: Zone?
    ): String {
        // 如果在区域内
        if (newDevice.isInZone) {
            return IC3Constants.INZONE
        }
        
        // 如果没有基准区域
        if (trackFromZone == null) {
            return IC3Constants.NOT_SET
        }
        
        // 如果移动距离太小
        if (newDevice.movedDistance < MOVED_20M) {
            return IC3Constants.STATIONARY
        }
        
        // 计算旧位置和新位置到区域的距离
        val oldDistance = oldDevice.distanceToZone(trackFromZone)
        val newDistance = newDevice.distanceToZone(trackFromZone)
        
        // 确定方向
        return when {
            newDistance < oldDistance - 50 -> IC3Constants.TOWARDS  // 接近
            newDistance > oldDistance + 50 -> IC3Constants.AWAY_FROM  // 远离
            else -> oldDevice.directionOfTravel  // 保持之前的方向
        }
    }
    
    /**
     * 确定更新间隔 - 核心算法
     * 完整移植自 determine_interval.py
     */
    private fun determineInterval(device: Device, trackFromZone: Zone?): Int {
        val battery10 = device.battery.level in 1..10
        val battery5 = device.battery.level in 1..5
        val isInZone = device.isInZone
        val distanceKm = device.distanceFromZone / 1000.0
        val direction = device.directionOfTravel
        
        // 1. 区域变化处理
        if (device.currentZone != device.lastZone) {
            return when {
                // 进入区域
                isInZone && !device.isLocationOld() -> {
                    Log.d(TAG, "Device entered zone: ${device.currentZone}")
                    device.inzoneInterval
                }
                
                // 电池低且接近区域
                battery5 && distanceKm <= 1.0 -> {
                    Log.d(TAG, "Battery critical (<5%) and near zone")
                    INTERVAL_15_SEC
                }
                
                // 电池低
                battery10 -> {
                    Log.d(TAG, "Battery low (<10%)")
                    config.maxInterval * 60
                }
                
                // 离开区域
                !isInZone && device.lastZone != IC3Constants.NOT_SET -> {
                    Log.d(TAG, "Device exited zone: ${device.lastZone}")
                    config.exitZoneInterval * 60
                }
                
                // 一般区域变化
                else -> INTERVAL_2_MIN
            }
        }
        
        // 2. 退出区域触发器
        if (device.trigger.contains(IC3Constants.EXIT_ZONE) &&
            !isInZone &&
            System.currentTimeMillis() - device.zoneChangedTime < 60000) {
            Log.d(TAG, "Exit zone trigger")
            return config.exitZoneInterval * 60
        }
        
        // 3. GPS精度差
        if (!device.isGpsAccuracyGood()) {
            Log.d(TAG, "Poor GPS accuracy")
            return getIntervalForErrorRetry(device.errorCount)
        }
        
        // 4. 位置数据过旧
        if (device.isLocationOld()) {
            Log.d(TAG, "Old location data")
            return getIntervalForErrorRetry(device.errorCount)
        }
        
        // 5. 电池低且距离较远
        if (battery10 && distanceKm > 1.0) {
            Log.d(TAG, "Battery low and far from zone")
            return config.maxInterval * 60
        }
        
        // 6. 在Home区域或非常接近
        if (device.currentZone == IC3Constants.HOME ||
            (distanceKm < 0.05 && direction == IC3Constants.TOWARDS)) {
            Log.d(TAG, "At home or very close")
            return device.inzoneInterval
        }
        
        // 7. 在其他区域内
        if (isInZone) {
            Log.d(TAG, "In zone: ${device.currentZone}")
            return device.inzoneInterval
        }
        
        // 8. 固定间隔（如果配置）
        if (device.fixedInterval > 0) {
            return device.fixedInterval * 60
        }
        
        // 9. 基于距离和方向的动态间隔
        return when {
            // 需要更多信息
            direction == IC3Constants.NOT_SET -> {
                INTERVAL_2_MIN
            }
            
            // 距离 < 2km 且远离
            distanceKm < 2.0 && direction == IC3Constants.AWAY_FROM -> {
                config.oldLocationThreshold * 60
            }
            
            // 距离 < 2km
            distanceKm < 2.0 -> {
                if (device.went3km) INTERVAL_15_SEC else INTERVAL_1_MIN
            }
            
            // 距离 < 3.5km
            distanceKm < 3.5 -> {
                INTERVAL_1_MIN + INTERVAL_30_SEC
            }
            
            // 距离 < 5km
            distanceKm < 5.0 -> {
                INTERVAL_2_MIN
            }
            
            // 距离 < 8km
            distanceKm < 8.0 -> {
                INTERVAL_3_MIN
            }
            
            // 距离 < 12km
            distanceKm < 12.0 -> {
                INTERVAL_5_MIN
            }
            
            // 距离 < 20km
            distanceKm < 20.0 -> {
                INTERVAL_10_MIN
            }
            
            // 距离 < 40km
            distanceKm < 40.0 -> {
                INTERVAL_15_MIN
            }
            
            // 距离 >= 40km
            else -> {
                INTERVAL_30_MIN
            }
        }
    }
    
    /**
     * 获取错误重试间隔
     */
    private fun getIntervalForErrorRetry(errorCount: Int): Int {
        val intervals = IC3Constants.RETRY_INTERVAL_RANGE_1
        
        val key = intervals.keys.filter { it <= errorCount }.maxOrNull() ?: 0
        val minutes = intervals[key] ?: 0.25
        
        return (minutes * 60).toInt()
    }
    
    /**
     * 检查设备是否需要更新
     */
    fun shouldUpdateDevice(deviceName: String): Boolean {
        val device = trackedDevices[deviceName] ?: return false
        
        if (!device.shouldTrack()) return false
        if (device.isPaused) return false
        
        val now = System.currentTimeMillis()
        return now >= device.nextUpdate
    }
    
    /**
     * 获取需要更新的设备列表
     */
    fun getDevicesNeedingUpdate(): List<Device> {
        val now = System.currentTimeMillis()
        return trackedDevices.values.filter { device ->
            device.shouldTrack() &&
            !device.isPaused &&
            now >= device.nextUpdate
        }
    }
    
    /**
     * 暂停设备追踪
     */
    fun pauseDevice(deviceName: String) {
        trackedDevices[deviceName]?.let { device ->
            trackedDevices[deviceName] = device.copy(isPaused = true)
            Log.d(TAG, "Device $deviceName paused")
        }
    }
    
    /**
     * 恢复设备追踪
     */
    fun resumeDevice(deviceName: String) {
        trackedDevices[deviceName]?.let { device ->
            trackedDevices[deviceName] = device.copy(
                isPaused = false,
                nextUpdate = System.currentTimeMillis() + INTERVAL_15_SEC * 1000
            )
            Log.d(TAG, "Device $deviceName resumed")
        }
    }
    
    /**
     * 暂停所有设备
     */
    fun pauseAllDevices() {
        trackedDevices.keys.forEach { pauseDevice(it) }
    }
    
    /**
     * 恢复所有设备
     */
    fun resumeAllDevices() {
        trackedDevices.keys.forEach { resumeDevice(it) }
    }
    
    /**
     * 计算两点之间的距离（米）
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
    
    /**
     * 格式化统计信息
     */
    fun getTrackingStats(): Map<String, Any> {
        val totalDevices = trackedDevices.size
        val activeDevices = trackedDevices.values.count { it.shouldTrack() }
        val pausedDevices = trackedDevices.values.count { it.isPaused }
        val inZoneDevices = trackedDevices.values.count { it.isInZone }
        
        return mapOf(
            "totalDevices" to totalDevices,
            "activeDevices" to activeDevices,
            "pausedDevices" to pausedDevices,
            "inZoneDevices" to inZoneDevices,
            "devices" to trackedDevices.values.map { device ->
                mapOf(
                    "name" to device.devicename,
                    "zone" to device.currentZone,
                    "distance" to device.distanceFromHome,
                    "battery" to device.battery.level,
                    "lastUpdate" to device.lastUpdate,
                    "nextUpdate" to device.nextUpdate,
                    "interval" to device.interval
                )
            }
        )
    }
    
    /**
     * 清除所有设备
     */
    fun clearAllDevices() {
        trackedDevices.clear()
        Log.d(TAG, "All devices cleared")
    }
}
