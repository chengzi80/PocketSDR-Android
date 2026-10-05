package com.chengzi80.pocketsdr.drivers.rtl2832u

class R82xxTuner(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        /*
         * R820T / R820T2 I2C address
         */
        private const val R820T_I2C_ADDR = 0x34

        /*
         * R828D I2C address
         */
        private const val R828D_I2C_ADDR = 0x74

        /*
         * R82xx register start address
         */
        private const val REG_START = 0x05
    }

    enum class TunerType {
        UNKNOWN,
        R820T,
        R820T2,
        R828D
    }

    data class DetectionResult(
        val type: TunerType,
        val i2cAddress: Int,
        val registers: ByteArray
    )

    private var detectedType =
        TunerType.UNKNOWN

    private var detectedAddress =
        0

    private var initialized =
        false

    fun detect(): DetectionResult? {

        /*
         * Enable RTL2832U I2C repeater.
         */
        if (
            !control.setI2cRepeater(true)
        ) {
            return null
        }

        /*
         * Try R820T / R820T2.
         */
        val r820Registers =
            readRegisters(
                address = R820T_I2C_ADDR,
                startRegister = REG_START,
                length = 5
            )

        if (
            r820Registers != null &&
            r820Registers.size == 5
        ) {

            val tunerType =
                detectR82xxType(
                    r820Registers
                )

            if (
                tunerType !=
                TunerType.UNKNOWN
            ) {

                detectedType =
                    tunerType

                detectedAddress =
                    R820T_I2C_ADDR

                return DetectionResult(
                    type = tunerType,
                    i2cAddress = R820T_I2C_ADDR,
                    registers = r820Registers
                )
            }
        }

        /*
         * Try R828D.
         */
        val r828dRegisters =
            readRegisters(
                address = R828D_I2C_ADDR,
                startRegister = REG_START,
                length = 5
            )

        if (
            r828dRegisters != null &&
            r828dRegisters.size == 5
        ) {

            detectedType =
                TunerType.R828D

            detectedAddress =
                R828D_I2C_ADDR

            return DetectionResult(
                type = TunerType.R828D,
                i2cAddress = R828D_I2C_ADDR,
                registers = r828dRegisters
            )
        }

        detectedType =
            TunerType.UNKNOWN

        detectedAddress =
            0

        return null
    }

    private fun detectR82xxType(
        registers: ByteArray
    ): TunerType {

        /*
         * R820T and R820T2 use the same r82xx
         * driver family in rtl-sdr.
         *
         * At this stage we only verify that the
         * tuner returns meaningful register data.
         *
         * Exact revision detection will be added
         * together with the complete tuner
         * initialization sequence.
         */

        var nonZeroCount =
            0

        for (value in registers) {

            val unsignedValue =
                value.toInt() and 0xFF

            if (unsignedValue != 0) {
                nonZeroCount++
            }
        }

        if (nonZeroCount >= 2) {
            return TunerType.R820T2
        }

        return TunerType.UNKNOWN
    }

    private fun readRegisters(
        address: Int,
        startRegister: Int,
        length: Int
    ): ByteArray? {

        /*
         * Write the register address first.
         */
        val writeResult =
            control.i2cWrite(
                address,
                byteArrayOf(
                    startRegister
                        .and(0xFF)
                        .toByte()
                )
            )

        if (!writeResult) {
            return null
        }

        /*
         * Read tuner registers.
         */
        val data =
            control.i2cRead(
                address,
                length
            )
                ?: return null

        if (data.size != length) {
            return null
        }

        /*
         * Reverse the bits of every byte.
         */
        val result =
            ByteArray(data.size)

        for (i in data.indices) {

            result[i] =
                bitReverse(
                    data[i]
                ).toByte()
        }

        return result
    }

    private fun bitReverse(
        value: Byte
    ): Int {

        val input =
            value.toInt() and 0xFF

        var result =
            0

        for (i in 0 until 8) {

            val bit =
                (input shr i) and 0x01

            result =
                result or
                        (bit shl (7 - i))
        }

        return result
    }

    fun getTunerType():
            TunerType {

        return detectedType
    }

    fun getI2cAddress():
            Int {

        return detectedAddress
    }

    fun isInitialized():
            Boolean {

        return initialized
    }

    fun markInitialized() {

        initialized =
            true
    }

    fun reset() {

        initialized =
            false

        detectedType =
            TunerType.UNKNOWN

        detectedAddress =
            0

        control.setI2cRepeater(
            false
        )
    }
}
