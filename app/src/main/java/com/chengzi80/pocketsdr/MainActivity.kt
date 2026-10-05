package com.chengzi80.pocketsdr

import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chengzi80.pocketsdr.core.SdrDevice
import com.chengzi80.pocketsdr.core.SdrDeviceManager
import com.chengzi80.pocketsdr.drivers.rtl2832u.Rtl2832uDriver
import com.chengzi80.pocketsdr.drivers.rtl2832u.Rtl2832uUsbDevice
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var deviceManager: SdrDeviceManager

    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var detailText: TextView

    private var rtlDriver: Rtl2832uDriver? = null

    private var iqStartTimeMs = 0L

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
            closeRtlDriver()
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
                statusText.text = "USB 已连接，正在初始化 SDR..."
                detailText.text = "正在测试 RTL2832U USB 控制、Demodulator 和 R82xx Tuner"
            }

            if (
                device.vendorId == 0x0BDA &&
                device.productId == 0x2838
            ) {
                initializeRtl2832u(device)
            } else {
                runOnUiThread {
                    statusText.text = "SDR 设备已连接"
                    detailText.text = "USB SDR 设备已准备就绪"
                }
            }
        }

        deviceManager.onDeviceDisconnected = {
            closeRtlDriver()
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

    private fun initializeRtl2832u(device: SdrDevice) {
        thread(name = "rtl2832u-init") {
            try {
                val usbDevice =
                    Rtl2832uUsbDevice.find(device.usbDevice)

                if (usbDevice == null) {
                    runOnUiThread {
                        statusText.text = "RTL2832U USB接口检测失败"
                        detailText.text = "没有找到可用的 Bulk IN USB 接口"
                    }
                    return@thread
                }

                val usbManager =
                    getSystemService(USB_SERVICE) as UsbManager

                val driver =
                    Rtl2832uDriver(usbManager)

                rtlDriver = driver

                if (!driver.open(usbDevice)) {
                    val error =
                        driver.getLastError()
                            ?: "未知 USB 打开错误"

                    runOnUiThread {
                        statusText.text = "RTL2832U 打开失败"
                        detailText.text = error
                    }
                    return@thread
                }

                runOnUiThread {
                    statusText.text = "RTL2832U USB 控制正常，正在初始化..."
                }

                if (!driver.initialize()) {
                    val error =
                        driver.getLastError()
                            ?: "RTL2832U 初始化失败"

                    runOnUiThread {
                        statusText.text = "RTL2832U 初始化失败"
                        detailText.text = error
                    }

                    driver.close()
                    rtlDriver = null
                    return@thread
                }

                val tunerType =
                    driver.getTunerType().name

                val tunerAddress =
                    "0x" +
                        driver.getTunerI2cAddress()
                            .toString(16)
                            .uppercase()

                val pllLock =
                    if (driver.getTunerPllLock()) {
                        "已锁定"
                    } else {
                        "未锁定"
                    }

                runOnUiThread {
                    statusText.text = "RTL2832U + R82xx 初始化成功"
                    detailText.text =
                        "USB 控制传输：正常\n" +
                        "Demodulator：正常\n" +
                        "Tuner：$tunerType\n" +
                        "I²C 地址：$tunerAddress\n" +
                        "PLL：$pllLock\n" +
                        "采样率：\${driver.getSampleRateHz()} Hz\n" +
                        "中心频率：\${driver.getFrequencyHz()} Hz\n\n" +
                        "IQ 数据：正在启动..."
                }

                iqStartTimeMs = System.currentTimeMillis()

                val started =
                    driver.startSampleReading(
                        onSamples = { _, _ ->
                            val elapsedMs =
                                (System.currentTimeMillis() - iqStartTimeMs)
                                    .coerceAtLeast(1L)

                            val bytes =
                                driver.getTotalSampleBytes()

                            val bytesPerSecond =
                                bytes * 1000L / elapsedMs

                            runOnUiThread {
                                statusText.text = "RTL2832U + R82xx 初始化成功"
                                detailText.text =
                                    "USB 控制传输：正常\n" +
                                    "Demodulator：正常\n" +
                                    "Tuner：$tunerType\n" +
                                    "I²C 地址：$tunerAddress\n" +
                                    "PLL：$pllLock\n" +
                                    "采样率：\${driver.getSampleRateHz()} Hz\n" +
                                    "中心频率：\${driver.getFrequencyHz()} Hz\n\n" +
                                    "IQ 数据：正在接收\n" +
                                    "已接收：\${bytes} bytes\n" +
                                    "USB 吞吐：\${bytesPerSecond / 1024} KB/s"
                            }
                        },
                        onError = { error ->
                            runOnUiThread {
                                statusText.text = "IQ 数据读取失败"
                                detailText.text = error
                            }
                        }
                    )

                if (!started) {
                    val error =
                        driver.getLastError()
                            ?: "无法启动 USB IQ 读取"

                    runOnUiThread {
                        statusText.text = "IQ 数据读取启动失败"
                        detailText.text = error
                    }

                    return@thread
                }

            } catch (e: Exception) {
                closeRtlDriver()

                runOnUiThread {
                    statusText.text = "RTL2832U 硬件测试异常"
                    detailText.text =
                        e.message
                            ?: e.javaClass.simpleName
                }
            }
        }
    }

    private fun closeRtlDriver() {
        val driver = rtlDriver
        rtlDriver = null

        if (driver != null) {
            thread(name = "rtl2832u-close") {
                try {
                    driver.close()
                } catch (_: Exception) {
                }
            }
        }
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
        closeRtlDriver()
        deviceManager.stop()
        super.onDestroy()
    }
}
