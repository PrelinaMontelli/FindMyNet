package com.prelina.findmynet.icloud3.zone

import android.util.Log
import com.prelina.findmynet.icloud3.IC3Constants
import com.prelina.findmynet.icloud3.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Zone Manager - 区域管理系统
 * 完整移植自 zone.py 和 stationary_zone.py
 * 处理区域定义、进出检测、静止区域管理等
 */
class ZoneManager(
    private val zoneConfig: ZoneConfig = ZoneConfig(),
    private val generalConfig: GeneralConfig = GeneralConfig()
) {
    companion object {
        private const val TAG = "ZoneManager"
        
        // 静止区域配置
        private const val MAX_STATIONARY_ZONES = 10
        private const val STATZONE_MIN_DISTANCE = 50.0  // 最小距离（米）
        private const val STATZONE_MOVE_LIMIT = 125.0   // 移动限制（米）
    }
    
    // 区域存储
    private val zones = mutableMapOf<String, Zone>()
    private val stationaryZones = mutableMapOf<Int, StationaryZone>()
    private var homeZone: Zone? = null
    
    // 区域事件流
    private val _zoneEvents = MutableStateFlow<ZoneEvent?>(null)
    val zoneEvents: StateFlow<ZoneEvent?> = _zoneEvents.asStateFlow()
    
    // 静止区域计数器
    private var nextStatZoneId = 1
    
    // 设备静止时间追踪
    private val deviceStillTimes = mutableMapOf<String, DeviceStillTimeInfo>()
    
    init {
        Log.d(TAG, "ZoneManager initialized")
    }
    
    /**
     * 添加区域
     */
    fun addZone(zone: Zone) {
        zones[zone.name] = zone
        
        if (zone.isHomeZone) {
            homeZone = zone
            Log.d(TAG, "Home zone set: ${zone.fname} at ${zone.latitude}, ${zone.longitude}")
        }
        
        Log.d(TAG, "Zone added: ${zone.fname} (${zone.name})")
    }
    
    /**
     * 移除区域
     */
    fun removeZone(zoneName: String) {
        zones.remove(zoneName)
        Log.d(TAG, "Zone removed: $zoneName")
    }
    
    /**
     * 获取区域
     */
    fun getZone(zoneName: String): Zone? = zones[zoneName]
    
    /**
     * 获取Home区域
     */
    fun getHomeZone(): Zone? = homeZone
    
    /**
     * 获取所有区域
     */
    fun getAllZones(): List<Zone> = zones.values.toList()
    
    /**
     * 获取所有静止区域
     */
    fun getAllStationaryZones(): List<StationaryZone> = stationaryZones.values.toList()
    
    /**
     * 创建Home区域
     */
    fun createHomeZone(
        latitude: Double,
        longitude: Double,
        radius: Double = 100.0
    ) {
        val home = Zone(
            name = IC3Constants.HOME,
            fname = IC3Constants.HOME_FNAME,
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            isHomeZone = true
        )
        
        addZone(home)
    }
    
    /**
     * 检查设备是否在某个区域内
     */
    fun isDeviceInZone(device: Device, zoneName: String): Boolean {
        val zone = zones[zoneName] ?: return false
        return device.isInZone(zone)
    }
    
    /**
     * 获取设备当前所在的区域
     */
    fun getDeviceCurrentZone(device: Device): Zone? {
        // 首先检查Home区域
        homeZone?.let { home ->
            if (device.isInZone(home)) {
                return home
            }
        }
        
        // 然后检查其他区域
        for (zone in zones.values) {
            if (!zone.isHomeZone && device.isInZone(zone)) {
                return zone
            }
        }
        
        // 最后检查静止区域
        for (statZone in stationaryZones.values) {
            if (device.isInZone(statZone.toZone())) {
                return statZone.toZone()
            }
        }
        
        return null
    }
    
    /**
     * 检查并处理区域进出事件
     */
    suspend fun checkZoneTransition(
        device: Device,
        oldZone: String,
        newZone: String,
        trigger: String = ""
    ): ZoneEvent? {
        if (oldZone == newZone) return null
        
        val zone: Zone? = zones[newZone] ?: stationaryZones.values.find { it.name == newZone }?.toZone()
        
        val event = when {
            // 进入区域
            oldZone in IC3Constants.NOT_HOME_ZONES && newZone !in IC3Constants.NOT_HOME_ZONES -> {
                Log.d(TAG, "Device ${device.devicename} entered zone: $newZone")
                zone?.let {
                    ZoneEvent(
                        deviceName = device.devicename,
                        eventType = ZoneEventType.ENTER,
                        zone = it,
                        trigger = trigger,
                        location = device.location
                    )
                }
            }
            
            // 离开区域
            oldZone !in IC3Constants.NOT_HOME_ZONES && newZone in IC3Constants.NOT_HOME_ZONES -> {
                Log.d(TAG, "Device ${device.devicename} exited zone: $oldZone")
                val oldZoneObj: Zone? = zones[oldZone] ?: stationaryZones.values.find { it.name == oldZone }?.toZone()
                oldZoneObj?.let {
                    ZoneEvent(
                        deviceName = device.devicename,
                        eventType = ZoneEventType.EXIT,
                        zone = it,
                        trigger = trigger,
                        location = device.location
                    )
                }
            }
            
            // 区域间移动
            else -> {
                Log.d(TAG, "Device ${device.devicename} moved from $oldZone to $newZone")
                null
            }
        }
        
        // 发送事件
        event?.let { _zoneEvents.value = it }
        
        return event
    }
    
    /**
     * 更新设备静止时间
     */
    fun updateDeviceStillTime(device: Device) {
        val deviceName = device.devicename
        val stillInfo = deviceStillTimes.getOrPut(deviceName) {
            DeviceStillTimeInfo(deviceName)
        }
        
        // 如果在区域内，重置静止计时
        if (device.isInZone) {
            stillInfo.reset()
            return
        }
        
        // 检查是否移动
        if (device.movedDistance > STATZONE_MOVE_LIMIT) {
            // 移动距离超过阈值，重置
            stillInfo.reset()
            stillInfo.location = device.location
        } else {
            // 更新静止时间
            stillInfo.updateStillTime()
            
            // 检查是否应该创建静止区域
            val stillMinutes = stillInfo.getStillTimeMinutes()
            val requiredStillTime = generalConfig.statZoneStillTime
            if (stillMinutes >= requiredStillTime) {
                createStationaryZone(device, stillInfo)
            }
        }
    }
    
    /**
     * 创建静止区域
     */
    private fun createStationaryZone(device: Device, stillInfo: DeviceStillTimeInfo) {
        // 检查是否太接近现有区域
        if (isTooCloseToExistingZone(device.location.latitude, device.location.longitude)) {
            Log.d(TAG, "Too close to existing zone, not creating stationary zone")
            return
        }
        
        // 检查是否超过最大静止区域数量
        if (stationaryZones.size >= MAX_STATIONARY_ZONES) {
            // 删除最老的静止区域
            val oldestZone = stationaryZones.values.minByOrNull { it.createTime }
            oldestZone?.let { removeStationaryZone(it.id) }
        }
        
        // 创建新的静止区域
        val inzoneIntervalSeconds = generalConfig.statZoneInzoneInterval * 60
        val statZone = StationaryZone(
            id = nextStatZoneId++,
            latitude = device.location.latitude,
            longitude = device.location.longitude,
            radius = 100.0,
            deviceName = device.devicename,
            stillTime = stillInfo.getStillTimeSeconds(),
            inzoneInterval = inzoneIntervalSeconds
        )
        
        stationaryZones[statZone.id] = statZone
        Log.d(TAG, "Created stationary zone: ${statZone.fname} for ${device.devicename}")
        
        // 重置静止信息
        stillInfo.reset()
        stillInfo.stationaryZoneId = statZone.id
    }
    
    /**
     * 移除静止区域
     */
    fun removeStationaryZone(zoneId: Int) {
        stationaryZones.remove(zoneId)?.let { zone ->
            Log.d(TAG, "Removed stationary zone: ${zone.fname}")
        }
    }
    
    /**
     * 检查位置是否太接近现有区域
     */
    private fun isTooCloseToExistingZone(latitude: Double, longitude: Double): Boolean {
        // 检查所有普通区域
        for (zone in zones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                zone.latitude, zone.longitude
            )
            if (distance < zone.radius + STATZONE_MIN_DISTANCE) {
                return true
            }
        }
        
        // 检查所有静止区域
        for (statZone in stationaryZones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                statZone.latitude, statZone.longitude
            )
            if (distance < statZone.radius + STATZONE_MIN_DISTANCE) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 清理不再使用的静止区域
     */
    fun cleanupStationaryZones(deviceName: String) {
        val zonesToRemove = stationaryZones.values
            .filter { it.deviceName == deviceName }
            .map { it.id }
        
        zonesToRemove.forEach { removeStationaryZone(it) }
    }
    
    /**
     * 获取最近的区域
     */
    fun getNearestZone(latitude: Double, longitude: Double): Pair<Zone?, Double> {
        var nearestZone: Zone? = null
        var minDistance = Double.MAX_VALUE
        
        // 检查所有区域
        for (zone in zones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                zone.latitude, zone.longitude
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestZone = zone
            }
        }
        
        // 检查静止区域
        for (statZone in stationaryZones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                statZone.latitude, statZone.longitude
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestZone = statZone.toZone()
            }
        }
        
        return Pair(nearestZone, minDistance)
    }
    
    /**
     * 获取指定半径内的所有区域
     */
    fun getZonesWithinRadius(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<Pair<Zone, Double>> {
        val zonesInRadius = mutableListOf<Pair<Zone, Double>>()
        
        // 检查所有区域
        for (zone in zones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                zone.latitude, zone.longitude
            )
            if (distance <= radiusMeters) {
                zonesInRadius.add(Pair(zone, distance))
            }
        }
        
        // 检查静止区域
        for (statZone in stationaryZones.values) {
            val distance = Zone.calculateDistance(
                latitude, longitude,
                statZone.latitude, statZone.longitude
            )
            if (distance <= radiusMeters) {
                zonesInRadius.add(Pair(statZone.toZone(), distance))
            }
        }
        
        // 按距离排序
        return zonesInRadius.sortedBy { it.second }
    }
    
    /**
     * 格式化区域信息
     */
    fun formatZoneInfo(zone: Zone): String {
        return buildString {
            append(zone.fname)
            append(" (${zone.name})")
            append(" • ${zone.radius.toInt()}m radius")
            append(" • ${zone.latitude}, ${zone.longitude}")
            if (zone.isStatZone) {
                append(" • Stationary")
            }
        }
    }
    
    /**
     * 获取区域统计信息
     */
    fun getZoneStats(): Map<String, Any> {
        return mapOf(
            "totalZones" to zones.size,
            "stationaryZones" to stationaryZones.size,
            "homeZone" to (homeZone?.fname ?: "Not set"),
            "zones" to zones.values.map { zone ->
                mapOf(
                    "name" to zone.name,
                    "fname" to zone.fname,
                    "latitude" to zone.latitude,
                    "longitude" to zone.longitude,
                    "radius" to zone.radius,
                    "isHome" to zone.isHomeZone,
                    "isStationary" to zone.isStatZone
                )
            }
        )
    }
    
    /**
     * 清除所有静止区域
     */
    fun clearAllStationaryZones() {
        stationaryZones.clear()
        deviceStillTimes.clear()
        nextStatZoneId = 1
        Log.d(TAG, "All stationary zones cleared")
    }
    
    /**
     * 清除所有区域
     */
    fun clearAllZones() {
        zones.clear()
        homeZone = null
        clearAllStationaryZones()
        Log.d(TAG, "All zones cleared")
    }
}

