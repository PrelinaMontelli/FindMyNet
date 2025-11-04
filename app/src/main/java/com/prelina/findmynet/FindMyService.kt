package com.prelina.findmynet

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * FindMyService - 处理iCloud Find My设备定位功能
 * 
 * 参考iCloud3 v3的实现:
 * - PyiCloud_AppleAcctDevices.refresh_device()
 * - 调用 /fmipservice/client/web/refreshClient 端点
 * - 使用登录后的凭证: findme_url, session_token, trust_token, account_country
 */
class FindMyService(private val iCloudService: ICloudService) {
    
    companion object {
        private const val TAG = "FindMyService"
        private const val REFRESH_ENDPOINT = "/fmipservice/client/web/refreshClient"
        private const val PLAYSOUND_ENDPOINT = "/fmipservice/client/web/playSound"
        private const val MESSAGE_ENDPOINT = "/fmipservice/client/web/sendMessage"
    }

    //  关键修复: 使用 iCloudService 的 client，它有 CookieJar
    // 而不是创建新的 client（会丢失所有 cookies）
    private val gson = Gson()

    /**
     * 刷新所有设备的位置数据
     * 
     * @param locateAllDevices true=定位所有设备(包括家庭共享), false=仅定位账户拥有者的设备
     * @param deviceId 指定设备ID只刷新该设备，null则刷新所有
     * @param retryCount 重试次数（内部使用，用于处理 450 错误）
     * @return DevicesResponse包含所有设备数据
     */
    suspend fun refreshDevices(
        locateAllDevices: Boolean = true,
        deviceId: String? = null,
        retryCount: Int = 0
    ): Result<DevicesResponse> = withContext(Dispatchers.IO) {
        try {
            // 检查必要的凭证
            val findmeUrl = iCloudService.findme_url
            val sessionToken = iCloudService.sessionToken
            val accountCountry = iCloudService.accountCountry
            val trustToken = iCloudService.trustToken

            if (findmeUrl.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("findme_url未设置，请先登录"))
            }
            if (sessionToken.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("session_token未设置，请先登录"))
            }

            Log.d(TAG, "刷新设备位置: locateAll=$locateAllDevices, deviceId=$deviceId, retry=$retryCount")

            //  关键修复1: 添加查询参数（参考 iCloud3 v3 的 self.params）
            val clientBuildNumber = "2021Project52"
            val clientMasteringNumber = "2021B29"
            val clientId = iCloudService.getClientIdForParams() // 只用后半部分
            
            // 构建完整URL with 查询参数（参考 pyicloud_session.py）
            val fullUrl = buildString {
                append(findmeUrl)
                append(REFRESH_ENDPOINT)
                append("?clientBuildNumber=$clientBuildNumber")
                append("&clientMasteringNumber=$clientMasteringNumber")
                append("&ckjsBuildVersion=17DProjectDev77")
                append("&clientId=$clientId")
            }

            Log.d(TAG, "完整请求URL: $fullUrl")

            // 构建请求数据 - 完全参考iCloud3 v3的数据结构
            // data = {"clientContext":{
            //             "fmly": locate_all_devices,
            //             "shouldLocate": True,
            //             "selectedDevice": device_id,  # None or device_id string
            //             "deviceListVersion": 1, },
            //         "accountCountryCode": self.PyiCloud.session_data_token.get("account_country"),
            //         "dsWebAuthToken": self.PyiCloud.session_data_token.get("session_token"),
            //         "trustToken": self.PyiCloud.session_data_token.get("trust_token", ""),
            //         "extended_login": True,}
            
            val clientContext = mutableMapOf<String, Any>(
                "fmly" to locateAllDevices,           // Family Sharing设备
                "shouldLocate" to true,                // 是否请求定位
                "deviceListVersion" to 1
            )
            
            // selectedDevice: null 或具体的 device_id，不能是字符串 "all"
            deviceId?.let { clientContext["selectedDevice"] = it }
            
            val requestData = mutableMapOf<String, Any>(
                "clientContext" to clientContext,
                "dsWebAuthToken" to sessionToken,          // 必需: session token
                "extended_login" to true
            )

