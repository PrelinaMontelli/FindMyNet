package com.prelina.findmynet.icloud3.models

import com.prelina.findmynet.icloud3.IC3Constants
import com.google.gson.annotations.SerializedName

/**
 * 核心数据模型 - 完整移植自 iCloud3
 */

/**
 * Location Data - 位置信息
 */
data class LocationData(
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("horizontalAccuracy") val horizontalAccuracy: Double = 0.0,
    @SerializedName("verticalAccuracy") val verticalAccuracy: Double? = null,
    @SerializedName("positionType") val positionType: String = "",
    @SerializedName("timeStamp") val timestamp: Long = 0,
    val timestampSecs: Long = timestamp / 1000,
    val age: Long = 0, // 位置数据的年龄（秒）
    val isGpsGood: Boolean = horizontalAccuracy <= 100.0,
    val isGpsPoor: Boolean = horizontalAccuracy > 100.0,
    val isLocationOld: Boolean = age > 180 // 超过3分钟
) {
    fun toGpsString(): String = "$latitude, $longitude"
    fun getAccuracyText(): String = "${horizontalAccuracy.toInt()}m"
}

/**
 * Battery Data - 电池信息
 */
data class BatteryData(
    @SerializedName("batteryLevel") val level: Int = 0,
    @SerializedName("batteryStatus") val status: String = "",
    val updateTime: Long = System.currentTimeMillis(),
    val source: String = "" // "icloud" or "mobapp"
) {
    val isLow: Boolean get() = level in 1..20
    val isCritical: Boolean get() = level in 1..5
    val isCharging: Boolean get() = status.contains("charging", ignoreCase = true)
    
    val statusFname: String get() = IC3Constants.BATTERY_STATUS_FNAME[status] ?: status
    
    fun getBatteryIcon(): String {
        return when {
            isCharging -> "mdi:battery-charging"
            level >= 90 -> "mdi:battery"
            level >= 80 -> "mdi:battery-90"
            level >= 70 -> "mdi:battery-80"
            level >= 60 -> "mdi:battery-70"
            level >= 50 -> "mdi:battery-60"
            level >= 40 -> "mdi:battery-50"
            level >= 30 -> "mdi:battery-40"
            level >= 20 -> "mdi:battery-30"
            level >= 10 -> "mdi:battery-20"
            else -> "mdi:battery-10"
        }
    }
}

/**
 * Device Status - 设备状态
 */
data class DeviceStatus(
    @SerializedName("deviceStatus") val statusCode: Int = 200,
    val statusText: String = IC3Constants.DEVICE_STATUS_CODES[statusCode.toString()] ?: "Unknown",
    val isOnline: Boolean = statusCode in IC3Constants.DEVICE_STATUS_ONLINE,
    val isOffline: Boolean = statusCode == IC3Constants.DEVICE_STATUS_OFFLINE,
    val isPending: Boolean = statusCode == IC3Constants.DEVICE_STATUS_PENDING,
    val lowPowerMode: Boolean = false,
    val lostModeCapable: Boolean = false
)

/**
 * Device Model Info - 设备型号信息
 */
data class DeviceModelInfo(
    val rawModel: String = "",        // 例如: "iPhone15,2"
    val model: String = "",           // 例如: "iPhone"
    val modelDisplayName: String = "", // 例如: "iPhone 14 Pro"
    val deviceType: String = IC3Constants.IPHONE,
    val deviceClass: String = ""
) {
    val icon: String get() = IC3Constants.DEVICE_TYPE_ICONS[deviceType] ?: "mdi:cellphone"
    val inzoneInterval: Int get() = IC3Constants.DEVICE_TYPE_INZONE_INTERVALS[deviceType] ?: 120
}

/**
 * Zone - 区域定义
 */
