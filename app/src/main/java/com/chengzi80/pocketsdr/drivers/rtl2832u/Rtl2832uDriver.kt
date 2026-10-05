package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

class Rtl2832uDriver(
    private val usbManager: UsbManager
) {

    private var connection:
            UsbDeviceConnection? = null

    private var usbDevice:
            Rtl2832uUsbDevice? = null

    val isOpen: Boolean
        get() = connection != null

    val deviceInfo: Rtl2832uUsbDevice?
        get() = usbDevice

    fun open(
        device: Rtl2832uUsbDevice
    ): Boolean {

        close()

        val conn =
            usbManager.openDevice(
                device.device
            )
            ?: return false

        if (
            !conn.claimInterface(
                device.usbInterface,
                true
            )
        ) {

            conn.close()

            return false
        }

        connection = conn
        usbDevice = device

        return true
    }

    fun close() {

        try {

            val conn =
                connection

            val dev =
                usbDevice

            if (
                conn != null &&
                dev != null
            ) {

                conn.releaseInterface(
                    dev.usbInterface
                )
            }

        } catch (_: Exception) {
        }

        try {

            connection?.close()

        } catch (_: Exception) {
        }

        connection = null
        usbDevice = null
    }

    fun getConnection():
            UsbDeviceConnection? {

        return connection
    }

    fun controlTransfer(
        requestType: Int,
        request: Int,
        value: Int,
        index: Int,
        buffer: ByteArray?,
        length: Int,
        timeout: Int
    ): Int {

        val conn =
            connection
            ?: return -1

        return conn.controlTransfer(
            requestType,
            request,
            value,
            index,
            buffer,
            length,
            timeout
        )
    }
}
