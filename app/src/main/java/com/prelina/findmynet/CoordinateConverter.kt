package com.prelina.findmynet

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 坐标转换工具类
 * 
 * 将 Apple 返回的 WGS84 坐标 (GPS 坐标系) 转换为高德地图使用的 GCJ-02 坐标系
 * 
 * 坐标系说明：
 * - WGS84：国际标准，GPS 设备使用的坐标系，Apple 返回的就是这个
 * - GCJ-02：国家测绘局规定的坐标系，高德地图、腾讯地图等使用
 * 
 * 高德坐标转换 API 文档：
 * https://lbs.amap.com/api/webservice/guide/api/convert
 */
class CoordinateConverter {
    
    companion object {
        private const val TAG = "CoordinateConverter"
        
        // 高德坐标转换 API 地址
        private const val CONVERT_URL = "https://restapi.amap.com/v3/assistant/coordinate/convert"
        
        // 高德 API Key
        private const val AMAP_KEY = "YOUR_AMAP_KEY_HERE"  // 请替换为你的高德地图 API Key
        
        // 原始坐标系类型：gps (WGS84)
        private const val COORD_SYS = "gps"
        
        // 批量转换时的最大坐标数量（高德限制每次最多40个坐标点）
        private const val MAX_BATCH_SIZE = 40
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * 转换单个坐标
     * 
     * @param longitude 原始经度 (WGS84)
     * @param latitude 原始纬度 (WGS84)
     * @return 转换后的坐标对 (GCJ-02)，转换失败返回 null
     */
    suspend fun convert(longitude: Double, latitude: Double): Pair<Double, Double>? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, " 开始转换坐标: ($longitude, $latitude) WGS84 -> GCJ-02")
                
