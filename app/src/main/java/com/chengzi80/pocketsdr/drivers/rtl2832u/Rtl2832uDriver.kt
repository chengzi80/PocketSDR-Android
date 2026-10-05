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

    private var control:
            Rtl2832uUsbControl? = null

    private var demodulator:
            Rtl2832uDemodulator? = null

    val isOpen: Boolean
        get() = connection != null

    val isInitialized: Boolean
        get() =
            demodulator?.isInitialized == true

    val deviceInfo:
            Rtl2832uUsbDevice?
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

        control =
            Rtl2832uUsbControl(
                conn
            )

        demodulator =
            Rtl2832uDemodulator(
                control!!
            )

        return true
    }

    fun initialize(): Boolean {

        val demod =
            demodulator
                ?: return false

        return demod.initialize()
    }

    fun getControl():
            Rtl2832uUsbControl? {

        return control
    }

    fun getConnection():
            UsbDeviceConnection? {

        return connection
    }

    fun resetDemodulator(): Boolean {

        return demodulator?.reset()
            ?: false
    }

    fun close() {

        try {

            demodulator?.reset()

        } catch (_: Exception) {
        }

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

        control = null

        demodulator = null
    }
}
