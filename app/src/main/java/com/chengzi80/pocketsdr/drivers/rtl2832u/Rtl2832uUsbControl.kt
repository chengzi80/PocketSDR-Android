package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection

class Rtl2832uUsbControl(
    private val connection: UsbDeviceConnection
) {

    companion object {
        private const val CTRL_IN = 0xC0
        private const val CTRL_OUT = 0x40
        private const val CTRL_TIMEOUT = 300

        const val BLOCK_DEMOD = 0
        const val BLOCK_USB = 1
        const val BLOCK_SYS = 2
        const val BLOCK_TUNER = 3
        const val BLOCK_ROM = 4
        const val BLOCK_IR = 5
        const val BLOCK_IIC = 6

        const val REG_USB_SYSCTL = 0x2000
        const val REG_USB_CTRL = 0x2010
        const val REG_USB_STAT = 0x2014
        const val REG_USB_EPA_CFG = 0x2144
        const val REG_USB_EPA_CTL = 0x2148
        const val REG_USB_EPA_MAXPKT = 0x2158
        const val REG_USB_EPA_MAXPKT_2 = 0x215A
        const val REG_USB_EPA_FIFO_CFG = 0x2160

        const val REG_DEMOD_CTL = 0x3000
        const val REG_GPO = 0x3001
        const val REG_GPI = 0x3002
        const val REG_GPOE = 0x3003
        const val REG_GPD = 0x3004
        const val REG_SYSINTE = 0x3005
        const val REG_SYSINTS = 0x3006
        const val REG_GP_CFG0 = 0x3007
        const val REG_GP_CFG1 = 0x3008
        const val REG_SYSINTE_1 = 0x3009
        const val REG_SYSINTS_1 = 0x300A
        const val REG_DEMOD_CTL_1 = 0x300B

        private const val I2C_READ_LENGTH = 16
    }

    fun readRegister(
        block: Int,
        address: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) return null

        val buffer = ByteArray(length)
        val index = (block and 0xFF) shl 8

        val result = connection.controlTransfer(
            CTRL_IN,
            0,
            address and 0xFFFF,
            index,
            buffer,
            length,
            CTRL_TIMEOUT
        )

        return if (result == length) buffer else null
    }

    fun writeRegister(
        block: Int,
        address: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) return false

        val index = ((block and 0xFF) shl 8) or 0x10

        val result = connection.controlTransfer(
            CTRL_OUT,
            0,
            address and 0xFFFF,
            index,
            data,
            data.size,
            CTRL_TIMEOUT
        )

        return result == data.size
    }

    fun readRegisterByte(
        block: Int,
        address: Int
    ): Int? =
        readRegister(block, address, 1)
            ?.get(0)
            ?.toInt()
            ?.and(0xFF)

    fun writeRegisterByte(
        block: Int,
        address: Int,
        value: Int
    ): Boolean =
        writeRegister(
            block,
            address,
            byteArrayOf((value and 0xFF).toByte())
        )

    fun readRegister16(
        block: Int,
        address: Int
    ): Int? {

        val data =
            readRegister(block, address, 2)
                ?: return null

        return ((data[1].toInt() and 0xFF) shl 8) or
            (data[0].toInt() and 0xFF)
    }

    fun writeRegister16(
        block: Int,
        address: Int,
        value: Int
    ): Boolean =
        writeRegister(
            block,
            address,
            byteArrayOf(
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        )

    fun readDemodRegister(
        page: Int,
        address: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) return null

        val buffer = ByteArray(length)

        val value =
            ((address and 0xFF) shl 8) or 0x20

        val index =
            page and 0xFF

        val result = connection.controlTransfer(
            CTRL_IN,
            0,
            value,
            index,
            buffer,
            length,
            CTRL_TIMEOUT
        )

        return if (result == length) buffer else null
    }

    fun writeDemodRegister(
        page: Int,
        address: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) return false

        val value =
            ((address and 0xFF) shl 8) or 0x20

        val index =
            (page and 0xFF) or 0x10

        val result = connection.controlTransfer(
            CTRL_OUT,
            0,
            value,
            index,
            data,
            data.size,
            CTRL_TIMEOUT
        )

        return result == data.size
    }

    fun readDemodRegisterByte(
        page: Int,
        address: Int
    ): Int? =
        readDemodRegister(page, address, 1)
            ?.get(0)
            ?.toInt()
            ?.and(0xFF)

    fun writeDemodRegisterByte(
        page: Int,
        address: Int,
        value: Int
    ): Boolean =
        writeDemodRegister(
            page,
            address,
            byteArrayOf((value and 0xFF).toByte())
        )

    /*
     * RTL2832U I2C write.
     *
     * Important: for RTL2832U the I2C slave address is
     * the USB control-transfer wValue. The wIndex contains
     * only the IIC block plus the write flag (0x10).
     */
    fun i2cWrite(
        address: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) return false

        val index =
            ((BLOCK_IIC and 0xFF) shl 8) or 0x10

        val result = connection.controlTransfer(
            CTRL_OUT,
            0,
            address and 0xFF,
            index,
            data,
            data.size,
            CTRL_TIMEOUT
        )

        return result == data.size
    }

    /*
     * RTL2832U I2C read.
     *
     * The register number is written first, then the tuner
     * is read using the same I2C slave address.
     */
    fun i2cRead(
        address: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) return null
        if (length > I2C_READ_LENGTH) return null

        val buffer = ByteArray(length)

        val index =
            (BLOCK_IIC and 0xFF) shl 8

        val result = connection.controlTransfer(
            CTRL_IN,
            0,
            address and 0xFF,
            index,
            buffer,
            length,
            CTRL_TIMEOUT
        )

        return if (result == length) buffer else null
    }

    fun i2cWriteRegister(
        address: Int,
        register: Int,
        value: Int
    ): Boolean =
        i2cWrite(
            address,
            byteArrayOf(
                (register and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        )

    fun i2cReadRegister(
        address: Int,
        register: Int
    ): Int? {

        if (
            !i2cWrite(
                address,
                byteArrayOf((register and 0xFF).toByte())
            )
        ) {
            return null
        }

        return i2cRead(address, 1)
            ?.get(0)
            ?.toInt()
            ?.and(0xFF)
    }

    fun setI2cRepeater(
        enabled: Boolean
    ): Boolean =
        writeDemodRegisterByte(
            1,
            0x01,
            if (enabled) 0x18 else 0x10
        )
}
