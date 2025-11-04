package com.prelina.findmynet.icloud3

/**
 * iCloud3 Constants - 完整移植自 const.py
 * 这个文件包含了所有iCloud3使用的常量定义
 */
object IC3Constants {
    
    // 版本信息
    const val VERSION = "3.2.4.2"
    const val VERSION_BETA = ""
    const val ICLOUD3_VERSION_MSG = "iCloud3 v$VERSION$VERSION_BETA"
    
    // 域名和平台
    const val DOMAIN = "icloud3"
    const val ICLOUD3 = "iCloud3"
    val PLATFORMS = listOf("sensor", "device_tracker")
    const val PLATFORM_DEVICE_TRACKER = "device_tracker"
    const val PLATFORM_SENSOR = "sensor"
    
    // 操作模式
    const val MODE_PLATFORM = -1
    const val MODE_INTEGRATION = 1
    
    // 基础常量
    const val HOME = "home"
    const val HOME_FNAME = "Home"
    const val NOT_HOME = "not_home"
    const val NOT_HOME_FNAME = "NotHome"
    const val AWAY = "Away"
    const val NEAR_HOME = "NearHome"
    const val NOT_SET = "not_set"
    const val NOT_SET_FNAME = "NotSet"
    const val UNKNOWN = "Unknown"
    const val STATIONARY = "stationary"
    const val STATIONARY_FNAME = "Stationary"
    val NOT_HOME_ZONES = listOf(NOT_HOME, AWAY, NOT_SET)
    
    // 位置状态
    const val AWAY_FROM = "AwayFrom"
    const val AWAY_FROM_HOME = "AwayFromHome"
    const val NEAR = "Near"
    const val TOWARDS = "Towards"
    const val TOWARDS_HOME = "TowardsHome"
    const val FAR_AWAY = "FarAway"
    const val INZONE = "inZone"
    const val INZONE_HOME = "inHomeZone"
    const val INZONE_STATZONE = "inStatZone"
    val INZONE_CODES = mapOf(
        INZONE to "Z",
        INZONE_HOME to "H",
        INZONE_STATZONE to "S"
    )
    const val STATZONE = "StatZone"
    const val PAUSED = "PAUSED"
    const val PAUSED_CAPS = "PAUSED"
    const val RESUMING = "RESUMING"
    const val RESUMING_CAPS = "RESUMING"
    const val NEVER = "Never"
    const val ERROR = 0
    const val NONE = "none"
    const val NONE_FNAME = "None"
    const val SEARCH = "search"
    const val SEARCH_FNAME = "Search"
    const val VALID_DATA = 1
    
    // 追踪方法
    const val ICLOUD = "iCloud"
    const val FAMSHR = "iCloud"
    const val MOBAPP = "MobApp"
    const val NO_MOBAPP = "no_mobapp"
    const val IOSAPP = "iosapp"
    const val NO_IOSAPP = "no_iosapp"
    
    // Apple设备类型
    const val IPHONE_FNAME = "iPhone"
    const val WATCH_FNAME = "Watch-WiFi+Cell"
    const val WATCH_WIFI_FNAME = "Watch-WiFi"
    const val IPAD_FNAME = "iPad-WiFi"
    const val IPAD_CELL_FNAME = "iPad-WiFi+Cell"
    const val MAC_FNAME = "Mac"
    const val IPOD_FNAME = "iPod"
    const val AIRPODS_FNAME = "AirPods"
    const val OTHER_FNAME = "Other"
    
    const val IPHONE = "iphone"
    const val WATCH = "watch"
    const val WATCH_WIFI = "watch_wifi"
    const val IPAD = "ipad"
    const val IPAD_CELL = "ipad_cell"
    const val MAC = "mac"
    const val IPOD = "ipod"
    const val AIRPODS = "airpods"
    const val OTHER = "other"
    
    val DEVICE_TYPES = listOf(
        IPHONE, IPAD, IPAD_CELL, WATCH, WATCH_WIFI,
        IPHONE_FNAME, IPAD_FNAME, IPAD_CELL_FNAME, WATCH_FNAME, WATCH_WIFI_FNAME,
        MAC, IPOD, AIRPODS,
        MAC_FNAME, IPOD_FNAME, AIRPODS_FNAME,
        ICLOUD
    )
    
