package com.chengzi80.pocketsdr.drivers.rtl2832u

class Rtl2832uDemodulator(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        /*
         * RTL2832U demodulator register blocks.
         *
         * 这些地址来自 rtl-sdr 的 RTL2832U
         * demodulator 控制方式。
         */

        const val DEMOD_BLOCK =
            0x0A

        const val USB_BLOCK =
            0x01

        const val SYS_BLOCK =
            0x02

        private const val REG_DEMOD_CTL =
            0x01

        private const val REG_DEMOD_CTL_1 =
            0x02

        private const val REG_DEMOD_CTL_2 =
            0x03
    }

    private var initialized =
        false

    fun initialize(): Boolean {

        /*
         * 目前先进行安全的寄存器访问测试。
         *
         * 后续会在这里加入完整 RTL2832U
         * 初始化序列：
         *
         * 1. USB FIFO
         * 2. Demodulator reset
         * 3. ADC
         * 4. FIR
         * 5. AGC
         * 6. sample format
         * 7. sample rate
         */

        val value =
            control.readRegByte(
                DEMOD_BLOCK,
                REG_DEMOD_CTL
            )

        if (value == null) {

            initialized = false

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

        return true
    }
}
