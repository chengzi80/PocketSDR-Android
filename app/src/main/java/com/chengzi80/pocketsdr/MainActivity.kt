package com.chengzi80.pocketsdr

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.chengzi80.pocketsdr.core.SdrDevice
import com.chengzi80.pocketsdr.core.SdrDeviceManager
import com.chengzi80.pocketsdr.core.SdrDeviceState

class MainActivity : AppCompatActivity() {

    private lateinit var rootView: View
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var scanButton: Button

    private lateinit var deviceManager: SdrDeviceManager

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setupTransparentSystemBars()

        createUi()

        deviceManager =
            SdrDeviceManager(this)

        setupDeviceManager()

        deviceManager.start()
    }

    private fun setupTransparentSystemBars() {

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.TRANSPARENT

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = true

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).isAppearanceLightNavigationBars = true
    }

    private fun createUi() {

        val scrollView =
            ScrollView(this)

        rootView =
            scrollView

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(20),
                    dp(40),
                    dp(20),
                    dp(30)
                )
            }

        val title =
            TextView(this).apply {

                text =
                    "PocketSDR"

                textSize =
                    30f

                setTextColor(
                    Color.BLACK
                )

                setPadding(
                    0,
                    dp(20),
                    0,
                    dp(8)
                )
            }

        val subtitle =
            TextView(this).apply {

                text =
                    "通用 SDR 软件无线电接收器"

                textSize =
                    16f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(25)
                )
            }

        statusText =
            TextView(this).apply {

                text =
                    "正在检测 SDR 设备..."

                textSize =
                    18f

                setTextColor(
                    Color.BLACK
                )

                setPadding(
                    0,
                    dp(20),
                    0,
                    dp(15)
                )
            }

        deviceText =
            TextView(this).apply {

                text =
                    ""

                textSize =
                    15f

                setTextColor(
                    Color.DKGRAY
                )

                setPadding(
                    0,
                    0,
                    0,
                    dp(20)
                )
            }

        scanButton =
            Button(this).apply {

                text =
                    "重新扫描 SDR 设备"

                setOnClickListener {

                    showTip(
                        "正在扫描 USB SDR 设备..."
                    )

                    deviceManager.scanDevices()
                }
            }

        container.addView(title)

        container.addView(subtitle)

        container.addView(statusText)

        container.addView(deviceText)

        container.addView(scanButton)

        scrollView.addView(
            container
        )

        setContentView(
            scrollView
        )

        ViewCompat.setOnApplyWindowInsetsListener(
            scrollView
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                0,
                systemBars.top,
                0,
                systemBars.bottom
            )

            insets
        }
    }

    private fun setupDeviceManager() {

        deviceManager.onDeviceAttached =
            { device ->

                runOnUiThread {

                    showTip(
                        "✓ 检测到 SDR 设备：${device.name}"
                    )

                    updateDeviceInfo(
                        device
                    )
                }
            }

        deviceManager.onPermissionRequired =
            { device ->

                runOnUiThread {

                    statusText.text =
                        "正在请求 USB 权限..."

                    deviceText.text =
                        buildDeviceInfo(
                            device
                        )
                }
            }

        deviceManager.onDeviceConnected =
            { device ->

                runOnUiThread {

                    statusText.text =
                        "✓ SDR 设备已连接"

                    updateDeviceInfo(
                        device
                    )

                    showTip(
                        "✓ ${device.name} 已连接"
                    )
                }
            }

        deviceManager.onDeviceDetached =
            { device ->

                runOnUiThread {

                    statusText.text =
                        "⚠ SDR 设备已断开"

                    deviceText.text =
                        "请通过 USB-C OTG 连接 SDR 设备"

                    showTip(
                        "⚠ SDR 设备已断开"
                    )
                }
            }

        deviceManager.onDeviceDisconnected =
            {

                runOnUiThread {

                    statusText.text =
                        "未检测到 SDR 设备"

                    deviceText.text =
                        "请通过 USB-C OTG 连接 SDR 设备"
                }
            }

        deviceManager.onDeviceError =
            { message ->

                runOnUiThread {

                    statusText.text =
                        "⚠ SDR 设备错误"

                    showTip(
                        message
                    )
                }
            }
    }

    private fun updateDeviceInfo(
        device: SdrDevice
    ) {

        statusText.text =
            "✓ SDR 设备已连接"

        deviceText.text =
            buildDeviceInfo(
                device
            )
    }

    private fun buildDeviceInfo(
        device: SdrDevice
    ): String {

        return """
            设备：${device.name}
            
            厂商：${device.manufacturer}
            
            类型：${device.description}
            
            USB VID：0x%04X
            
            USB PID：0x%04X
            
            状态：${deviceManager.stateText()}
        """.trimIndent().format(
            device.vendorId,
            device.productId
        )
    }

    private fun showTip(
        message: String
    ) {

        Snackbar
            .make(
                rootView,
                message,
                Snackbar.LENGTH_SHORT
            )
            .show()
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    override fun onDestroy() {

        deviceManager.stop()

        super.onDestroy()
    }
}

private fun SdrDeviceManager.stateText(): String {

    return when (state) {

        SdrDeviceState.DISCONNECTED ->
            "未连接"

        SdrDeviceState.DETECTING ->
            "正在检测"

        SdrDeviceState.PERMISSION_REQUIRED ->
            "等待 USB 权限"

        SdrDeviceState.CONNECTING ->
            "正在连接"

        SdrDeviceState.CONNECTED ->
            "已连接"

        SdrDeviceState.ERROR ->
            "错误"
    }
}