data class Zone(
    val name: String,                    // 内部名称，如 "home"
    val fname: String,                   // 友好名称，如 "Home"
    val displayName: String = fname,     // 显示名称
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Double = 100.0,          // 米
    val passive: Boolean = false,
    val isStatZone: Boolean = false,     // 是否是静止区域
    val isHomeZone: Boolean = name == IC3Constants.HOME
) {
    /**
     * 计算点到区域中心的距离（米）
     */
    fun distanceToCenter(lat: Double, lon: Double): Double {
        return calculateDistance(this.latitude, this.longitude, lat, lon)
    }
    
    /**
     * 检查点是否在区域内
     */
    fun isPointInZone(lat: Double, lon: Double): Boolean {
        val distance = distanceToCenter(lat, lon)
        return distance <= radius
    }
    
    /**
     * 计算点到区域边缘的距离（米）
     */
    fun distanceToEdge(lat: Double, lon: Double): Double {
        val distanceToCenter = distanceToCenter(lat, lon)
        return maxOf(0.0, distanceToCenter - radius)
    }
    
    companion object {
        /**
         * 计算两点之间的距离（米）- Haversine公式
         */
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0 // 地球半径（米）
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return R * c
        }
    }
}

/**
 * Stationary Zone - 静止区域（设备长时间停留在某处时自动创建）
 */
data class StationaryZone(
    val id: Int,                          // 1-10
    val name: String = "ic3_${id}_stationary",
    val fname: String = "StatZon$id",
    val latitude: Double,
    val longitude: Double,
    val radius: Double = 100.0,
    val createTime: Long = System.currentTimeMillis(),
    val deviceName: String = "",          // 创建此静止区域的设备
    val stillTime: Long = 0,              // 设备在此位置静止的时间（秒）
    val inzoneInterval: Int = 30 * 60,    // 在静止区域内的更新间隔（秒）
    val isStatZone: Boolean = true
) {
    /**
     * 转换为Zone对象
     */
    fun toZone(): Zone = Zone(
        name = name,
        fname = fname,
        displayName = fname,
        latitude = latitude,
        longitude = longitude,
        radius = radius,
        passive = false,
        isStatZone = true,
        isHomeZone = false
    )
}

/**
 * Device - iCloud设备
 * 完整移植自 device.py 中的 iCloud3_Device 类
 */