/**
 * Device Still Time Info - 设备静止时间信息
 */
private data class DeviceStillTimeInfo(
    val deviceName: String,
    var location: LocationData = LocationData(),
    var firstStillTime: Long = 0,
    var lastUpdateTime: Long = 0,
    var stationaryZoneId: Int? = null
) {
    fun reset() {
        firstStillTime = 0
        lastUpdateTime = 0
        stationaryZoneId = null
    }
    
    fun updateStillTime() {
        val now = System.currentTimeMillis()
        if (firstStillTime == 0L) {
            firstStillTime = now
        }
        lastUpdateTime = now
    }
    
    fun getStillTimeSeconds(): Long {
        if (firstStillTime == 0L) return 0
        return (System.currentTimeMillis() - firstStillTime) / 1000
    }
    
    fun getStillTimeMinutes(): Int {
        return (getStillTimeSeconds() / 60).toInt()
    }
}

/**
 * Zone Distance Info - 区域距离信息
 */
data class ZoneDistanceInfo(
    val zone: Zone,
    val distanceToCenter: Double,
    val distanceToEdge: Double,
    val isInZone: Boolean
)

/**
 * Zone Helper Functions - 区域辅助函数
 */
object ZoneHelper {
    
    /**
     * 计算两点之间的距离（米）- Haversine公式
     */
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        return Zone.calculateDistance(lat1, lon1, lat2, lon2)
    }
    
    /**
     * 计算方位角（度）
     */
    fun calculateBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    /**
     * 获取方位角描述
     */
    fun getBearingDescription(bearing: Double): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "North"
            bearing < 67.5 -> "NorthEast"
            bearing < 112.5 -> "East"
            bearing < 157.5 -> "SouthEast"
            bearing < 202.5 -> "South"
            bearing < 247.5 -> "SouthWest"
            bearing < 292.5 -> "West"
            else -> "NorthWest"
        }
    }
    
    /**
     * 格式化距离
     */
    fun formatDistance(meters: Double, useMetric: Boolean = true): String {
        return if (useMetric) {
            when {
                meters < 1000 -> "${meters.toInt()}m"
                else -> String.format("%.1fkm", meters / 1000)
            }
        } else {
            val miles = meters * 0.000621371
            when {
                miles < 0.1 -> "${(meters * 3.28084).toInt()}ft"
                else -> String.format("%.1fmi", miles)
            }
        }
    }
}
