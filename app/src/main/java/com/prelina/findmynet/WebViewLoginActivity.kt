package com.prelina.findmynet

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prelina.findmynet.ui.theme.FindMyNetTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)

/**
 * WebView 登录活动
 * 使用真实浏览器环境登录 iCloud，避免 503 错误
 */
class WebViewLoginActivity : ComponentActivity() {
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            FindMyNetTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewLoginScreen(
                        onLoginSuccess = { cookies ->
                            // 登录成功，将 cookies 传回主界面
                            // TODO: 实现 Cookie 传递
                            finish()
                        },
                        onClose = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WebViewLoginScreen(
    onLoginSuccess: (Map<String, String>) -> Unit,
    onClose: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var loginDetected by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部工具栏
        TopAppBar(
            title = { Text("使用浏览器登录 iCloud") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Text("✕")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        
        // 加载进度条
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // URL 显示
        if (currentUrl.isNotEmpty()) {
            Text(
                text = currentUrl,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                maxLines = 1
            )
        }
        
        // WebView
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }
                        
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                currentUrl = url ?: ""
                                
                                // 检测登录成功
                                if (url?.contains("www.icloud.com") == true && 
                                    !url.contains("signin") && 
                                    !url.contains("auth") &&
                                    !loginDetected) {
                                    
                                    loginDetected = true
                                    
                                    // 提取所有 Cookie
                                    scope.launch {
                                        kotlinx.coroutines.delay(1000) // 等待 Cookie 完全设置
                                        
                                        val cookies = mutableMapOf<String, String>()
                                        
                                        listOf(
                                            "https://idmsa.apple.com",
                                            "https://setup.icloud.com",
                                            "https://www.icloud.com"
                                        ).forEach { domain ->
                                            val cookieString = cookieManager.getCookie(domain)
                                            if (!cookieString.isNullOrEmpty()) {
                                                cookieString.split(";").forEach { cookie ->
                                                    val parts = cookie.trim().split("=", limit = 2)
                                                    if (parts.size == 2) {
                                                        cookies[parts[0]] = parts[1]
                                                    }
                                                }
                                            }
                                        }
                                        
                                        println(" 检测到登录成功！提取了 ${cookies.size} 个 Cookie")
                                        onLoginSuccess(cookies)
                                    }
                                }
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                errorCode: Int,
                                description: String?,
                                failingUrl: String?
                            ) {
                                super.onReceivedError(view, errorCode, description, failingUrl)
                                println(" WebView 错误: $description")
                            }
                        }
                        
                        // 加载 iCloud 登录页（使用中国区）
                        loadUrl("https://www.icloud.com.cn/")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // 登录成功提示
            if (loginDetected) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            " 登录成功！",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "正在获取认证信息...",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // 底部提示
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "提示：",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• 在上方浏览器中正常登录 iCloud\n" +
                    "• 支持双因素认证 (2FA)\n" +
                    "• 登录成功后将自动提取认证信息\n" +
                    "• 此方法可避免 503 错误",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
