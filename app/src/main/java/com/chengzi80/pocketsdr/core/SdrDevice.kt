package com.chengzi80.pocketsdr.core

import android.hardware.usb.UsbDevice

data class SdrDevice(
    val usbDevice: UsbDevice,
    val name: String,
    val manufacturer: String,
    val description: String,
    val vendorId: Int,
    val productId: Int,
    val supported: Boolean
)
