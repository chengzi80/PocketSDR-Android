package com.chengzi80.pocketsdr.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat

class SdrDeviceManager(
    private val context: Context
) {

    companion object {

        private const val ACTION_USB_PERMISSION =
            "com.chengzi80.pocketsdr.USB_PERMISSION"

        // RTL-SDR / RTL2832U
        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838

        private const val REALTEK_VID = 0x0BDA

        // HackRF One
        private const val HACKRF_VID = 0x1D50
        private const val HACKRF_PID = 0x6089

        // Airspy
        private const val AIRSPY_VID = 0x1D50
        private const val AIRSPY_PID = 0x60A1

        // LimeSDR
        private const val LIMESDR_VID = 0x0403
        private const val LIMESDR_PID = 0x601F

        // PlutoSDR
        private const val PLUTOSDR_VID = 0x0456
        private const val PLUTOSDR_PID = 0xB673

        // SDRplay
        private const val SDRPLAY_VID = 0x1DF7
    }

    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    var currentDevice: SdrDevice? = null
        private set

    var state: SdrDeviceState =
        SdrDeviceState.DISCONNECTED
        private set

    var onDeviceAttached:
            ((SdrDevice) -> Unit)? = null

    var onDeviceDetached:
            ((SdrDevice?) -> Unit)? = null

    var onPermissionRequired:
            ((SdrDevice) -> Unit)? = null

    var onDeviceConnected:
            ((SdrDevice) -> Unit)? = null

    var onDeviceDisconnected:
            (() -> Unit)? = null

    var onDeviceError:
            ((String) -> Unit)? = null

    private var receiverRegistered = false

    private val usbReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {

                when (intent.action) {

                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {

                        val device =
                            getUsbDevice(intent)

                        if (device != null) {

                            handleDeviceAttached(
                                device
                            )
                        }
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {

                        val device =
                            getUsbDevice(intent)

                        if (device != null) {

                            handleDeviceDetached(
                                device
                            )
                        }
                    }

                    ACTION_USB_PERMISSION -> {

                        handlePermissionResult(
                            intent
                        )
                    }
                }
            }
        }

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
             * 使用 EXPORTED，让系统 USB 广播和
             * USB 权限 PendingIntent 都能够正常到达。
             */
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )

            receiverRegistered = true
        }

        scanDevices()
    }

    fun stop() {

        if (receiverRegistered) {

            try {

                context.unregisterReceiver(
                    usbReceiver
                )

            } catch (_: Exception) {
            }

            receiverRegistered = false
        }
    }

    /**
     * 扫描当前所有 USB 设备
     */
    fun scanDevices() {

        state =
            SdrDeviceState.DETECTING

        val devices =
            usbManager.deviceList.values.toList()

        var detectedSdr:
                SdrDevice? = null

        for (device in devices) {

            val sdr =
                identifyDevice(device)

            if (sdr.supported) {

                detectedSdr = sdr
                break
            }
        }

        if (detectedSdr == null) {

            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

            return
        }

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
            identifyDevice(usbDevice)

        if (!sdr.supported) {
            return
        }

        currentDevice = sdr

        onDeviceAttached?.invoke(
            sdr
        )

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

        if (
            current != null &&
            current.usbDevice.deviceId ==
            usbDevice.deviceId
        ) {

            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

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

        currentDevice = sdr

        /*
         * 已经获得 USB 权限
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
         * 尚未获得 USB 权限
         */
        state =
            SdrDeviceState.PERMISSION_REQUIRED

        onPermissionRequired?.invoke(
            sdr
        )

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

                setPackage(
                    context.packageName
                )

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
     * USB 权限回调
     */
    private fun handlePermissionResult(
        intent: Intent
    ) {

        val device =
            getUsbDevice(intent)

        /*
         * 没有拿到设备对象时，
         * 稍后重新扫描。
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
         * 不直接相信回调结果，
         * 再向 UsbManager 确认一次。
         */
        val hasPermission =
            usbManager.hasPermission(
                device
            )

        if (
            granted &&
            hasPermission
        ) {

            val sdr =
                identifyDevice(device)

            currentDevice =
                sdr

            state =
                SdrDeviceState.CONNECTED

            onDeviceConnected?.invoke(
                sdr
            )

        } else {

            /*
             * 某些 Android / 手机厂商上，
             * 权限结果广播到达时 hasPermission
             * 可能还没有立即更新。
             *
             * 延迟一点再检查一次。
             */
            state =
                SdrDeviceState.PERMISSION_REQUIRED

            android.os.Handler(
                android.os.Looper.getMainLooper()
            ).postDelayed({

                checkPermissionAgain(
                    device
                )

            }, 300)
        }
    }

    /**
     * 再次确认 USB 权限
     */
    private fun checkPermissionAgain(
        device: UsbDevice
    ) {

        /*
         * 设备已经被拔出
         */
        val stillExists =
            usbManager.deviceList.values.any {
                it.deviceId == device.deviceId
            }

        if (!stillExists) {

            currentDevice = null

            state =
                SdrDeviceState.DISCONNECTED

            onDeviceDisconnected?.invoke()

            return
        }

        /*
         * 权限已经成功获得
         */
        if (
            usbManager.hasPermission(
                device
            )
        ) {

            val sdr =
                identifyDevice(device)

            currentDevice =
                sdr

            state =
                SdrDeviceState.CONNECTED

            onDeviceConnected?.invoke(
                sdr
            )

            return
        }

        /*
         * 确实没有权限
         */
        state =
            SdrDeviceState.ERROR

        onDeviceError?.invoke(
            "USB 权限未获得，请重新连接 SDR 设备"
        )
    }

    /**
     * 权限广播异常情况下重新扫描
     */
    private fun rescanAfterPermission() {

        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).postDelayed({

            scanDevices()

        }, 300)
    }

    /**
     * 识别 SDR
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
             * RTL-SDR
             */
            vid == RTL2832U_VID &&
                    pid == RTL2832U_PID -> {

                name =
                    "RTL-SDR / RTL2832U"

                manufacturer =
                    "Realtek"

                description =
                    "RTL2832U USB SDR"

                supported = true
            }

            /*
             * HackRF One
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
             * Airspy
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
             * LimeSDR
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
             * PlutoSDR
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
             * SDRplay
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

            /*
             * 其他 Realtek USB SDR
             */
            vid == REALTEK_VID -> {

                name =
                    "Realtek USB SDR"

                manufacturer =
                    "Realtek"

                description =
                    "可能是 RTL-SDR 兼容设备"

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
