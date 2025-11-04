package com.prelina.findmynet.icloud3

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.prelina.findmynet.icloud3.auth.PyiCloudManager
import com.prelina.findmynet.icloud3.models.*
import com.prelina.findmynet.icloud3.tracking.DeviceTracker
import com.prelina.findmynet.icloud3.zone.ZoneManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * iCloud3 Service - 主服务类
 * 整合PyiCloud认证、设备追踪和区域管理
 */
class IC3Service : Service() {
    
    companion object {
        private const val TAG = "IC3Service"
        
        // SharedPreferences
        private const val PREFS_NAME = "ic3_config"
        private const val KEY_CONFIG = "configuration"
        
        // Service actions
        const val ACTION_START_TRACKING = "com.prelina.findmynet.START_TRACKING"
        const val ACTION_STOP_TRACKING = "com.prelina.findmynet.STOP_TRACKING"
        const val ACTION_REFRESH_DEVICES = "com.prelina.findmynet.REFRESH_DEVICES"
        const val ACTION_PAUSE_DEVICE = "com.prelina.findmynet.PAUSE_DEVICE"
        const val ACTION_RESUME_DEVICE = "com.prelina.findmynet.RESUME_DEVICE"
        
        // Extras
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // SharedPreferences
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val gson = Gson()
    
    // 核心组件
    private var pyiCloudManager: PyiCloudManager? = null
    private lateinit var zoneManager: ZoneManager
    private lateinit var deviceTracker: DeviceTracker
    
    // 配置
    private lateinit var config: Configuration
    
    // 状态
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _authState = MutableStateFlow<AuthenticationState?>(null)
    val authState: StateFlow<AuthenticationState?> = _authState.asStateFlow()
    
    // 定时更新Job
    private var updateJob: Job? = null
    
    // Binder用于本地绑定
    private val binder = IC3Binder()
    
    inner class IC3Binder : Binder() {
        fun getService(): IC3Service = this@IC3Service
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "IC3Service created")
        
        // 初始化组件
        initializeComponents()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "IC3Service destroyed")
        
