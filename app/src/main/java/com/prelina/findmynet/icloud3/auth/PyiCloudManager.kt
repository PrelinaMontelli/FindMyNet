package com.prelina.findmynet.icloud3.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.prelina.findmynet.icloud3.IC3Constants
import com.prelina.findmynet.icloud3.models.AuthenticationState
import com.prelina.findmynet.icloud3.models.Device
import com.prelina.findmynet.icloud3.models.LocationData
import com.prelina.findmynet.icloud3.models.BatteryData
import com.prelina.findmynet.icloud3.models.DeviceStatus
import com.prelina.findmynet.icloud3.models.DeviceModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PyiCloud Manager - 完整移植自 pyicloud_ic3.py
 * 处理所有iCloud认证、会话管理和设备访问
 */
class PyiCloudManager(
    private val context: Context,
    private val username: String,
    private val password: String,
    private val appleServerLocation: String = "usa"
) {
    companion object {
        private const val TAG = "PyiCloudManager"
        private const val CLIENT_BUILD_NUMBER = "2021Project52"
        private const val CLIENT_MASTERING_NUMBER = "2021B29"
    }
    
    private val gson = Gson()
    private val cookieJar = PersistentCookieJar(context)
    
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d(TAG, "Request: ${request.method} ${request.url}")
            val response = chain.proceed(request)
            Log.d(TAG, "Response: ${response.code} ${response.message}")
            response
        }
        .build()
    
    // 会话信息
    private var clientId: String = "auth-${UUID.randomUUID().toString().lowercase()}"
    private var sessionToken: String? = null
    private var sessionId: String? = null
    private var scnt: String? = null
    private var dsid: String? = null
    private var trustToken: String? = null
    private var accountName: String = ""
    private var accountCountryCode: String = ""
    
    // 认证状态
    private var isAuthenticated = false
    var requires2FA = false
        private set
    var responseCode = 0
        private set
    private var lastResponseCode = 0
    private var authMethod = ""
    
    // Apple服务器端点
    private var homeEndpoint: String
    private var setupEndpoint: String
    private var authEndpoint: String
    
    // 设备数据
    private var findmeUrlRoot: String? = null
    private val devicesById = mutableMapOf<String, Device>()
    private val deviceIdByName = mutableMapOf<String, String>()
    
    // 会话数据存储
    private val sessionFile: File
    private val tokenFile: File
    
    init {
        // 设置Apple服务器端点
        val endpointSuffix = if (appleServerLocation.startsWith(".")) {
            "icloud.com$appleServerLocation"
        } else {
            "icloud.com"
        }
        
        homeEndpoint = IC3Constants.APPLE_SERVER_ENDPOINT["home"]!!.replace("icloud.com", endpointSuffix)
        setupEndpoint = IC3Constants.APPLE_SERVER_ENDPOINT["setup"]!!.replace("icloud.com", endpointSuffix)
        authEndpoint = IC3Constants.APPLE_SERVER_ENDPOINT["auth"]!!
        
        // 设置会话文件路径
        val cookieDir = File(context.filesDir, "icloud_cookies")
        if (!cookieDir.exists()) cookieDir.mkdirs()
        
        val cookieFilename = username.filter { it.isLetterOrDigit() }
        sessionFile = File(cookieDir, "$cookieFilename.session")
        tokenFile = File(cookieDir, "$cookieFilename.token")
        
        Log.d(TAG, "PyiCloudManager initialized for $username")
    }
    
    /**
     * 认证 - 主入口
     */
    suspend fun authenticate(): Result<AuthenticationState> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting authentication for $username")
            
            // 1. 尝试从会话文件加载
            loadSessionData()
            
            // 2. 验证Token
            if (sessionToken != null && validateToken()) {
                Log.d(TAG, "Token validation successful")
                authMethod = "ValidToken"
                isAuthenticated = true
                return@withContext Result.success(getCurrentAuthState())
            }
            
            // 3. 使用Token认证
            if (authenticateWithToken()) {
                Log.d(TAG, "Token authentication successful")
                authMethod = "Token"
                isAuthenticated = true
                return@withContext Result.success(getCurrentAuthState())
            }
            
            // 4. 使用密码认证
            if (authenticateWithPassword()) {
                Log.d(TAG, "Password authentication successful")
                authMethod = "Password"
                
                // 密码认证成功后需要重新获取Token
                if (authenticateWithToken()) {
                    isAuthenticated = true
                    return@withContext Result.success(getCurrentAuthState())
                }
            }
            
            // 认证失败
            val error = "Authentication failed: ${IC3Constants.HTTP_RESPONSE_CODES[responseCode]}"
            Log.e(TAG, error)
            Result.failure(Exception(error))
            
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 验证Token
     */
    private suspend fun validateToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$setupEndpoint/validate"
            val request = Request.Builder()
                .url(url)
                .post("null".toRequestBody("application/json".toMediaType()))
                .addHeader("Origin", homeEndpoint)
                .addHeader("Referer", homeEndpoint)
                .apply {
                    sessionId?.let { addHeader("X-Apple-ID-Session-Id", it) }
                    scnt?.let { addHeader("scnt", it) }
                }
                .build()
            
            val response = client.newCall(request).execute()
            responseCode = response.code
            
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Token validation error", e)
            false
        }
    }
    
    /**
     * 使用Token认证
     */
    private suspend fun authenticateWithToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (sessionToken == null) {
                Log.d(TAG, "No session token available")
                return@withContext false
            }
            
            val url = "$setupEndpoint/accountLogin"
            val data = JsonObject().apply {
                addProperty("accountCountryCode", accountCountryCode)
                addProperty("dsWebAuthToken", sessionToken!!)
                addProperty("extended_login", true)
                addProperty("trustToken", trustToken ?: "")
                addProperty("appName", "iCloud3")
            }
            
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                .addHeader("Origin", homeEndpoint)
                .addHeader("Referer", homeEndpoint)
                .build()
            
            val response = client.newCall(request).execute()
            responseCode = response.code
            
            if (response.isSuccessful) {
                val responseData = gson.fromJson(response.body?.string(), JsonObject::class.java)
                parseAuthResponse(responseData)
                saveSessionData()
                return@withContext true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Token authentication error", e)
            false
        }
    }
    
    /**
     * 使用密码认证（标准方式）
     */
    private suspend fun authenticateWithPassword(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Authenticating with password")
            
            // 首先尝试SRP认证
            val srpResult = authenticateWithPasswordSRP()
            if (srpResult) {
                authMethod = "PasswordSRP"
                return@withContext true
            }
            
            // SRP失败，使用传统方式
            Log.d(TAG, "SRP failed, using traditional password auth")
            
            val url = "$authEndpoint/signin"
            val params = "?isRememberMeEnabled=true"
            
            val data = JsonObject().apply {
                addProperty("accountName", username)
                addProperty("password", password)
                addProperty("rememberMe", true)
                add("trustTokens", gson.toJsonTree(trustToken?.let { listOf(it) } ?: emptyList<String>()))
            }
            
            val request = Request.Builder()
                .url(url + params)
                .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Apple-OAuth-Client-Id", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .addHeader("X-Apple-OAuth-Client-Type", "firstPartyAuth")
                .addHeader("X-Apple-OAuth-Redirect-URI", "https://www.icloud.com")
                .addHeader("X-Apple-OAuth-Require-Grant-Code", "true")
                .addHeader("X-Apple-OAuth-Response-Mode", "web_message")
                .addHeader("X-Apple-OAuth-Response-Type", "code")
                .addHeader("X-Apple-OAuth-State", clientId)
                .addHeader("X-Apple-Widget-Key", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .build()
            
            val response = client.newCall(request).execute()
            responseCode = response.code
            lastResponseCode = responseCode
            
            if (response.isSuccessful || responseCode == 409) {
                val responseData = gson.fromJson(response.body?.string(), JsonObject::class.java)
                parseAuthResponse(responseData)
                saveSessionData()
                
                // 检查是否需要2FA
                requires2FA = check2FANeeded(responseData)
                
                authMethod = "Password"
                return@withContext responseCode in listOf(200, 409)
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Password authentication error", e)
            false
        }
    }
    
    /**
     * 使用SRP (Secure Remote Password) 认证
     * 完整实现pyicloud_ic3.py中的authenticate_with_password_srp方法
     */
    private suspend fun authenticateWithPasswordSRP(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Authenticating with Password SRP")
            
            // Step 1: 创建SRP User并生成公钥A
            Log.d(TAG, "SRP Step 1: Generate client public key A")
            val srpUser = SRPUser(
                username = username,
                password = password,
                hashAlg = SRPHashAlgorithm.SHA256,
                ngType = SRPNGType.NG_2048
            )
            
            val (uname, A) = srpUser.startAuthentication()
            val aBase64 = Base64.encodeToString(A, Base64.NO_WRAP)
            
            // 发送初始化请求到服务器
            val initUrl = "$authEndpoint/signin/init"
            val initData = JsonObject().apply {
                addProperty("a", aBase64)
                addProperty("accountName", uname)
                add("protocols", gson.toJsonTree(listOf("s2k", "s2k_fo")))
            }
            
            val initRequest = Request.Builder()
                .url(initUrl)
                .post(gson.toJson(initData).toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json, text/javascript")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Apple-OAuth-Client-Id", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .addHeader("X-Apple-OAuth-Client-Type", "firstPartyAuth")
                .addHeader("X-Apple-OAuth-Redirect-URI", "https://www.icloud.com")
                .addHeader("X-Apple-OAuth-Require-Grant-Code", "true")
                .addHeader("X-Apple-OAuth-Response-Mode", "web_message")
                .addHeader("X-Apple-OAuth-Response-Type", "code")
                .addHeader("X-Apple-OAuth-State", clientId)
                .addHeader("X-Apple-Widget-Key", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .build()
            
            val initResponse = client.newCall(initRequest).execute()
            if (!initResponse.isSuccessful) {
                Log.e(TAG, "SRP init failed: ${initResponse.code}")
                return@withContext false
            }
            
            // Step 2: 解析服务器响应，获取salt、b、c、iteration、protocol
            Log.d(TAG, "SRP Step 2: Process server challenge")
            val body = gson.fromJson(initResponse.body?.string(), JsonObject::class.java)
            
            if (!body.has("salt")) {
                Log.e(TAG, "SRP response missing 'salt'")
                return@withContext false
            }
            
            val saltBase64 = body.get("salt").asString
            val bBase64 = body.get("b").asString
            val c = body.get("c").asString
            val iterations = body.get("iteration").asInt
            val protocol = body.get("protocol").asString
            
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val b = Base64.decode(bBase64, Base64.NO_WRAP)
            
            // Step 3: 使用PBKDF2派生密钥
            Log.d(TAG, "SRP Step 3: Derive encryption key with PBKDF2")
            val encryptedPassword = derivePasswordKey(password, protocol, salt, iterations)
            
            // 创建临时SRP User with encrypted password
            val srpUserEncrypted = SRPUser(
                username = username,
                password = encryptedPassword.decodeToString(),
                hashAlg = SRPHashAlgorithm.SHA256,
                ngType = SRPNGType.NG_2048,
                bytesA = A  // 使用相同的A
            )
            
            // 处理服务器挑战，生成M1
            val m1 = srpUserEncrypted.processChallenge(salt, b)
            if (m1 == null) {
                Log.e(TAG, "SRP challenge processing failed (safety check)")
                return@withContext false
            }
            
            val m2 = srpUserEncrypted.M ?: return@withContext false
            
            val m1Base64 = Base64.encodeToString(m1, Base64.NO_WRAP)
            val m2Base64 = Base64.encodeToString(m2, Base64.NO_WRAP)
            
            // Step 4: 发送完成请求到服务器
            Log.d(TAG, "SRP Step 4: Send completion request to server")
            val completeUrl = "$authEndpoint/signin/complete"
            val completeParams = "?isRememberMeEnabled=true"
            
            val completeData = JsonObject().apply {
                addProperty("accountName", uname)
                addProperty("c", c)
                addProperty("m1", m1Base64)
                addProperty("m2", m2Base64)
                addProperty("rememberMe", true)
                add("trustTokens", gson.toJsonTree(trustToken?.let { listOf(it) } ?: emptyList<String>()))
            }
            
            val completeRequest = Request.Builder()
                .url(completeUrl + completeParams)
                .post(gson.toJson(completeData).toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Apple-OAuth-Client-Id", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .addHeader("X-Apple-OAuth-Client-Type", "firstPartyAuth")
                .addHeader("X-Apple-OAuth-Redirect-URI", "https://www.icloud.com")
                .addHeader("X-Apple-OAuth-Require-Grant-Code", "true")
                .addHeader("X-Apple-OAuth-Response-Mode", "web_message")
                .addHeader("X-Apple-OAuth-Response-Type", "code")
                .addHeader("X-Apple-OAuth-State", clientId)
                .addHeader("X-Apple-Widget-Key", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .build()
            
            val completeResponse = client.newCall(completeRequest).execute()
            responseCode = completeResponse.code
            lastResponseCode = responseCode
            
            if (completeResponse.isSuccessful || responseCode == 409) {
                val responseData = gson.fromJson(completeResponse.body?.string(), JsonObject::class.java)
                parseAuthResponse(responseData)
                saveSessionData()
                
                // 检查是否需要2FA
                requires2FA = check2FANeeded(responseData)
                
                Log.d(TAG, "SRP authentication successful")
                return@withContext responseCode in listOf(200, 409)
            }
            
            Log.e(TAG, "SRP authentication failed: ${completeResponse.code}")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "SRP authentication error", e)
            false
        }
    }
    
    /**
     * 使用PBKDF2派生密码密钥
     * 对应Python中的SrpPassword.encode()方法
     */
    private fun derivePasswordKey(password: String, protocol: String, salt: ByteArray, iterations: Int): ByteArray {
        // Step 1: SHA256(password)
        val md = MessageDigest.getInstance("SHA-256")
        val passwordHash = md.digest(password.toByteArray(Charsets.UTF_8))
        
        // Step 2: 根据protocol选择hexdigest或digest
        val passwordDigest = if (protocol == "s2k_fo") {
            // hexdigest: 转换为十六进制字符串再编码
            passwordHash.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
        } else {
            // digest: 直接使用字节
            passwordHash
        }
        
        // Step 3: PBKDF2-HMAC-SHA256
        val keyLength = 32 // 256 bits
        val spec = PBEKeySpec(
            passwordDigest.toString(Charsets.ISO_8859_1).toCharArray(),
            salt,
            iterations,
            keyLength * 8
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * 使用密码认证（旧版，保留作为备用）
     */
    private suspend fun authenticateWithPasswordLegacy(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Authenticating with password")
            
            val url = "$authEndpoint/signin"
            val params = "?isRememberMeEnabled=true"
            
            val data = JsonObject().apply {
                addProperty("accountName", username)
                addProperty("password", password)
                addProperty("rememberMe", true)
                add("trustTokens", gson.toJsonTree(trustToken?.let { listOf(it) } ?: emptyList<String>()))
            }
            
            val request = Request.Builder()
                .url(url + params)
                .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Apple-OAuth-Client-Id", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .addHeader("X-Apple-OAuth-Client-Type", "firstPartyAuth")
                .addHeader("X-Apple-OAuth-Redirect-URI", "https://www.icloud.com")
                .addHeader("X-Apple-OAuth-Require-Grant-Code", "true")
                .addHeader("X-Apple-OAuth-Response-Mode", "web_message")
                .addHeader("X-Apple-OAuth-Response-Type", "code")
                .addHeader("X-Apple-OAuth-State", clientId)
                .addHeader("X-Apple-Widget-Key", "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d")
                .build()
            
            val response = client.newCall(request).execute()
            responseCode = response.code
            lastResponseCode = responseCode
            
            if (response.isSuccessful || responseCode == 409) {
                val responseData = gson.fromJson(response.body?.string(), JsonObject::class.java)
                parseAuthResponse(responseData)
                saveSessionData()
                
                // 检查是否需要2FA
                requires2FA = check2FANeeded(responseData)
                
                return@withContext responseCode in listOf(200, 409)
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Password authentication error", e)
            false
        }
    }
    
    /**
     * 解析认证响应
     */
    private fun parseAuthResponse(data: JsonObject) {
        try {
            // 解析dsInfo
            data.getAsJsonObject("dsInfo")?.let { dsInfo ->
                dsid = dsInfo.get("dsid")?.asString
                accountName = dsInfo.get("fullName")?.asString ?: ""
            }
            
            // 解析webservices
            data.getAsJsonObject("webservices")?.let { webservices ->
                webservices.getAsJsonObject("findme")?.let { findme ->
                    findmeUrlRoot = findme.get("url")?.asString
                }
            }
            
            // 保存会话信息
            data.get("sessionToken")?.asString?.let { sessionToken = it }
            data.get("sessionId")?.asString?.let { sessionId = it }
            data.get("scnt")?.asString?.let { scnt = it }
            data.get("trustToken")?.asString?.let { trustToken = it }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing auth response", e)
        }
    }
    
    /**
     * 检查是否需要2FA
     */
    private fun check2FANeeded(data: JsonObject): Boolean {
        try {
            val dsInfo = data.getAsJsonObject("dsInfo") ?: return false
            val hsaVersion = dsInfo.get("hsaVersion")?.asInt ?: 0
            val hsaChallengeRequired = data.get("hsaChallengeRequired")?.asBoolean ?: false
            val hsaTrustedBrowser = data.get("hsaTrustedBrowser")?.asBoolean ?: true
            
            return hsaVersion == 2 && (hsaChallengeRequired || !hsaTrustedBrowser)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking 2FA requirement", e)
            return false
        }
    }
    
    /**
     * 验证2FA代码
     */
    suspend fun validate2FACode(code: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Validating 2FA code")
            
            val url = "$authEndpoint/verify/trusteddevice/securitycode"
            val data = JsonObject().apply {
                putJsonObject("securityCode") {
                    put("code", code)
                }
            }
            
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply {
                    sessionId?.let { addHeader("X-Apple-ID-Session-Id", it) }
                    scnt?.let { addHeader("scnt", it) }
                }
                .build()
            
            val response = client.newCall(request).execute()
            responseCode = response.code
            
            if (response.isSuccessful) {
                // 信任会话
                trustSession()
                
                // 重新认证获取Token
                authenticateWithToken()
                
                requires2FA = false
                isAuthenticated = true
                
                Result.success(true)
            } else {
                Result.failure(Exception("Invalid verification code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "2FA validation error", e)
            Result.failure(e)
        }
    }
    
    /**
     * 信任会话
     */
    private suspend fun trustSession() = withContext(Dispatchers.IO) {
        try {
            val url = "$authEndpoint/2sv/trust"
            val request = Request.Builder()
                .url(url)
                .get()
                .apply {
                    sessionId?.let { addHeader("X-Apple-ID-Session-Id", it) }
                    scnt?.let { addHeader("scnt", it) }
                }
                .build()
            
            client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Error trusting session", e)
        }
    }
    
    /**
     * 刷新设备数据
     */
    suspend fun refreshDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try {
            if (!isAuthenticated || findmeUrlRoot == null) {
                return@withContext Result.failure(Exception("Not authenticated"))
            }
            
            Log.d(TAG, "Refreshing device data")
            
            val url = "$findmeUrlRoot/fmipservice/client/web/refreshClient"
            val data = JsonObject().apply {
                putJsonObject("clientContext") {
                    put("fmly", true)
                    put("shouldLocate", true)
                    put("selectedDevice", "all")
                    putJsonObject("deviceListVersion") {
                        put("clientVersion", "2.1")
                    }
                }
            }
            
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                .addHeader("Origin", homeEndpoint)
                .addHeader("Referer", homeEndpoint)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseData = gson.fromJson(response.body?.string(), JsonObject::class.java)
                val devices = parseDevicesResponse(responseData)
                
                Log.d(TAG, "Refreshed ${devices.size} devices")
                Result.success(devices)
            } else {
                Result.failure(Exception("Failed to refresh devices: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析设备响应数据
     */
    private fun parseDevicesResponse(data: JsonObject): List<Device> {
        val devices = mutableListOf<Device>()
        
        try {
            val content = data.getAsJsonObject("content") ?: return devices
            val deviceArray = content.getAsJsonArray("devices") ?: return devices
            
            for (element in deviceArray) {
                val deviceData = element.asJsonObject
                
                try {
                    val device = parseDevice(deviceData)
                    devices.add(device)
                    
                    // 保存设备映射
                    devicesById[device.deviceId] = device
                    deviceIdByName[device.devicename] = device.deviceId
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing device", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing devices response", e)
        }
        
        return devices
    }
    
    /**
     * 解析单个设备数据
     */
    private fun parseDevice(data: JsonObject): Device {
        val deviceId = data.get("id")?.asString ?: ""
        val name = data.get("name")?.asString ?: ""
        val deviceDisplayName = data.get("deviceDisplayName")?.asString ?: name
        val deviceClass = data.get("deviceClass")?.asString ?: ""
        val rawDeviceModel = data.get("rawDeviceModel")?.asString ?: ""
        val deviceModel = data.get("deviceModel")?.asString ?: ""
        
        // 解析位置
        val location = data.getAsJsonObject("location")?.let { loc ->
            LocationData(
                latitude = loc.get("latitude")?.asDouble ?: 0.0,
                longitude = loc.get("longitude")?.asDouble ?: 0.0,
                altitude = loc.get("altitude")?.asDoubleOrNull(),
                horizontalAccuracy = loc.get("horizontalAccuracy")?.asDouble ?: 0.0,
                verticalAccuracy = loc.get("verticalAccuracy")?.asDoubleOrNull(),
                positionType = loc.get("positionType")?.asString ?: "",
                timestamp = loc.get("timeStamp")?.asLong ?: 0
            )
        } ?: LocationData()
        
        // 解析电池
        val battery = BatteryData(
            level = ((data.get("batteryLevel")?.asDouble ?: 0.0) * 100).toInt(),
            status = data.get("batteryStatus")?.asString ?: "",
            source = IC3Constants.ICLOUD
        )
        
        // 解析状态
        val status = DeviceStatus(
            statusCode = data.get("deviceStatus")?.asInt ?: 200,
            lowPowerMode = data.get("lowPowerMode")?.asBoolean ?: false,
            lostModeCapable = data.get("lostModeCapable")?.asBoolean ?: false
        )
        
        // 设备型号信息
        val modelInfo = DeviceModelInfo(
            rawModel = rawDeviceModel,
            model = deviceModel,
            modelDisplayName = deviceDisplayName,
            deviceClass = deviceClass
        )
        
        return Device(
            devicename = name.lowercase().replace(" ", "_"),
            fname = name,
            deviceId = deviceId,
            modelInfo = modelInfo,
            appleAccount = username,
            location = location,
            battery = battery,
            status = status,
            lastUpdate = System.currentTimeMillis(),
            locationSource = IC3Constants.ICLOUD,
            dataSource = IC3Constants.ICLOUD
        )
    }
    
    /**
     * 获取设备
     */
    fun getDevice(deviceId: String): Device? = devicesById[deviceId]
    
    fun getDeviceByName(deviceName: String): Device? {
        val deviceId = deviceIdByName[deviceName] ?: return null
        return devicesById[deviceId]
    }
    
    fun getAllDevices(): List<Device> = devicesById.values.toList()
    
    /**
     * 播放声音
     */
    suspend fun playSound(deviceId: String, subject: String = "Find My iPhone Alert"): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                if (findmeUrlRoot == null) {
                    return@withContext Result.failure(Exception("Not authenticated"))
                }
                
                val url = "$findmeUrlRoot/fmipservice/client/web/playSound"
                val data = JsonObject().apply {
                    put("device", deviceId)
                    put("subject", subject)
                }
                
                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(data).toRequestBody("application/json".toMediaType()))
                    .build()
                
                val response = client.newCall(request).execute()
                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sound", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 保存会话数据
     */
    private fun saveSessionData() {
        try {
            val sessionData = JsonObject().apply {
                put("client_id", clientId)
                sessionToken?.let { put("session_token", it) }
                sessionId?.let { put("session_id", it) }
                scnt?.let { put("scnt", it) }
                trustToken?.let { put("trust_token", it) }
                put("account_country", accountCountryCode)
            }
            
            sessionFile.writeText(gson.toJson(sessionData))
            Log.d(TAG, "Session data saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session data", e)
        }
    }
    
    /**
     * 加载会话数据
     */
    private fun loadSessionData() {
        try {
            if (!sessionFile.exists()) return
            
            val sessionData = gson.fromJson(sessionFile.readText(), JsonObject::class.java)
            
            clientId = sessionData.get("client_id")?.asString ?: clientId
            sessionToken = sessionData.get("session_token")?.asString
            sessionId = sessionData.get("session_id")?.asString
            scnt = sessionData.get("scnt")?.asString
            trustToken = sessionData.get("trust_token")?.asString
            accountCountryCode = sessionData.get("account_country")?.asString ?: ""
            
            Log.d(TAG, "Session data loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading session data", e)
        }
    }
    
    /**
     * 清除会话
     */
    fun clearSession() {
        sessionToken = null
        sessionId = null
        scnt = null
        trustToken = null
        isAuthenticated = false
        requires2FA = false
        
        try {
            sessionFile.delete()
            Log.d(TAG, "Session cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session", e)
        }
    }
    
    /**
     * 获取当前认证状态
     */
    fun getCurrentAuthState() = AuthenticationState(
        isAuthenticated = isAuthenticated,
        requires2FA = requires2FA,
        sessionToken = sessionToken,
        accountName = accountName,
        error = if (!isAuthenticated) IC3Constants.HTTP_RESPONSE_CODES[responseCode] else null
    )
    
    // JsonObject扩展函数
    private fun JsonObject.put(key: String, value: String?) {
        if (value != null) addProperty(key, value)
    }
    
    private fun JsonObject.put(key: String, value: Boolean) {
        addProperty(key, value)
    }
    
    private fun JsonObject.put(key: String, value: Int) {
        addProperty(key, value)
    }
    
    private fun JsonObject.putJsonObject(key: String, block: JsonObject.() -> Unit) {
        val obj = JsonObject()
        obj.block()
        add(key, obj)
    }
    
    private fun com.google.gson.JsonElement.asDoubleOrNull(): Double? {
        return try {
            asDouble
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Persistent Cookie Jar - 持久化Cookie管理
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private val cookieFile = File(context.filesDir, "icloud_cookies.json")
    private val gson = Gson()
    
    init {
        loadCookies()
    }
    
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
        saveCookies()
    }
    
    private fun saveCookies() {
        try {
            val cookieData = mutableMapOf<String, List<Map<String, String>>>()
            
            cookieStore.forEach { (host, cookies) ->
                cookieData[host] = cookies.map { cookie ->
                    mapOf(
                        "name" to cookie.name,
                        "value" to cookie.value,
                        "domain" to cookie.domain,
                        "path" to cookie.path,
                        "expiresAt" to cookie.expiresAt.toString()
                    )
                }
            }
            
            cookieFile.writeText(gson.toJson(cookieData))
        } catch (e: Exception) {
            Log.e("CookieJar", "Error saving cookies", e)
        }
    }
    
    private fun loadCookies() {
        try {
            if (!cookieFile.exists()) return
            
            val cookieData = gson.fromJson(
                cookieFile.readText(),
                Map::class.java
            ) as? Map<String, List<Map<String, String>>> ?: return
            
            cookieData.forEach { (host, cookies) ->
                val cookieList = cookies.mapNotNull { cookieMap ->
                    try {
                        Cookie.Builder()
                            .name(cookieMap["name"] ?: return@mapNotNull null)
                            .value(cookieMap["value"] ?: return@mapNotNull null)
                            .domain(cookieMap["domain"] ?: return@mapNotNull null)
                            .path(cookieMap["path"] ?: "/")
                            .build()
                    } catch (e: Exception) {
                        null
                    }
                }
                
                if (cookieList.isNotEmpty()) {
                    cookieStore[host] = cookieList
                }
            }
        } catch (e: Exception) {
            Log.e("CookieJar", "Error loading cookies", e)
        }
    }
    
    fun clear() {
        cookieStore.clear()
        cookieFile.delete()
    }
}
