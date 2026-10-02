package com.chengzi80.pocketsdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var statusText: TextView

    companion object {

        private const val ACTION_USB_PERMISSION =
            "com.chengzi80.pocketsdr.USB_PERMISSION"

        /*
         * 当前 V0.1 暂时识别 RTL2832U。
         *
         * 后续会在这里扩展：
         * RTL-SDR
         * HackRF
         * Airspy
         * LimeSDR
         * PlutoSDR
         * 等其他 SDR。
         */
        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838
    }

    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context,
            intent: Intent
        ) {

            if (intent.action != ACTION_USB_PERMISSION) {
                return
            }

            val device =
                if (android.os.Build.VERSION.SDK_INT >= 33) {

                    intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE,
                        UsbDevice::class.java
                    )

                } else {

                    @Suppress("DEPRECATION")

                    intent.getParcelableExtra<UsbDevice>(
                        UsbManager.EXTRA_DEVICE
                    )
                }

            val granted =
                intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )

            if (granted && device != null) {

                showDevice(
                    device,
                    "✓ SDR 设备 USB 权限已授予"
                )

            } else {

                statusText.text =
                    "✗ USB 权限被拒绝"
            }
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        /*
         * Android 15 / targetSdk 35：
         *
         * 开启 Edge-to-Edge。
         *
         * 这样状态栏可以真正透明，
         * App 背景可以延伸到状态栏区域。
         *
         * 实际内容的位置由 WindowInsets 控制，
         * 因此不会遮挡系统时间、电量等信息。
         */
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        /*
         * 状态栏和导航栏透明。
         */
        window.statusBarColor =
            Color.TRANSPARENT

        window.navigationBarColor =
            Color.TRANSPARENT

        /*
         * 系统状态栏使用深色图标。
         *
         * 因为目前 PocketSDR 背景是白色，
         * 所以时间、Wi-Fi、电量等使用黑色/深色显示。
         */
        val controller =
            WindowCompat.getInsetsController(
                window,
                window.decorView
            )

        controller.isAppearanceLightStatusBars =
            true

        controller.isAppearanceLightNavigationBars =
            true

        usbManager =
            getSystemService(
                Context.USB_SERVICE
            ) as UsbManager

        val filter =
            IntentFilter(
                ACTION_USB_PERMISSION
            )

        registerReceiver(
            usbReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        )

        buildUserInterface()

        scanUsbDevices()
    }

    /**
     * 创建 PocketSDR 主界面
     */
    private fun buildUserInterface() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(32),
                    dp(24),
                    dp(32),
                    dp(32)
                )

                setBackgroundColor(
                    Color.WHITE
                )
            }

        /*
         * PocketSDR 标题
         */
        val title =
            TextView(this).apply {

                text =
                    "PocketSDR"

                textSize =
                    30f

                setTextColor(
                    Color.rgb(
                        70,
                        70,
                        70
                    )
                )
            }

        /*
         * V0.1 版本说明
         */
        val subtitle =
            TextView(this).apply {

                text =
                    "V0.1 · SDR 设备检测"

                textSize =
                    16f

                setTextColor(
                    Color.rgb(
                        90,
                        90,
                        90
                    )
                )

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(24)
                )
            }

        /*
         * USB / SDR 状态
         */
        statusText =
            TextView(this).apply {

                textSize =
                    17f

                setTextColor(
                    Color.rgb(
                        85,
                        85,
                        85
                    )
                )

                setPadding(
                    0,
                    dp(16),
                    0,
                    dp(16)
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            }

        /*
         * USB 扫描按钮
         */
        val scanButton =
            Button(this).apply {

                text =
                    "重新扫描 USB"

                setOnClickListener {

                    scanUsbDevices()
                }
            }

        root.addView(title)

        root.addView(subtitle)

        root.addView(statusText)

        root.addView(scanButton)

        /*
         * 使用 ScrollView，
         * 防止小屏手机内容超出屏幕。
         */
        val scroll =
            ScrollView(this).apply {

                setBackgroundColor(
                    Color.WHITE
                )

                addView(root)
            }

        /*
         * 关键部分：
         *
         * 状态栏保持透明，
         * 但是正文自动避开状态栏。
         *
         * 因此最终效果：
         *
         * ┌────────────────────┐
         * │ 18:53        33%  │
         * │                    │
         * │ PocketSDR          │
         * │                    │
         * └────────────────────┘
         *
         * 状态栏透明，
         * 但不会遮挡 PocketSDR。
         */
        ViewCompat.setOnApplyWindowInsetsListener(
            scroll
        ) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        setContentView(scroll)

        /*
         * 立即请求系统重新计算 Insets。
         */
        ViewCompat.requestApplyInsets(
            scroll
        )
    }

    /**
     * 扫描 USB SDR 设备
     */
    private fun scanUsbDevices() {

        val devices =
            usbManager.deviceList.values.toList()

        /*
         * 没有任何 USB 设备
         */
        if (devices.isEmpty()) {

            statusText.text =
                """
                未发现 SDR 设备。

                请通过 USB-C OTG 连接：

                SDR 设备
                """.trimIndent()

            return
        }

        /*
         * 当前 V0.1：
         *
         * 先识别 RTL2832U。
         *
         * 后续继续扩展其他 SDR。
         */
        val rtlDevices =
            devices.filter {

                it.vendorId ==
                    RTL2832U_VID &&
                it.productId ==
                    RTL2832U_PID
            }

        /*
         * 有 USB 设备，
         * 但当前还没有识别。
         */
        if (rtlDevices.isEmpty()) {

            val details =
                devices.joinToString(
                    separator = "\n\n"
                ) {

                    """
                    ${it.deviceName}

                    VID = ${hex(it.vendorId)}
                    PID = ${hex(it.productId)}

                    接口数 = ${it.interfaceCount}
                    """.trimIndent()
                }

            statusText.text =
                """
                发现 USB 设备。

                当前设备暂未识别为支持的 SDR。

                $details
                """.trimIndent()

            return
        }

        /*
         * 找到 RTL2832U
         */
        val device =
            rtlDevices.first()

        showDevice(
            device,
            "✓ 发现 SDR 设备"
        )

        /*
         * 检查 USB 权限
         */
        if (
            usbManager.hasPermission(
                device
            )
        ) {

            statusText.append(
                "\n\n✓ 已拥有 USB 权限"
            )

        } else {

            requestUsbPermission(
                device
            )
        }
    }

    /**
     * 请求 USB 权限
     */
    private fun requestUsbPermission(
        device: UsbDevice
    ) {

        val pendingIntent =
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(
                    ACTION_USB_PERMISSION
                ),
                PendingIntent.FLAG_IMMUTABLE
            )

        usbManager.requestPermission(
            device,
            pendingIntent
        )
    }

    /**
     * 显示 SDR 设备信息
     */
    private fun showDevice(
        device: UsbDevice,
        prefix: String
    ) {

        statusText.text =
            buildString {

                appendLine(prefix)

                appendLine()

                appendLine(
                    "设备：${device.deviceName}"
                )

                appendLine(
                    "VID：${hex(device.vendorId)}"
                )

                appendLine(
                    "PID：${hex(device.productId)}"
                )

                appendLine(
                    "接口数量：${device.interfaceCount}"
                )

                appendLine()

                appendLine(
                    "SDR 设备："
                )

                appendLine(
                    "RTL2832U"
                )

                appendLine()

                appendLine(
                    "V0.1：USB SDR 设备识别"
                )
            }
    }

    /**
     * dp 转 px
     */
    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }

    /**
     * VID / PID 转十六进制
     */
    private fun hex(
        value: Int
    ): String {

        return "0x%04X".format(
            value
        )
    }

    override fun onDestroy() {

        unregisterReceiver(
            usbReceiver
        )

        super.onDestroy()
    }
}
