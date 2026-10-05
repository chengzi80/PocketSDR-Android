package com.chengzi80.pocketsdr

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chengzi80.pocketsdr.core.SdrDevice
import com.chengzi80.pocketsdr.core.SdrDeviceManager
import com.chengzi80.pocketsdr.core.SdrDeviceState

class MainActivity : AppCompatActivity() {

    private lateinit var deviceManager: SdrDeviceManager

    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var detailText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createUi()

        deviceManager = SdrDeviceManager(this)

        deviceManager.onStateChanged = { state ->
            runOnUiThread {
                updateState(state)
            }
        }

        deviceManager.onDeviceChanged = { device ->
            runOnUiThread {
                updateDevice(device)
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

    private fun updateState(state: SdrDeviceState) {
        statusText.text = when (state) {
            SdrDeviceState.IDLE -> "等待连接 SDR 设备"
            SdrDeviceState.DETECTING -> "正在检测 SDR 设备..."
            SdrDeviceState.PERMISSION_REQUIRED -> "等待 USB 权限"
            SdrDeviceState.CONNECTING -> "正在连接 SDR 设备..."
            SdrDeviceState.CONNECTED -> "SDR 设备已连接"
            SdrDeviceState.DISCONNECTED -> "SDR 设备已断开"
            SdrDeviceState.ERROR -> "SDR 设备检测失败"
        }
    }

    private fun updateDevice(device: SdrDevice?) {
        if (device == null) {
            deviceText.text = "设备：未检测到"
            detailText.text = "请通过 USB-C OTG 连接：SDR设备"
        } else {
            deviceText.text = "设备：${device.name}"
            detailText.text = "USB SDR 设备已检测到"
        }
    }

    override fun onDestroy() {
        deviceManager.stop()
        super.onDestroy()
    }
}
