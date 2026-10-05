package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection

class Rtl2832uUsbControl(
    private val connection: UsbDeviceConnection
) {

    companion object {

        private const val USB_TIMEOUT = 1000

        // RTL2832U USB requests
        const val REQUEST_REG_BYTE =
            0x01

        const val REQUEST_REGS =
            0x02

        const val REQUEST_I2C =
            0x03

        const val REQUEST_I2C_DUMP =
            0x04

        const val REQUEST_IR =
            0x05

        const val REQUEST_EEPROM =
            0x06

        const val REQUEST_DEMOD =
            0x00

        const val REQUEST_I2C_WRITE =
            0x03

        const val REQUEST_I2C_READ =
            0x04

        const val USB_DIR_OUT =
            0x40

        const val USB_DIR_IN =
            0xC0
    }

    fun readReg(
        block: Int,
        addr: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) {
            return null
        }

        val buffer =
            ByteArray(length)

        val index =
            ((block and 0xFF) shl 8) or
                    (addr and 0xFF)

        val result =
            connection.controlTransfer(
                USB_DIR_IN,
                REQUEST_REGS,
                0,
                index,
                buffer,
                length,
                USB_TIMEOUT
            )

        if (result < 0) {
            return null
        }

        return buffer.copyOf(result)
    }

    fun writeReg(
        block: Int,
        addr: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) {
            return false
        }

        val index =
            ((block and 0xFF) shl 8) or
                    (addr and 0xFF)

        val result =
            connection.controlTransfer(
                USB_DIR_OUT,
                REQUEST_REGS,
                0,
                index,
                data,
                data.size,
                USB_TIMEOUT
            )

        return result == data.size
    }

    fun readRegByte(
        block: Int,
        addr: Int
    ): Int? {

        val result =
            readReg(
                block,
                addr,
                1
            )

        if (
            result == null ||
            result.isEmpty()
        ) {
            return null
        }

        return result[0].toInt() and 0xFF
    }

    fun writeRegByte(
        block: Int,
        addr: Int,
        value: Int
    ): Boolean {

        return writeReg(
            block,
            addr,
            byteArrayOf(
                (value and 0xFF).toByte()
            )
        )
    }

    fun readI2c(
        address: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) {
            return null
        }

        val buffer =
            ByteArray(length)

        val result =
            connection.controlTransfer(
                USB_DIR_IN,
                REQUEST_I2C_READ,
                0,
                address and 0xFF,
                buffer,
                length,
                USB_TIMEOUT
            )

        if (result < 0) {
            return null
        }

        return buffer.copyOf(result)
    }

    fun writeI2c(
        address: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) {
            return false
        }

        val result =
            connection.controlTransfer(
                USB_DIR_OUT,
                REQUEST_I2C_WRITE,
                0,
                address and 0xFF,
                data,
                data.size,
                USB_TIMEOUT
            )

        return result == data.size
    }
}
