package com.prelina.findmynet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.MapView
import com.amap.api.maps2d.MapsInitializer
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.LatLngBounds
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.maps2d.model.MyLocationStyle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * MapActivity - 地图页面
 * 显示所有设备在地图上的位置，并在Bottom Sheet中显示设备列表
 * 使用高德地图 2D SDK (避免模拟器上的 OpenGL 问题)
 * 
 * 【定时刷新机制】
 * 模仿 iCloud3 V3 的持续定位机制:
 * - 应用在前台时，每30秒自动刷新一次设备位置
 * - 对于没有位置信息的设备，通过 shouldLocate=true 持续请求
 * - Apple 服务器会尝试唤醒设备并获取新位置
 */
class MapActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MapActivity"
        
        //  刷新间隔：30秒
        private const val REFRESH_INTERVAL_MS = 30_000L
        
        //  需要过滤的设备名称列表（不在地图和列表中显示）
        // 注意：设备名称必须与 Apple API 返回的完全一致（包括引号、空格等字符）
        private val FILTERED_DEVICE_NAMES = setOf<String>(
            // 示例：
            // "Device Name 1",
            // "Device Name 2",
        )
    }

    private lateinit var aMap: AMap
    private lateinit var mapView: MapView
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var findMyService: FindMyService
    private lateinit var iCloudService: ICloudService
    private lateinit var coordinateConverter: CoordinateConverter

    // UI组件
    private lateinit var toolbar: Toolbar
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvDeviceCount: TextView
    private lateinit var progressBar: ProgressBar

    // 地图标记
    private val markers = mutableMapOf<String, Marker>()
    
    //  定时刷新
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoRefreshEnabled = true  // 是否启用自动刷新
    private var refreshCount = 0  // 刷新次数计数器（用于日志）
    
    // 定时刷新任务
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (isAutoRefreshEnabled) {
                refreshCount++
                Log.d(TAG, " 自动刷新 #$refreshCount (模仿 iCloud3 V3 的持续定位机制)")
                refreshDevices()
                
                // 继续下一次定时刷新
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 高德地图 2D 隐私合规设置（必须在初始化地图之前调用）
        // 注意：2D 地图使用 MapsInitializer 的静态方法
        MapsInitializer.setApiKey("YOUR_AMAP_API_KEY") // 也可以在这里设置 Key
        
        setContentView(R.layout.activity_map)

        // 初始化服务 - 使用单例获取已登录的 ICloudService
        iCloudService = ICloudService.getInstance()
        findMyService = FindMyService(iCloudService)
        coordinateConverter = CoordinateConverter()

        // 初始化UI
        initViews()
        initMap(savedInstanceState)
        initBottomSheet()
        initRecyclerView()

        //  首次加载设备数据
        refreshDevices()
        
        Log.i(TAG, " MapActivity 已启动，将每 ${REFRESH_INTERVAL_MS / 1000} 秒自动刷新设备位置")
        Log.i(TAG, " 这模仿了 iCloud3 V3 的持续定位机制（iCloud3 每5秒刷新）")
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        
        //  启动定时刷新（应用进入前台时）
        startAutoRefresh()
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        
        //  停止定时刷新（应用进入后台时）
        stopAutoRefresh()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        
        //  确保停止定时刷新
        stopAutoRefresh()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    
    /**
     *  启动自动刷新
     * 模仿 iCloud3 V3 的 5 秒循环机制
     */
    private fun startAutoRefresh() {
        if (!isAutoRefreshEnabled) {
            isAutoRefreshEnabled = true
            refreshCount = 0
            Log.i(TAG, " 自动刷新已启动 (间隔: ${REFRESH_INTERVAL_MS / 1000}秒)")
            
            // 延迟 REFRESH_INTERVAL_MS 后开始第一次自动刷新
            // 不立即刷新，因为 onCreate 中已经刷新过一次
            handler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS)
        }
    }
    
    /**
     * 停止自动刷新
     */
    private fun stopAutoRefresh() {
        if (isAutoRefreshEnabled) {
            isAutoRefreshEnabled = false
            handler.removeCallbacks(autoRefreshRunnable)
            Log.i(TAG, " 自动刷新已停止 (共执行了 $refreshCount 次自动刷新)")
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        fabRefresh = findViewById(R.id.fabRefresh)
        recyclerView = findViewById(R.id.recyclerViewDevices)
        tvDeviceCount = findViewById(R.id.tvDeviceCount)
        progressBar = findViewById(R.id.progressBar)
        mapView = findViewById(R.id.mapView)

        // 设置Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 刷新按钮
        fabRefresh.setOnClickListener {
            refreshDevices()
        }
    }

    private fun initMap(savedInstanceState: Bundle?) {
        // 创建地图
        mapView.onCreate(savedInstanceState)
        
        // 获取地图对象
        aMap = mapView.map

        // 设置定位蓝点样式 (2D地图)
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
        myLocationStyle.interval(2000) // 定位间隔2秒
        aMap.setMyLocationStyle(myLocationStyle)
        
        // 启用定位蓝点
        aMap.setMyLocationEnabled(true)

        // 地图UI设置 (2D地图的设置方法)
        aMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
            isScaleControlsEnabled = true
        }

        // 标记点击事件
        aMap.setOnMarkerClickListener { marker ->
            val deviceId = marker.`object` as? String
            deviceId?.let {
                // 展开Bottom Sheet并滚动到对应设备
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                // TODO: 滚动到对应设备
            }
            false
        }
    }

    private fun initBottomSheet() {
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // 设置 Bottom Sheet 初始状态为半展开
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        
        // 设置是否可以通过拖拽折叠
        bottomSheetBehavior.isDraggable = true
        
        // Bottom Sheet回调
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // 可以在这里处理状态变化
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        Log.d(TAG, "Bottom Sheet 完全展开")
                    }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        Log.d(TAG, "Bottom Sheet 半展开")
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        Log.d(TAG, "Bottom Sheet 折叠")
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // 可以在这里处理滑动动画
            }
        })
    }

    private fun initRecyclerView() {
        deviceAdapter = DeviceAdapter(
            onDeviceClick = { device ->
                Log.d(TAG, "点击设备: ${device.name}")
            },
            onPlaySoundClick = { device ->
                playSound(device)
            },
            onShowOnMapClick = { device ->
                showDeviceOnMap(device)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MapActivity)
            adapter = deviceAdapter
        }
    }

    /**
     * 刷新设备数据
     * 模仿 iCloud3 V3 的 refresh_device() 方法:
     * - 设置 shouldLocate=true 触发 Apple 主动定位
     * - 对于没有位置的设备，Apple 会尝试唤醒设备
     * - 通过定时刷新持续获取最新位置
     */
    private fun refreshDevices() {
        lifecycleScope.launch {
            try {
                // 显示加载状态
                showLoading(true)
                tvDeviceCount.text = "正在刷新设备位置..."
                
                // 调试信息：检查登录状态（仅在第一次刷新时显示详细信息）
                if (refreshCount == 0) {
                    Log.i(TAG, "========== 检查登录状态 ==========")
                    Log.i(TAG, "findme_url: ${iCloudService.findme_url}")
                    Log.i(TAG, "sessionToken: ${iCloudService.sessionToken?.take(20)}...")
                    Log.i(TAG, "accountCountry: ${iCloudService.accountCountry}")
                    Log.i(TAG, "trustToken: ${iCloudService.trustToken?.take(20)}...")
                    Log.i(TAG, "===================================")
                } else {
                    Log.d(TAG, " 定时刷新 #$refreshCount")
                }

                // ⭐ 调用 FindMyService 获取设备数据
                // locateAllDevices=true 类似 iCloud3 的 shouldLocate=true
                val result = findMyService.refreshDevices(
                    locateAllDevices = true
                )

                result.onSuccess { response ->
                    Log.d(TAG, "📦 收到 ${response.devices.size} 个设备，准备过滤...")
                    
                    // 打印所有设备名称用于调试
                    response.devices.forEach { device ->
                        val inFilterList = device.name in FILTERED_DEVICE_NAMES
                        Log.d(TAG, "  设备: '${device.name}' - ${if (inFilterList) " 将被过滤" else " 保留"}")
                    }
                    
                    //  过滤不需要显示的设备
                    val filteredDevices = filterDevices(response.devices)
                    val filteredCount = response.devices.size - filteredDevices.size
                    
                    if (filteredCount > 0) {
                        val filteredNames = response.devices
                            .filter { it.name in FILTERED_DEVICE_NAMES }
                            .joinToString(", ") { it.name }
                        Log.d(TAG, " 已过滤 $filteredCount 个设备: $filteredNames")
                    }
                    
                    // 统计设备状态（使用过滤后的设备）
                    val totalDevices = filteredDevices.size
                    val onlineDevices = filteredDevices.count { it.location != null }
                    val offlineDevices = totalDevices - onlineDevices
                    
                    Log.i(TAG, " 成功获取 $totalDevices 个设备 (在线: $onlineDevices, 离线: $offlineDevices)")
                    
                    // 列出没有位置信息的设备（类似 iCloud3 的设备状态检查）
                    if (offlineDevices > 0) {
                        val offlineDeviceNames = filteredDevices
                            .filter { it.location == null }
                            .joinToString(", ") { it.name }
                        
                        Log.w(TAG, " 以下设备暂无位置信息: $offlineDeviceNames")
                        Log.i(TAG, "将在下次刷新时继续尝试获取位置")
                    }
                    
                    //  坐标转换：WGS84 (Apple GPS) -> GCJ-02 (高德地图)
                    if (onlineDevices > 0) {
                        Log.d(TAG, " 开始坐标转换 (WGS84 -> GCJ-02)...")
                        
                        // 转换前打印几个示例坐标
                        filteredDevices.filter { it.location != null }.take(3).forEach { device ->
                            val loc = device.location!!
                            Log.d(TAG, "  转换前 - ${device.name}: (${loc.longitude}, ${loc.latitude})")
                        }
                        
                        val convertedDevices = coordinateConverter.convertDevices(filteredDevices)
                        
                        // 转换后打印几个示例坐标
                        convertedDevices.filter { it.location != null }.take(3).forEach { device ->
                            val loc = device.location!!
                            Log.d(TAG, "  转换后 - ${device.name}: (${loc.longitude}, ${loc.latitude})")
                        }
                        
                        // 更新UI
                        updateDeviceList(convertedDevices)
                        updateMapMarkers(convertedDevices)
                    } else {
                        // 没有在线设备，直接更新UI
                        updateDeviceList(filteredDevices)
                        updateMapMarkers(filteredDevices)
                    }

                    // 更新设备数量显示
                    tvDeviceCount.text = "共 $totalDevices 个设备，$onlineDevices 个在线"

                    // 如果没有设备
                    if (totalDevices == 0) {
                        tvDeviceCount.text = "未找到任何设备"
                        if (refreshCount == 0) {  // 仅在第一次刷新时显示 Toast
                            Toast.makeText(this@MapActivity, "未找到任何设备", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure { error ->
                    Log.e(TAG, " 刷新设备失败", error)
                    tvDeviceCount.text = "加载失败"
                    
                    // 仅在手动刷新或前几次自动刷新时显示错误 Toast
                    if (refreshCount <= 1) {
                        Toast.makeText(
                            this@MapActivity,
                            "刷新失败: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, " 刷新设备异常", e)
                tvDeviceCount.text = "加载失败"
                
                // 仅在手动刷新或前几次自动刷新时显示错误 Toast
                if (refreshCount <= 1) {
                    Toast.makeText(
                        this@MapActivity,
                        "刷新失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * 过滤设备列表
     * 移除不需要显示的设备（既不在地图上显示，也不在列表中显示）
     */
    private fun filterDevices(devices: List<DeviceData>): List<DeviceData> {
        val filtered = devices.filter { device ->
            val shouldFilter = device.name in FILTERED_DEVICE_NAMES
            if (shouldFilter) {
                Log.d(TAG, " 过滤设备: ${device.name}")
            } else {
                // 如果没有在过滤列表中找到精确匹配，检查是否是引号字符问题
                // 将弯引号转换为普通引号
                val normalizedName = device.name.replace('\u2018', '\'').replace('\u2019', '\'')
                val shouldFilterNormalized = normalizedName in FILTERED_DEVICE_NAMES
                if (shouldFilterNormalized) {
                    Log.w(TAG, " 过滤设备（标准化后匹配）: ${device.name} → $normalizedName")
                    return@filter false
                }
            }
            !shouldFilter  // 返回 true 保留设备，false 过滤掉
        }
        
        Log.d(TAG, "📊 过滤结果: 原始 ${devices.size} 个设备 → 保留 ${filtered.size} 个设备")
        
        return filtered
    }

    /**
     * 更新设备列表
     */
    private fun updateDeviceList(devices: List<DeviceData>) {
        deviceAdapter.submitList(devices)
    }

    /**
     * 更新地图标记
     */
    private fun updateMapMarkers(devices: List<DeviceData>) {
        // 清除旧标记
        aMap.clear()
        markers.clear()

        val boundsBuilder = LatLngBounds.Builder()
        var hasValidLocation = false

        // 添加新标记
        devices.forEach { device ->
            device.location?.let { location ->
                if (location.isValid()) {
                    val position = LatLng(location.latitude, location.longitude)
                    
                    // 创建标记选项
                    val markerOptions = MarkerOptions()
                        .position(position)
                        .title(device.deviceDisplayName.ifEmpty { device.name })
                        .snippet("电量: ${device.batteryLevel}% | ${location.getFormattedTime()}")
                        .icon(getMarkerIcon(device.deviceClass))
                        .draggable(false)

                    // 添加标记到地图
                    val marker = aMap.addMarker(markerOptions)
                    marker?.`object` = device.id  // 使用反引号因为object是Kotlin关键字
                    marker?.let { markers[device.id] = it }

                    boundsBuilder.include(position)
                    hasValidLocation = true
                }
            }
        }

        // 调整地图视图以显示所有标记
        if (hasValidLocation) {
            try {
                val bounds = boundsBuilder.build()
                val padding = 100 // 边距（像素）
                aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            } catch (e: Exception) {
                Log.e(TAG, "调整地图视图失败", e)
            }
        }
    }

    /**
     * 根据设备类型获取标记图标
     */
    private fun getMarkerIcon(deviceClass: String): com.amap.api.maps2d.model.BitmapDescriptor {
        val hue = when (deviceClass.lowercase()) {
            "iphone" -> BitmapDescriptorFactory.HUE_BLUE
            "ipad" -> BitmapDescriptorFactory.HUE_GREEN
            "mac" -> BitmapDescriptorFactory.HUE_ORANGE
            "watch" -> BitmapDescriptorFactory.HUE_RED
            "airpods" -> BitmapDescriptorFactory.HUE_VIOLET
            else -> BitmapDescriptorFactory.HUE_AZURE
        }
        return BitmapDescriptorFactory.defaultMarker(hue)
    }

    /**
     * 在地图上显示设备
     */
    private fun showDeviceOnMap(device: DeviceData) {
        device.location?.let { location ->
            if (location.isValid()) {
                val position = LatLng(location.latitude, location.longitude)
                
                // 移动相机到设备位置
                aMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(position, 15f)
                )

                // 显示标记的信息窗口
                markers[device.id]?.showInfoWindow()

                // 收起Bottom Sheet
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            } else {
                Toast.makeText(
                    this,
                    "该设备位置不可用",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } ?: run {
            Toast.makeText(
                this,
                "该设备没有位置信息",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 播放设备声音
     */
    private fun playSound(device: DeviceData) {
        lifecycleScope.launch {
            try {
                Toast.makeText(
                    this@MapActivity,
                    "正在向 ${device.name} 发送播放声音请求...",
                    Toast.LENGTH_SHORT
                ).show()

                val result = findMyService.playSound(device.id)
                
                result.onSuccess {
                    Toast.makeText(
                        this@MapActivity,
                        "已成功向 ${device.name} 发送播放声音请求",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this@MapActivity,
                        "播放声音失败: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "播放声音异常", e)
                Toast.makeText(
                    this@MapActivity,
                    "播放声音失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 显示/隐藏加载状态
     */
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        fabRefresh.isEnabled = !show
    }
}
