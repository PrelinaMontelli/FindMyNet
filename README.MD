# FindMyNet

**中文 | [English](README_EN.MD)**

---

一个允许在 Android 上访问 Apple 查找网络的开源软件项目。

> **声明**  
> iCloud、Apple、Find My、查找、Find My Network 的权利人和所有人是 Apple Inc. 美国苹果公司。本项目与 Apple Inc. 无任何官方关联。

---

## 项目简介

FindMyNet 是一个 Android 应用程序，允许用户通过 iCloud 账户追踪和定位他们的 Apple 设备（iPhone、iPad、Mac、Apple Watch、AirPods 等）。该应用实现了完整的 iCloud 认证流程，包括双因素认证（2FA）支持，并在高德地图上实时显示设备位置。

### 主要功能

- **iCloud 认证**
  - 完整的 Apple ID 登录流程
  - 双因素认证（2FA）支持
  - 会话持久化，自动保存登录状态

- **设备定位**
  - 实时获取所有 Apple 设备的位置信息
  - 自动刷新机制

- **地图显示**
  - 基于高德地图 2D SDK 的设备位置展示
  - WGS84 → GCJ-02 坐标系自动转换
  - 自定义设备标记和信息窗口

- **设备过滤**
  - 支持隐藏特定设备（不在地图和列表中显示）
  - 灵活的设备名称过滤机制

- **会话管理**
  - 自动保存 iCloud 会话 cookies
  - 应用重启后无需重新登录
  - 安全的本地存储

---

## 致谢

