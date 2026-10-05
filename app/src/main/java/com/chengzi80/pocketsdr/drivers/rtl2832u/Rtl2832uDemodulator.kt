package com.chengzi80.pocketsdr.drivers.rtl2832u

class Rtl2832uDemodulator(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        private const val DEFAULT_SAMPLE_RATE_HZ = 2_048_000L
        private const val MIN_SAMPLE_RATE_HZ = 225_001L
        private const val MAX_SAMPLE_RATE_HZ = 3_200_000L

        private const val RTL_XTAL_HZ = 28_800_000L

        private const val PAGE_0 = 0
        private const val PAGE_1 = 1

        private const val REG_IF_FREQ_0 = 0x19
        private const val REG_IF_FREQ_1 = 0x1A
        private const val REG_IF_FREQ_2 = 0x1B

        private const val USB_BLOCK =
            Rtl2832uUsbControl.BLOCK_USB

        private const val USB_SYS_BLOCK =
            Rtl2832uUsbControl.BLOCK_SYS

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

        /*
         * RTL2832U demodulator registers used during
         * initialization.
         */
        private const val REG_DEMOD_CTL =
            Rtl2832uUsbControl.REG_DEMOD_CTL

        private const val REG_DEMOD_CTL_1 =
            Rtl2832uUsbControl.REG_DEMOD_CTL_1
    }

    private var initialized = false

    private var currentSampleRateHz =
        DEFAULT_SAMPLE_RATE_HZ

    fun initialize(): Boolean {

        initialized = false

        /*
         * -------------------------------------------------
         * USB / endpoint initialization
         * -------------------------------------------------
         *
         * These values follow the initialization sequence
         * used by rtl-sdr for RTL2832 based devices.
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
         * Endpoint maximum packet size.
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
         * Enable endpoint.
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
         * -------------------------------------------------
         * Demodulator power / reset
         * -------------------------------------------------
         */

        if (
            !control.writeRegisterByte(
                USB_SYS_BLOCK,
                REG_DEMOD_CTL_1,
                0x22
            )
        ) {
            return false
        }

        if (
            !control.writeRegisterByte(
                USB_SYS_BLOCK,
                REG_DEMOD_CTL,
                0xE8
            )
        ) {
            return false
        }

        /*
         * Soft reset.
         */
        if (
            !softReset()
        ) {
            return false
        }

        /*
         * Disable digital AGC during initial setup.
         */
        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x04,
                0x00
            )
        ) {
            return false
        }

        /*
         * Disable spectrum inversion.
         */
        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x15,
                0x00
            )
        ) {
            return false
        }

        /*
         * Disable adjacent channel rejection.
         */
        if (
            !control.writeDemodRegister(
                PAGE_1,
                0x16,
                byteArrayOf(
                    0x00,
                    0x00
                )
            )
        ) {
            return false
        }

        /*
         * Clear a number of baseband registers used by
         * the RTL2832 demodulator.
         */
        for (
            address in 0x16..0x1B
        ) {

            if (
                !control.writeDemodRegisterByte(
                    PAGE_1,
                    address,
                    0x00
                )
            ) {
                return false
            }
        }

        /*
         * Default demodulator settings.
         */
        if (
            !control.writeDemodRegisterByte(
                PAGE_0,
                0x19,
                0x05
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x93,
                0xF0
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x94,
                0x0F
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x11,
                0x00
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_0,
                0x61,
                0x60
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_0,
                0x06,
                0x80
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0xB1,
                0x1B
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_0,
                0x0D,
                0x83
            )
        ) {
            return false
        }

        /*
         * Set the default sample rate.
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
     * Set RTL2832U sample rate.
     *
     * This follows the algorithm used by rtl-sdr:
     *
     *   rsamp_ratio =
     *       (rtl_xtal * 2^22) / sample_rate
     *
     *   rsamp_ratio &= 0x0ffffffc
     *
     * Then the 32-bit ratio is written as:
     *
     *   0x9F : high 16 bits
     *   0xA1 : low 16 bits
     *
     * on demodulator page 1.
     */
    fun setSampleRate(
        sampleRateHz: Long
    ): Boolean {

        /*
         * RTL2832U has a discontinuous valid sample-rate
         * range. 900001..3200000 Hz is valid, while
         * 300001..900000 Hz is not supported by the
         * hardware resampler.
         */
        if (
            sampleRateHz <= 225_000L
        ) {
            return false
        }

        if (
            sampleRateHz > MAX_SAMPLE_RATE_HZ
        ) {
            return false
        }

        if (
            sampleRateHz > 300_000L &&
            sampleRateHz <= 900_000L
        ) {
            return false
        }

        /*
         * Official rtl-sdr calculation:
         *
         * (28.8 MHz * 2^22) / sample rate
         */
        val numerator =
            RTL_XTAL_HZ *
                (1L shl 22)

        if (
            numerator <= 0L
        ) {
            return false
        }

        var ratio =
            numerator /
                sampleRateHz

        /*
         * Hardware limitation:
         *
         * lower two bits are forced to zero.
         */
        ratio =
            ratio and
                0x0FFFFFFCL

        if (
            ratio <= 0L
        ) {
            return false
        }

        /*
         * Calculate the actual sample rate produced
         * by the RTL2832U.
         *
         * This is important because the requested rate
         * is not always exactly achievable.
         */
        val realRatio =
            ratio or
                (
                    (ratio and 0x08000000L)
                        shl 1
                    )

        if (
            realRatio <= 0L
        ) {
            return false
        }

        val realRate =
            numerator /
                realRatio

        if (
            realRate <= 0L
        ) {
            return false
        }

        /*
         * The hardware writes the ratio as two
         * little-endian 16-bit values.
         *
         * 0x9F = high 16 bits
         * 0xA1 = low 16 bits
         */
        val high =
            (
                (ratio shr 16) and
                    0xFFFFL
                ).toInt()

        val low =
            (
                ratio and
                    0xFFFFL
                ).toInt()

        if (
            !control.writeDemodRegister(
                PAGE_1,
                0x9F,
                byteArrayOf(
                    (high and 0xFF).toByte(),
                    ((high shr 8) and 0xFF).toByte()
                )
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegister(
                PAGE_1,
                0xA1,
                byteArrayOf(
                    (low and 0xFF).toByte(),
                    ((low shr 8) and 0xFF).toByte()
                )
            )
        ) {
            return false
        }

        /*
         * Reset the demodulator after changing the
         * sample-rate ratio.
         */
        if (
            !softReset()
        ) {
            return false
        }

        currentSampleRateHz =
            realRate

        return true
    }

    fun getSampleRateHz(): Long =
        currentSampleRateHz

    /**
     * Set RTL2832U IF frequency.
     *
     * The IF frequency is represented by a 22-bit
     * two's-complement value relative to the
     * 28.8 MHz RTL2832 crystal.
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

        /*
         * 22-bit phase accumulator.
         */
        val ifFrequency =
            (
                frequencyHz *
                    (1L shl 22)
            ) /
                RTL_XTAL_HZ

        if (
            ifFrequency < 0L
        ) {
            return false
        }

        val value =
            ifFrequency and
                0x3FFFFFL

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                REG_IF_FREQ_0,
                (
                    (value shr 16) and
                        0x3FL
                    ).toInt()
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                REG_IF_FREQ_1,
                (
                    (value shr 8) and
                        0xFFL
                    ).toInt()
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                REG_IF_FREQ_2,
                (
                    value and
                        0xFFL
                    ).toInt()
            )
        ) {
            return false
        }

        return true
    }

    /**
     * Reset the USB endpoint/FIFO before starting IQ
     * acquisition.
     */
    fun resetBuffer(): Boolean {

        /*
         * RTL-SDR reference sequence:
         *
         *   USB_EPA_CTL = 0x1002
         *   USB_EPA_CTL = 0x0000
         *
         * This resets the RTL2832U USB endpoint/FIFO
         * before bulk IQ acquisition.
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

        if (
            !control.writeRegister16(
                USB_BLOCK,
                REG_USB_EPA_CTL,
                0x0000
            )
        ) {
            return false
        }

        return true
    }

    private fun softReset(): Boolean {

        /*
         * RTL2832U demodulator soft reset:
         *
         * 0x14 = reset
         * 0x10 = release reset
         */
        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x01,
                0x14
            )
        ) {
            return false
        }

        if (
            !control.writeDemodRegisterByte(
                PAGE_1,
                0x01,
                0x10
            )
        ) {
            return false
        }

        return true
    }

    fun isInitialized(): Boolean =
        initialized

    fun reset(): Boolean {

        initialized = false

        currentSampleRateHz =
            DEFAULT_SAMPLE_RATE_HZ

        return softReset()
    }
}