                // 构建请求 URL
                val url = "$CONVERT_URL?locations=$longitude,$latitude&coordsys=$COORD_SYS&key=$AMAP_KEY"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, " 坐标转换请求失败: ${response.code}")
                    return@withContext null
                }
                
                // 解析响应
                val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                val status = jsonResponse.get("status")?.asString
                
                if (status != "1") {
                    val info = jsonResponse.get("info")?.asString ?: "未知错误"
                    Log.e(TAG, " 坐标转换失败: $info")
                    return@withContext null
                }
                
                // 获取转换后的坐标
                val locations = jsonResponse.get("locations")?.asString
                if (locations.isNullOrEmpty()) {
                    Log.e(TAG, " 坐标转换返回空结果")
                    return@withContext null
                }
                
                // 解析坐标字符串 "lng,lat"
                val parts = locations.split(",")
                if (parts.size != 2) {
                    Log.e(TAG, " 坐标格式错误: $locations")
                    return@withContext null
                }
                
                val convertedLng = parts[0].toDoubleOrNull()
                val convertedLat = parts[1].toDoubleOrNull()
                
                if (convertedLng == null || convertedLat == null) {
                    Log.e(TAG, " 坐标解析失败: $locations")
                    return@withContext null
                }
                
                Log.d(TAG, " 坐标转换成功: ($longitude, $latitude) -> ($convertedLng, $convertedLat)")
                
                return@withContext Pair(convertedLng, convertedLat)
                
            } catch (e: Exception) {
                Log.e(TAG, " 坐标转换异常", e)
                return@withContext null
            }
        }
    }
    
    /**
     * 批量转换坐标
     * 
     * @param coordinates 坐标列表，每个元素是 (经度, 纬度) 对
     * @return 转换后的坐标列表，转换失败的坐标保持原值
     */
    suspend fun convertBatch(coordinates: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (coordinates.isEmpty()) {
            return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            try {
                // 如果坐标数量超过限制，分批处理
                if (coordinates.size > MAX_BATCH_SIZE) {
                    Log.d(TAG, "📦 批量转换 ${coordinates.size} 个坐标 (分批处理)")
                    
                    val results = mutableListOf<Pair<Double, Double>>()
                    coordinates.chunked(MAX_BATCH_SIZE).forEach { batch ->
                        val batchResults = convertBatchInternal(batch)
                        results.addAll(batchResults)
                    }
                    return@withContext results
                } else {
                    return@withContext convertBatchInternal(coordinates)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, " 批量坐标转换异常", e)
                // 转换失败时返回原坐标
                return@withContext coordinates
            }
        }
    }
    
    /**
     * 内部批量转换方法（处理单批）
     */
    private suspend fun convertBatchInternal(coordinates: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, " 批量转换 ${coordinates.size} 个坐标")
                
                // 构建坐标字符串 "lng1,lat1|lng2,lat2|lng3,lat3"
                val locationsStr = coordinates.joinToString("|") { "${it.first},${it.second}" }
                
                // 构建请求 URL
                val url = "$CONVERT_URL?locations=$locationsStr&coordsys=$COORD_SYS&key=$AMAP_KEY"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful || responseBody == null) {
                    Log.e(TAG, " 批量坐标转换请求失败: ${response.code}")
                    return@withContext coordinates // 返回原坐标
                }
                
                // 解析响应
                val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                val status = jsonResponse.get("status")?.asString
                
                if (status != "1") {
                    val info = jsonResponse.get("info")?.asString ?: "未知错误"
                    Log.e(TAG, " 批量坐标转换失败: $info")
                    return@withContext coordinates // 返回原坐标
                }
                
                // 获取转换后的坐标
                val locations = jsonResponse.get("locations")?.asString
                if (locations.isNullOrEmpty()) {
                    Log.e(TAG, " 批量坐标转换返回空结果")
                    return@withContext coordinates // 返回原坐标
                }
                
                // 解析坐标字符串 "lng1,lat1;lng2,lat2;lng3,lat3"
                val convertedCoords = locations.split(";").mapNotNull { coordStr ->
                    val parts = coordStr.split(",")
                    if (parts.size == 2) {
                        val lng = parts[0].toDoubleOrNull()
                        val lat = parts[1].toDoubleOrNull()
                        if (lng != null && lat != null) {
                            Pair(lng, lat)
                        } else null
                    } else null
                }
                
                // 检查转换结果数量是否匹配
                if (convertedCoords.size != coordinates.size) {
                    Log.w(TAG, " 转换结果数量不匹配: 期望 ${coordinates.size}, 实际 ${convertedCoords.size}")
                    return@withContext coordinates // 返回原坐标
                }
                
                Log.d(TAG, " 批量坐标转换成功: ${convertedCoords.size} 个坐标")
                
                return@withContext convertedCoords
                
            } catch (e: Exception) {
                Log.e(TAG, " 批量坐标转换异常", e)
                return@withContext coordinates // 返回原坐标
            }
        }
    }
    
    /**
     * 转换 DeviceLocation 对象
     * 
     * @param location 原始位置数据 (WGS84)
     * @return 转换后的位置数据 (GCJ-02)，转换失败返回原数据
     */
    suspend fun convertLocation(location: DeviceLocation): DeviceLocation {
        val converted = convert(location.longitude, location.latitude)
        
        return if (converted != null) {
            // 创建新的 DeviceLocation，保留其他字段
            DeviceLocation(
                latitude = converted.second,
                longitude = converted.first,
                altitude = location.altitude,
                horizontalAccuracy = location.horizontalAccuracy,
                verticalAccuracy = location.verticalAccuracy,
                timestamp = location.timestamp,
                positionType = location.positionType,
                locationFinished = location.locationFinished,
                isOld = location.isOld,
                isInaccurate = location.isInaccurate
            )
        } else {
            // 转换失败，返回原数据
            Log.w(TAG, " 坐标转换失败，使用原始坐标")
            location
        }
    }
    
    /**
     * 批量转换 DeviceData 列表中的坐标
     * 
     * @param devices 设备列表
     * @return 坐标转换后的设备列表
     */
    suspend fun convertDevices(devices: List<DeviceData>): List<DeviceData> {
        if (devices.isEmpty()) {
            return emptyList()
        }
        
        // 收集所有需要转换的坐标
        val devicesWithLocation = devices.filter { it.location != null && it.location.isValid() }
        
        if (devicesWithLocation.isEmpty()) {
            Log.d(TAG, "没有需要转换的坐标")
            return devices
        }
        
        Log.d(TAG, " 准备转换 ${devicesWithLocation.size} 个设备的坐标")
        
        // 提取原始坐标
        val originalCoords = devicesWithLocation.map { device ->
            val loc = device.location!!
            Pair(loc.longitude, loc.latitude)
        }
        
        // 批量转换
        val convertedCoords = convertBatch(originalCoords)
        
        // 创建坐标映射表
        val coordMap = devicesWithLocation.mapIndexed { index, device ->
            device.id to convertedCoords[index]
        }.toMap()
        
        // 更新设备列表
        return devices.map { device ->
            if (device.location != null && coordMap.containsKey(device.id)) {
                val converted = coordMap[device.id]!!
                device.copy(
                    location = DeviceLocation(
                        latitude = converted.second,
                        longitude = converted.first,
                        altitude = device.location.altitude,
                        horizontalAccuracy = device.location.horizontalAccuracy,
                        verticalAccuracy = device.location.verticalAccuracy,
                        timestamp = device.location.timestamp,
                        positionType = device.location.positionType,
                        locationFinished = device.location.locationFinished,
                        isOld = device.location.isOld,
                        isInaccurate = device.location.isInaccurate
                    )
                )
            } else {
                device
            }
        }
    }
}
