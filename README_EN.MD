# FindMyNet

**[中文](README.MD) | English**

---

An open-source software project that enables access to Apple's Find My network on Android.

> **Disclaimer**  
> iCloud, Apple, Find My, and Find My Network are trademarks owned by Apple Inc. This project has no official affiliation with Apple Inc.

---

## Project Overview

FindMyNet is an Android application that allows users to track and locate their Apple devices (iPhone, iPad, Mac, Apple Watch, AirPods, etc.) through their Apple account. The app implements a complete iCloud authentication flow, including two-factor authentication (2FA) support, and displays device locations in real-time on Amap.

### Key Features

- **iCloud Authentication**
  - Complete Apple ID login process
  - Two-factor authentication (2FA) support
  - Session persistence with automatic login state saving

- **Device Tracking**
  - Real-time location retrieval for all Apple devices
  - Automatic refresh mechanism

- **Map Display**
  - Device location display based on Amap 2D SDK
  - Automatic WGS84 → GCJ-02 coordinate system conversion
  - Custom device markers and info windows

- **Device Filtering**
  - Support for hiding specific devices (not shown on map or list)
  - Flexible device name filtering mechanism

- **Session Management**
  - Automatic saving of iCloud session cookies
  - No need to re-login after app restart
  - Secure local storage

---

## Acknowledgments

