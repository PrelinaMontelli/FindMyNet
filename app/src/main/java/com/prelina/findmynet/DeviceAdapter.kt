package com.prelina.findmynet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * DeviceAdapter - 设备列表适配器
 * 显示所有iCloud设备的信息
 */
class DeviceAdapter(
    private val onDeviceClick: (DeviceData) -> Unit,
    private val onPlaySoundClick: (DeviceData) -> Unit,
    private val onShowOnMapClick: (DeviceData) -> Unit
) : ListAdapter<DeviceData, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    private var expandedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, position == expandedPosition)
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivDeviceIcon: ImageView = itemView.findViewById(R.id.ivDeviceIcon)
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceModel: TextView = itemView.findViewById(R.id.tvDeviceModel)
        private val ivBatteryIcon: ImageView = itemView.findViewById(R.id.ivBatteryIcon)
        private val tvBatteryLevel: TextView = itemView.findViewById(R.id.tvBatteryLevel)
        private val layoutLocationInfo: LinearLayout = itemView.findViewById(R.id.layoutLocationInfo)
        private val tvLocationCoordinates: TextView = itemView.findViewById(R.id.tvLocationCoordinates)
        private val tvLocationAccuracy: TextView = itemView.findViewById(R.id.tvLocationAccuracy)
        private val tvLocationTime: TextView = itemView.findViewById(R.id.tvLocationTime)
        private val tvDeviceStatus: TextView = itemView.findViewById(R.id.tvDeviceStatus)
        private val btnPlaySound: MaterialButton = itemView.findViewById(R.id.btnPlaySound)
        private val btnShowOnMap: MaterialButton = itemView.findViewById(R.id.btnShowOnMap)

        fun bind(device: DeviceData, isExpanded: Boolean) {
            // 设置设备名称和型号
            // 优先显示用户自定义的设备名称 (name)，而不是 Apple 返回的 deviceDisplayName
            tvDeviceName.text = device.name
            // 副标题显示设备型号
            tvDeviceModel.text = device.deviceDisplayName.ifEmpty { 
                device.modelDisplayName.ifEmpty { device.rawDeviceModel }
            }

            // 设置设备图标
            ivDeviceIcon.setImageResource(getDeviceIcon(device.deviceClass))

            // 设置电池信息
            tvBatteryLevel.text = "${device.batteryLevel}%"
            ivBatteryIcon.setImageResource(getBatteryIcon(device.batteryLevel, device.lowPowerMode))

            // 设置电池颜色
            val batteryColor = when {
                device.batteryLevel < 20 -> android.graphics.Color.RED
                device.batteryLevel < 50 -> android.graphics.Color.parseColor("#FFA500")
                else -> android.graphics.Color.parseColor("#4CAF50")
            }
            tvBatteryLevel.setTextColor(batteryColor)

            // 设置展开/收起状态
            layoutLocationInfo.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) {
                // 显示详细位置信息
                device.location?.let { location ->
                    tvLocationCoordinates.text = String.format(
                        "%.6f, %.6f",
                        location.latitude,
                        location.longitude
                    )
                    tvLocationAccuracy.text = "±${location.horizontalAccuracy.toInt()}m"
                    tvLocationTime.text = location.getFormattedTime()

                    // 设置状态文本
                    val statusText = when {
                        location.isOld -> "离线 (位置过旧)"
                        location.isInaccurate -> "在线 (位置不准确)"
                        !location.locationFinished -> "定位中..."
                        else -> "在线"
                    }
                    tvDeviceStatus.text = statusText

                    // 根据位置有效性设置文本颜色
                    val statusColor = when {
                        location.isOld || location.isInaccurate -> android.graphics.Color.parseColor("#FFA500")
                        location.locationFinished -> android.graphics.Color.parseColor("#4CAF50")
                        else -> android.graphics.Color.GRAY
                    }
                    tvDeviceStatus.setTextColor(statusColor)
                } ?: run {
                    tvLocationCoordinates.text = "无位置数据"
                    tvLocationAccuracy.text = "-"
                    tvLocationTime.text = "-"
                    tvDeviceStatus.text = when (device.deviceStatus) {
                        201 -> "设备离线"
                        203 -> "定位服务已关闭"
                        else -> "未知状态"
                    }
                    tvDeviceStatus.setTextColor(android.graphics.Color.GRAY)
                }
            }

            // 点击事件
            itemView.setOnClickListener {
                val previousExpandedPosition = expandedPosition
                if (isExpanded) {
                    expandedPosition = -1
                } else {
                    expandedPosition = bindingAdapterPosition
                }
                notifyItemChanged(previousExpandedPosition)
                notifyItemChanged(bindingAdapterPosition)
                onDeviceClick(device)
            }

            // 按钮点击事件
            btnPlaySound.setOnClickListener {
                onPlaySoundClick(device)
            }

            btnShowOnMap.setOnClickListener {
                onShowOnMapClick(device)
            }
        }

        /**
         * 根据设备类型获取图标
         */
        private fun getDeviceIcon(deviceClass: String): Int {
            return when (deviceClass.lowercase()) {
                "iphone" -> android.R.drawable.ic_menu_call
                "ipad" -> android.R.drawable.ic_dialog_map
                "mac" -> android.R.drawable.ic_menu_myplaces
                "watch" -> android.R.drawable.ic_menu_recent_history
                "airpods" -> android.R.drawable.ic_btn_speak_now
                else -> android.R.drawable.ic_menu_mylocation
            }
        }

        /**
         * 根据电量获取电池图标
         */
        private fun getBatteryIcon(level: Int, lowPowerMode: Boolean): Int {
            return when {
                lowPowerMode -> android.R.drawable.ic_lock_power_off
                level < 20 -> android.R.drawable.ic_dialog_alert
                else -> android.R.drawable.ic_lock_power_off
            }
        }
    }
}

/**
 * DiffUtil回调用于高效更新列表
 */
class DeviceDiffCallback : DiffUtil.ItemCallback<DeviceData>() {
    override fun areItemsTheSame(oldItem: DeviceData, newItem: DeviceData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DeviceData, newItem: DeviceData): Boolean {
        return oldItem == newItem
    }
}