            // 添加可选字段（按照 iCloud3 的方式）
            accountCountry?.let { requestData["accountCountryCode"] = it }
            if (!trustToken.isNullOrEmpty()) {
                requestData["trustToken"] = trustToken
            }

            val jsonBody = gson.toJson(requestData)
            Log.d(TAG, "📤 请求数据完整内容:")
            Log.d(TAG, jsonBody)

            // 构建HTTP请求
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(fullUrl)  //  使用带查询参数的完整 URL
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Origin", iCloudService.iCloudUrl)
                .header("Referer", "${iCloudService.iCloudUrl}/")
                .apply {
                    // 添加所有session headers
                    iCloudService.sessionId?.let { header("X-Apple-ID-Session-Id", it) }
                    iCloudService.scnt?.let { header("scnt", it) }
                }
                .build()

            Log.i(TAG, "========== 发送设备刷新请求 ==========")
            Log.i(TAG, "URL: $fullUrl")
            Log.i(TAG, "Headers: X-Apple-ID-Session-Id=${iCloudService.sessionId}, scnt=${iCloudService.scnt}")
            Log.i(TAG, " shouldLocate: true (请求 Apple 主动定位设备)")
            Log.i(TAG, "RetryCnt: $retryCount")
            Log.i(TAG, "=========================================")

            //  关键修复2: 使用 iCloudService 的 client（带 CookieJar）
            val response = iCloudService.executeRequest(request)
            val responseBody = response.body?.string() ?: ""

            Log.i(TAG, "📥 响应码: ${response.code}, Body长度: ${responseBody.length}")
            
            // 📋 打印响应体前 1000 字符用于调试
            if (responseBody.isNotEmpty()) {
                Log.d(TAG, "📥 响应体前 1000 字符:")
                Log.d(TAG, responseBody.take(1000))
            }

