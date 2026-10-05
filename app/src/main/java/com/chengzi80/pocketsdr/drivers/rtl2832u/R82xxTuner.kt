package com.chengzi80.pocketsdr.drivers.rtl2832u

class R82xxTuner(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        /*
         * R820T / R820T2 I2C address
         */
        private const val R820T_I2C_ADDR =
            0x34

        /*
         * R828D I2C address
         */
        private const val R828D_I2C_ADDR =
            0x74

        /*
         * R820T register range
         */
        private const val REG_START =
            0x05

        private const val REG_END =
            0x1F

        /*
         * R820T chip ID register.
         *
         * We first read a small register block
         * and use the returned values to determine
         * whether an R82xx tuner is responding.
         */
        private const val REG_CHIP_ID =
            0x00
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
         * The RTL2832U I2C repeater must be enabled
         * before communicating with the tuner.
         */

        if (
            !control.setI2cRepeater(
                true
            )
        ) {
            return null
        }

        /*
         * R820T/R820T2 normally use 0x34.
         */

        val r820Registers =
            readRegisters(
                R820T_I2C_ADDR,
                REG_START,
                5
            )

        if (
            r820Registers != null &&
            r820Registers.size >= 5
        ) {

            val result =
                detectR82xxType(
                    r820Registers
                )

            if (
                result !=
                TunerType.UNKNOWN
            ) {

                detectedType =
                    result

                detectedAddress =
                    R820T_I2C_ADDR

                return DetectionResult(
                    type = result,
                    i2cAddress =
                        R820T_I2C_ADDR,
                    registers =
                        r820Registers
                )
            }
        }

        /*
         * Try R828D.
         */

        val r828dRegisters =
            readRegisters(
                R828D_I2C_ADDR,
                REG_START,
                5
            )

        if (
            r828dRegisters != null &&
            r828dRegisters.size >= 5
        ) {

            detectedType =
                TunerType.R828D

            detectedAddress =
                R828D_I2C_ADDR

            return DetectionResult(
                type = TunerType.R828D,
                i2cAddress =
                    R828D_I2C_ADDR,
                registers =
                    r828dRegisters
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
         * At this stage we deliberately do not
         * hard-code a single byte as "R820T2".
         *
         * R820T and R820T2 share the r82xx driver
         * in rtl-sdr.
         *
         * We first establish that the tuner responds
         * correctly. Detailed chip revision detection
         * will be added when the full initialization
         * sequence is implemented.
         */

        var nonZeroCount =
            0

        for (value in registers) {

            if (
                (value.toInt() and 0xFF) != 0x00
            ) {
                nonZeroCount++
            }
        }

        if (
            nonZeroCount >= 2
        ) {

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
         * First write the tuner register address.
         */

        val writeResult =
            control.i2cWrite(
                address,
                byteArrayOf(
                    (
                        startRegister and
                                0xFF
                        ).toByte()
                )
            )

        if (!writeResult) {
            return null
        }

        /*
         * Then read the tuner registers.
         */

        val data =
            control.i2cRead(
                address,
                length
            )
                ?: return null

        if (
            data.size != length
        ) {
            return null
        }

        /*
         * R82xx I2C data is bit-reversed.
         */

        val result =
            ByteArray(
                data.size
            )

        for (
            i in data.indices
        ) {

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

        for (
            i in 0 until 8
        ) {

            result =
                result or
                        (
                            (
                                input shr i
                            ) and 0x01
                            shl (7 - i)
                        )
            )
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

        initialized = true
    }

    fun reset() {

        initialized = false

        detectedType =
            TunerType.UNKNOWN

        detectedAddress =
            0

        control.setI2cRepeater(
            false
        )
    }
}