        stopTracking()
        serviceScope.cancel()
    }
    
    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        // 加载配置
        config = loadConfiguration()
        
        // 初始化区域管理器
        zoneManager = ZoneManager(config.zones, config.general)
        
        // 创建Home区域（如果配置了）
        if (config.zones.homeLatitude != 0.0 && config.zones.homeLongitude != 0.0) {
            zoneManager.createHomeZone(config.zones.homeLatitude, config.zones.homeLongitude)
        }
        
        // 初始化设备追踪器
        deviceTracker = DeviceTracker(zoneManager, config.general)
        
        Log.d(TAG, "Components initialized")
    }
    
    /**
     * 处理Intent
     */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START_TRACKING -> {
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: return
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return
                serviceScope.launch {
                    startTracking(username, password)
                }
            }
            
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
            
            ACTION_REFRESH_DEVICES -> {
                serviceScope.launch {
                    refreshDevices()
                }
            }
            
            ACTION_PAUSE_DEVICE -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: return
                pauseDevice(deviceName)
            }
            
            ACTION_RESUME_DEVICE -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: return
                resumeDevice(deviceName)
            }
        }
    }
    
    /**
     * 开始追踪 - 公开给ViewModel调用
     */
    suspend fun startTracking(username: String, password: String) {
        try {
            _serviceState.value = ServiceState.Authenticating
            Log.d(TAG, "Starting tracking for $username")
            
            // 1. 创建并认证PyiCloud
            pyiCloudManager = PyiCloudManager(
                context = applicationContext,
                username = username,
                password = password
            )
            
            val authResult = pyiCloudManager!!.authenticate()
            
            if (authResult.isFailure) {
                Log.e(TAG, "Authentication failed", authResult.exceptionOrNull())
                _serviceState.value = ServiceState.Error("Authentication failed")
                return
            }
            
            val authState = authResult.getOrNull()!!
            _authState.value = authState
            
            if (authState.requires2FA) {
                _serviceState.value = ServiceState.Requires2FA
                Log.d(TAG, "2FA required")
                return
            }
            
            Log.d(TAG, "Authentication successful")
            
            // 2. 刷新设备列表
            val devicesResult = pyiCloudManager!!.refreshDevices()
            if (devicesResult.isFailure) {
                Log.e(TAG, "Failed to refresh devices", devicesResult.exceptionOrNull())
                _serviceState.value = ServiceState.Error("Failed to load devices")
                return
            }
            
            val icloudDevices = devicesResult.getOrNull()!!
            Log.d(TAG, "Found ${icloudDevices.size} devices")
            
            // 3. 添加设备到追踪器
            icloudDevices.forEach { device ->
                deviceTracker.addDevice(device)
            }
            
            _devices.value = icloudDevices
            
            // 4. 开始定时更新
            startPeriodicUpdates()
            
            _serviceState.value = ServiceState.Tracking
            Log.d(TAG, "Tracking started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting tracking", e)
            _serviceState.value = ServiceState.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 停止追踪 - 公开给ViewModel调用
     */
    fun stopTracking() {
        Log.d(TAG, "Stopping tracking")
        
        updateJob?.cancel()
        updateJob = null
        
        deviceTracker.clearAllDevices()
        pyiCloudManager?.clearSession()
        pyiCloudManager = null
        
        _serviceState.value = ServiceState.Idle
        _devices.value = emptyList()
        _authState.value = null
    }
    
    /**
     * 刷新设备 - 公开给ViewModel调用
     */
    suspend fun refreshDevices() {
        try {
            val manager = pyiCloudManager ?: return
            
            Log.d(TAG, "Refreshing devices")
            
            val result = manager.refreshDevices()
            if (result.isSuccess) {
                val devices = result.getOrNull()!!
                
                // 更新每个设备
                devices.forEach { newDevice ->
                    val oldDevice = deviceTracker.getDevice(newDevice.devicename)
                    
                    if (oldDevice != null) {
                        // 更新现有设备
                        deviceTracker.updateDeviceLocation(
                            newDevice.devicename,
                            newDevice.location,
                            "Refresh"
                        )
                    } else {
                        // 添加新设备
                        deviceTracker.addDevice(newDevice)
                    }
                }
                
                _devices.value = deviceTracker.getAllDevices()
                Log.d(TAG, "Devices refreshed: ${devices.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices", e)
        }
    }
    
    /**
     * 开始定时更新
     */
    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        
        updateJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 检查需要更新的设备
                    val devicesToUpdate = deviceTracker.getDevicesNeedingUpdate()
                    
                    if (devicesToUpdate.isNotEmpty()) {
                        Log.d(TAG, "${devicesToUpdate.size} devices need update")
                        refreshDevices()
                    }
                    
                    // 等待一段时间再检查
                    delay(15000) // 每15秒检查一次
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in update loop", e)
                }
            }
        }
    }
    
    /**
     * 暂停设备追踪 - 公开给ViewModel调用
     */
    fun pauseDevice(deviceName: String) {
        deviceTracker.pauseDevice(deviceName)
        _devices.value = deviceTracker.getAllDevices()
        Log.d(TAG, "Device $deviceName paused")
    }
    
    /**
     * 恢复设备追踪 - 公开给ViewModel调用
     */
    fun resumeDevice(deviceName: String) {
        deviceTracker.resumeDevice(deviceName)
        _devices.value = deviceTracker.getAllDevices()
        Log.d(TAG, "Device $deviceName resumed")
    }
    
    /**
     * 验证2FA代码
     */
    suspend fun validate2FACode(code: String): Result<Boolean> {
        val manager = pyiCloudManager ?: return Result.failure(Exception("Not authenticated"))
        
        val result = manager.validate2FACode(code)
        
        if (result.isSuccess && result.getOrNull() == true) {
            _authState.value = manager.getCurrentAuthState()
            _serviceState.value = ServiceState.Tracking
            
            // 继续启动追踪
            serviceScope.launch {
                refreshDevices()
                startPeriodicUpdates()
            }
        }
        
        return result
    }
    
    /**
     * 播放设备声音
     */
    suspend fun playDeviceSound(deviceName: String): Result<Boolean> {
        val manager = pyiCloudManager ?: return Result.failure(Exception("Not authenticated"))
        val device = deviceTracker.getDevice(deviceName) 
            ?: return Result.failure(Exception("Device not found"))
        
        return manager.playSound(device.deviceId)
    }
    
    /**
     * 获取追踪统计信息
     */
    fun getTrackingStats(): Map<String, Any> {
        return mapOf(
            "serviceState" to _serviceState.value,
            "deviceStats" to deviceTracker.getTrackingStats(),
            "zoneStats" to zoneManager.getZoneStats()
        )
    }
    
    /**
     * 加载配置
     */
    private fun loadConfiguration(): Configuration {
        try {
            val json = prefs.getString(KEY_CONFIG, null)
            if (json != null) {
                val config = gson.fromJson(json, Configuration::class.java)
                Log.d(TAG, "Configuration loaded from SharedPreferences")
                return config
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configuration", e)
        }
        
        // 返回默认配置
        Log.d(TAG, "Using default configuration")
        return createDefaultConfiguration()
    }
    
    /**
     * 保存配置
     */
    private fun saveConfiguration() {
        try {
            val json = gson.toJson(config)
            prefs.edit().putString(KEY_CONFIG, json).apply()
            Log.d(TAG, "Configuration saved to SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving configuration", e)
        }
    }
    
    /**
     * 创建默认配置
     */
    private fun createDefaultConfiguration(): Configuration {
        return Configuration(
            general = GeneralConfig(
                // 设备配置
                // 追踪间隔配置
                trackingMode = IC3Constants.TRACK_DEVICE,
                inzoneInterval = 60,
                maxInterval = 1800,
                exitZoneInterval = 120,
                mobileAppDeviceTrackersync = true,
                
                // GPS配置
                gpsAccuracyThreshold = 100,
                oldLocationThreshold = 180,
                oldLocationAdjustment = true,
                
                // 距离配置
                travelTimeInterval = 15,
                distanceMethodWaze = false,
                
                // Waze配置
                wazeRegion = "US",
                wazeMaxDistance = 1000,
                wazeMinDistance = 1,
                wazeRealtimeEnabled = true,
                wazeHistoryEnabled = true,
                wazeHistoryMaxDistance = 40,
                wazeHistoryTrackDirection = IC3Constants.TOWARDS,
                
                // 静止区域配置
                statZoneStillTime = 8,
                statZoneInzoneInterval = 30,
                
                // 通知配置
                logLevelEventsEnabled = false,
                displayZoneFriendlyName = true,
                displayGpsLatLong = false,
                unitOfMeasurement = "km",
                timeFormat = "12",
                
                // 其他
                centerInZone = false,
                discardPoorAccuracy = true,
                passthruZoneTime = 0
            ),
            zones = ZoneConfig(
                // 静止区域基准位置（默认为0,0，需要用户配置）
                statZoneBaseLatitude = 0.0,
                statZoneBaseLongitude = 0.0,
                statZoneRadius = 125,
                
                // 家区域（默认为0,0，需要用户配置）
                homeLatitude = 0.0,
                homeLongitude = 0.0,
                homeRadius = 100,
                
                // 区域配置
                passthruZoneTime = 0,
                inzoneInterval = 60
            )
        )
    }
    
    /**
     * 更新配置
     */
    fun updateConfiguration(newConfig: Configuration) {
        config = newConfig
        saveConfiguration()
        
        // 重新初始化组件
        if (::zoneManager.isInitialized) {
            zoneManager = ZoneManager(config.zones, config.general)
            
            // 重新创建Home区域
            if (config.zones.homeLatitude != 0.0 && config.zones.homeLongitude != 0.0) {
                zoneManager.createHomeZone(config.zones.homeLatitude, config.zones.homeLongitude)
            }
        }
        
        if (::deviceTracker.isInitialized) {
            deviceTracker = DeviceTracker(zoneManager, config.general)
        }
        
        Log.d(TAG, "Configuration updated")
    }
    
    /**
     * 获取当前配置
     */
    fun getCurrentConfiguration(): Configuration = config
}