            when (response.code) {
                200 -> {
                    // 成功获取设备数据
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    
                    // 📊 打印 content 数组的内容
                    val contentArray = jsonResponse.getAsJsonArray("content")
                    Log.i(TAG, "📊 content 数组大小: ${contentArray?.size()}")
                    
                    // 解析设备数据
                    val content = jsonResponse.getAsJsonArray("content")
                    val devices = mutableListOf<DeviceData>()
                    
                    content?.forEachIndexed { index, element ->
                        try {
                            val deviceJson = element.asJsonObject
                            
                            // 📋 打印每个设备的 location 字段
                            val deviceName = deviceJson.get("name")?.asString ?: "未知"
                            val locationElement = deviceJson.get("location")
                            
                            Log.d(TAG, " 设备 #$index: $deviceName")
                            Log.d(TAG, "   location 字段存在: ${locationElement != null}")
                            Log.d(TAG, "   location 是否为 null: ${locationElement?.isJsonNull}")
                            Log.d(TAG, "   location 是否为对象: ${locationElement?.isJsonObject}")
                            
                            if (locationElement != null && !locationElement.isJsonNull && locationElement.isJsonObject) {
                                val locationJson = locationElement.asJsonObject
                                Log.d(TAG, "   location 对象大小: ${locationJson.size()}")
                                Log.d(TAG, "   location 内容: $locationJson")
                            }
                            
                            val device = parseDeviceData(deviceJson)
                            devices.add(device)
                            Log.d(TAG, " 解析设备: ${device.name}, 有位置: ${device.location != null}")
                        } catch (e: Exception) {
                            Log.e(TAG, " 解析设备 #$index 数据失败", e)
                        }
                    }

                    Log.i(TAG, "成功获取 ${devices.size} 个设备")
                    Result.success(DevicesResponse(
                        devices = devices,
                        statusCode = jsonResponse.get("statusCode")?.asString,
                        serverTimestamp = jsonResponse.get("serverTimestamp")?.asLong
                    ))
                }
                
                // 参考 iCloud3 v3: AUTHENTICATION_NEEDED_421_450_500
                421, 450, 500 -> {
                    // 需要重新认证（参考 pyicloud_session.py 的处理逻辑）
                    Log.w(TAG, "收到认证错误 ${response.code}，需要重新认证")
                    
                    if (retryCount < 2) {
                        Log.i(TAG, "自动重新认证并重试 (尝试 ${retryCount + 1}/2)")
                        
                        // 调用 authenticateWithToken 刷新会话
                        val refreshSuccess = iCloudService.refreshSessionForRetry()
                        
                        if (refreshSuccess) {
                            // 延迟后重试
                            kotlinx.coroutines.delay(1000)
                            return@withContext refreshDevices(locateAllDevices, deviceId, retryCount + 1)
                        } else {
                            Log.e(TAG, "会话刷新失败")
                            Result.failure(Exception("认证失败 (${response.code})，会话刷新失败"))
                        }
                    } else {
                        Log.e(TAG, "重试次数已用尽，认证失败")
                        Result.failure(Exception("认证失败 (${response.code})，已重试 $retryCount 次"))
                    }
                }
                
                501 -> {
                    // Service Not Available
                    Log.e(TAG, "iCloud服务不可用 (501)")
                    Log.e(TAG, "响应内容: $responseBody")
                    Result.failure(Exception("iCloud定位服务不可用"))
                }
                
                401 -> {
                    // Unauthorized - 需要重新登录
                    Log.e(TAG, "认证失败 (401)，需要重新登录")
                    Log.e(TAG, "响应内容: $responseBody")
                    Result.failure(Exception("认证失败，请重新登录"))
                }
                
                503 -> {
                    // Connection Error
                    Log.e(TAG, "服务器连接错误 (503)")
                    Log.e(TAG, "响应内容: $responseBody")
                    Result.failure(Exception("服务器连接错误，请稍后重试"))
                }
                
                else -> {
                    Log.e(TAG, "未知错误: ${response.code}")
                    Log.e(TAG, "响应内容: $responseBody")
                    Result.failure(Exception("请求失败: ${response.code}, $responseBody"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "刷新设备失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析设备数据JSON为DeviceData对象
     * 
     * 参考 iCloud3 V3 的 is_location_data_available:
     * - 检查 LOCATION 是否存在
     * - 检查 LOCATION 是否为 {} (空对象)
     * - 检查 LOCATION 是否为 None (null)
     */
    private fun parseDeviceData(json: JsonObject): DeviceData {
        val id = json.get("id")?.asString ?: ""
        val name = json.get("name")?.asString ?: "未知设备"
        
        //  关键修复: 安全解析 location 字段（参考 iCloud3 V3 的 is_location_data_available）
        // 检查: LOCATION not in device_data or device_data[LOCATION] == {} or device_data[LOCATION] is None
        val locationElement = json.get("location")
        val location = if (locationElement != null 
            && !locationElement.isJsonNull 
            && locationElement.isJsonObject) {
            
            val locationJson = locationElement.asJsonObject
            
            // 检查是否为空对象 {}
            if (locationJson.size() == 0) {
                Log.d(TAG, "设备 $name 的 location 为空对象，跳过位置解析")
                null
            } else {
                try {
                    DeviceLocation(
                        latitude = locationJson.get("latitude")?.asDouble ?: 0.0,
                        longitude = locationJson.get("longitude")?.asDouble ?: 0.0,
                        altitude = locationJson.get("altitude")?.asDouble,
                        horizontalAccuracy = locationJson.get("horizontalAccuracy")?.asDouble ?: 0.0,
                        verticalAccuracy = locationJson.get("verticalAccuracy")?.asDouble,
                        timestamp = locationJson.get("timeStamp")?.asLong ?: 0L,
                        positionType = locationJson.get("positionType")?.asString,
                        locationFinished = locationJson.get("locationFinished")?.asBoolean ?: false,
                        isOld = locationJson.get("isOld")?.asBoolean ?: false,
                        isInaccurate = locationJson.get("isInaccurate")?.asBoolean ?: false
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "解析设备 $name 的位置信息失败: ${e.message}")
                    null
                }
            }
        } else {
            Log.d(TAG, "设备 $name 没有位置信息 (location 为 null 或不存在)")
            null
        }

        // 解析电池信息（安全处理）
        val batteryLevel = try {
            json.get("batteryLevel")?.asDouble?.times(100)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
        val batteryStatus = try {
            val statusElement = json.get("batteryStatus")
            if (statusElement != null && !statusElement.isJsonNull) {
                statusElement.asString
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }

        // 解析设备状态（安全处理 JsonNull）
        val deviceStatus = try {
            json.get("deviceStatus")?.asInt ?: 0
        } catch (e: Exception) {
            0
        }
        
        val deviceClass = try {
            val element = json.get("deviceClass")
            if (element != null && !element.isJsonNull) element.asString else ""
        } catch (e: Exception) {
            ""
        }
        
        val deviceModel = try {
            val element = json.get("deviceModel")
            if (element != null && !element.isJsonNull) element.asString else ""
        } catch (e: Exception) {
            ""
        }
        
        val rawDeviceModel = try {
            val element = json.get("rawDeviceModel")
            if (element != null && !element.isJsonNull) element.asString else ""
        } catch (e: Exception) {
            ""
        }
        
        val deviceDisplayName = try {
            val element = json.get("deviceDisplayName")
            if (element != null && !element.isJsonNull) element.asString else name
        } catch (e: Exception) {
            name
        }
        
        val modelDisplayName = try {
            val element = json.get("modelDisplayName")
            if (element != null && !element.isJsonNull) element.asString else deviceModel
        } catch (e: Exception) {
            deviceModel
        }

        // 其他信息
        val lowPowerMode = json.get("lowPowerMode")?.asBoolean ?: false
        val fmlyShare = json.get("fmlyShare")?.asBoolean ?: false
        val thisDevice = json.get("thisDevice")?.asBoolean ?: false

        // 📋 调试日志：打印设备名称相关字段
        Log.d(TAG, "📋 设备名称字段:")
        Log.d(TAG, "   name: $name")
        Log.d(TAG, "   deviceDisplayName: $deviceDisplayName")
        Log.d(TAG, "   modelDisplayName: $modelDisplayName")
        Log.d(TAG, "   rawDeviceModel: $rawDeviceModel")

        return DeviceData(
            id = id,
            name = name,
            deviceClass = deviceClass,
            deviceModel = deviceModel,
            rawDeviceModel = rawDeviceModel,
            deviceDisplayName = deviceDisplayName,
            modelDisplayName = modelDisplayName,
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus,
            deviceStatus = deviceStatus,
            location = location,
            lowPowerMode = lowPowerMode,
            fmlyShare = fmlyShare,
            thisDevice = thisDevice
        )
    }

    /**
     * 播放设备声音
     */
    suspend fun playSound(
        deviceId: String,
        subject: String = "Find My iPhone Alert"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val findmeUrl = iCloudService.findme_url ?: return@withContext Result.failure(
                Exception("findme_url未设置")
            )
            
            val url = "$findmeUrl$PLAYSOUND_ENDPOINT"
            
            val requestData = mapOf(
                "device" to deviceId,
                "subject" to subject
            )
            
            val jsonBody = gson.toJson(requestData)
            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            
            //  使用 iCloudService 的 client（带 cookies）
            val response = iCloudService.executeRequest(request)
            
            when (response.code) {
                200, 205 -> Result.success(true)
                else -> Result.failure(Exception("播放声音失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放声音失败", e)
            Result.failure(e)
        }
    }
}

/**
 * 设备响应数据
 */
data class DevicesResponse(
    val devices: List<DeviceData>,
    val statusCode: String?,
    val serverTimestamp: Long?
)

/**
 * 单个设备数据
 */
data class DeviceData(
    val id: String,
    val name: String,
    val deviceClass: String,
    val deviceModel: String,
    val rawDeviceModel: String,
    val deviceDisplayName: String,
    val modelDisplayName: String,
    val batteryLevel: Int,
    val batteryStatus: String,
    val deviceStatus: Int,
    val location: DeviceLocation?,
    val lowPowerMode: Boolean,
    val fmlyShare: Boolean,
    val thisDevice: Boolean
)

/**
 * 设备位置数据
 */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val horizontalAccuracy: Double,
    val verticalAccuracy: Double?,
    val timestamp: Long,
    val positionType: String?,
    val locationFinished: Boolean,
    val isOld: Boolean,
    val isInaccurate: Boolean
) {
    /**
     * 获取格式化的时间戳
     */
    fun getFormattedTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
    
    /**
     * 位置是否可用
     */
    fun isValid(): Boolean {
        return latitude != 0.0 && longitude != 0.0 && !isOld && !isInaccurate
    }
}
