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

        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838

        private const val REALTEK_VID = 0x0BDA

        private const val HACKRF_VID = 0x1D50
        private const val HACKRF_PID = 0x6089

        private const val AIRSPY_VID = 0x1D50
        private const val AIRSPY_PID = 0x60A1

        private const val LIMESDR_VID = 0x0403
        private const val LIMESDR_PID = 0x601F

        private const val PLUTOSDR_VID = 0x0456
        private const val PLUTOSDR_PID = 0xB673

        private const val SDRPLAY_VID = 0x1DF7
    }

    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    var currentDevice: SdrDevice? = null
        private set

    var state: SdrDeviceState = SdrDeviceState.DISCONNECTED
        private set

    var onDeviceAttached: ((SdrDevice) -> Unit)? = null

    var onDeviceDetached: ((SdrDevice?) -> Unit)? = null

    var onPermissionRequired: ((SdrDevice) -> Unit)? = null

    var onDeviceConnected: ((SdrDevice) -> Unit)? = null

    var onDeviceDisconnected: (() -> Unit)? = null

    var onDeviceError: ((String) -> Unit)? = null

    private var receiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {

                    val device = getUsbDevice(intent)

                    if (device != null) {
                        handleDeviceAttached(device)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {

                    val device = getUsbDevice(intent)

                    if (device != null) {
                        handleDeviceDetached(device)
                    }
                }

                ACTION_USB_PERMISSION -> {

                    val device = getUsbDevice(intent)

                    if (device != null) {

                        val granted =
                            intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )

                        if (granted) {

                            state = SdrDeviceState.CONNECTING

                            currentDevice = identifyDevice(device)

                            state = SdrDeviceState.CONNECTED

                            currentDevice?.let {
                                onDeviceConnected?.invoke(it)
                            }

                        } else {

                            state = SdrDeviceState.ERROR

                            onDeviceError?.invoke(
                                "USB 权限被拒绝"
                            )
                        }
                    }
                }
            }
        }
    }

    fun start() {

        if (!receiverRegistered) {

            val filter = IntentFilter().apply {

                addAction(
                    UsbManager.ACTION_USB_DEVICE_ATTACHED
                )

                addAction(
                    UsbManager.ACTION_USB_DEVICE_DETACHED
                )

                addAction(ACTION_USB_PERMISSION)
            }

            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            receiverRegistered = true
        }

        scanDevices()
    }

    fun stop() {

        if (receiverRegistered) {

            context.unregisterReceiver(
                usbReceiver
            )

            receiverRegistered = false
        }
    }

    fun scanDevices() {

        state = SdrDeviceState.DETECTING

        val devices =
            usbManager.deviceList.values.toList()

        var detectedSdr: SdrDevice? = null

        for (device in devices) {

            val sdr = identifyDevice(device)

            if (sdr.supported) {

                detectedSdr = sdr
                break
            }
        }

        if (detectedSdr == null) {

            currentDevice = null
            state = SdrDeviceState.DISCONNECTED

            return
        }

        handleKnownDevice(
            detectedSdr
        )
    }

    private fun handleDeviceAttached(
        usbDevice: UsbDevice
    ) {

        val sdr =
            identifyDevice(usbDevice)

        if (!sdr.supported) {
            return
        }

        onDeviceAttached?.invoke(sdr)

        handleKnownDevice(sdr)
    }

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

    private fun handleKnownDevice(
        sdr: SdrDevice
    ) {

        currentDevice = sdr

        if (!usbManager.hasPermission(sdr.usbDevice)) {

            state =
                SdrDeviceState.PERMISSION_REQUIRED

            onPermissionRequired?.invoke(sdr)

            requestPermission(
                sdr.usbDevice
            )

            return
        }

        state =
            SdrDeviceState.CONNECTED

        onDeviceConnected?.invoke(sdr)
    }

    private fun requestPermission(
        device: UsbDevice
    ) {

        val permissionIntent =
            PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        usbManager.requestPermission(
            device,
            permissionIntent
        )
    }

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

            vid == SDRPLAY_VID -> {

                name =
                    "SDRplay"

                manufacturer =
                    "SDRplay"

                description =
                    "SDRplay Software Defined Radio"

                supported = true
            }

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