    val DEVICE_TYPE_FNAMES = mapOf(
        IPHONE to IPHONE_FNAME,
        WATCH to WATCH_FNAME,
        WATCH_WIFI to WATCH_WIFI_FNAME,
        IPAD to IPAD_FNAME,
        IPAD_CELL to IPAD_CELL_FNAME,
        AIRPODS to AIRPODS_FNAME,
        MAC to MAC_FNAME,
        IPOD to IPOD_FNAME,
        OTHER to OTHER_FNAME
    )
    
    val DEVICE_TYPE_ICONS = mapOf(
        IPHONE to "mdi:cellphone",
        IPAD to "mdi:tablet",
        IPAD_CELL to "mdi:tablet",
        WATCH to "mdi:watch-variant",
        WATCH_WIFI to "mdi:watch-variant",
        AIRPODS to "mdi:earbuds-outline",
        MAC to "mdi:laptop",
        IPOD to "mdi:ipod",
        OTHER to "mdi:laptop"
    )
    
    val DEVICE_TYPE_INZONE_INTERVALS = mapOf(
        IPHONE to 120,
        IPAD to 120,
        IPAD_CELL to 120,
        WATCH to 15,
        WATCH_WIFI to 15,
        MAC to 120,
        AIRPODS to 15,
        NO_MOBAPP to 15,
        OTHER to 120
    )
    
    // Apple服务器端点
    val ICLOUD_SERVER_COUNTRY_CODE = listOf("cn", "CN")
    val APPLE_SERVER_ENDPOINT = mapOf(
        "home" to "https://www.icloud.com",
        "setup" to "https://setup.icloud.com/setup/ws/1",
        "auth" to "https://idmsa.apple.com/appleauth/auth",
        "auth_url" to "https://setup.icloud.com/setup/authenticate"
    )
    
    // 日期时间格式
    const val DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"
    const val DATETIME_ZERO = "0000-00-00 00:00:00"
    const val HHMMSS_ZERO = "00:00:00"
    const val HHMM_ZERO = "00:00"
    const val HIGH_INTEGER = 9999999999
    
    // 设备追踪状态
    const val TRACKING_NORMAL = 0
    const val TRACKING_PAUSED = 1
    const val TRACKING_RESUMED = 2
    
    // 追踪模式
    const val TRACK_DEVICE = "track"
    const val MONITOR_DEVICE = "monitor"
    const val INACTIVE_DEVICE = "inactive"
    val TRACKING_MODE_FNAME = mapOf(
        TRACK_DEVICE to "Tracked",
        MONITOR_DEVICE to "Monitored",
        INACTIVE_DEVICE to "INACTIVE"
    )
    
    // iCloud属性字段名
    const val ICLOUD_TIMESTAMP = "timeStamp"
    const val ICLOUD_HORIZONTAL_ACCURACY = "horizontalAccuracy"
    const val ICLOUD_VERTICAL_ACCURACY = "verticalAccuracy"
    const val ICLOUD_POSITION_TYPE = "positionType"
    const val ICLOUD_BATTERY_STATUS = "batteryStatus"
    const val ICLOUD_BATTERY_LEVEL = "batteryLevel"
    const val ICLOUD_DEVICE_CLASS = "deviceClass"
    const val ICLOUD_DEVICE_STATUS = "deviceStatus"
    const val ICLOUD_LOW_POWER_MODE = "lowPowerMode"
    const val ICLOUD_LOST_MODE_CAPABLE = "lostModeCapable"
    const val ID = "id"
    
    // 设备tracker属性
    const val LOCATION = "location"
    const val ATTRIBUTES = "attributes"
    const val RADIUS = "radius"
    const val NAME = "name"
    const val FRIENDLY_NAME = "friendly_name"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val POSITION_TYPE = "position_type"
    const val DEVICE_CLASS = "device_class"
    const val DEVICE_ID = "device_id"
    const val PASSIVE = "passive"
    
    // 实体属性
    const val DEVICE_TRACKER_STATE = "device_tracker_state"
    const val LOCATION_SOURCE = "location_source"
    const val NEAR_DEVICE_USED = "nearby_device_used"
    const val INTO_ZONE_DATETIME = "into_zone"
    const val FROM_ZONE = "from_zone"
    const val TIMESTAMP = "timestamp"
    const val TIMESTAMP_SECS = "timestamp_secs"
    const val TIMESTAMP_TIME = "timestamp_time"
    const val LOCATION_TIME = "location_time"
    const val TRACKING_METHOD = "data_source"
    const val DATA_SOURCE = "data_source"
    const val DATETIME = "date_time"
    const val AGE = "age"
    
