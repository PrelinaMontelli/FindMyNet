package com.prelina.findmynet.icloud3.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prelina.findmynet.icloud3.IC3Service
import com.prelina.findmynet.icloud3.ServiceState
import com.prelina.findmynet.icloud3.models.AuthenticationState
import com.prelina.findmynet.icloud3.models.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * IC3 ViewModel - 连接Service和UI
 * 通过ServiceConnection绑定到IC3Service
 */
class IC3ViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    // Service绑定
    private var ic3Service: IC3Service? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val ic3Binder = binder as? IC3Service.IC3Binder
            ic3Service = ic3Binder?.getService()
            isBound = true
            
            // 开始监听Service状态
            observeServiceState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            ic3Service = null
            isBound = false
        }
    }
    
    // 状态流
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _authState = MutableStateFlow<AuthenticationState?>(null)
    val authState: StateFlow<AuthenticationState?> = _authState.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    init {
        // 绑定Service
        bindService()
    }
    
    override fun onCleared() {
        super.onCleared()
        // 解绑Service
        unbindService()
    }
    
    /**
     * 绑定Service
     */
    private fun bindService() {
        val intent = Intent(context, IC3Service::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 解绑Service
     */
    private fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
    
    /**
     * 监听Service状态变化
     */
    private fun observeServiceState() {
        ic3Service?.let { service ->
            viewModelScope.launch {
                // 监听Service状态
                service.serviceState.collect { state ->
                    _serviceState.value = state
                }
            }
            
            viewModelScope.launch {
                // 监听设备列表
                service.devices.collect { devices ->
                    _devices.value = devices
                }
            }
            
            viewModelScope.launch {
                // 监听认证状态
                service.authState.collect { state ->
                    _authState.value = state
                }
            }
        }
    }
    
    /**
     * 登录并开始追踪
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                // 等待Service绑定
                while (!isBound || ic3Service == null) {
                    kotlinx.coroutines.delay(100)
                }
                
                // 调用Service的startTracking
                ic3Service?.startTracking(username, password)
                
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _serviceState.value = ServiceState.Error(e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 登出并停止追踪
     */
    fun logout() {
        ic3Service?.stopTracking()
        _serviceState.value = ServiceState.Idle
        _devices.value = emptyList()
        _authState.value = null
    }
    
    /**
     * 验证2FA代码
     */
    fun validate2FACode(code: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                
                // 调用Service的validate2FACode
                val result = ic3Service?.validate2FACode(code)
                if (result?.isSuccess == true) {
                    _serviceState.value = ServiceState.Tracking
                } else {
                    _errorMessage.value = result?.exceptionOrNull()?.message ?: "2FA验证失败"
                }
                
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新设备
     */
    fun refreshDevices() {
        viewModelScope.launch {
            try {
                ic3Service?.refreshDevices()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    
    /**
     * 暂停设备追踪
     */
    fun pauseDevice(deviceName: String) {
        viewModelScope.launch {
            try {
                ic3Service?.pauseDevice(deviceName)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    
    /**
     * 恢复设备追踪
     */
    fun resumeDevice(deviceName: String) {
        viewModelScope.launch {
            try {
                ic3Service?.resumeDevice(deviceName)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
