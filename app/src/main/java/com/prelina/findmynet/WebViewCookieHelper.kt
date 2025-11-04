package com.prelina.findmynet

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 使用 WebView 获取真实浏览器 Cookie 的辅助类
 * 这种方法可以绕过 Apple 的反爬虫检测
 */
class WebViewCookieHelper {
    
    /**
     * 使用 WebView 登录 iCloud 并获取 Cookie
     * 这个方法会打开一个隐藏的 WebView 来模拟真实浏览器行为
     */
    suspend fun loginWithWebView(
        webView: WebView,
        email: String,
        password: String
    ): Result<Map<String, String>> = suspendCancellableCoroutine { continuation ->
        
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        
        // 清除之前的 Cookie
        cookieManager.removeAllCookies { success ->
            if (!success) {
                continuation.resume(Result.failure(Exception("无法清除 Cookie")))
                return@removeAllCookies
            }
            
            // 启用 JavaScript
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
            }
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // 检查是否已经登录成功（URL 变化到 iCloud 主页）
                    if (url?.contains("www.icloud.com") == true && !url.contains("signin")) {
                        // 登录成功，获取所有 Cookie
                        val allCookies = cookieManager.getCookie(url)
                        val cookieMap = parseCookieString(allCookies)
                        
                        if (continuation.isActive) {
                            continuation.resume(Result.success(cookieMap))
                        }
                    }
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("WebView 错误: $description")))
                    }
                }
            }
            
            // 加载 iCloud 登录页面
            webView.loadUrl("https://www.icloud.com.cn/")
            
            // 等待页面加载完成后自动填充账号密码
            webView.postDelayed({
                // 注入 JavaScript 来自动填充表单
                val js = """
                    (function() {
                        var emailInput = document.querySelector('input[type="email"]') || 
                                       document.querySelector('input[name="accountName"]');
                        var passwordInput = document.querySelector('input[type="password"]');
                        
                        if (emailInput) {
                            emailInput.value = '$email';
                            emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        
                        if (passwordInput) {
                            passwordInput.value = '$password';
                            passwordInput.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                        
                        // 提交表单
                        var submitButton = document.querySelector('button[type="submit"]') ||
                                         document.querySelector('.sign-in-button');
                        if (submitButton) {
                            submitButton.click();
                        }
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(js) { result ->
                    println("自动填充结果: $result")
                }
            }, 2000) // 等待 2 秒让页面完全加载
        }
        
        // 设置超时
        continuation.invokeOnCancellation {
            println("WebView 登录被取消")
        }
    }
    
    /**
     * 解析 Cookie 字符串
     */
    private fun parseCookieString(cookieString: String?): Map<String, String> {
        if (cookieString.isNullOrEmpty()) return emptyMap()
        
        return cookieString.split(";")
            .mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()
    }
    
    /**
     * 从 WebView 获取特定域的 Cookie
     */
    fun getCookiesForDomain(domain: String): String? {
        val cookieManager = CookieManager.getInstance()
        return cookieManager.getCookie(domain)
    }
}