    // 电池相关
    const val BATTERY_SOURCE = "battery_data_source"
    const val BATTERY_LEVEL = "battery_level"
    const val BATTERY_UPDATE_TIME = "battery_level_updated"
    const val BATTERY_ICLOUD = "icloud_battery_info"
    const val BATTERY_MOBAPP = "mobapp_battery_info"
    const val BATTERY_LATEST = "battery_info"
    const val BATTERY = "battery"
    const val BATTERY_STATUS = "battery_status"
    const val BATTERY_LEVEL_LOW = 20
    
    val BATTERY_STATUS_CODES = mapOf(
        "full" to "not charging",
        "charged" to "not charging",
        "charging" to "charging",
        "notcharging" to "not charging",
        "not charging" to "not charging",
        "not_charging" to "not charging",
        "unknown" to ""
    )
    
    val BATTERY_STATUS_FNAME = mapOf(
        "full, charging" to "Full, Charging",
        "full, not charging" to "Full, Not Charging",
        "charging" to "Charging",
        "not charging" to "Not Charging",
        "unknown" to "Charging Unknown"
    )
    
    // Waze相关
    const val WAZE = "waze"
    const val CALC = "calc"
    const val DIST = "dist"
    const val WAZE_METHOD = "waze_method"
    const val MAX_DISTANCE = "max_distance"
    const val WENT_3KM = "went_3km"
    const val WAZE_DISTANCE = "waze_distance"
    const val CALC_DISTANCE = "calc_distance"
    
    const val WAZE_USED = 0
    const val WAZE_NOT_USED = 1
    const val WAZE_PAUSED = 2
    const val WAZE_OUT_OF_RANGE = 3
    const val WAZE_NO_DATA = 4
    
    val WAZE_SERVERS_BY_COUNTRY_CODE = mapOf(
        "us" to "us",
        "ca" to "us",
        "il" to "il",
        "row" to "row"
    )
    
    val WAZE_SERVERS_FNAME = mapOf(
        "us" to "United States, Canada",
        "US" to "United States, Canada",
        "il" to "Israel",
        "IL" to "Israel",
        "row" to "Rest of the World",
        "ROW" to "Rest of the World"
    )
    
    // 区域相关
    const val ZONE = "zone"
    const val ZONE_INFO = "zone_info"
    const val ZONE_DNAME = "zone_dname"
    const val ZONE_FNAME = "zone_fname"
    const val ZONE_NAME = "zone_name"
    const val ZONE_DATETIME = "zone_changed"
    const val LAST_ZONE = "last_zone"
    const val LAST_ZONE_DNAME = "last_zone_dname"
    const val LAST_ZONE_FNAME = "last_zone_fname"
    const val LAST_ZONE_NAME = "last_zone_name"
    const val LAST_ZONE_DATETIME = "last_zone_changed"
    
    const val ZONE_DISTANCE = "zone_distance"
    const val ZONE_DISTANCE_M = "distance (meters)"
    const val ZONE_DISTANCE_M_EDGE = "distance_to_zone_edge (meters)"
    const val HOME_DISTANCE = "home_distance"
    const val DISTANCE_HOME = "distance_home"
    
    // 时间和距离
    const val TRAVEL_TIME = "travel_time"
    const val TRAVEL_TIME_MIN = "travel_time_min"
    const val TRAVEL_TIME_HHMM = "travel_time_hhmm"
    const val ARRIVAL_TIME = "arrival_time"
    const val DIR_OF_TRAVEL = "dir_of_travel"
    const val MOVED_DISTANCE = "moved_distance"
    const val MOVED_TIME_FROM = "moved_from"
    const val MOVED_TIME_TO = "moved_to"
    
    const val INTERVAL = "interval"
    const val LAST_UPDATE = "last_update"
    const val LAST_UPDATE_TIME = "last_update_time"
    const val LAST_UPDATE_DATETIME = "last_updated_date/time"
    const val NEXT_UPDATE = "next_update"
    const val NEXT_UPDATE_TIME = "next_update_time"
    const val NEXT_UPDATE_DATETIME = "next_update_date/time"
    const val LAST_LOCATED = "last_located"
    const val LAST_LOCATED_SECS = "last_located_secs"
    const val LAST_LOCATED_TIME = "last_located_time"
    const val LAST_LOCATED_DATETIME = "last_located_date/time"
    
