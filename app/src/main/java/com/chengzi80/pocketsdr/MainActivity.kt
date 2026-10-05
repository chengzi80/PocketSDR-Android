package com.chengzi80.pocketsdr

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chengzi80.pocketsdr.core.SdrDevice
import com.chengzi80.pocketsdr.core.SdrDeviceManager

class MainActivity : AppCompatActivity() {

private lateinit var deviceManager: SdrDevice

private lateinit var statusText: TextView
private lateinit var deviceText: TextView
private lateinit var detailText: TextView

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    createUi()

    deviceManager = SdrDeviceManager(this)

    deviceManager.onDeviceAttached = { device ->
        runOnUiThread {
            updateDevice(device)
            statusText.text = "检测到 SDR 设备"
        }
    }

    deviceManager.onDeviceDetached = {
        runOnUiThread {
            statusText.text = "SDR 设备已断开"
            deviceText.text = "设备：未检测到"
            detailText.text = "请通过 USB-C OTG 连接：SDR设备"
        }
    }

    deviceManager.onPermissionRequired = { device ->
        runOnUiThread {
            updateDevice(device)
            statusText.text = "等待 USB 权限"
            detailText.text = "请在系统弹窗中允许 USB 设备访问权限"
        }
    }

    deviceManager.onDeviceConnected = { device ->
        runOnUiThread {
            updateDevice(device)
            statusText.text = "SDR 设备已连接"
            detailText.text = "USB SDR 设备已准备就绪"
        }
    }

    deviceManager.onDeviceDisconnected = {
        runOnUiThread {
            statusText.text = "SDR 设备已断开"
            deviceText.text = "设备：未检测到"
            detailText.text = "请通过 USB-C OTG 连接：SDR设备"
        }
    }

    deviceManager.onDeviceError = { message ->
        runOnUiThread {
            statusText.text = "SDR 设备检测失败"
            detailText.text = message
        }
    }

    deviceManager.start()
}

private fun createUi() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setPadding(32, 48, 32, 32)
    }

    val titleText = TextView(this).apply {
        text = "PocketSDR"
        textSize = 26f
        gravity = Gravity.CENTER
    }

    val subtitleText = TextView(this).apply {
        text = "SDR设备检测"
        textSize = 20f
        gravity = Gravity.CENTER
        setPadding(0, 24, 0, 24)
    }

    statusText = TextView(this).apply {
        text = "正在检测 SDR 设备..."
        textSize = 18f
        setPadding(0, 24, 0, 16)
    }

    deviceText = TextView(this).apply {
        text = "设备：未检测到"
        textSize = 17f
        setPadding(0, 8, 0, 8)
    }

    detailText = TextView(this).apply {
        text = "请通过 USB-C OTG 连接：SDR设备"
        textSize = 16f
        setPadding(0, 8, 0, 8)
    }

    root.addView(titleText)
    root.addView(subtitleText)
    root.addView(statusText)
    root.addView(deviceText)
    root.addView(detailText)

    setContentView(root)
}

private fun updateDevice(device: SdrDevice?) {
    if (device == null) {
        deviceText.text = "设备：未检测到"
        detailText.text = "请通过 USB-C OTG 连接：SDR设备"
    } else {
        deviceText.text = "设备：${device.name}"
        detailText.text =
            "USB SDR 设备已检测到\n" +
            "厂商：${device.manufacturer}\n" +
            "VID：0x${device.vendorId.toString(16).uppercase().padStart(4, '0')}\n" +
            "PID：0x${device.productId.toString(16).uppercase().padStart(4, '0')}"
    }
}

override fun onDestroy() {
    deviceManager.stop()
    super.onDestroy()
}

}
