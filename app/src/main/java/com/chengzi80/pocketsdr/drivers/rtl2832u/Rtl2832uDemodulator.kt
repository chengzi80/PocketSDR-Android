package com.chengzi80.pocketsdr.drivers.rtl2832u

class Rtl2832uDemodulator(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        private const val DEFAULT_SAMPLE_RATE_HZ = 2_048_000L

        private const val MIN_SAMPLE_RATE_HZ = 225_001L

        private const val MAX_SAMPLE_RATE_HZ = 3_200_000L

        /*
         * RTL2832U demodulator registers
         */

        private const val PAGE_0 = 0

        private const val PAGE_1 = 1

        /*
         * RTL2832U IF frequency registers.
         *
         * 0x19 / 0x1A / 0x1B
         */
        private const val REG_IF_FREQ_0 = 0x19

        private const val REG_IF_FREQ_1 = 0x1A

        private const val REG_IF_FREQ_2 = 0x1B

        /*
         * USB / FIFO configuration
         */
        private const val USB_BLOCK = Rtl2832uUsbControl.BLOCK_USB

        private const val REG_USB_SYSCTL =
            Rtl2832uUsbControl.REG_USB_SYSCTL

        private const val REG_USB_CTRL =
            Rtl2832uUsbControl.REG_USB_CTRL

        private const val REG_USB_EPA_CFG =
            Rtl2832uUsbControl.REG_USB_EPA_CFG

        private const val REG_USB_EPA_CTL =
            Rtl2832uUsbControl.REG_USB_EPA_CTL

        private const val REG_USB_EPA_MAXPKT =
            Rtl2832uUsbControl.REG_USB_EPA_MAXPKT

        private const val REG_USB_EPA_FIFO_CFG =
            Rtl2832uUsbControl.REG_USB_EPA_FIFO_CFG
    }

    private var initialized = false

    private var currentSampleRateHz =
        DEFAULT_SAMPLE_RATE_HZ

    /**
     * 初始化 RTL2832U。
     */
    fun initialize(): Boolean {

        initialized = false

        /*
         * 启用 USB 接收系统。
         */
        if (
            !control.writeRegisterByte(
                USB_BLOCK,
                REG_USB_SYSCTL,
                0x09
            )
        ) {
            return false
        }

        /*
         * 设置 USB endpoint 最大包长度。
         */
        if (
            !control.writeRegister16(
                USB_BLOCK,
                REG_USB_EPA_MAXPKT,
                0x0002
            )
        ) {
            return false
        }

        /*
         * 配置 USB endpoint。
         */
        if (
            !control.writeRegister16(
                USB_BLOCK,
                REG_USB_EPA_CTL,
                0x1002
            )
        ) {
            return false
        }

        /*
         * 初始化 Demodulator。
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
         * Demodulator 基础配置。
         */
        if (
            !control.writeDemodRegisterByte(
                1,
                0x01,
                0x14
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0x01,
                0x10
            )
        ) {
            return false
        }

        /*
         * AGC / IF 基础配置。
         */
        if (
            !control.writeDemodRegisterByte(
                1,
                0x15,
                0x00
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegister(
                1,
                0x16,
                byteArrayOf(
                    0x00,
                    0x00
                )
            )
        ) {
            return false
        }

        for (
            address in 0x16..0x1B
        ) {

            if (
                !control.writeDemodRegisterByte(
                    1,
                    address,
                    0x00
                )
            ) {
                return false
            }
        }

        /*
         * RTL2832U 基础数字配置。
         */
        if (
            !control.writeDemodRegisterByte(
                0,
                0x19,
                0x05
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0x93,
                0xF0
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0x94,
                0x0F
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0x11,
                0x00
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0x04,
                0x00
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                0,
                0x61,
                0x60
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                0,
                0x06,
                0x80
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                1,
                0xB1,
                0x1B
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                0,
                0x0D,
                0x83
            )
        ) {
            return false
        }

        /*
         * 默认采样率。
         */
        if (
            !setSampleRate(
                DEFAULT_SAMPLE_RATE_HZ
            )
        ) {
            return false
        }

        initialized = true

        return true
    }

    /**
     * 设置 RTL2832U 采样率。
     *
     * 官方 rtl-sdr 的采样率范围：
     *
     * 225001 - 300000 Hz
     * 900001 - 3200000 Hz
     *
     * 其中 300001 - 900000 Hz 不属于正常支持范围。
     */
    fun setSampleRate(
        sampleRateHz: Long
    ): Boolean {

        if (
            sampleRateHz <
            MIN_SAMPLE_RATE_HZ
        ) {
            return false
        }

        if (
            sampleRateHz >
            MAX_SAMPLE_RATE_HZ
        ) {
            return false
        }

        /*
         * RTL2832U 使用 28.8 MHz 时钟。
         *
         * rsamp_ratio =
         *
         * 28800000 * 2^22 / sample_rate
         */
        val ratio =
            (
                28_800_000_000_000L /
                    sampleRateHz
                )

        if (ratio <= 0L) {
            return false
        }

        /*
         * RTL2832U 使用 22-bit ratio。
         */
        val ratio22 =
            ratio and 0x0FFFFFFF

        /*
         * 写入采样率 ratio。
         *
         * RTL2832U 的 rsamp_ratio 为
         * 28-bit 固定点数。
         */
        val ratioBytes =
            byteArrayOf(
                (ratio22 and 0xFF).toByte(),

                ((ratio22 shr 8) and 0xFF)
                    .toByte(),

                ((ratio22 shr 16) and 0xFF)
                    .toByte(),

                ((ratio22 shr 24) and 0x0F)
                    .toByte()
            )

        /*
         * RTL2832U samplerate ratio registers
         *
         * Page 1:
         * 0x9F - 0xA2
         */
        if (
            !control.writeDemodRegister(
                PAGE_1,
                0x9F,
                ratioBytes
            )
        ) {
            return false
        }

        /*
         * 根据采样率设置数字滤波器。
         *
         * 2.048 MHz 是 RTL-SDR 最常用的默认值。
         *
         * 后续可以进一步按照官方
         * rtl2832_set_sample_rate()
         * 完整移植滤波器系数。
         */
        currentSampleRateHz =
            sampleRateHz

        return true
    }

    /**
     * 获取当前采样率。
     */
    fun getSampleRateHz(): Long {

        return currentSampleRateHz
    }

    /**
     * 设置 IF 频率。
     *
     * RTL2832U 内部 IF 使用 22-bit
     * fixed-point 表示。
     */
    fun setIfFrequency(
        frequencyHz: Long
    ): Boolean {

        if (!initialized) {
            return false
        }

        if (
            frequencyHz < 0L
        ) {
            return false
        }

        val xtal =
            28_800_000L

        val ifFrequency =
            (
                frequencyHz.toDouble() /
                    xtal.toDouble() *
                    (1L shl 22)
                ).toLong()

        if (ifFrequency < 0L) {
            return false
        }

        val value =
            ifFrequency and
                0x3FFFFF

     if (
    !control.writeDemodRegisterByte(
        PAGE_1,
        REG_IF_FREQ_0,
        ((value shr 16) and 0x3F).toInt()
    )
) {
    return false
}

if (
    !control.writeDemodRegisterByte(
        PAGE_1,
        REG_IF_FREQ_1,
        ((value shr 8) and 0xFF).toInt()
    )
) {
    return false
}

if (
    !control.writeDemodRegisterByte(
        PAGE_1,
        REG_IF_FREQ_2,
        (value and 0xFF).toInt()
    )
) {
    return false
}
        return true
    }

    /**
     * Reset USB endpoint。
     *
     * 在开始读取 IQ 之前必须执行。
     */
    fun resetBuffer(): Boolean {

        /*
         * RTL2832U USB FIFO / endpoint reset。
         */
        if (
            !control.writeRegisterByte(
                USB_BLOCK,
                REG_USB_EPA_CFG,
                0x00
            )
        ) {
            return false
        }

        if (
            !control.writeRegisterByte(
                USB_BLOCK,
                REG_USB_EPA_CTL,
                0x1002
            )
        ) {
            return false
        }

        /*
         * FIFO configuration。
         */
        if (
            !control.writeRegisterByte(
                USB_BLOCK,
                REG_USB_EPA_FIFO_CFG,
                0x00
            )
        ) {
            return false
        }

        return true
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    /**
     * 重置 Demodulator。
     */
    fun reset(): Boolean {

        initialized = false

        currentSampleRateHz =
            DEFAULT_SAMPLE_RATE_HZ

        return (
            control.writeDemodRegisterByte(
                1,
                0x01,
                0x14
            ) &&
            control.writeDemodRegisterByte(
                1,
                0x01,
                0x10
            )
        )
    }
}