    // GPS相关
    const val GPS = "gps"
    const val GPS_ACCURACY = "gps_accuracy"
    const val VERT_ACCURACY = "vertical_accuracy"
    const val ALTITUDE = "altitude"
    
    // 设备状态
    const val DEVICE_STATUS = "device_status"
    const val LOW_POWER_MODE = "low_power_mode"
    const val RAW_MODEL = "raw_model"
    const val MODEL = "model"
    const val MODEL_DISPLAY_NAME = "model_display_name"
    
    val DEVICE_STATUS_CODES = mapOf(
        "200" to "Online",
        "201" to "Offline",
        "203" to "Pending",
        "204" to "Unregistered",
        "0" to "Unknown"
    )
    
    val DEVICE_STATUS_ONLINE = listOf(200, 203, 204, 0)
    const val DEVICE_STATUS_OFFLINE = 201
    const val DEVICE_STATUS_PENDING = 203
    
    // 触发器
    const val TRIGGER = "trigger"
    const val TRACKING = "tracking"
    const val DEVICENAME_MOBAPP = "mobapp_device"
    const val AUTHENTICATED = "authenticated"
    const val ALERT = "alert"
    
    // Mobile App触发器
    const val BACKGROUND_FETCH = "Background Fetch"
    const val BKGND_FETCH = "Bkgnd Fetch"
    const val GEOGRAPHIC_REGION_ENTERED = "Geographic Region Entered"
    const val GEOGRAPHIC_REGION_EXITED = "Geographic Region Exited"
    const val IBEACON_REGION_ENTERED = "iBeacon Region Entered"
    const val IBEACON_REGION_EXITED = "iBeacon Region Exited"
    const val REGION_ENTERED = "Region Entered"
    const val REGION_EXITED = "Region Exited"
    const val ENTER_ZONE = "Enter Zone"
    const val EXIT_ZONE = "Exit Zone"
    const val INITIAL = "Initial"
    const val MANUAL = "Manual"
    const val LAUNCH = "Launch"
    const val PERIODIC = "Periodic"
    const val SIGNIFICANT_LOC_CHANGE = "Significant Location Change"
    const val SIGNIFICANT_LOC_UPDATE = "Significant Location Update"
    const val SIG_LOC_CHANGE = "Sig Loc Change"
    const val PUSH_NOTIFICATION = "Push Notification"
    const val REQUEST_MOBAPP_LOC = "Request MobApp Loc"
    const val MOBAPP_LOC_CHANGE = "MobApp Loc Change"
    const val SIGNALED = "Signaled"
    
    val MOBAPP_TRIGGER_ABBREVIATIONS = mapOf(
        GEOGRAPHIC_REGION_ENTERED to ENTER_ZONE,
        GEOGRAPHIC_REGION_EXITED to EXIT_ZONE,
        IBEACON_REGION_ENTERED to ENTER_ZONE,
        IBEACON_REGION_EXITED to EXIT_ZONE,
        SIGNIFICANT_LOC_CHANGE to SIG_LOC_CHANGE,
        SIGNIFICANT_LOC_UPDATE to SIG_LOC_CHANGE,
        PUSH_NOTIFICATION to REQUEST_MOBAPP_LOC,
        BACKGROUND_FETCH to BKGND_FETCH
    )
    
    val MOBAPP_TRIGGERS_VERIFY_LOCATION = listOf(
        INITIAL,
        LAUNCH,
        SIGNALED,
        MANUAL,
        MOBAPP_LOC_CHANGE,
        BKGND_FETCH,
        SIG_LOC_CHANGE,
        REQUEST_MOBAPP_LOC
    )
    
    val MOBAPP_TRIGGERS_ENTER = listOf(ENTER_ZONE)
    val MOBAPP_TRIGGERS_EXIT = listOf(EXIT_ZONE)
    val MOBAPP_TRIGGERS_ENTER_EXIT = listOf(ENTER_ZONE, EXIT_ZONE)
    
