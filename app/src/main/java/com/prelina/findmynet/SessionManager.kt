package com.prelina.findmynet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * SessionManager - 会话持久化管理器
 * 参考 iCloud3 v3 的 session_data 和 token_pw_file 机制
 * 
 * 功能：
 * 1. 保存/加载 session tokens (sessionToken, sessionId, scnt, trustToken, accountCountry)
 * 2. 保存/加载 account info (dsid, findme_url, accountName)
 * 3. 检测密码变更（如果密码变更，清除会话）
 * 4. 自动过期管理
 */
class SessionManager(context: Context) {
    
    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_NAME = "icloud_session"
        
        // Session Token Keys
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SCNT = "scnt"
        private const val KEY_TRUST_TOKEN = "trust_token"
        private const val KEY_ACCOUNT_COUNTRY = "account_country"
        private const val KEY_CLIENT_ID = "client_id"
        
        // Account Info Keys
        private const val KEY_DSID = "dsid"
        private const val KEY_FINDME_URL = "findme_url"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_LOCKED = "account_locked"
        
        // Webservices
        private const val KEY_WEBSERVICES = "webservices"
        
        // Password Management (用于检测密码变更)
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD_HASH = "password_hash"
        
        // Timestamp
        private const val KEY_LAST_AUTH_TIME = "last_auth_time"
        private const val SESSION_EXPIRY_DAYS = 60 // 会话60天后过期
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 保存完整的会话数据
     */
    fun saveSession(
        username: String,
        password: String,
        sessionToken: String?,
        sessionId: String?,
        scnt: String?,
        trustToken: String?,
        accountCountry: String?,
        clientId: String,
        dsid: String?,
        findmeUrl: String?,
        accountName: String?,
        accountLocked: Boolean = false,
        webservices: String? = null
    ) {
        Log.i(TAG, "保存会话数据: username=$username, findmeUrl=$findmeUrl")
        
        prefs.edit().apply {
            // Session Tokens
            putString(KEY_SESSION_TOKEN, sessionToken)
            putString(KEY_SESSION_ID, sessionId)
            putString(KEY_SCNT, scnt)
            putString(KEY_TRUST_TOKEN, trustToken)
            putString(KEY_ACCOUNT_COUNTRY, accountCountry)
            putString(KEY_CLIENT_ID, clientId)
            
            // Account Info
            putString(KEY_DSID, dsid)
            putString(KEY_FINDME_URL, findmeUrl)
            putString(KEY_ACCOUNT_NAME, accountName)
            putBoolean(KEY_ACCOUNT_LOCKED, accountLocked)
            
            // Webservices
            putString(KEY_WEBSERVICES, webservices)
            
            // Username and Password Hash (用于检测密码变更)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD_HASH, hashPassword(password))
            
            // Timestamp
            putLong(KEY_LAST_AUTH_TIME, System.currentTimeMillis())
            
            apply()
        }
        
        Log.i(TAG, " 会话数据已保存")
    }
    
    /**
     * 检查会话是否有效
     */
    fun isSessionValid(username: String, password: String): Boolean {
        val savedUsername = prefs.getString(KEY_USERNAME, null)
        val savedPasswordHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val lastAuthTime = prefs.getLong(KEY_LAST_AUTH_TIME, 0)
        val sessionToken = prefs.getString(KEY_SESSION_TOKEN, null)
        
        // 检查用户名匹配
        if (savedUsername != username) {
            Log.i(TAG, "用户名不匹配，会话无效")
            return false
        }
        
        // 检查密码是否变更
        if (savedPasswordHash != hashPassword(password)) {
            Log.i(TAG, "密码已变更，清除旧会话")
            clearSession()
            return false
        }
        
        // 检查是否有 session token
        if (sessionToken.isNullOrEmpty()) {
            Log.i(TAG, "无 session token，会话无效")
            return false
        }
        
        // 检查会话是否过期（60天）
        val daysSinceAuth = (System.currentTimeMillis() - lastAuthTime) / (1000 * 60 * 60 * 24)
        if (daysSinceAuth > SESSION_EXPIRY_DAYS) {
            Log.i(TAG, "会话已过期 (${daysSinceAuth}天)，清除旧会话")
            clearSession()
            return false
        }
        
        Log.i(TAG, " 会话有效 (${daysSinceAuth}天前认证)")
        return true
    }
    