data class Device(
    // 基本信息
    val devicename: String,               // 设备名称（唯一标识）
    val fname: String,                    // 友好名称
    val deviceId: String = "",            // iCloud设备ID
    val modelInfo: DeviceModelInfo = DeviceModelInfo(),
    
    // Apple账户信息
    val appleAccount: String = "",        // 关联的Apple账户用户名
    val familyShare: Boolean = false,     // 是否是家庭共享设备
    
    // 追踪配置
    val trackingMode: String = IC3Constants.TRACK_DEVICE,  // track, monitor, inactive
    val trackFromZones: List<String> = listOf(IC3Constants.HOME),
    val trackFromBaseZone: String = IC3Constants.HOME,
    val inzoneInterval: Int = 120,        // 在区域内的更新间隔（秒）
    val fixedInterval: Int = 0,           // 固定更新间隔（0表示不使用）
    
    // 当前状态
    var location: LocationData = LocationData(),
    var battery: BatteryData = BatteryData(),
    var status: DeviceStatus = DeviceStatus(),
    var currentZone: String = IC3Constants.NOT_SET,
    var lastZone: String = IC3Constants.NOT_SET,
    var zoneChangedTime: Long = 0,
    
    // 追踪信息
    var lastUpdate: Long = 0,
    var nextUpdate: Long = 0,
    var lastLocated: Long = 0,
    var interval: Int = 0,                // 当前更新间隔（秒）
    var distanceFromHome: Double = 0.0,   // 距离Home的距离（米）
    var distanceFromZone: Double = 0.0,   // 距离当前追踪区域的距离（米）
    var directionOfTravel: String = IC3Constants.NOT_SET,  // towards, away_from, in_zone, stationary
    var movedDistance: Double = 0.0,      // 自上次更新移动的距离（米）
    
    // 状态标志
    var isInZone: Boolean = false,
    var isTracked: Boolean = trackingMode == IC3Constants.TRACK_DEVICE,
    var isMonitored: Boolean = trackingMode == IC3Constants.MONITOR_DEVICE,
    var isInactive: Boolean = trackingMode == IC3Constants.INACTIVE_DEVICE,
    var isPaused: Boolean = false,
    var isOnline: Boolean = true,
    var offlineSecs: Long = 0,
    var went3km: Boolean = false,          // 是否已经移动超过3km（用于间隔计算）
    
    // 触发器和更新源
    var trigger: String = "",
    var locationSource: String = IC3Constants.ICLOUD,
    var dataSource: String = IC3Constants.ICLOUD,
    
    // 统计信息
    var pollCount: Int = 0,
    var updateCount: Int = 0,
    var errorCount: Int = 0,
    
    // 其他
    var picture: String = "None",
    var icon: String = "mdi:cellphone",
    var badge: Int = 0,
    var info: String = "",
    var alert: String = ""
) {
    /**
     * 检查设备是否应该被追踪
     */
    fun shouldTrack(): Boolean = isTracked && !isPaused && !isInactive
    
    /**
     * 检查GPS精度是否良好
     */
    fun isGpsAccuracyGood(): Boolean = location.horizontalAccuracy <= 100.0
    
    /**
     * 检查位置数据是否过旧
     */
    fun isLocationOld(): Boolean = location.age > 180 // 3分钟
    
    /**
     * 检查设备是否在指定区域内
     */
    fun isInZone(zone: Zone): Boolean {
        return zone.isPointInZone(location.latitude, location.longitude)
    }
    
    /**
     * 计算到指定区域的距离
     */
    fun distanceToZone(zone: Zone): Double {
        return zone.distanceToCenter(location.latitude, location.longitude)
    }
    
    /**
     * 计算到指定区域边缘的距离
     */
    fun distanceToZoneEdge(zone: Zone): Double {
        return zone.distanceToEdge(location.latitude, location.longitude)
    }
    
    /**
     * 更新设备位置
     */
    fun updateLocation(newLocation: LocationData, updateTime: Long = System.currentTimeMillis()): Device {
        // 计算移动距离
        val moved = if (location.latitude != 0.0 && location.longitude != 0.0) {
            Zone.calculateDistance(
                location.latitude, location.longitude,
                newLocation.latitude, newLocation.longitude
            )
        } else {
            0.0
        }
        
        return copy(
            location = newLocation,
            lastUpdate = updateTime,
            lastLocated = newLocation.timestamp / 1000,
            movedDistance = moved,
            pollCount = pollCount + 1
        )
    }
    
    /**
     * 更新设备电池信息
     */
    fun updateBattery(newBattery: BatteryData): Device {
        return copy(battery = newBattery)
    }
    
    /**
     * 更新设备状态
     */
    fun updateStatus(newStatus: DeviceStatus): Device {
        return copy(
            status = newStatus,
            isOnline = newStatus.isOnline,
            offlineSecs = if (newStatus.isOnline) 0 else offlineSecs
        )
    }
    
    /**
     * 更新设备所在区域
     */
    fun updateZone(newZone: String, updateTime: Long = System.currentTimeMillis()): Device {
        val zoneChanged = newZone != currentZone
        return copy(
            lastZone = if (zoneChanged) currentZone else lastZone,
            currentZone = newZone,
            isInZone = newZone !in IC3Constants.NOT_HOME_ZONES,
            zoneChangedTime = if (zoneChanged) updateTime else zoneChangedTime
        )
    }
    
    /**
     * 格式化设备信息用于显示
     */
    fun formatInfo(): String {
        return buildString {
            append(fname)
            if (currentZone != IC3Constants.NOT_SET) {
                append(" • $currentZone")
            }
            if (distanceFromHome > 0) {
                append(" • ${formatDistance(distanceFromHome)}")
            }
            if (battery.level > 0) {
                append(" • ${battery.level}%")
            }
        }
    }
    
    companion object {
        /**
         * 格式化距离
         */
        fun formatDistance(meters: Double): String {
            return when {
                meters < 1000 -> "${meters.toInt()}m"
                else -> String.format("%.1fkm", meters / 1000)
            }
        }
    }
}

/**
 * Tracking Update Result - 追踪更新结果
 */
