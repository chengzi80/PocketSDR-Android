package com.chengzi80.pocketsdr.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class SdrDeviceManager(
    private val context: Context
) {

    companion object {

        /*
         * USB 权限广播
         */
        private const val ACTION_USB_PERMISSION =
            "com.chengzi80.pocketsdr.USB_PERMISSION"

        /*
         * RTL2832U 兼容 USB SDR
         *
         * 注意：
         * 0x0BDA:0x2838 表示 RTL2832U 类 USB 设备，
         * 不能仅凭这个 VID/PID 判断具体品牌。
         */
        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838

        /*
         * HackRF One
         */
        private const val HACKRF_VID = 0x1D50
        private const val HACKRF_PID = 0x6089

        /*
         * Airspy
         */
        private const val AIRSPY_VID = 0x1D50
        private const val AIRSPY_PID = 0x60A1

        /*
         * LimeSDR
         */
        private const val LIMESDR_VID = 0x0403
        private const val LIMESDR_PID = 0x601F

        /*
         * PlutoSDR
         */
        private const val PLUTOSDR_VID = 0x0456
        private const val PLUTOSDR_PID = 0xB673

        /*
         * SDRplay
         */
        private const val SDRPLAY_VID = 0x1DF7
    }

    private val usbManager =
        context.getSystemService(
            Context.USB_SERVICE
        ) as UsbManager

    /*
     * 当前 SDR 设备
     */
    var currentDevice: SdrDevice? = null
        private set

    /*
     * 当前设备状态
     */
    var state: SdrDeviceState =
        SdrDeviceState.DISCONNECTED
        private set

    /*
     * 设备插入
     */
    var onDeviceAttached:
            ((SdrDevice) -> Unit)? = null

    /*
     * 设备拔出
     */
    var onDeviceDetached:
            ((SdrDevice?) -> Unit)? = null

    /*
     * 需要 USB 权限
     */
    var onPermissionRequired:
            ((SdrDevice) -> Unit)? = null

    /*
     * 设备已经连接
     */
    var onDeviceConnected:
            ((SdrDevice) -> Unit)? = null

    /*
     * 设备已经断开
     */
    var onDeviceDisconnected:
            (() -> Unit)? = null

    /*
     * 错误
     */
    var onDeviceError:
            ((String) -> Unit)? = null

    /*
     * Receiver 是否已经注册
     */
    private var receiverRegistered = false

    /*
     * USB 广播接收器
     */
    private val usbReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {

                when (intent.action) {

                    /*
                     * USB 设备插入
                     */
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {

                        val device =
                            getUsbDevice(intent)

                        if (device != null) {

                            handleDeviceAttached(
                                device
                            )
                        }
                    }

                    /*
                     * USB 设备拔出
                     */
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {

                        val device =
                            getUsbDevice(intent)

                        if (device != null) {

                            handleDeviceDetached(
                                device
                            )
                        }
                    }

                    /*
                     * USB 权限结果
                     */
                    ACTION_USB_PERMISSION -> {

                        handlePermissionResult(
                            intent
                        )
                    }
                }
            }
        }

    /**
     * 启动设备管理器
     */
    fun start() {

        if (!receiverRegistered) {

            val filter =
                IntentFilter().apply {

                    addAction(
                        UsbManager.ACTION_USB_DEVICE_ATTACHED
                    )

                    addAction(
                        UsbManager.ACTION_USB_DEVICE_DETACHED
                    )

                    addAction(
                        ACTION_USB_PERMISSION
                    )
                }

            /*
             * USB 插入/拔出属于系统广播，
             * 因此使用 RECEIVER_EXPORTED。
             */
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            receiverRegistered = true
        }

        /*
         * APP 启动时主动扫描一次。
         *
         * 如果 SDR 在打开 APP 之前就已经插入，
         * 也能够被发现。
         */
        scanDevices()
    }

    /**
     * 停止设备管理器
     */
    fun stop() {

        if (receiverRegistered) {

            try {

                context.unregisterReceiver(
                    usbReceiver
                )

            } catch (_: Exception) {
                // Receiver 已经注销时忽略
            }

            receiverRegistered = false
        }
    }

    /**
     * 扫描当前 USB 设备
     */
    fun scanDevices() {

        state =
            SdrDeviceState.DETECTING

        val devices =
            usbManager.deviceList.values.toList()

        var detectedSdr:
                SdrDevice? = null

        /*
         * 遍历所有 USB 设备
         */
        for (device in devices) {

            val sdr =
                identifyDevice(device)

            if (sdr.supported) {

                detectedSdr = sdr

                break
            }
        }

        /*
         * 没有找到支持的 SDR
         */
        if (detectedSdr == null) {

            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

            return
        }

        /*
         * 找到 SDR
         */
        handleKnownDevice(
            detectedSdr
        )
    }

    /**
     * USB 设备插入
     */
    private fun handleDeviceAttached(
        usbDevice: UsbDevice
    ) {

        val sdr =
            identifyDevice(
                usbDevice
            )

        /*
         * 不是我们支持的 SDR
         */
        if (!sdr.supported) {
            return
        }

        currentDevice =
            sdr

        /*
         * 通知 UI：
         * 检测到 SDR
         */
        onDeviceAttached?.invoke(
            sdr
        )

        /*
         * 继续处理权限
         */
        handleKnownDevice(
            sdr
        )
    }

    /**
     * USB 设备拔出
     */
    private fun handleDeviceDetached(
        usbDevice: UsbDevice
    ) {

        val current =
            currentDevice

        /*
         * 判断拔出的是否是当前 SDR
         */
        if (
            current != null &&
            current.usbDevice.deviceId ==
            usbDevice.deviceId
        ) {

            /*
             * 清除当前设备
             */
            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

            /*
             * 通知 UI
             */
            onDeviceDetached?.invoke(
                current
            )

            onDeviceDisconnected?.invoke()
        }
    }

    /**
     * 处理已经识别的 SDR
     */
    private fun handleKnownDevice(
        sdr: SdrDevice
    ) {

        currentDevice =
            sdr

        /*
         * 已经拥有 USB 权限
         */
        if (
            usbManager.hasPermission(
                sdr.usbDevice
            )
        ) {

            state =
                SdrDeviceState.CONNECTED

            onDeviceConnected?.invoke(
                sdr
            )

            return
        }

        /*
         * 没有 USB 权限
         */
        state =
            SdrDeviceState.PERMISSION_REQUIRED

        onPermissionRequired?.invoke(
            sdr
        )

        /*
         * 请求权限
         */
        requestPermission(
            sdr.usbDevice
        )
    }

    /**
     * 请求 USB 权限
     */
    private fun requestPermission(
        device: UsbDevice
    ) {

        val permissionIntent =
            Intent(
                ACTION_USB_PERMISSION
            ).apply {

                /*
                 * 限制为本 APP
                 */
                setPackage(
                    context.packageName
                )

                /*
                 * 保存设备 ID，
                 * 方便异常情况下重新扫描。
                 */
                putExtra(
                    "device_id",
                    device.deviceId
                )
            }

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                device.deviceId,
                permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        usbManager.requestPermission(
            device,
            pendingIntent
        )
    }

    /**
     * USB 权限结果
     */
    private fun handlePermissionResult(
        intent: Intent
    ) {

        val device =
            getUsbDevice(intent)

        /*
         * 如果系统没有把设备对象带回来，
         * 延迟重新扫描。
         */
        if (device == null) {

            rescanAfterPermission()

            return
        }

        /*
         * Android 返回的权限结果
         */
        val granted =
            intent.getBooleanExtra(
                UsbManager.EXTRA_PERMISSION_GRANTED,
                false
            )

        /*
         * 再主动向 UsbManager 确认一次。
         *
         * 这样可以避免某些手机上：
         *
         * 点击允许
         * ↓
         * 回调已经到达
         * ↓
         * hasPermission() 暂时还是 false
         *
         * 导致界面一直卡在“等待 USB 权限”。
         */
        val hasPermission =
            usbManager.hasPermission(
                device
            )

        if (
            granted &&
            hasPermission
        ) {

            connectAfterPermission(
                device
            )

            return
        }

        /*
         * 某些手机权限状态更新可能存在
         * 极短的延迟。
         *
         * 300ms 后再检查一次。
         */
        state =
            SdrDeviceState.PERMISSION_REQUIRED

        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            checkPermissionAgain(
                device
            )

        }, 300)
    }

    /**
     * 获得权限后连接
     */
    private fun connectAfterPermission(
        device: UsbDevice
    ) {

        val sdr =
            identifyDevice(
                device
            )

        /*
         * 再确认一次是否还是支持的 SDR
         */
        if (!sdr.supported) {

            state =
                SdrDeviceState.ERROR

            onDeviceError?.invoke(
                "无法识别该 SDR 设备"
            )

            return
        }

        currentDevice =
            sdr

        state =
            SdrDeviceState.CONNECTED

        /*
         * 通知 MainActivity
         */
        onDeviceConnected?.invoke(
            sdr
        )
    }

    /**
     * 延迟再次检查 USB 权限
     */
    private fun checkPermissionAgain(
        device: UsbDevice
    ) {

        /*
         * 先确认设备还在不在
         */
        val stillExists =
            usbManager.deviceList.values.any {

                it.deviceId ==
                        device.deviceId
            }

        /*
         * 设备已经被拔出
         */
        if (!stillExists) {

            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

            onDeviceDisconnected?.invoke()

            return
        }

        /*
         * USB 权限已经获得
         */
        if (
            usbManager.hasPermission(
                device
            )
        ) {

            connectAfterPermission(
                device
            )

            return
        }

        /*
         * 权限确实没有获得
         */
        state =
            SdrDeviceState.ERROR

        onDeviceError?.invoke(
            "USB 权限未获得，请重新连接 SDR 设备"
        )
    }

    /**
     * 权限回调没有设备对象时，
     * 延迟重新扫描。
     */
    private fun rescanAfterPermission() {

        Handler(
            Looper.getMainLooper()
        ).postDelayed({

            scanDevices()

        }, 300)
    }

    /**
     * 识别 USB SDR
     */
    private fun identifyDevice(
        device: UsbDevice
    ): SdrDevice {

        val vid =
            device.vendorId

        val pid =
            device.productId

        var name =
            "未知 USB 设备"

        var manufacturer =
            "未知厂商"

        var description =
            "当前设备暂未识别"

        var supported =
            false

        when {

            /*
             * ========================================
             * RTL2832U 兼容 SDR
             * ========================================
             *
             * 注意：
             *
             * 这里不是把它称为“RTL-SDR”。
             *
             * 它可能是：
             *
             * 国产 DVB-T 电视棒
             * RTL2832U + R820T
             * RTL2832U + R820T2
             * RTL2832U + R860
             * 其他兼容方案
             *
             * 这里只确认 RTL2832U USB 接口。
             */
            vid == RTL2832U_VID &&
                    pid == RTL2832U_PID -> {

                name =
                    "RTL2832U 兼容 SDR"

                manufacturer =
                    "Realtek"

                description =
                    "RTL2832U USB 软件无线电设备"

                supported = true
            }

            /*
             * ========================================
             * HackRF One
             * ========================================
             */
            vid == HACKRF_VID &&
                    pid == HACKRF_PID -> {

                name =
                    "HackRF One"

                manufacturer =
                    "Great Scott Gadgets"

                description =
                    "HackRF Software Defined Radio"

                supported = true
            }

            /*
             * ========================================
             * Airspy
             * ========================================
             */
            vid == AIRSPY_VID &&
                    pid == AIRSPY_PID -> {

                name =
                    "Airspy"

                manufacturer =
                    "Airspy"

                description =
                    "Airspy Software Defined Radio"

                supported = true
            }

            /*
             * ========================================
             * LimeSDR
             * ========================================
             */
            vid == LIMESDR_VID &&
                    pid == LIMESDR_PID -> {

                name =
                    "LimeSDR"

                manufacturer =
                    "Lime Microsystems"

                description =
                    "LimeSDR Software Defined Radio"

                supported = true
            }

            /*
             * ========================================
             * PlutoSDR
             * ========================================
             */
            vid == PLUTOSDR_VID &&
                    pid == PLUTOSDR_PID -> {

                name =
                    "PlutoSDR"

                manufacturer =
                    "Analog Devices"

                description =
                    "ADALM-Pluto Software Defined Radio"

                supported = true
            }

            /*
             * ========================================
             * SDRplay
             * ========================================
             */
            vid == SDRPLAY_VID -> {

                name =
                    "SDRplay"

                manufacturer =
                    "SDRplay"

                description =
                    "SDRplay Software Defined Radio"

                supported = true
            }
        }

        return SdrDevice(
            usbDevice = device,
            name = name,
            manufacturer = manufacturer,
            description = description,
            vendorId = vid,
            productId = pid,
            supported = supported
        )
    }

    /**
     * Android 13+ / Android 12- 兼容获取 UsbDevice
     */
    @Suppress("DEPRECATION")
    private fun getUsbDevice(
        intent: Intent
    ): UsbDevice? {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE,
                UsbDevice::class.java
            )

        } else {

            intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE
            )
        }
    }
}