    /**
     * 恢复会话到 ICloudService
     */
    fun restoreSession(iCloudService: ICloudService): Boolean {
        try {
            val sessionToken = prefs.getString(KEY_SESSION_TOKEN, null)
            if (sessionToken.isNullOrEmpty()) {
                Log.i(TAG, "无可恢复的会话")
                return false
            }
            
            Log.i(TAG, "正在恢复会话...")
            
            // 恢复 session tokens
            iCloudService.sessionToken = sessionToken
            iCloudService.sessionId = prefs.getString(KEY_SESSION_ID, null)
            iCloudService.scnt = prefs.getString(KEY_SCNT, null)
            iCloudService.trustToken = prefs.getString(KEY_TRUST_TOKEN, null)
            iCloudService.accountCountry = prefs.getString(KEY_ACCOUNT_COUNTRY, null)
            
            // 恢复 account info (使用反射访问 private 字段)
            val dsid = prefs.getString(KEY_DSID, null)
            val findmeUrl = prefs.getString(KEY_FINDME_URL, null)
            val accountName = prefs.getString(KEY_ACCOUNT_NAME, null)
            
            // 使用反射设置 private 字段
            try {
                val dsidField = ICloudService::class.java.getDeclaredField("dsid")
                dsidField.isAccessible = true
                dsidField.set(iCloudService, dsid)
                
                val findmeUrlField = ICloudService::class.java.getDeclaredField("findme_url")
                findmeUrlField.isAccessible = true
                findmeUrlField.set(iCloudService, findmeUrl)
                
                val accountNameField = ICloudService::class.java.getDeclaredField("accountName")
                accountNameField.isAccessible = true
                accountNameField.set(iCloudService, accountName)
            } catch (e: Exception) {
                Log.e(TAG, "反射设置字段失败: ${e.message}")
            }
            
            Log.i(TAG, " 会话已恢复:")
            Log.i(TAG, "  sessionToken: ${sessionToken.take(20)}...")
            Log.i(TAG, "  sessionId: ${iCloudService.sessionId}")
            Log.i(TAG, "  findme_url: $findmeUrl")
            Log.i(TAG, "  accountName: $accountName")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "恢复会话失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 清除会话数据
     */
    fun clearSession() {
        Log.i(TAG, "清除会话数据")
        prefs.edit().clear().apply()
    }
    
    /**
     * 获取保存的用户名
     */
    fun getSavedUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * 获取 FindMe URL
     */
    fun getFindmeUrl(): String? {
        return prefs.getString(KEY_FINDME_URL, null)
    }
    
    /**
     * 简单的密码哈希（用于检测变更）
     */
    private fun hashPassword(password: String): String {
        return password.hashCode().toString()
    }
    
    /**
     * 打印会话信息（调试用）
     */
    fun printSessionInfo() {
        Log.i(TAG, "========== 会话信息 ==========")
        Log.i(TAG, "username: ${prefs.getString(KEY_USERNAME, "null")}")
        Log.i(TAG, "sessionToken: ${prefs.getString(KEY_SESSION_TOKEN, "null")?.take(20)}...")
        Log.i(TAG, "sessionId: ${prefs.getString(KEY_SESSION_ID, "null")}")
        Log.i(TAG, "scnt: ${prefs.getString(KEY_SCNT, "null")?.take(50)}...")
        Log.i(TAG, "trustToken: ${prefs.getString(KEY_TRUST_TOKEN, "null")?.take(20)}...")
        Log.i(TAG, "accountCountry: ${prefs.getString(KEY_ACCOUNT_COUNTRY, "null")}")
        Log.i(TAG, "dsid: ${prefs.getString(KEY_DSID, "null")}")
        Log.i(TAG, "findme_url: ${prefs.getString(KEY_FINDME_URL, "null")}")
        Log.i(TAG, "accountName: ${prefs.getString(KEY_ACCOUNT_NAME, "null")}")
        val lastAuthTime = prefs.getLong(KEY_LAST_AUTH_TIME, 0)
        val daysSince = (System.currentTimeMillis() - lastAuthTime) / (1000 * 60 * 60 * 24)
        Log.i(TAG, "lastAuthTime: ${daysSince}天前")
        Log.i(TAG, "==============================")
    }
}
