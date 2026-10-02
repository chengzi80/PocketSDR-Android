package com.chengzi80.pocketsdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var statusText: TextView

    companion object {

        private const val ACTION_USB_PERMISSION =
            "com.chengzi80.pocketsdr.USB_PERMISSION"

        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838
    }

    private val usbReceiver = object : BroadcastReceiver {

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
                    "✓ USB 权限已授予"
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

    private fun buildUserInterface() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    40,
                    32,
                    32
                )
            }

        val title =
            TextView(this).apply {

                text = "PocketSDR"

                textSize = 30f
            }

        val subtitle =
            TextView(this).apply {

                text =
                    "V0.1 · RTL2832U USB 检测"

                textSize = 16f

                setPadding(
                    0,
                    8,
                    0,
                    24
                )
            }

        statusText =
            TextView(this).apply {

                textSize = 17f

                setPadding(
                    0,
                    16,
                    0,
                    16
                )
            }

        val scanButton =
            Button(this).apply {

                text = "重新扫描 USB"

                setOnClickListener {

                    scanUsbDevices()
                }
            }

        root.addView(title)

        root.addView(subtitle)

        root.addView(statusText)

        root.addView(scanButton)

        val scroll =
            ScrollView(this).apply {

                addView(root)
            }

        setContentView(scroll)
    }

    private fun scanUsbDevices() {

        val devices =
            usbManager.deviceList.values.toList()

        if (devices.isEmpty()) {

            statusText.text =
                """
                未发现 USB 设备。

                请通过 USB-C OTG 连接：

                RTL2832U + R820T2
                """.trimIndent()

            return
        }

        val rtlDevices =
            devices.filter {

                it.vendorId ==
                    RTL2832U_VID &&
                it.productId ==
                    RTL2832U_PID
            }

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
                发现 USB 设备，

                但是没有匹配常见
                RTL2832U ID。

                $details
                """.trimIndent()

            return
        }

        val device =
            rtlDevices.first()

        showDevice(
            device,
            "✓ 发现 RTL2832U"
        )

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
                    "目标硬件："
                )

                appendLine(
                    "RTL2832U + R820T2"
                )

                appendLine()

                appendLine(
                    "V0.1：USB 设备识别"
                )
            }
    }

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
