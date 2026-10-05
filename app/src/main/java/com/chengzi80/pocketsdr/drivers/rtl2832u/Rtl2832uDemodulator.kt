package com.chengzi80.pocketsdr.drivers.rtl2832u

class Rtl2832uDemodulator(
    private val control: Rtl2832uUsbControl
) {

    private var initialized =
        false

    fun initialize(): Boolean {

        /*
         * USB FIFO
         */

        if (
            !control.writeRegisterByte(
                Rtl2832uUsbControl.BLOCK_USB,
                Rtl2832uUsbControl.REG_USB_SYSCTL,
                0x09
            )
        ) {
            return false
        }

        if (
            !control.writeRegister16(
                Rtl2832uUsbControl.BLOCK_USB,
                Rtl2832uUsbControl.REG_USB_EPA_MAXPKT,
                0x0002
            )
        ) {
            return false
        }

        if (
            !control.writeRegister16(
                Rtl2832uUsbControl.BLOCK_USB,
                Rtl2832uUsbControl.REG_USB_EPA_CTL,
                0x1002
            )
        ) {
            return false
        }

        /*
         * Power on demodulator
         */

        if (
            !control.writeRegisterByte(
                Rtl2832uUsbControl.BLOCK_SYS,
                Rtl2832uUsbControl.REG_DEMOD_CTL_1,
                0x22
            )
        ) {
            return false
        }

        if (
            !control.writeRegisterByte(
                Rtl2832uUsbControl.BLOCK_SYS,
                Rtl2832uUsbControl.REG_DEMOD_CTL,
                0xE8
            )
        ) {
            return false
        }

        /*
         * Demodulator reset
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x01,
                value = 0x14
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x01,
                value = 0x10
            )
        ) {
            return false
        }

        /*
         * Disable spectrum inversion
         * and adjacent channel rejection.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x15,
                value = 0x00
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegister(
                page = 1,
                address = 0x16,
                data = byteArrayOf(
                    0x00,
                    0x00
                )
            )
        ) {
            return false
        }

        /*
         * Clear DDC shift and IF registers.
         */

        for (
            address in 0x16..0x1B
        ) {

            if (
                !control.writeDemodRegisterByte(
                    page = 1,
                    address = address,
                    value = 0x00
                )
            ) {
                return false
            }
        }

        /*
         * Enable SDR mode.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 0,
                address = 0x19,
                value = 0x05
            )
        ) {
            return false
        }

        /*
         * Initialize FSM state.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x93,
                value = 0xF0
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x94,
                value = 0x0F
            )
        ) {
            return false
        }

        /*
         * Disable AGC.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x11,
                value = 0x00
            )
        ) {
            return false
        }

        /*
         * Disable RF / IF AGC loop.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0x04,
                value = 0x00
            )
        ) {
            return false
        }

        /*
         * Disable PID filter.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 0,
                address = 0x61,
                value = 0x60
            )
        ) {
            return false
        }

        /*
         * Default ADC I/Q data path.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 0,
                address = 0x06,
                value = 0x80
            )
        ) {
            return false
        }

        /*
         * Enable Zero-IF mode,
         * DC cancellation,
         * IQ estimation / compensation.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 1,
                address = 0xB1,
                value = 0x1B
            )
        ) {
            return false
        }

        /*
         * Disable 4.096 MHz clock output.
         */

        if (
            !control.writeDemodRegisterByte(
                page = 0,
                address = 0x0D,
                value = 0x83
            )
        ) {
            return false
        }

        initialized = true

        return true
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    fun reset(): Boolean {

        initialized = false

        return (
            control.writeDemodRegisterByte(
                page = 1,
                address = 0x01,
                value = 0x14
            ) &&
                    control.writeDemodRegisterByte(
                        page = 1,
                        address = 0x01,
                        value = 0x10
                    )
        )
    }
}
