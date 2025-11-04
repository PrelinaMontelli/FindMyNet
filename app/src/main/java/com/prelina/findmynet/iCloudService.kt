package com.prelina.findmynet

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * iCloud 服务客户端
 * 基于 https://github.com/ElyaConrad/iCloud-API 实现
 * 单例模式 - 保证登录状态在整个应用中共享
 * 
 * 新增功能（参考 iCloud3 v3）:
 * - Session 持久化（自动保存/恢复会话）
 * - 450 错误自动重新认证并重试
 * - Cookie 持久化（已通过 PersistentCookieJar 实现）
 */
class ICloudService private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ICloudService? = null
        
        fun getInstance(): ICloudService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ICloudService().also { INSTANCE = it }
            }
        }
        
        /**
         * 初始化 SessionManager（需要 Context）
         */
        fun initSessionManager(context: android.content.Context) {
            val instance = getInstance()
            instance.sessionManager = SessionManager(context)
            println(" SessionManager 已初始化: ${instance.sessionManager != null}")
        }
    }
    
    private val gson = Gson()
    private val cookieJar = PersistentCookieJar()
    
    // SessionManager（会话持久化）
    private var sessionManager: SessionManager? = null
    
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    // 会话信息
    internal var sessionToken: String? = null
    internal var sessionId: String? = null
    internal var scnt: String? = null
    private var dsid: String? = null
    private var webservices: JsonObject? = null
    private var accountInfo: JsonObject? = null
    internal var accountCountry: String? = null
    private var accountName: String? = null
    private var accountLocked: Boolean = false
    internal var findme_url: String? = null
    
    // 2FA 状态
    private var requiresTwoFactor = false
    internal var trustToken: String? = null
    
    // 客户端配置（参考 main.js）
    private val clientId: String = generateClientId()
    private val widgetKey = "83545bf919730e51dbfba24e7e8a78d2"
    private val clientBuildNumber = "2018Project35"
    private val clientMasteringNumber = "2018B29"
    private val locale = "zh_CN"
    private val language = "zh-cn"
    internal val iCloudUrl = "https://www.icloud.com.cn"  // 中国区iCloud
    private val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    
    private fun generateClientId(): String {
        return java.util.UUID.randomUUID().toString().uppercase()
    }
    
    /**
     * 获取用于查询参数的 clientId（去掉 "auth-" 前缀，参考 iCloud3 v3）
     */
    fun getClientIdForParams(): String {
        return clientId.removePrefix("auth-")
    }
    
    /**
     * 执行请求（供 FindMyService 使用，确保使用相同的 client 和 cookies）
     */
    fun executeRequest(request: Request): Response {
        return client.newCall(request).execute()
    }
    
    /**
     * 登录到 iCloud
     * 参考 setup.js 的 getAuthToken 和 accountLogin
     * 
     * 参考 iCloud3 v3 的模式：
     * 1. 在登录前自动尝试加载并验证保存的会话
     * 2. 如果会话有效，直接使用（跳过密码登录）
     * 3. 如果会话无效或不存在，执行完整的登录流程
     */
    suspend fun login(email: String, password: String, retryCount: Int = 3): LoginResult = withContext(Dispatchers.IO) {
        var lastError: String? = null
        
        // 步骤 0: 尝试恢复保存的会话（参考 iCloud3 v3 的 _setup_PyiCloudSession）
        println("=== 步骤 0: 检查保存的会话 ===")
        if (sessionManager != null) {
            val sessionRestored = tryRestoreSession(email, password)
            if (sessionRestored) {
                println(" 会话恢复成功，验证会话有效性...")
                
                // 验证 session token 是否仍然有效（参考 iCloud3 v3 的 _validate_token）
                val tokenValid = authenticateWithToken()
                if (tokenValid) {
                    println(" 保存的会话仍然有效，跳过密码登录")
                    return@withContext LoginResult(
                        success = true,
                        requiresTwoFactor = false
                    )
                } else {
                    println(" 保存的会话已过期，将使用密码登录")
                    // 清除无效会话
                    clearSavedSession()
                }
            } else {
                println(" 没有找到有效的保存会话，将使用密码登录")
            }
        }
        
        // 重试机制 - Apple 可能临时限流
        repeat(retryCount) { attempt ->
            try {
                if (attempt > 0) {
                    val waitTime = (attempt * 5000L) // 5秒、10秒、15秒
                    println("⏳ 等待 ${waitTime/1000} 秒后重试 (${attempt+1}/$retryCount)...")
                    kotlinx.coroutines.delay(waitTime)
                }
                
                println("=== 开始 iCloud 登录流程 ===")
                println("步骤 1/3: 获取认证令牌...")
                
                // 第一步：获取认证令牌
                val authResult = getAuthToken(email, password)
                if (!authResult.success) {
                    // 如果是 503 错误且还有重试次数，继续重试
                    if (authResult.errorMessage?.contains("503") == true && attempt < retryCount - 1) {
                        lastError = authResult.errorMessage
                        println(" 收到 503 错误，将自动重试...")
                        return@repeat
                    }
                    
                    return@withContext LoginResult(
                        success = false,
                        requiresTwoFactor = authResult.requiresTwoFactor,
                        errorMessage = authResult.errorMessage
                    )
                }
                
                println("步骤 2/3: 账户登录...")
                
                // 第二步：账户登录
                val loginSuccess = accountLogin(authResult.token!!)
                if (!loginSuccess) {
                    return@withContext LoginResult(
                        success = false,
                        errorMessage = "账户登录失败"
                    )
                }
                
                println("步骤 2.5/3: 使用 Token 认证（刷新会话）...")
                
                // 重要：参考 iCloud3 v3，在密码登录后需要再次用 token 认证
                // 这一步是必需的，否则会收到 450 错误
                val tokenAuthSuccess = authenticateWithToken()
                if (!tokenAuthSuccess) {
                    println(" Token 认证失败，但继续执行...")
                }
                
                println("步骤 3/3: 获取 Push Token...")
                
                // 第三步：获取 Push Token（可选，但能提高兼容性）
                try {
                    getPushToken()
                } catch (e: Exception) {
                    println(" Push Token 获取失败（可忽略）: ${e.message}")
                }
                
                println("=== 登录成功 ===")
                
                // 保存会话到 SharedPreferences（参考 iCloud3 v3 的 session_data）
                saveSessionToStorage(email, password)
                
                return@withContext LoginResult(
                    success = true,
                    requiresTwoFactor = requiresTwoFactor
                )
                
            } catch (e: Exception) {
                println(" 登录异常 (尝试 ${attempt+1}/$retryCount): ${e.message}")
                e.printStackTrace()
                lastError = e.message ?: "未知错误"
                
                // 如果还有重试次数，延迟后重试
                if (attempt < retryCount - 1) {
                    delay(1000)
                    // 继续下一次循环
                }
            }
        }
        
        // 所有重试都失败
        return@withContext LoginResult(
            success = false,
            errorMessage = lastError ?: "登录失败，已重试 $retryCount 次"
        )
    }
    
    /**
     * 获取认证令牌（参考 setup.js 的 getAuthToken）
     */
    private fun getAuthToken(email: String, password: String): AuthResult {
        // 使用与 Node.js 参考代码完全一致的 User-Agent
        val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/603.3.1 (KHTML, like Gecko) Version/10.1.2 Safari/603.3.1"
        
        // 构建客户端信息（参考 setup.js line 16-22）
        val xAppleIFDClientInfo = mapOf(
            "U" to userAgent,
            "L" to locale,
            "Z" to "GMT+02:00",  // 使用与参考代码一致的时区
            "V" to "1.1",
            "F" to ""
        )
        
        val loginData = mapOf(
            "accountName" to email,
            "password" to password,
            "rememberMe" to true,
            "trustTokens" to emptyList<String>()
        )
        
        val requestBody = gson.toJson(loginData).toRequestBody("application/json".toMediaType())
        
        // 按照参考代码的顺序添加请求头（setup.js line 30-38）
        val request = Request.Builder()
            .url("https://idmsa.apple.com.cn/appleauth/auth/signin")  // 使用 .cn 域名
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Referer", "https://idmsa.apple.com.cn/appleauth/auth/signin")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("User-Agent", userAgent)
            .addHeader("Origin", "https://idmsa.apple.com.cn")
            .addHeader("X-Apple-Widget-Key", widgetKey)
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-Apple-I-FD-Client-Info", gson.toJson(xAppleIFDClientInfo))
            .addHeader("Accept-Language", "$language,en;q=0.9")
            .addHeader("Accept-Encoding", "gzip, deflate, br")
            .addHeader("Connection", "keep-alive")
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        println("认证响应码: ${response.code}")
        
        // 打印响应头用于调试
        println("📋 响应头:")
        response.headers.forEach { (name, value) ->
            println("  $name: ${value.take(50)}${if(value.length > 50) "..." else ""}")
        }
        
        return when (response.code) {
            200 -> {
                // 保存会话信息（参考 HEADER_DATA）
                sessionToken = response.header("X-Apple-Session-Token")
                sessionId = response.header("X-Apple-ID-Session-Id")
                scnt = response.header("scnt")
                accountCountry = response.header("X-Apple-ID-Account-Country")  // 重要：保存账户国家代码
                trustToken = response.header("X-Apple-TwoSV-Trust-Token")  // 保存信任令牌
                
                println(" Token: ${sessionToken?.take(20)}...")
                println(" Session ID: $sessionId")
                println(" scnt: $scnt")
                println(" Account Country: $accountCountry")
                
                // 检查是否需要 2FA（参考 main.js line 227）
                responseBody?.let {
                    try {
                        val jsonResponse = gson.fromJson(it, JsonObject::class.java)
                        val authType = jsonResponse.get("authType")?.asString
                        
                        if (authType == "hsa2") {
                            requiresTwoFactor = true
                            println(" 需要双因素认证")
                            return AuthResult(
                                success = false,
                                requiresTwoFactor = true,
                                errorMessage = "需要双因素认证"
                            )
                        }
                    } catch (e: Exception) {
                        println("解析响应失败: ${e.message}")
                    }
                }
                
                AuthResult(success = true, token = sessionToken)
            }
            409 -> {
                // 409 Conflict - 通常表示需要额外验证或账户状态异常
                println(" 409 - 账户需要验证")
                
                // 保存关键的会话头信息（参考 HEADER_DATA）
                sessionToken = response.header("X-Apple-Session-Token")
                sessionId = response.header("X-Apple-ID-Session-Id")
                scnt = response.header("scnt")
                accountCountry = response.header("X-Apple-ID-Account-Country")  // 重要！
                trustToken = response.header("X-Apple-TwoSV-Trust-Token")
                
                println("📋 Session Token: ${sessionToken?.take(20)}...")
                println("📋 Session ID: $sessionId")
                println("📋 scnt: $scnt")
                println("📋 Account Country: $accountCountry")
                
                // 先打印原始响应体
                println(" 原始响应体: $responseBody")
                
                // 尝试解析响应体
                responseBody?.let { body ->
                    try {
                        // 先尝试解析为 JsonElement 以处理不同类型
                        val jsonElement = gson.fromJson(body, com.google.gson.JsonElement::class.java)
                        
                        // 如果是字符串类型，可能是简单的文本响应
                        if (jsonElement.isJsonPrimitive && jsonElement.asJsonPrimitive.isString) {
                            val message = jsonElement.asString
                            println(" 字符串响应: $message")
                            
                            // 根据响应内容判断是否需要 2FA
                            if (message.contains("hsa2", ignoreCase = true) || 
                                message.contains("two factor", ignoreCase = true) ||
                                message.contains("双因素", ignoreCase = true)) {
                                requiresTwoFactor = true
                                println(" 需要双因素认证 (2FA)")
                                return AuthResult(
                                    success = false,
                                    requiresTwoFactor = true,
                                    errorMessage = "需要双因素认证码"
                                )
                            }
                            
                            return AuthResult(
                                success = false,
                                errorMessage = message
                            )
                        }
                        
                        // 如果是 JSON 对象
                        if (jsonElement.isJsonObject) {
                            val jsonResponse = jsonElement.asJsonObject
                            println(" JSON响应: $jsonResponse")
                            
                            // 检查是否需要双因素认证
                            val authType = jsonResponse.get("authType")?.asString
                            val serviceErrors = jsonResponse.getAsJsonArray("serviceErrors")
                            
                            if (authType == "hsa2" || serviceErrors?.any { 
                                it.asJsonObject.get("code")?.asString == "-22993" 
                            } == true) {
                                requiresTwoFactor = true
                                println(" 需要双因素认证 (2FA)")
                                return AuthResult(
                                    success = false,
                                    requiresTwoFactor = true,
                                    errorMessage = "需要双因素认证码"
                                )
                            }
                            
                            // 其他错误
                            val errorMsg = serviceErrors?.firstOrNull()
                                ?.asJsonObject?.get("message")?.asString 
                                ?: "账户需要额外验证"
                            
                            println(" 错误信息: $errorMsg")
                            
                            return AuthResult(
                                success = false,
                                errorMessage = errorMsg
                            )
                        }
                        
                        // 未知格式
                        AuthResult(
                            success = false,
                            errorMessage = "服务器返回未知格式: $jsonElement"
                        )
                    } catch (e: Exception) {
                        println(" 解析 409 响应失败: ${e.message}")
                        e.printStackTrace()
                        
                        // 由于我们已经有了会话信息，可能是 2FA
                        // 检查响应头中的 X-Apple-TwoSV-Trust-Eligible
                        val twoSvEligible = response.header("X-Apple-TwoSV-Trust-Eligible")
                        if (twoSvEligible == "true" && sessionToken != null) {
                            requiresTwoFactor = true
                            println(" 根据响应头判断需要双因素认证 (2FA)")
                            return AuthResult(
                                success = false,
                                requiresTwoFactor = true,
                                errorMessage = "需要双因素认证码"
                            )
                        }
                        
                        AuthResult(
                            success = false,
                            errorMessage = "账户验证失败: ${e.message}"
                        )
                    }
                } ?: run {
                    // 没有响应体，但有会话信息，可能需要 2FA
                    val twoSvEligible = response.header("X-Apple-TwoSV-Trust-Eligible")
                    if (twoSvEligible == "true" && sessionToken != null) {
                        requiresTwoFactor = true
                        println(" 根据响应头判断需要双因素认证 (2FA)")
                        AuthResult(
                            success = false,
                            requiresTwoFactor = true,
                            errorMessage = "需要双因素认证码"
                        )
                    } else {
                        AuthResult(
                            success = false,
                            errorMessage = "账户需要验证"
                        )
                    }
                }
            }
            503 -> {
                println(" 503 - Apple 服务器限流")
                println(" 响应体: ${responseBody?.take(200)}")
                println(" 提示: Apple 可能正在进行反爬虫检测")
                println(" 建议:")
                println("   1. 等待几分钟后重试")
                println("   2. 检查账号是否在其他设备登录过多")
                println("   3. 尝试使用 VPN 更换 IP 地址")
                println("   4. 确认账号状态正常（在 iCloud.com 网页能否登录）")
                AuthResult(
                    success = false,
                    errorMessage = "Apple 服务器限流 (503)。建议: 1) 等待后重试 2) 尝试更换网络 3) 检查账号状态"
                )
            }
            401, 403 -> {
                println(" ${response.code} - 认证失败")
                AuthResult(
                    success = false,
                    errorMessage = "账号或密码错误"
                )
            }
            else -> {
                println(" 错误 ${response.code}: $responseBody")
                AuthResult(
                    success = false,
                    errorMessage = "登录失败 (${response.code})"
                )
            }
        }
    }
    
    /**
     * 账户登录（参考 setup.js 的 accountLogin）
     */
    /**
     * 账户登录（参考 setup.js 的 accountLogin 和 pyicloud_ic3.py 的 _authenticate_with_token）
     */
    private fun accountLogin(authToken: String): Boolean {
        // 参考 _authenticate_with_token，包含账户国家代码和信任令牌
        val authData = mutableMapOf<String, Any>(
            "dsWebAuthToken" to authToken,
            "extended_login" to true,
            "appName" to "iCloud3"  // 添加应用名称
        )
        
        // 添加账户国家代码（如果存在）
        accountCountry?.let { authData["accountCountryCode"] = it }
        
        // 添加信任令牌（如果存在）
        trustToken?.let { authData["trustToken"] = it }
        
        val requestBody = gson.toJson(authData).toRequestBody("text/plain".toMediaType())
        
        val request = Request.Builder()
            .url("https://setup.icloud.com.cn/setup/ws/1/accountLogin?clientBuildNumber=$clientBuildNumber&clientId=$clientId&clientMasteringNumber=$clientMasteringNumber")
            .post(requestBody)
            .addHeader("Content-Type", "text/plain")
            .addHeader("Accept", "*/*")
            .addHeader("Referer", "https://www.icloud.com.cn/")
            .addHeader("Origin", "https://www.icloud.com.cn")
            .addHeader("User-Agent", userAgent)
            .build()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
        
        println("账户登录响应码: ${response.code}")
        
        if (response.isSuccessful && responseBody != null) {
            try {
                accountInfo = gson.fromJson(responseBody, JsonObject::class.java)
                
                // 保存 dsInfo 信息（参考 _authenticate_with_token）
                accountInfo?.getAsJsonObject("dsInfo")?.let { dsInfo ->
                    dsid = dsInfo.get("dsid")?.asString
                    accountName = dsInfo.get("fullName")?.asString
                    accountLocked = dsInfo.get("locked")?.asBoolean ?: false
                    
                    println(" DSID: $dsid")
                    println(" 账户名: $accountName")
                    println(" 账户锁定: $accountLocked")
                }
                
                // 保存 webservices 信息
                webservices = accountInfo?.getAsJsonObject("webservices")
                if (webservices != null) {
                    // 提取 findme URL
                    webservices?.getAsJsonObject("findme")?.get("url")?.asString?.let { findmeUrl ->
                        findme_url = findmeUrl
                        println(" FindMe URL: $findmeUrl")
                    }
                    println(" Webservices 加载成功")
                }
                
                // 检查 items 中的 2FA 状态（参考 _authenticate_with_token）
                accountInfo?.getAsJsonObject("items")?.let { items ->
                    val hsaTrustedBrowser = items.get("hsaTrustedBrowser")?.asBoolean ?: false
                    val hsaChallengeRequired = items.get("hsaChallengeRequired")?.asBoolean ?: false
                    println(" hsaTrustedBrowser: $hsaTrustedBrowser")
                    println(" hsaChallengeRequired: $hsaChallengeRequired")
                }
                
                return true
            } catch (e: Exception) {
                println(" 解析账户信息失败: ${e.message}")
                e.printStackTrace()
            }
        } else {
            println(" 账户登录失败: ${responseBody?.take(200)}")
        }
        
        return false
    }
    
    /**
     * 使用 Token 认证（参考 pyicloud_ic3.py 的 _authenticate_with_token）
     * 这一步在密码登录后是必需的，用于刷新会话并避免 450 错误
     */
    private fun authenticateWithToken(): Boolean {
        try {
            println("=== 使用 Token 认证 ===")
            
            if (sessionToken.isNullOrEmpty()) {
                println(" session_token 为空，无法认证")
                return false
            }
            
            // 构建认证数据（与 accountLogin 相同的数据结构）
            val authData = mutableMapOf<String, Any>(
                "dsWebAuthToken" to sessionToken!!,
                "extended_login" to true,
                "appName" to "iCloud3"
            )
            
            // 添加账户国家代码（如果存在）
            accountCountry?.let { authData["accountCountryCode"] = it }
            
            // 添加信任令牌（如果存在）
            trustToken?.let { authData["trustToken"] = it }
            
            val requestBody = gson.toJson(authData).toRequestBody("text/plain".toMediaType())
            
            val request = Request.Builder()
                .url("https://setup.icloud.com.cn/setup/ws/1/accountLogin?clientBuildNumber=$clientBuildNumber&clientId=$clientId&clientMasteringNumber=$clientMasteringNumber")
                .post(requestBody)
                .addHeader("Content-Type", "text/plain")
                .addHeader("Accept", "*/*")
                .addHeader("Referer", "https://www.icloud.com.cn/")
                .addHeader("Origin", "https://www.icloud.com.cn")
                .addHeader("User-Agent", userAgent)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            println("Token 认证响应码: ${response.code}")
            
            if (response.isSuccessful && responseBody != null) {
                try {
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    
                    // 更新 dsInfo（如果有）
                    jsonResponse.getAsJsonObject("dsInfo")?.let { dsInfo ->
                        dsid = dsInfo.get("dsid")?.asString ?: dsid
                        accountName = dsInfo.get("fullName")?.asString ?: accountName
                        println(" Token 认证成功: $accountName")
                    }
                    
                    // 更新 webservices（如果有）
                    jsonResponse.getAsJsonObject("webservices")?.let { ws ->
                        ws.getAsJsonObject("findme")?.get("url")?.asString?.let { findmeUrl ->
                            findme_url = findmeUrl
                            println(" FindMe URL 已更新: $findmeUrl")
                        }
                    }
                    
                    // 检查 items 中的 2FA 状态
                    jsonResponse.getAsJsonObject("items")?.let { items ->
                        val hsaTrustedBrowser = items.get("hsaTrustedBrowser")?.asBoolean ?: false
                        println(" hsaTrustedBrowser: $hsaTrustedBrowser")
                    }
                    
                    return true
                } catch (e: Exception) {
                    println(" 解析 Token 认证响应失败: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println(" Token 认证失败: ${response.code}, ${responseBody?.take(100)}")
            }
            
        } catch (e: Exception) {
            println(" Token 认证异常: ${e.message}")
            e.printStackTrace()
        }
        
        return false
    }
    
    /**
     * 刷新会话用于重试（处理 450 错误）
     * 参考 iCloud3 v3 的 authenticate(refresh_session=True)
     * 
     * 这是一个同步方法，用于在 FindMyService 中处理 450 错误时调用
     */
    fun refreshSessionForRetry(): Boolean {
        return try {
            println("=== 刷新会话 (450 错误重试) ===")
            
            if (sessionToken.isNullOrEmpty()) {
                println(" session_token 为空，无法刷新")
                return false
            }
            
            // 使用 runBlocking 调用挂起函数
            val success = runBlocking {
                authenticateWithToken()
            }
            
            if (success) {
                println(" 会话刷新成功，可以重试")
            } else {
                println(" 会话刷新失败")
            }
            
            success
        } catch (e: Exception) {
            println(" 会话刷新异常: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取 Push Token（参考 setup.js 的 getPushToken）
     * 这个步骤在某些情况下不是必须的
```
     */
    private fun getPushToken() {
        val host = getHostFromWebservice("push") ?: return
        
        val pushData = mapOf(
            "pushTopics" to emptyList<String>(),
            "pushTokenTTL" to 43200
        )
        
        val requestBody = gson.toJson(pushData).toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url("https://$host/getToken?attempt=1&clientBuildNumber=$clientBuildNumber&clientId=$clientId&clientMasteringNumber=$clientMasteringNumber&dsid=$dsid")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Cookie", cookieJar.getCookiesAsString(host))
            .build()
        
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            println(" Push Token 获取成功")
        }
    }
    
    /**
     * 验证2FA代码（参考 pyicloud_ic3.py 的 validate_2fa_code）
     */
    suspend fun verify2FACode(code: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            println("=== 验证 2FA 代码 ===")
            println("验证码: $code")
            
            if (sessionToken == null || sessionId == null || scnt == null) {
                println(" 缺少会话信息")
                return@withContext LoginResult(
                    success = false,
                    errorMessage = "会话已过期，请重新登录"
                )
            }
            
            // 构建请求头（参考 _get_auth_headers）
            val requestBuilder = Request.Builder()
                .url("https://idmsa.apple.com.cn/appleauth/auth/verify/trusteddevice/securitycode")
                .post(
                    gson.toJson(mapOf(
                        "securityCode" to mapOf("code" to code)
                    )).toRequestBody("application/json".toMediaType())
                )
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
                .addHeader("X-Apple-ID-Session-Id", sessionId!!)
                .addHeader("scnt", scnt!!)
                .addHeader("Referer", "https://idmsa.apple.com.cn/")
                .addHeader("User-Agent", userAgent)
            
            // 关键：添加 X-Apple-Session-Token 到请求头
            sessionToken?.let { requestBuilder.addHeader("X-Apple-Session-Token", it) }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            // 使用 response.body?.string() 会自动处理 gzip 解压
            val responseBody = response.body?.string()
            
            println("📋 2FA 验证响应:")
            println("  状态码: ${response.code}")
            println("  响应体: ${responseBody?.take(500)}")
            
            // 更新会话信息（重要！）
            response.header("X-Apple-Session-Token")?.let { 
                sessionToken = it 
                println("   更新 Session-Token: ${it.take(20)}...")
            }
            response.header("X-Apple-ID-Session-Id")?.let { 
                sessionId = it 
                println("   更新 Session-Id")
            }
            response.header("scnt")?.let { 
                scnt = it 
                println("   更新 scnt")
            }
            response.header("X-Apple-TwoSV-Trust-Token")?.let {
                trustToken = it
                println("   更新 Trust-Token: ${it.take(20)}...")
            }
            response.header("X-Apple-ID-Account-Country")?.let {
                accountCountry = it
                println("   更新 Account-Country: $it")
            }
            
            // 关键发现：如果服务器返回401但响应体为空，且更新了scnt，
            // 说明验证码被接受了，但需要继续完成认证流程
            val hasUpdatedSession = response.header("scnt") != null
            
            // 解析响应体检查错误码（参考 _resolve_error_code_reason）
            if (responseBody != null && responseBody.isNotEmpty()) {
                try {
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    
                    // 检查 errorCode 字段
                    val errorCode = jsonResponse.get("errorCode")?.asInt
                    val errorMessage = jsonResponse.get("errorMessage")?.asString
                        ?: jsonResponse.get("reason")?.asString
                        ?: jsonResponse.get("error")?.asString
                    
                    println("  JSON errorCode: $errorCode")
                    println("  JSON errorMessage: $errorMessage")
                    
                    // -21669 = 验证码错误（参考 validate_2fa_code）
                    if (errorCode == -21669) {
                        println(" 验证码错误 (errorCode: -21669)")
                        return@withContext LoginResult(
                            success = false,
                            errorMessage = "验证码错误，请重试"
                        )
                    }
                    
                    // 其他错误码
                    if (errorCode != null && errorCode < 0) {
                        println(" 验证失败，errorCode: $errorCode")
                        return@withContext LoginResult(
                            success = false,
                            errorMessage = errorMessage ?: "验证失败 (错误码: $errorCode)"
                        )
                    }
                } catch (e: Exception) {
                    println(" 解析响应JSON失败: ${e.message}")
                    // 继续处理 HTTP 状态码
                }
            }
            
            // 根据 HTTP 状态码判断结果
            when (response.code) {
                200, 204 -> {
                    println(" 2FA 验证成功 (HTTP ${response.code})")
                    
                    // 验证成功后，请求信任会话（参考 trust_session）
                    trustSession()
                    
                    // 然后进行账户登录（参考 _authenticate_with_token）
                    sessionToken?.let { token ->
                        if (accountLogin(token)) {
                            println(" 账户登录成功")
                            requiresTwoFactor = false
                            LoginResult(success = true)
                        } else {
                            println(" 账户登录失败")
                            LoginResult(
                                success = false,
                                errorMessage = "2FA验证成功，但账户登录失败"
                            )
                        }
                    } ?: LoginResult(
                        success = false,
                        errorMessage = "缺少会话令牌"
                    )
                }
                412 -> {
                    // 412 表示需要重新认证
                    println(" 需要重新认证 (HTTP 412)")
                    LoginResult(
                        success = false,
                        errorMessage = "会话已过期，请重新登录"
                    )
                }
                401 -> {
                    // 关键修复：401但响应体为空且更新了scnt，说明验证码被接受
                    if (hasUpdatedSession && (responseBody == null || responseBody.isEmpty())) {
                        println(" 2FA 验证成功 (HTTP 401 with session update)")
                        
                        // 验证成功后，请求信任会话（参考 trust_session）
                        trustSession()
                        
                        // 然后进行账户登录（参考 _authenticate_with_token）
                        sessionToken?.let { token ->
                            if (accountLogin(token)) {
                                println(" 账户登录成功")
                                requiresTwoFactor = false
                                return@withContext LoginResult(success = true)
                            } else {
                                println(" 账户登录失败")
                                return@withContext LoginResult(
                                    success = false,
                                    errorMessage = "2FA验证成功，但账户登录失败"
                                )
                            }
                        } ?: run {
                            println(" 缺少会话令牌")
                            return@withContext LoginResult(
                                success = false,
                                errorMessage = "缺少会话令牌"
                            )
                        }
                    } else {
                        // 401但没有更新会话，真正的认证失败
                        println(" 认证失败 (HTTP 401)")
                        return@withContext LoginResult(
                            success = false,
                            errorMessage = "验证码错误或会话已过期"
                        )
                    }
                }
                403 -> {
                    println(" 验证失败 (HTTP ${response.code})")
                    return@withContext LoginResult(
                        success = false,
                        errorMessage = "验证失败 (${response.code})"
                    )
                }
                else -> {
                    println(" 未知响应码: ${response.code}")
                    return@withContext LoginResult(
                        success = false,
                        errorMessage = "验证失败 (${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            println(" 2FA 验证异常: ${e.message}")
            e.printStackTrace()
            LoginResult(
                success = false,
                errorMessage = "验证失败: ${e.message}"
            )
        }
    }
    
    /**
     * 信任当前会话（参考 pyicloud_ic3.py 的 trust_session）
     */
    private fun trustSession(): Boolean {
        try {
            println("=== 信任会话 ===")
            
            // 构建请求头
            val headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "X-Apple-OAuth-Client-Id" to "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d",
                "X-Apple-OAuth-Client-Type" to "firstPartyAuth",
                "X-Apple-OAuth-Redirect-URI" to "https://www.icloud.com",
                "X-Apple-OAuth-Require-Grant-Code" to "true",
                "X-Apple-OAuth-Response-Mode" to "web_message",
                "X-Apple-OAuth-Response-Type" to "code",
                "X-Apple-OAuth-State" to clientId,
                "X-Apple-Widget-Key" to "d39ba9916b7251055b22c7f910e2ea796ee65e98b2ddecea8f5dde8d9d1a815d",
                "X-Apple-ID-Session-Id" to (sessionId ?: ""),
                "X-Apple-Session-Token" to (sessionToken ?: ""),
                "scnt" to (scnt ?: ""),
                "Referer" to "https://idmsa.apple.com.cn/",
                "User-Agent" to userAgent
            )
            
            // URL: {AUTH_ENDPOINT}/2sv/trust
            val request = Request.Builder()
                .url("https://idmsa.apple.com.cn/appleauth/auth/2sv/trust")
                .get()
                .apply {
                    headers.forEach { (key, value) -> addHeader(key, value) }
                }
                .build()
            
            val response = client.newCall(request).execute()
            println("信任会话响应码: ${response.code}")
            
            if (response.isSuccessful) {
                println(" 会话已信任")
                return true
            } else {
                println(" 信任会话失败: ${response.code}")
                return false
            }
        } catch (e: Exception) {
            println(" 信任会话异常（可忽略）: ${e.message}")
            return false
        }
    }
    
    /**
     * 信任当前设备（已弃用，使用 trustSession 代替）
     */
    @Deprecated("Use trustSession() instead")
    private fun trustDevice() {
        try {
            println("=== 信任设备 ===")
            
            val request = Request.Builder()
                .url("https://idmsa.apple.com.cn/appleauth/auth/2sv/trust")
                .get()
                .addHeader("X-Apple-Widget-Key", widgetKey)
                .addHeader("X-Apple-ID-Session-Id", sessionId ?: "")
                .addHeader("X-Apple-Session-Token", sessionToken ?: "")
                .addHeader("scnt", scnt ?: "")
                .addHeader("Referer", "https://idmsa.apple.com.cn/")
                .addHeader("User-Agent", userAgent)
                .build()
            
            val response = client.newCall(request).execute()
            println("信任设备响应码: ${response.code}")
            
            if (response.isSuccessful) {
                println(" 设备已信任")
            }
        } catch (e: Exception) {
            println(" 信任设备失败（可忽略）: ${e.message}")
        }
    }
    
    /**
     * 获取设备列表（参考 FindMe.js）
     */
    suspend fun getDevices(): List<Device> = withContext(Dispatchers.IO) {
        try {
            println("=== 获取设备列表 ===")
            
            // 获取 FindMe 服务的主机地址
            val host = getHostFromWebservice("findme")
            if (host == null) {
                println(" 无法获取 FindMe 服务地址")
                return@withContext emptyList()
            }
            
            println("FindMe 主机: $host")
            
            // 构建请求体（参考 FindMe.js line 36-46）
            val clientContext = mapOf(
                "appName" to "iCloud Find (Web)",
                "appVersion" to "2.0",
                "timezone" to java.util.TimeZone.getDefault().id,
                "inactiveTime" to 0,
                "apiVersion" to "3.0",
                "deviceListVersion" to 1,
                "fmly" to true
            )
            
            val requestBody = gson.toJson(mapOf("clientContext" to clientContext))
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("https://$host/fmipservice/client/web/initClient?clientBuildNumber=$clientBuildNumber&clientId=$clientId&clientMasteringNumber=$clientMasteringNumber&dsid=$dsid")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "*/*")
                .addHeader("Referer", "https://www.icloud.com.cn/")
                .addHeader("Cookie", cookieJar.getCookiesAsString(host))
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            println("设备列表响应码: ${response.code}")
            
            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val contentArray = jsonResponse.getAsJsonArray("content")
                
                val devices = mutableListOf<Device>()
                contentArray?.forEach { element ->
                    val deviceObj = element.asJsonObject
                    val location = deviceObj.getAsJsonObject("location")
                    
                    val device = Device(
                        id = deviceObj.get("id")?.asString ?: "",
                        name = deviceObj.get("name")?.asString ?: "Unknown",
                        deviceModel = deviceObj.get("deviceDisplayName")?.asString ?: "Unknown",
                        batteryLevel = deviceObj.get("batteryLevel")?.asFloat ?: 0f,
                        latitude = location?.get("latitude")?.asDouble,
                        longitude = location?.get("longitude")?.asDouble,
                        locationFinished = location?.get("locationFinished")?.asBoolean ?: false
                    )
                    
                    devices.add(device)
                    println(" 设备: ${device.name} - ${device.deviceModel}")
                }
                
                println("=== 共找到 ${devices.size} 个设备 ===")
                return@withContext devices
            } else {
                println(" 获取设备失败: $responseBody")
            }
            
            emptyList()
        } catch (e: Exception) {
            println(" 获取设备异常: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 从 webservices 中获取服务主机地址
     */
    private fun getHostFromWebservice(serviceName: String): String? {
        return try {
            webservices?.getAsJsonObject(serviceName)?.get("url")?.asString?.let { url ->
                url.replace("https://", "").replace("http://", "")
            }
        } catch (e: Exception) {
            println("获取 $serviceName 服务地址失败: ${e.message}")
            null
        }
    }
}

/**
 * 登录结果
 */
data class LoginResult(
    val success: Boolean,
    val requiresTwoFactor: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 认证结果
 */
private data class AuthResult(
    val success: Boolean,
    val token: String? = null,
    val requiresTwoFactor: Boolean = false,
    val errorMessage: String? = null
)

// ==================== 会话持久化扩展方法（参考 iCloud3 v3）====================

/**
 * 尝试从存储恢复会话
 */
fun ICloudService.tryRestoreSession(email: String, password: String): Boolean {
    return try {
        val sessionManagerField = ICloudService::class.java.getDeclaredField("sessionManager")
        sessionManagerField.isAccessible = true
        val sessionManager = sessionManagerField.get(this) as? SessionManager
        
        if (sessionManager == null) {
            println(" SessionManager 未初始化")
            return false
        }
        
        if (!sessionManager.isSessionValid(email, password)) {
            println("会话无效或已过期")
            return false
        }
        
        println("=== 恢复已保存的会话 ===")
        val restored = sessionManager.restoreSession(this)
        
        if (restored) {
            println(" 会话已成功恢复")
            sessionManager.printSessionInfo()
        }
        
        restored
    } catch (e: Exception) {
        println(" 恢复会话失败: ${e.message}")
        e.printStackTrace()
        false
    }
}

/**
 * 保存会话到存储
 */
fun ICloudService.saveSessionToStorage(email: String, password: String) {
    try {
        println("=== 开始保存会话数据 ===")
        println("  email: $email")
        
        val sessionManagerField = ICloudService::class.java.getDeclaredField("sessionManager")
        sessionManagerField.isAccessible = true
        val sessionManager = sessionManagerField.get(this) as? SessionManager
        
        println("  sessionManager: ${sessionManager != null}")
        
        if (sessionManager == null) {
            println(" SessionManager 未初始化，跳过会话保存")
            println(" 请确保在 Application 或 MainActivity 中调用了 ICloudService.initSessionManager(context)")
            return
        }
        
        println("=== 获取会话字段 ===")
        
        // 获取 private 字段
        val dsidField = ICloudService::class.java.getDeclaredField("dsid")
        dsidField.isAccessible = true
        val dsid = dsidField.get(this) as? String
        
        val findmeUrlField = ICloudService::class.java.getDeclaredField("findme_url")
        findmeUrlField.isAccessible = true
        val findme_url = findmeUrlField.get(this) as? String
        
        val accountNameField = ICloudService::class.java.getDeclaredField("accountName")
        accountNameField.isAccessible = true
        val accountName = accountNameField.get(this) as? String
        
        val accountLockedField = ICloudService::class.java.getDeclaredField("accountLocked")
        accountLockedField.isAccessible = true
        val accountLocked = accountLockedField.get(this) as? Boolean ?: false
        
        val webservicesField = ICloudService::class.java.getDeclaredField("webservices")
        webservicesField.isAccessible = true
        val webservices = webservicesField.get(this) as? com.google.gson.JsonObject
        
        val clientIdField = ICloudService::class.java.getDeclaredField("clientId")
        clientIdField.isAccessible = true
        val clientId = clientIdField.get(this) as String
        
        // 序列化 webservices
        val webservicesJson = webservices?.toString()
        
        sessionManager.saveSession(
            username = email,
            password = password,
            sessionToken = sessionToken,
            sessionId = sessionId,
            scnt = scnt,
            trustToken = trustToken,
            accountCountry = accountCountry,
            clientId = clientId,
            dsid = dsid,
            findmeUrl = findme_url,
            accountName = accountName,
            accountLocked = accountLocked,
            webservices = webservicesJson
        )
        
        println(" 会话数据已保存到 SharedPreferences")
    } catch (e: Exception) {
        println(" 保存会话失败: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * 清除保存的会话
 */
fun ICloudService.clearSavedSession() {
    try {
        val sessionManagerField = ICloudService::class.java.getDeclaredField("sessionManager")
        sessionManagerField.isAccessible = true
        val sessionManager = sessionManagerField.get(this) as? SessionManager
        sessionManager?.clearSession()
        println(" 已清除保存的会话")
    } catch (e: Exception) {
        println(" 清除会话失败: ${e.message}")
    }
}

/**
 * 持久化 Cookie 管理器
 * 
 *  关键修复: 支持根域名共享 (如 .icloud.com.cn)
 * 
 * 问题: Cookies 保存在 setup.icloud.com.cn，但请求发送到 p234-fmipweb.icloud.com.cn
 * 解决: 同时将 cookies 保存到根域名，让所有子域名都能访问
 * 
 * 参考 iCloud3 V3: Python requests.Session 自动处理 cookie 域共享
 */
class PersistentCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        
        //  关键修复: 同时保存到当前域名和根域名
        val rootDomain = getRootDomain(host)
        
        // 保存到当前域名
        saveToHost(host, cookies)
        
        // 同时保存到根域名 (如果不同)
        if (host != rootDomain) {
            saveToHost(rootDomain, cookies)
            println(" 保存 ${cookies.size} 个 cookies 到 $host 和根域 $rootDomain")
        } else {
            println(" 保存 ${cookies.size} 个 cookies 到 $host")
        }
    }
    
    private fun saveToHost(host: String, cookies: List<Cookie>) {
        val existing = cookieStore.getOrPut(host) { mutableListOf() }
        
        // 更新或添加 cookies
        cookies.forEach { newCookie ->
            existing.removeIf { it.name == newCookie.name }
            existing.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val rootDomain = getRootDomain(host)
        
        //  关键修复: 从当前域名和根域名加载 cookies
        val hostCookies = cookieStore[host] ?: emptyList()
        val rootCookies = if (host != rootDomain) {
            cookieStore[rootDomain] ?: emptyList()
        } else {
            emptyList()
        }
        
        // 合并并去重
        val allCookies = (hostCookies + rootCookies)
            .distinctBy { it.name }  // 去重（优先使用当前域名的 cookie）
            .filter { cookie ->
                cookie.expiresAt > System.currentTimeMillis()  // 过滤过期 cookie
            }
        
        if (allCookies.isNotEmpty()) {
            println("📤 加载 ${allCookies.size} 个 cookies for $host (从 $host[${hostCookies.size}] + $rootDomain[${rootCookies.size}])")
            // 调试: 打印 cookie 名称
            println("   Cookie 列表: ${allCookies.joinToString(", ") { it.name }}")
        } else {
            println(" 没有可用的 cookies for $host")
        }
        
        return allCookies
    }
    
    /**
     * 提取根域名
     * 例如: 
     *   p234-fmipweb.icloud.com.cn -> icloud.com.cn
     *   setup.icloud.com.cn -> icloud.com.cn
     *   www.apple.com -> apple.com
     */
    private fun getRootDomain(host: String): String {
        val parts = host.split(".")
        return when {
            // 对于 .com.cn、.co.uk 等双后缀域名，保留最后 3 段
            parts.size >= 3 && parts[parts.size - 2].length <= 3 -> {
                parts.takeLast(3).joinToString(".")
            }
            // 对于普通域名，保留最后 2 段
            parts.size >= 2 -> {
                parts.takeLast(2).joinToString(".")
            }
            // 否则返回原域名
            else -> host
        }
    }
    
    fun getCookiesAsString(host: String): String {
        return cookieStore[host]?.joinToString("; ") { "${it.name}=${it.value}" } ?: ""
    }
}