    // HTTP响应代码
    val HTTP_RESPONSE_CODES = mapOf(
        -2 to "Apple Server not Available (Connection Error)",
        200 to "iCloud Server Response",
        201 to "Device Offline",
        204 to "Verification Code Accepted",
        302 to "Apple Server not Available (Connection Refused or Other Error)",
        400 to "Invalid Verification Code",
        401 to "INVALID USERNAME/PASSWORD",
        403 to "Verification Code Requested",
        404 to "Apple http Error, Web Page not Found",
        409 to "Valid Username/Password",
        421 to "Verification Code May Be Needed",
        421.1 to "INVALID USERNAME/PASSWORD",
        450 to "Verification Code May Be Needed",
        500 to "Verification Code May Be Needed",
        503 to "Apple Server Refused Password Validation Request, Retry Later"
    )
    
    // 配置参数
    const val CONF_VERSION = "version"
    const val CONF_IC3_VERSION = "ic3_version"
    const val CONF_USERNAME = "username"
    const val CONF_PASSWORD = "password"
    const val CONF_TOTP_KEY = "totp_key"
    const val CONF_LOCATE_ALL = "locate_all"
    const val CONF_APPLE_ACCOUNTS = "apple_accounts"
    const val CONF_DEVICES = "devices"
    const val CONF_DATA_SOURCE = "data_source"
    const val CONF_VERIFICATION_CODE = "verification_code"
    const val CONF_ENCODE_PASSWORD = "encode_password"
    
    const val CONF_DEVICENAME = "device_name"
    const val CONF_IC3_DEVICENAME = "ic3_devicename"
    const val CONF_FNAME = "fname"
    const val CONF_APPLE_ACCOUNT = "apple_account"
    const val CONF_ICLOUD_DEVICENAME = "famshr_devicename"
    const val CONF_ICLOUD_DEVICE_ID = "famshr_device_id"
    const val CONF_FAMSHR_DEVICENAME = "famshr_devicename"
    const val CONF_FAMSHR_DEVICE_ID = "famshr_device_id"
    const val CONF_RAW_MODEL = "raw_model"
    const val CONF_MODEL = "model"
    const val CONF_MODEL_DISPLAY_NAME = "model_display_name"
    const val CONF_MOBILE_APP_DEVICE = "mobile_app_device"
    const val CONF_PICTURE = "picture"
    const val CONF_ICON = "icon"
    const val CONF_TRACKING_MODE = "tracking_mode"
    const val CONF_DEVICE_TYPE = "device_type"
    const val CONF_INZONE_INTERVAL = "inzone_interval"
    const val CONF_FIXED_INTERVAL = "fixed_interval"
    const val CONF_TRACK_FROM_BASE_ZONE_USED = "track_from_base_zone_used"
    const val CONF_TRACK_FROM_BASE_ZONE = "track_from_base_zone"
    const val CONF_TRACK_FROM_HOME_ZONE = "track_from_home_zone"
    const val CONF_TRACK_FROM_ZONES = "track_from_zones"
    const val CONF_LOG_ZONES = "log_zones"
    
    // 通用配置参数
    const val CONF_UNIT_OF_MEASUREMENT = "unit_of_measurement"
    const val CONF_TIME_FORMAT = "time_format"
    const val CONF_MAX_INTERVAL = "max_interval"
    const val CONF_OFFLINE_INTERVAL = "offline_interval"
    const val CONF_EXIT_ZONE_INTERVAL = "exit_zone_interval"
    const val CONF_MOBAPP_ALIVE_INTERVAL = "mobapp_alive_interval"
    const val CONF_GPS_ACCURACY_THRESHOLD = "gps_accuracy_threshold"
    const val CONF_OLD_LOCATION_THRESHOLD = "old_location_threshold"
    const val CONF_OLD_LOCATION_ADJUSTMENT = "old_location_adjustment"
    const val CONF_TRAVEL_TIME_FACTOR = "travel_time_factor"
    const val CONF_TFZ_TRACKING_MAX_DISTANCE = "tfz_tracking_max_distance"
    const val CONF_PASSTHRU_ZONE_TIME = "passthru_zone_time"
    const val CONF_LOG_LEVEL = "log_level"
    const val CONF_LOG_LEVEL_DEVICES = "log_level_devices"
    const val CONF_DISPLAY_GPS_LAT_LONG = "display_gps_lat_long"
    
