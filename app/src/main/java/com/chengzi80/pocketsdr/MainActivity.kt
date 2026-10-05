package com.chengzi80.pocketsdr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chengzi80.pocketsdr.drivers.rtl2832u.Rtl2832uDriver
import com.chengzi80.pocketsdr.drivers.rtl2832u.Rtl2832uUsbDevice
import com.chengzi80.pocketsdr.core.SdrDeviceManager
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val USB_PERMISSION_ACTION =
            "com.chengzi80.pocketsdr.USB_PERMISSION"

        private const val TEST_FREQUENCY_HZ =
            100_000_000L

        private const val TEST_SAMPLE_RATE_HZ =
            2_048_000L
    }

    private lateinit var usbManager: UsbManager

    private lateinit var deviceManager:
        SdrDeviceManager

    private var rtlDriver:
        Rtl2832uDriver? = null

    private var rtlUsbDevice:
        Rtl2832uUsbDevice? = null

    private lateinit var statusText:
        TextView

    private lateinit var deviceText:
        TextView

    private lateinit var tunerText:
        TextView

    private lateinit var iqText:
        TextView

    private lateinit var initializeButton:
        Button

    private lateinit var startButton:
        Button

    private lateinit var stopButton:
        Button

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private var statsRunning = false

    private var lastByteCount = 0L

    private var lastTimeMs = 0L

    private val permissionIntent: PendingIntent by lazy {

        val intent =
            Intent(
                USB_PERMISSION_ACTION
            ).apply {

                setPackage(
                    packageName
                )
            }

        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_MUTABLE
        )
    }

    private val usbReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action !=
                    USB_PERMISSION_ACTION
                ) {
                    return
                }

                val device =
                    intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE
                    ) ?: return

                val granted =
                    intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                if (!granted) {

                    updateStatus(
                        "USB权限被拒绝"
                    )

                    return
                }

                if (
                    !usbManager.hasPermission(
                        device
                    )
                ) {

                    updateStatus(
                        "USB权限状态异常"
                    )

                    return
                }

                updateStatus(
                    "USB权限已获得，开始打开SDR..."
                )

                val rtl =
                    Rtl2832uUsbDevice.find(
                        device
                    )

                if (rtl == null) {

                    updateStatus(
                        "USB设备不是RTL2832U兼容设备"
                    )

                    return
                }

                rtlUsbDevice =
                    rtl

                initializeRtlDevice(
                    rtl
                )
            }
        }

    private val statsRunnable =
        object : Runnable {

            override fun run() {

                if (!statsRunning) {
                    return
                }

                val driver =
                    rtlDriver

                if (
                    driver == null
                ) {
                    return
                }

                val now =
                    System.currentTimeMillis()

                val bytes =
                    driver.getTotalSampleBytes()

                val elapsed =
                    now - lastTimeMs

                val delta =
                    bytes - lastByteCount

                val bytesPerSecond =
                    if (
                        elapsed > 0L
                    ) {

                        delta *
                            1000.0 /
                            elapsed

                    } else {
                        0.0
                    }

                lastByteCount =
                    bytes

                lastTimeMs =
                    now

                runOnUiThread {

                    iqText.text =
                        buildIqText(
                            bytes,
                            bytesPerSecond
                        )
                }

                handler.postDelayed(
                    this,
                    1000L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        usbManager =
            getSystemService(
                Context.USB_SERVICE
            ) as UsbManager

        createUi()

        registerUsbReceiver()

        deviceManager =
            SdrDeviceManager(
                this
            )

        deviceManager.setCallback(
            object :
                SdrDeviceManager.Callback {

                override fun onDeviceDetected(
                    device:
                    com.chengzi80.pocketsdr.core.SdrDevice
                ) {

                    runOnUiThread {

                        deviceText.text =
                            "USB设备：${device.name}\n" +
                            "VID:PID = %04X:%04X\n".format(
                                device.vendorId,
                                device.productId
                            ) +
                            "状态：已检测到"
                    }
                }

                override fun onDeviceRemoved(
                    device:
                    com.chengzi80.pocketsdr.core.SdrDevice
                ) {

                    runOnUiThread {

                        deviceText.text =
                            "USB设备：未连接"

                        tunerText.text =
                            "Tuner：未检测"

                        statusText.text =
                            "状态：SDR设备已拔出"
                    }
                }

                override fun onPermissionRequired(
                    device:
                    com.chengzi80.pocketsdr.core.SdrDevice
                ) {

                    runOnUiThread {

                        deviceText.text =
                            "USB设备：${device.name}"

                        statusText.text =
                            "状态：等待USB权限..."

                        usbManager.requestPermission(
                            device.usbDevice,
                            permissionIntent
                        )
                    }
                }

                override fun onStateChanged(
                    state:
                    com.chengzi80.pocketsdr.core.SdrDeviceState
                ) {

                    runOnUiThread {

                        statusText.text =
                            "状态：$state"
                    }
                }
            }
        )

        deviceManager.start()
    }

    private fun createUi() {

        val root =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    32,
                    48,
                    32,
                    32
                )
            }

        val title =
            TextView(
                this
            ).apply {

                text =
                    "PocketSDR"

                textSize =
                    30f
            }

        val subtitle =
            TextView(
                this
            ).apply {

                text =
                    "RTL2832U / R820T2 硬件测试"

                textSize =
                    17f

                setPadding(
                    0,
                    8,
                    0,
                    24
                )
            }

        deviceText =
            TextView(
                this
            ).apply {

                text =
                    "USB设备：等待连接"

                textSize =
                    16f
            }

        statusText =
            TextView(
                this
            ).apply {

                text =
                    "状态：等待SDR设备"

                textSize =
                    16f

                setPadding(
                    0,
                    16,
                    0,
                    16
                )
            }

        tunerText =
            TextView(
                this
            ).apply {

                text =
                    "Tuner：未检测"

                textSize =
                    16f

                setPadding(
                    0,
                    0,
                    0,
                    16
                )
            }

        initializeButton =
            Button(
                this
            ).apply {

                text =
                    "初始化 SDR"

                setOnClickListener {

                    initializeConnectedDevice()
                }
            }

        startButton =
            Button(
                this
            ).apply {

                text =
                    "开始接收 IQ"

                isEnabled =
                    false

                setOnClickListener {

                    startIqTest()
                }
            }

        stopButton =
            Button(
                this
            ).apply {

                text =
                    "停止接收"

                isEnabled =
                    false

                setOnClickListener {

                    stopIqTest()
                }
            }

        iqText =
            TextView(
                this
            ).apply {

                text =
                    buildIqText(
                        0L,
                        0.0
                    )

                textSize =
                    16f

                setPadding(
                    0,
                    24,
                    0,
                    0
                )
            }

        root.addView(
            title
        )

        root.addView(
            subtitle
        )

        root.addView(
            deviceText
        )

        root.addView(
            statusText
        )

        root.addView(
            tunerText
        )

        root.addView(
            initializeButton
        )

        root.addView(
            startButton
        )

        root.addView(
            stopButton
        )

        root.addView(
            iqText
        )

        val scroll =
            ScrollView(
                this
            ).apply {

                addView(
                    root
                )
            }

        setContentView(
            scroll
        )
    }

    private fun registerUsbReceiver() {

        val filter =
            IntentFilter(
                USB_PERMISSION_ACTION
            )

        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initializeConnectedDevice() {

        val rtl =
            rtlUsbDevice

        if (
            rtl == null
        ) {

            updateStatus(
                "请先连接RTL2832U USB设备"
            )

            return
        }

        initializeRtlDevice(
            rtl
        )
    }

    private fun initializeRtlDevice(
        device:
        Rtl2832uUsbDevice
    ) {

        updateStatus(
            "正在打开RTL2832U..."
        )

        val driver =
            Rtl2832uDriver(
                usbManager
            )

        rtlDriver =
            driver

        if (
            !driver.open(
                device
            )
        ) {

            updateStatus(
                "打开USB设备失败：${driver.getLastError()}"
            )

            return
        }

        updateStatus(
            "USB打开成功，正在初始化RTL2832U..."
        )

        Thread {

            val success =
                driver.initialize()

            runOnUiThread {

                if (!success) {

                    updateStatus(
                        "初始化失败：${driver.getLastError()}"
                    )

                    tunerText.text =
                        "Tuner：检测失败"

                    startButton.isEnabled =
                        false

                    return@runOnUiThread
                }

                val tuner =
                    driver.getTunerType()

                val address =
                    driver.getTunerI2cAddress()

                val lock =
                    driver.getTunerPllLock()

                tunerText.text =
                    "Tuner：$tuner\n" +
                    "I²C地址：0x%02X\n".format(
                        address
                    ) +
                    "PLL Lock：$lock\n" +
                    "频率：%.3f MHz\n".format(
                        driver.getFrequencyHz()
                            .toDouble() /
                            1_000_000.0
                    ) +
                    "采样率：%.3f MS/s".format(
                        driver.getSampleRateHz()
                            .toDouble() /
                            1_000_000.0
                    )

                updateStatus(
                    "SDR初始化成功，可以开始IQ测试"
                )

                startButton.isEnabled =
                    true
            }

        }.start()
    }

    private fun startIqTest() {

        val driver =
            rtlDriver

        if (
            driver == null
        ) {

            updateStatus(
                "SDR Driver不存在"
            )

            return
        }

        updateStatus(
            "正在启动USB IQ数据流..."
        )

        lastByteCount =
            0L

        lastTimeMs =
            System.currentTimeMillis()

        val started =
            driver.startSampleReading(

                onSamples = {
                    _,
                    _ ->
                },

                onError = {
                    error ->

                    runOnUiThread {

                        updateStatus(
                            "IQ读取错误：$error"
                        )

                        stopIqTestUi()
                    }
                }
            )

        if (!started) {

            updateStatus(
                "IQ启动失败：${driver.getLastError()}"
            )

            return
        }

        statsRunning =
            true

        startButton.isEnabled =
            false

        stopButton.isEnabled =
            true

        updateStatus(
            "IQ数据接收中..."
        )

        handler.post(
            statsRunnable
        )
    }

    private fun stopIqTest() {

        rtlDriver?.stopSampleReading()

        stopIqTestUi()

        updateStatus(
            "IQ数据接收已停止"
        )
    }

    private fun stopIqTestUi() {

        statsRunning =
            false

        handler.removeCallbacks(
            statsRunnable
        )

        startButton.isEnabled =
            rtlDriver?.isInitialized == true

        stopButton.isEnabled =
            false
    }

    private fun buildIqText(
        totalBytes: Long,
        bytesPerSecond: Double
    ): String {

        val totalMb =
            totalBytes /
                1024.0 /
                1024.0

        val rateMb =
            bytesPerSecond /
                1024.0 /
                1024.0

        return (
            "IQ数据\n" +
            "状态：${if (statsRunning) "接收中" else "未接收"}\n" +
            "累计：%.2f MB\n".format(
                Locale.US,
                totalMb
            ) +
            "实时速率：%.2f MB/s".format(
                Locale.US,
                rateMb
            )
        )
    }

    private fun updateStatus(
        message: String
    ) {

        runOnUiThread {

            statusText.text =
                "状态：$message"
        }
    }

    override fun onDestroy() {

        statsRunning =
            false

        handler.removeCallbacks(
            statsRunnable
        )

        try {
            unregisterReceiver(
                usbReceiver
            )
        } catch (_: Exception) {
        }

        try {
            deviceManager.stop()
        } catch (_: Exception) {
        }

        try {
            rtlDriver?.close()
        } catch (_: Exception) {
        }

        rtlDriver = null

        super.onDestroy()
    }
}
