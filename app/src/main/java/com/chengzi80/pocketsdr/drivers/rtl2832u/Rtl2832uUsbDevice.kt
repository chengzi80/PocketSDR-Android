package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

data class Rtl2832uUsbDevice(
    val device: UsbDevice,
    val usbInterface: UsbInterface,
    val bulkInEndpoint: UsbEndpoint,
    val bulkOutEndpoint: UsbEndpoint?
) {

    companion object {

        private const val RTL2832U_VID = 0x0BDA
        private const val RTL2832U_PID = 0x2838

        fun find(device: UsbDevice): Rtl2832uUsbDevice? {

            if (
                device.vendorId != RTL2832U_VID ||
                device.productId != RTL2832U_PID
            ) {
                return null
            }

            for (i in 0 until device.interfaceCount) {

                val usbInterface =
                    device.getInterface(i)

                var bulkIn: UsbEndpoint? = null
                var bulkOut: UsbEndpoint? = null

                for (j in 0 until usbInterface.endpointCount) {

                    val endpoint =
                        usbInterface.getEndpoint(j)

                    if (
                        endpoint.type ==
                        UsbConstants.USB_ENDPOINT_XFER_BULK
                    ) {

                        if (
                            endpoint.direction ==
                            UsbConstants.USB_DIR_IN
                        ) {
                            bulkIn = endpoint
                        } else if (
                            endpoint.direction ==
                            UsbConstants.USB_DIR_OUT
                        ) {
                            bulkOut = endpoint
                        }
                    }
                }

                if (bulkIn != null) {

                    return Rtl2832uUsbDevice(
                        device = device,
                        usbInterface = usbInterface,
                        bulkInEndpoint = bulkIn,
                        bulkOutEndpoint = bulkOut
                    )
                }
            }

            return null
        }
    }
}