    // 区域配置参数
    const val CONF_DEVICE_TRACKER_STATE_SOURCE = "device_tracker_state_source"
    const val CONF_DISPLAY_ZONE_FORMAT = "display_zone_format"
    const val CONF_CENTER_IN_ZONE = "center_in_zone"
    const val CONF_DISCARD_POOR_GPS_INZONE = "discard_poor_gps_inzone"
    const val CONF_DISTANCE_BETWEEN_DEVICES = "distance_between_devices"
    
    // Waze配置参数
    const val CONF_WAZE_USED = "waze_used"
    const val CONF_WAZE_REGION = "waze_region"
    const val CONF_WAZE_SERVER = "waze_region"
    const val CONF_WAZE_MAX_DISTANCE = "waze_max_distance"
    const val CONF_WAZE_MIN_DISTANCE = "waze_min_distance"
    const val CONF_WAZE_REALTIME = "waze_realtime"
    const val CONF_WAZE_HISTORY_DATABASE_USED = "waze_history_database_used"
    const val CONF_WAZE_HISTORY_MAX_DISTANCE = "waze_history_max_distance"
    const val CONF_WAZE_HISTORY_TRACK_DIRECTION = "waze_history_track_direction"
    
    // 静止区域配置参数
    const val CONF_STAT_ZONE_FNAME = "stat_zone_fname"
    const val CONF_STAT_ZONE_STILL_TIME = "stat_zone_still_time"
    const val CONF_STAT_ZONE_INZONE_INTERVAL = "stat_zone_inzone_interval"
    const val CONF_STAT_ZONE_BASE_LATITUDE = "stat_zone_base_latitude"
    const val CONF_STAT_ZONE_BASE_LONGITUDE = "stat_zone_base_longitude"
    
    // 传感器
    const val CONF_SENSORS = "sensors"
    const val BADGE = "badge"
    const val INFO = "info"
    const val PICTURE = "entity_picture"
    const val ICON = "icon"
    const val POLL_COUNT = "poll_count"
    const val ICLOUD3_VERSION = "icloud3_version"
    const val EVENT_LOG = "event_log"
    
    // 其他常量
    const val NEAR_DEVICE_DISTANCE = 25  // 附近设备的距离阈值(米)
    const val PASS_THRU_ZONE_INTERVAL_SECS = 60  // 穿过区域的延迟时间
    const val STATZONE_RADIUS_1M = 1
    const val ICLOUD3_ERROR_MSG = "ICLOUD3 ERROR-SEE EVENT LOG"
    
    // 特殊字符 - 用于事件日志显示
    const val NBSP = "⠈"
    const val NBSP2 = "⠉"
    const val NBSP3 = "⠋"
    const val NBSP4 = "⠛"
    const val NBSP5 = "⠟"
    const val NBSP6 = "⠿"
    const val CRLF = "⣇"
    const val NL = "\n"
    const val DOT = "• "
    const val PDOT = "•"
    const val RARROW = " → "
    const val RARROW2 = "→"
    const val RED_X = ""
    const val YELLOW_ALERT = "❎ "
    const val RED_ALERT = "⛔ "
    const val RED_STOP = "🛑"
    const val CHECK_MARK = "✓ "
    const val INFO_SEPARATOR = "/"
    const val BLANK_SENSOR_FIELD = "———"
    
    // 事件日志颜色标记
    const val EVLOG_GREEN = "^1^"
    const val EVLOG_VIOLET = "^2^"
    const val EVLOG_ORANGE = "^3^"
    const val EVLOG_PINK = "^4^"
    const val EVLOG_RED = "^5^"
    const val EVLOG_BLUE = "^6^"
    const val EVLOG_TIME_RECD = "^t^"
    const val EVLOG_UPDATE_HDR = "^u^"
    const val EVLOG_UPDATE_START = "^s^"
    const val EVLOG_UPDATE_END = "^c^"
    const val EVLOG_ERROR = "^e^"
    const val EVLOG_ALERT = "^a^"
    const val EVLOG_WARNING = "^w^"
    const val EVLOG_INIT_HDR = "^i^"
    const val EVLOG_HIGHLIGHT = "^h^"
    const val EVLOG_IC3_STARTING = "^i^"
    const val EVLOG_IC3_STAGE_HDR = "^g^"
    const val EVLOG_NOTICE = "^5^"
    const val EVLOG_TRACE = "^3^"
    const val EVLOG_DEBUG = "^6^"
    const val EVLOG_MONITOR = "^m^"
    