data class TrackingUpdateResult(
    val success: Boolean,
    val device: Device,
    val error: String? = null,
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * Zone Enter/Exit Event - 区域进出事件
 */
data class ZoneEvent(
    val deviceName: String,
    val eventType: ZoneEventType,
    val zone: Zone,
    val timestamp: Long = System.currentTimeMillis(),
    val trigger: String = "",
    val location: LocationData
)

enum class ZoneEventType {
    ENTER,      // 进入区域
    EXIT,       // 离开区域
    UPDATE      // 区域内更新
}

/**
 * Authentication State - 认证状态
 */
data class AuthenticationState(
    val isAuthenticated: Boolean = false,
    val requires2FA: Boolean = false,
    val requiresTrustedDevice: Boolean = false,
    val sessionToken: String? = null,
    val accountName: String = "",
    val error: String? = null
)

/**
 * iCloud Account - iCloud账户
 */
data class ICloudAccount(
    val username: String,
    val password: String,
    val accountName: String = "",
    val isAuthenticated: Boolean = false,
    val requires2FA: Boolean = false,
    val sessionToken: String? = null,
    val devices: List<Device> = emptyList(),
    val lastUpdate: Long = 0
)

/**
 * Configuration - 配置信息
 */
data class Configuration(
    // Apple账户
    val appleAccounts: List<AppleAccountConfig> = emptyList(),
    
    // 设备配置
    val devices: List<DeviceConfig> = emptyList(),
    
    // 通用配置
    val general: GeneralConfig = GeneralConfig(),
    
    // 区域配置
    val zones: ZoneConfig = ZoneConfig()
)

data class AppleAccountConfig(
    val username: String,
    val password: String,
    val locateAll: Boolean = true,
    val serverLocation: String = "usa"
)

data class DeviceConfig(
    val devicename: String,
    val fname: String,
    val appleAccount: String,
    val deviceType: String = IC3Constants.IPHONE,
    val trackingMode: String = IC3Constants.TRACK_DEVICE,
    val trackFromZones: List<String> = listOf(IC3Constants.HOME),
    val inzoneInterval: Int = 120,
    val picture: String = "None",
    val icon: String = "mdi:cellphone"
)

data class GeneralConfig(
    // 设备配置
    val unitOfMeasurement: String = "km",  // "mi" or "km"
    val timeFormat: String = "12",         // "12" or "24"
    
    // 追踪配置
    val trackingMode: String = IC3Constants.TRACK_DEVICE,
    val inzoneInterval: Int = 60,          // 秒
    val maxInterval: Int = 1800,           // 秒
    val exitZoneInterval: Int = 120,       // 秒
    val mobileAppDeviceTrackersync: Boolean = true,
    
    // GPS配置
    val gpsAccuracyThreshold: Int = 100,   // 米
    val oldLocationThreshold: Int = 180,   // 秒
    val oldLocationAdjustment: Boolean = true,
    val discardPoorGpsInzone: Boolean = false,
    
    // 距离配置
    val travelTimeInterval: Int = 15,      // 分钟
    val distanceMethodWaze: Boolean = false,
    
    // Waze配置
    val wazeRegion: String = "US",
    val wazeMaxDistance: Int = 1000,       // km
    val wazeMinDistance: Int = 1,          // km
    val wazeRealtimeEnabled: Boolean = true,
    val wazeHistoryEnabled: Boolean = true,
    val wazeHistoryMaxDistance: Int = 40,  // km
    val wazeHistoryTrackDirection: String = IC3Constants.TOWARDS,
    
    // 静止区域配置
    val statZoneStillTime: Int = 8,        // 分钟
    val statZoneInzoneInterval: Int = 30,  // 分钟
    
    // 通知配置
    val logLevel: String = "info",
    val logLevelDevices: List<String> = emptyList(),
    val logLevelEventsEnabled: Boolean = false,
    val displayZoneFriendlyName: Boolean = true,
    val displayGpsLatLong: Boolean = false,
    
    // 其他
    val centerInZone: Boolean = false,
    val discardPoorAccuracy: Boolean = true,
    val passthruZoneTime: Int = 0          // 分钟
)

data class ZoneConfig(
    // 静止区域基准位置
    val statZoneBaseLatitude: Double = 0.0,
    val statZoneBaseLongitude: Double = 0.0,
    val statZoneRadius: Int = 125,         // 米
    
    // 家区域
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0,
    val homeRadius: Int = 100,             // 米
    
    // 区域配置
    val passthruZoneTime: Int = 0,         // 分钟
    val inzoneInterval: Int = 60,          // 秒
    val centerInZone: Boolean = false,
    val discardPoorGpsInzone: Boolean = false
)