我诚挚地感谢 **Gary Cobb**（aka GeeksterGary），他的 [iCloud3 v3](https://github.com/gcobb321/icloud3_v3) 项目为我的代码提供了巨大的帮助。该项目是一个优秀的 Home Assistant 集成，其 Python 实现为理解 iCloud Find My 网络协议提供了宝贵的参考。没有他的工作，我无法完成这个软件。

特别感谢以下方面的参考：
- iCloud 认证流程和 API 端点
- Find My 网络的请求参数和响应格式
- 设备数据解析和位置信息处理
- 持续定位机制的实现思路

---

## 技术栈

### 开发环境
- **Android Studio**: Ladybug | 2024.2.1 或更高版本
- **Gradle**: 8.13.0
- **Kotlin**: 2.0.21
- **Android Gradle Plugin**: 8.13.0
- **Java**: JDK 11

### 最低系统要求
- **最低 SDK**: Android 10 (API 29)
- **目标 SDK**: Android 36
- **编译 SDK**: Android 36

### 核心依赖

#### UI 框架
- **Jetpack Compose**: 2024.09.00 (Material3)
- **Material Components**: 1.11.0
- **AppCompat**: 1.6.1
- **CoordinatorLayout**: 1.2.0
- **RecyclerView**: 1.3.2

#### 网络通信
- **OkHttp**: 4.12.0
  - HTTP 客户端和 Cookie 管理
  - 支持持久化 Cookie 存储
- **Gson**: 2.10.1
  - JSON 数据解析

#### 地图服务
- **高德地图 2D SDK**: latest.integration
  - 支持 GCJ-02 坐标系

#### 异步处理
- **Kotlin Coroutines**: 1.7.3
  - 异步网络请求
  - 定时刷新任务

---

##  快速开始

### 1. 克隆项目

```bash
git clone https://github.com/PrelinaMontelli/FindMyNet.git
cd FindMyNet
```

### 2. 配置高德地图 API Key

#### 方法一：修改 AndroidManifest.xml
编辑 `app/src/main/AndroidManifest.xml`，找到以下行：

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="YOUR_AMAP_API_KEY" />
```

将 `YOUR_AMAP_API_KEY` 替换为您的高德地图 API Key。

#### 方法二：修改 CoordinateConverter.kt
编辑 `app/src/main/java/com/prelina/findmynet/CoordinateConverter.kt`，找到：

```kotlin
private const val AMAP_KEY = "YOUR_AMAP_KEY_HERE"
```

替换为您的 API Key。

> **如何获取高德地图 API Key？**
> 1. 访问 [高德开放平台](https://lbs.amap.com/)
> 2. 注册账号并登录
> 3. 进入控制台创建应用
> 4. 添加 Android 平台 Key
> 5. 配置应用包名：`com.prelina.findmynet`
> 6. 配置 SHA1 签名（开发版和发布版）

### 3. 构建项目

#### 使用 Android Studio
1. 使用 Android Studio 打开项目
2. 等待 Gradle 同步完成
3. 点击 **Run** 或按 `Shift + F10`

#### 使用命令行

```bash
# 调试版本
./gradlew assembleDebug

# 发布版本（需要配置签名）
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

### 4. 首次使用

1. **登录**: 使用您的 Apple ID 和密码登录
2. **2FA 验证**: 如果启用了双因素认证，输入发送到您 Apple 设备的 6 位验证码
3. **查看设备**: 登录成功后，应用会自动跳转到地图界面
4. **刷新位置**: 点击右上角的刷新按钮可手动更新设备位置

---

## 项目结构

```
FindMyNet/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/prelina/findmynet/
│   │   │   │   ├── MainActivity.kt              # 登录界面
│   │   │   │   ├── MapActivity.kt               # 地图显示和设备列表
│   │   │   │   ├── ICloudService.kt             # iCloud 认证服务
│   │   │   │   ├── FindMyService.kt             # Find My 网络 API
│   │   │   │   ├── CoordinateConverter.kt       # 坐标转换工具
│   │   │   │   ├── SessionManager.kt            # 会话持久化
│   │   │   │   ├── DeviceAdapter.kt             # 设备列表适配器
│   │   │   │   └── ui/theme/                    # Compose UI 主题
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_map.xml         # 地图布局
│   │   │   │   │   └── item_device.xml          # 设备列表项
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml              # 字符串资源
│   │   │   │   │   ├── colors.xml               # 颜色资源
│   │   │   │   │   └── themes.xml               # 主题样式
│   │   │   │   └── drawable/                    # 图形资源
│   │   │   └── AndroidManifest.xml              # 应用配置
│   │   └── test/                                # 单元测试
│   └── build.gradle.kts                         # 应用级构建配置
├── gradle/
│   └── libs.versions.toml                       # 依赖版本管理
├── build.gradle.kts                             # 项目级构建配置
├── settings.gradle.kts                          # Gradle 设置
└── README.md                                    # 本文件
```

---

## 核心功能实现

### iCloud 认证流程

1. **初始认证**: 使用 Apple ID 和密码进行 SRP（Secure Remote Password）认证
2. **2FA 验证**: 如果账户启用了双因素认证，请求并验证 6 位验证码
3. **Session 管理**: 保存认证 cookies 到本地存储
4. **自动登录**: 应用重启时自动恢复会话

### 设备定位机制

```kotlin
// 请求设备位置时使用 shouldLocate=true 参数
// 这会触发设备主动更新位置信息（类似 iCloud3 V3 的实现）
val payload = """
{
    "clientContext": {
        "shouldLocate": true,
        "selectedDevice": "all"
    }
}
"""
```

### 坐标转换

由于 Apple 使用 WGS84 坐标系，而国内地图使用 GCJ-02 坐标系，应用自动进行坐标转换：

```kotlin
// WGS84 (Apple) → GCJ-02 (高德地图)
val convertedDevices = coordinateConverter.convertDevices(devices)
```

### 设备过滤

在 `MapActivity.kt` 中配置需要隐藏的设备：

```kotlin
private val FILTERED_DEVICE_NAMES = setOf<String>(
    "家人的设备名称",
    "不想显示的设备",
)
```

---

## TODO 列表

以下功能尚未实现，欢迎贡献：

- [ ] **远程抹掉设备**
  - 通过 iCloud API 触发设备数据清除
  - 需要额外的身份验证和确认机制

- [ ] **远程振铃**
  - 让设备发出声音以便查找
  - 支持 iPhone、iPad、Apple Watch、AirPods 等

- [ ] **设备图标获取**
  - 从 iCloud API 获取设备的真实图标
  - 显示设备型号图片而非默认标记

- [ ] **位置历史记录**
  - 保存设备的历史位置数据
  - 显示设备移动轨迹

- [ ] **通知推送**
  - 设备位置变化通知
  - 设备离线/上线提醒

- [ ] **多账户支持**
  - 支持切换不同的 iCloud 账户
  - 管理多个账户的设备

---

## 隐私与安全

- **本地存储**: 所有认证信息仅存储在设备本地，不会上传到任何第三方服务器
- **加密传输**: 所有网络请求使用 HTTPS 加密
- **Cookie 管理**: Session cookies 使用 Android 的 SharedPreferences 安全存储
- **权限控制**: 应用仅请求必要的权限（网络访问、位置权限）

> **安全提示**  
> 请勿在公共设备上登录您的 Apple 账户。建议启用双因素认证以提高账户安全性。

---


## 贡献指南

欢迎提交 Issue 和 Pull Request！

### 提交 Issue
- 清晰描述问题或功能请求
- 提供复现步骤（如果是 Bug）
- 附上相关日志和截图

### 提交 PR
1. Fork 本项目
2. 创建您的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交您的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request





## 许可证

本项目采用 CC BY-NC 4.0（Creative Commons Attribution-NonCommercial 4.0 International）许可证。

**阁下可以：**
- ✅ 分享 — 在任何媒介以任何形式复制、发行本作品
- ✅ 演绎 — 修改、转换或以本作品为基础进行创作

**惟须遵守下列条件：**
- 📝 **署名** — 阁下必须给出适当的署名，提供指向本许可协议的链接，同时标明是否（对原始作品）作了修改
- 🚫 **非商业性使用** — 阁下不得将本作品用于商业目的

**原作者：** Prelina Montelli

详细许可条款请参见：https://creativecommons.org/licenses/by-nc/4.0/