    // 默认配置值
    val DEFAULT_GENERAL_CONF = mapOf(
        CONF_LOG_LEVEL to "debug-auto-reset",
        CONF_UNIT_OF_MEASUREMENT to "mi",
        CONF_TIME_FORMAT to "12-hour",
        CONF_DISPLAY_ZONE_FORMAT to "fname",
        CONF_DEVICE_TRACKER_STATE_SOURCE to "ic3_fname",
        CONF_MAX_INTERVAL to 240,
        CONF_OFFLINE_INTERVAL to 20,
        CONF_EXIT_ZONE_INTERVAL to 3,
        CONF_MOBAPP_ALIVE_INTERVAL to 60,
        CONF_OLD_LOCATION_THRESHOLD to 3,
        CONF_OLD_LOCATION_ADJUSTMENT to 0,
        CONF_GPS_ACCURACY_THRESHOLD to 100,
        CONF_DISPLAY_GPS_LAT_LONG to true,
        CONF_TRAVEL_TIME_FACTOR to 0.5,
        CONF_TFZ_TRACKING_MAX_DISTANCE to 8,
        CONF_PASSTHRU_ZONE_TIME to 0.5,
        CONF_TRACK_FROM_BASE_ZONE_USED to true,
        CONF_TRACK_FROM_BASE_ZONE to HOME,
        CONF_TRACK_FROM_HOME_ZONE to true,
        CONF_CENTER_IN_ZONE to false,
        CONF_DISCARD_POOR_GPS_INZONE to false,
        CONF_DISTANCE_BETWEEN_DEVICES to true,
        CONF_WAZE_USED to true,
        CONF_WAZE_REGION to "us",
        CONF_WAZE_MIN_DISTANCE to 1,
        CONF_WAZE_MAX_DISTANCE to 1000,
        CONF_WAZE_REALTIME to false,
        CONF_WAZE_HISTORY_DATABASE_USED to true,
        CONF_WAZE_HISTORY_MAX_DISTANCE to 20,
        CONF_WAZE_HISTORY_TRACK_DIRECTION to "north_south",
        CONF_STAT_ZONE_FNAME to "StatZon#",
        CONF_STAT_ZONE_STILL_TIME to 8,
        CONF_STAT_ZONE_INZONE_INTERVAL to 30,
        CONF_STAT_ZONE_BASE_LATITUDE to 1.0,
        CONF_STAT_ZONE_BASE_LONGITUDE to 0.0
    )
    
    val DEFAULT_DEVICE_CONF = mapOf(
        CONF_IC3_DEVICENAME to " ",
        CONF_FNAME to "",
        CONF_PICTURE to "None",
        CONF_ICON to "mdi:account",
        CONF_DEVICE_TYPE to IPHONE,
        CONF_INZONE_INTERVAL to 120,
        CONF_FIXED_INTERVAL to 0,
        CONF_TRACKING_MODE to TRACK_DEVICE,
        CONF_APPLE_ACCOUNT to "",
        CONF_FAMSHR_DEVICENAME to "None",
        CONF_FAMSHR_DEVICE_ID to "",
        CONF_RAW_MODEL to "",
        CONF_MODEL to "",
        CONF_MODEL_DISPLAY_NAME to "",
        CONF_MOBILE_APP_DEVICE to "None",
        CONF_TRACK_FROM_BASE_ZONE to HOME,
        CONF_TRACK_FROM_ZONES to listOf(HOME),
        CONF_LOG_ZONES to listOf("none")
    )
    
    // 重试间隔范围表
    val RETRY_INTERVAL_RANGE_1 = mapOf(
        0 to 0.25,
        4 to 0.5,
        8 to 1.0,
        12 to 5.0,
        16 to 15.0,
        20 to 30.0,
        24 to 60.0
    )
    
    val RETRY_INTERVAL_RANGE_2 = mapOf(
        0 to 0.5,
        4 to 2.0,
        8 to 30.0,
        12 to 60.0,
        16 to 60.0
    )
    
    const val OLD_LOCATION_CNT = 1.1
    const val AUTH_ERROR_CNT = 1.2
    const val MOBAPP_REQUEST_LOC_CNT = 2.1
}
