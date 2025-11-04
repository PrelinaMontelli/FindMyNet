package com.prelina.findmynet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.prelina.findmynet.ui.theme.FindMyNetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivityNew : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 SessionManager（参考 iCloud3 v3 在 __init__ 时自动设置）
        ICloudService.initSessionManager(applicationContext)
        
        setContent {
            FindMyNetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FindMyApp()
                }
            }
        }
    }
}

data class Device(
    val id: String,
    val name: String,
    val deviceModel: String,
    val batteryLevel: Float,
    val latitude: Double?,
    val longitude: Double?,
    val locationFinished: Boolean
)

@Composable
fun FindMyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var isCheckingSession by remember { mutableStateOf(true) }
    
    // 保存登录凭据（用于 2FA 验证成功后保存会话）
    var savedEmail by remember { mutableStateOf("") }
    var savedPassword by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val iCloudService = remember { ICloudService.getInstance() }
    
    // 🔑 获取 SessionManager 实例（与 ICloudService 中的是同一个）
    val sessionManager = remember { 
        SessionManager(context)
    }
    
    // 🔑 应用启动时自动检查并恢复会话（类似 iCloud3 V3 的自动登录）
    LaunchedEffect(Unit) {
        isCheckingSession = true
        android.util.Log.i("FindMyApp", "🔍 检查是否有已保存的会话...")
        
        try {
            // 获取保存的用户名
            val savedUsername = sessionManager.getSavedUsername()
            
            if (savedUsername != null) {
                android.util.Log.i("FindMyApp", " 找到已保存的账号: $savedUsername")
                
                // 尝试恢复会话
                if (sessionManager.restoreSession(iCloudService)) {
                    android.util.Log.i("FindMyApp", " 会话恢复成功，自动登录")
                    
                    // 自动跳转到地图页面
                    val intent = Intent(context, MapActivity::class.java)
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                } else {
                    android.util.Log.w("FindMyApp", " 会话恢复失败或已过期，需要重新登录")
                    // 预填用户名
                    email = savedUsername
                }
            } else {
                android.util.Log.i("FindMyApp", " 没有已保存的会话，显示登录页面")
            }
        } catch (e: Exception) {
            android.util.Log.e("FindMyApp", " 检查会话时出错: ${e.message}", e)
        } finally {
            isCheckingSession = false
        }
    }

    // 2FA 对话框
    if (showTwoFactorDialog) {
        TwoFactorDialog(
            onDismiss = { showTwoFactorDialog = false },
            onCodeSubmit = { code ->
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val result = iCloudService.verify2FACode(code)
                        if (result.success) {
                            showTwoFactorDialog = false
                            
                            // 🔑 2FA 验证成功后，保存会话
                            android.util.Log.i("FindMyApp", " 2FA 验证成功，保存会话...")
                            iCloudService.saveSessionToStorage(savedEmail, savedPassword)
                            
                            // 跳转到地图页面
                            val intent = Intent(context, MapActivity::class.java)
                            context.startActivity(intent)
                            (context as? ComponentActivity)?.finish() // 关闭登录页面
                        } else {
                            errorMessage = result.errorMessage ?: "验证码错误"
                        }
                    } catch (e: Exception) {
                        errorMessage = "验证失败: ${e.message}"
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 检查会话中的加载状态
        if (isCheckingSession) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在检查登录状态...",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        
        if (!isLoggedIn) {
            // 登录界面
            Text(
                text = "FindMyNet",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 重要提示卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = " 重要提示",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "如果遇到问题请到GitHub提交issue！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Apple ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        
                        // 🔑 保存登录凭据（用于 2FA 验证后保存会话）
                        savedEmail = email
                        savedPassword = password
                        
                        try {
                            val result = iCloudService.login(email, password)
                            if (result.success) {
                                // 登录成功，跳转到地图页面
                                val intent = Intent(context, MapActivity::class.java)
                                context.startActivity(intent)
                                (context as? ComponentActivity)?.finish() // 关闭登录页面
                            } else if (result.requiresTwoFactor) {
                                // 显示 2FA 对话框
                                showTwoFactorDialog = true
                                errorMessage = "请输入验证码"
                            } else {
                                errorMessage = result.errorMessage ?: "登录失败"
                            }
                        } catch (e: java.net.UnknownHostException) {
                            errorMessage = " 网络错误：无法连接到 Apple 服务器\n\n可能是设备的问题，请检查网络连接"
                        } catch (e: java.io.IOException) {
                            if (e.message?.contains("503") == true) {
                                errorMessage = " Apple 服务器繁忙 (503)\n\n可能的原因：\n• Apple 检测到非浏览器访问\n• 请求过于频繁"
                            } else {
                                errorMessage = "网络错误: ${e.message}"
                            }
                        } catch (e: Exception) {
                            errorMessage = "错误: ${e.message}"
                            e.printStackTrace()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isLoading) "登录中..." else "登录")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 直接跳转到地图按钮
            Button(
                onClick = {
                    val intent = Intent(context, MapActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("转到MapActivity")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 网络诊断按钮
            OutlinedButton(
                onClick = {
                    scope.launch {
                        errorMessage = "🔍 测试网络连接...\n"
                        try {
                            // 测试 DNS 解析
                            withContext(Dispatchers.IO) {
                                val address = java.net.InetAddress.getByName("www.apple.com")
                                errorMessage += " DNS 解析成功: ${address.hostAddress}\n"
                                
                                // 测试 HTTPS 连接
                                val url = java.net.URL("https://www.apple.com")
                                val connection = url.openConnection()
                                connection.connectTimeout = 5000
                                connection.connect()
                                errorMessage += " HTTPS 连接成功\n\n建议：网络正常，可以登录"
                            }
                        } catch (e: Exception) {
                            errorMessage += " 连接失败: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(" 测试网络连接")
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // 设备列表界面
            Text(
                text = "我的设备",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator()
                Text("加载设备中...", modifier = Modifier.padding(top = 8.dp))
            } else if (devices.isEmpty()) {
                Text("没有找到设备")
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        DeviceCard(device)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            try {
                                val deviceList = iCloudService.getDevices()
                                devices = deviceList
                                if (deviceList.isEmpty()) {
                                    errorMessage = "未找到设备"
                                }
                            } catch (e: Exception) {
                                errorMessage = "刷新失败: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("刷新位置")
                }
                
                OutlinedButton(
                    onClick = {
                        isLoggedIn = false
                        devices = emptyList()
                        email = ""
                        password = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("退出登录")
                }
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun TwoFactorDialog(
    onDismiss: () -> Unit,
    onCodeSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("双因素认证")
        },
        text = {
            Column {
                Text(
                    text = "请输入发送到您设备的 6 位验证码",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { 
                        // 只允许输入数字，最多6位
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            code = it
                            error = null
                        }
                    },
                    label = { Text("验证码") },
                    placeholder = { Text("000000") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = " 提示：请查看您的 Apple 设备上的验证码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.length == 6) {
                        onCodeSubmit(code)
                    } else {
                        error = "请输入完整的 6 位验证码"
                    }
                },
                enabled = code.length == 6
            ) {
                Text("验证")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DeviceCard(device: Device) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = device.deviceModel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("电量: ${(device.batteryLevel * 100).toInt()}%")
            
            if (device.latitude != null && device.longitude != null) {
                Text("位置: ${String.format("%.4f", device.latitude)}, ${String.format("%.4f", device.longitude)}")
                if (device.locationFinished) {
                    Text("✓ 位置已确定", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("⋯ 正在定位...", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                Text("位置不可用", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