/**
 * Service State - 服务状态
 */
sealed class ServiceState {
    object Idle : ServiceState()
    object Authenticating : ServiceState()
    object Requires2FA : ServiceState()
    object Tracking : ServiceState()
    data class Error(val message: String) : ServiceState()
}

/**
 * IC3 Helper - 辅助类，提供便捷的Service访问方法
 */
object IC3Helper {
    
    /**
     * 启动追踪
     */
    fun startTracking(context: Context, username: String, password: String) {
        val intent = Intent(context, IC3Service::class.java).apply {
            action = IC3Service.ACTION_START_TRACKING
            putExtra(IC3Service.EXTRA_USERNAME, username)
            putExtra(IC3Service.EXTRA_PASSWORD, password)
        }
        context.startService(intent)
    }
    
    /**
     * 停止追踪
     */
    fun stopTracking(context: Context) {
        val intent = Intent(context, IC3Service::class.java).apply {
            action = IC3Service.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }
    
    /**
     * 刷新设备
     */
    fun refreshDevices(context: Context) {
        val intent = Intent(context, IC3Service::class.java).apply {
            action = IC3Service.ACTION_REFRESH_DEVICES
        }
        context.startService(intent)
    }
    
    /**
     * 暂停设备
     */
    fun pauseDevice(context: Context, deviceName: String) {
        val intent = Intent(context, IC3Service::class.java).apply {
            action = IC3Service.ACTION_PAUSE_DEVICE
            putExtra(IC3Service.EXTRA_DEVICE_NAME, deviceName)
        }
        context.startService(intent)
    }
    
    /**
     * 恢复设备
     */
    fun resumeDevice(context: Context, deviceName: String) {
        val intent = Intent(context, IC3Service::class.java).apply {
            action = IC3Service.ACTION_RESUME_DEVICE
            putExtra(IC3Service.EXTRA_DEVICE_NAME, deviceName)
        }
        context.startService(intent)
    }
}