I sincerely thank **Gary Cobb** (aka GeeksterGary), whose [iCloud3 v3](https://github.com/gcobb321/icloud3_v3) project has been tremendously helpful to my code. This project is an excellent Home Assistant integration, and its Python implementation provided valuable reference for understanding the iCloud Find My network protocol. Without his work, I would not have been able to complete this software.

Special thanks for reference in the following areas:
- iCloud authentication flow and API endpoints
- Find My network request parameters and response formats
- Device data parsing and location information processing
- Implementation ideas for continuous location tracking mechanism

---

## Tech Stack

### Development Environment
- **Android Studio**: Ladybug | 2024.2.1 or higher
- **Gradle**: 8.13.0
- **Kotlin**: 2.0.21
- **Android Gradle Plugin**: 8.13.0
- **Java**: JDK 11

### Minimum System Requirements
- **Minimum SDK**: Android 10 (API 29)
- **Target SDK**: Android 36
- **Compile SDK**: Android 36

### Core Dependencies

#### UI Framework
- **Jetpack Compose**: 2024.09.00 (Material3)
- **Material Components**: 1.11.0
- **AppCompat**: 1.6.1
- **CoordinatorLayout**: 1.2.0
- **RecyclerView**: 1.3.2

#### Network Communication
- **OkHttp**: 4.12.0
  - HTTP client and Cookie management
  - Persistent Cookie storage support
- **Gson**: 2.10.1
  - JSON data parsing

#### Map Services
- **Amap 2D SDK**: latest.integration
  - GCJ-02 coordinate system support

#### Asynchronous Processing
- **Kotlin Coroutines**: 1.7.3
  - Asynchronous network requests
  - Timed refresh tasks

---

## Quick Start

### 1. Clone the Project

```bash
git clone https://github.com/PrelinaMontelli/FindMyNet.git
cd FindMyNet
```

### 2. Configure Amap API Key

#### Method 1: Modify AndroidManifest.xml
Edit `app/src/main/AndroidManifest.xml` and find the following line:

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="YOUR_AMAP_API_KEY" />
```

Replace `YOUR_AMAP_API_KEY` with your Amap API Key.

#### Method 2: Modify CoordinateConverter.kt
Edit `app/src/main/java/com/prelina/findmynet/CoordinateConverter.kt` and find:

```kotlin
private const val AMAP_KEY = "YOUR_AMAP_KEY_HERE"
```

Replace with your API Key.

> **How to Obtain Amap API Key?**
> 1. Visit [Amap Open Platform](https://lbs.amap.com/)
> 2. Register an account and log in
> 3. Go to the console to create an application
> 4. Add an Android platform Key
> 5. Configure package name: `com.prelina.findmynet`
> 6. Configure SHA1 signature (debug and release versions)

### 3. Build the Project

#### Using Android Studio
1. Open the project with Android Studio
2. Wait for Gradle sync to complete
3. Click **Run** or press `Shift + F10`

#### Using Command Line

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease

# Install to device
./gradlew installDebug
```

### 4. First Time Use

1. **Login**: Log in with your Apple ID and password
2. **2FA Verification**: If two-factor authentication is enabled, enter the 6-digit verification code sent to your Apple device
3. **View Devices**: After successful login, the app will automatically navigate to the map interface
4. **Refresh Location**: Click the refresh button in the top right corner to manually update device locations

---

## Project Structure

```
FindMyNet/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/prelina/findmynet/
│   │   │   │   ├── MainActivity.kt              # Login interface
│   │   │   │   ├── MapActivity.kt               # Map display and device list
│   │   │   │   ├── ICloudService.kt             # iCloud authentication service
│   │   │   │   ├── FindMyService.kt             # Find My network API
│   │   │   │   ├── CoordinateConverter.kt       # Coordinate conversion tool
│   │   │   │   ├── SessionManager.kt            # Session persistence
│   │   │   │   ├── DeviceAdapter.kt             # Device list adapter
│   │   │   │   └── ui/theme/                    # Compose UI theme
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_map.xml         # Map layout
│   │   │   │   │   └── item_device.xml          # Device list item
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml              # String resources
│   │   │   │   │   ├── colors.xml               # Color resources
│   │   │   │   │   └── themes.xml               # Theme styles
│   │   │   │   └── drawable/                    # Graphic resources
│   │   │   └── AndroidManifest.xml              # App configuration
│   │   └── test/                                # Unit tests
│   └── build.gradle.kts                         # App-level build config
├── gradle/
│   └── libs.versions.toml                       # Dependency version management
├── build.gradle.kts                             # Project-level build config
├── settings.gradle.kts                          # Gradle settings
└── README.md                                    # This file
```

---

## Core Feature Implementation

### iCloud Authentication Flow

1. **Initial Authentication**: SRP (Secure Remote Password) authentication using Apple ID and password
2. **2FA Verification**: If the account has two-factor authentication enabled, request and verify the 6-digit code
3. **Session Management**: Save authentication cookies to local storage
4. **Automatic Login**: Automatically restore session when the app restarts

### Device Location Mechanism

```kotlin
// Use shouldLocate=true parameter when requesting device locations
// This triggers devices to actively update their location information (similar to iCloud3 V3 implementation)
val payload = """
{
    "clientContext": {
        "shouldLocate": true,
        "selectedDevice": "all"
    }
}
"""
```

### Coordinate Conversion

Since Apple uses the WGS84 coordinate system, while domestic maps use the GCJ-02 coordinate system, the app automatically performs coordinate conversion:

```kotlin
// WGS84 (Apple) → GCJ-02 (Amap)
val convertedDevices = coordinateConverter.convertDevices(devices)
```

### Device Filtering

Configure devices to hide in `MapActivity.kt`:

```kotlin
private val FILTERED_DEVICE_NAMES = setOf<String>(
    "Family member's device name",
    "Device you don't want to show",
)
```

---

## TODO List

The following features are not yet implemented, contributions are welcome:

- [ ] **Remote Wipe Device**
  - Trigger device data erasure through iCloud API
  - Requires additional authentication and confirmation mechanism

- [ ] **Remote Ring**
  - Make the device emit sound for easier finding
  - Support for iPhone, iPad, Apple Watch, AirPods, etc.

- [ ] **Device Icon Retrieval**
  - Retrieve actual device icons from iCloud API
  - Display device model images instead of default markers

- [ ] **Location History**
  - Save device historical location data
  - Display device movement tracks

- [ ] **Push Notifications**
  - Notifications for device location changes
  - Device offline/online alerts

- [ ] **Multi-Account Support**
  - Support switching between different iCloud accounts
  - Manage devices from multiple accounts

---

## Privacy & Security

- **Local Storage**: All authentication information is stored only on the local device and is not uploaded to any third-party servers
- **Encrypted Transmission**: All network requests use HTTPS encryption
- **Cookie Management**: Session cookies are securely stored using Android's SharedPreferences
- **Permission Control**: The app only requests necessary permissions (network access, location permission)

> **Security Tip**  
> Do not log into your Apple account on public devices. It is recommended to enable two-factor authentication to improve account security.

---

## Contributing Guidelines

Issues and Pull Requests are welcome!

### Submit an Issue
- Clearly describe the problem or feature request
- Provide reproduction steps (if it's a bug)
- Attach relevant logs and screenshots

### Submit a PR
1. Fork this project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## License

This project is licensed under the CC BY-NC 4.0 (Creative Commons Attribution-NonCommercial 4.0 International) license.

**You are free to:**
- ✅ Share — copy and redistribute the material in any medium or format
- ✅ Adapt — remix, transform, and build upon the material

**Under the following terms:**
- 📝 **Attribution** — You must give appropriate credit, provide a link to the license, and indicate if changes were made
- 🚫 **NonCommercial** — You may not use the material for commercial purposes

**Original Author:** Prelina Montelli

For full license terms, please visit: https://creativecommons.org/licenses/by-nc/4.0/
