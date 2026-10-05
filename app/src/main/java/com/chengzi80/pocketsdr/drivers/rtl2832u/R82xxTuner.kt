package com.chengzi80.pocketsdr.drivers.rtl2832u

class R82xxTuner(
    private val control: Rtl2832uUsbControl
) {

    companion object {

        private const val R820T_I2C_ADDR = 0x34
        private const val R828D_I2C_ADDR = 0x74

        private const val REG_START = 0x05
        private const val NUM_REGS = 27

        private const val DEFAULT_XTAL_HZ = 28_800_000L

        private const val MIN_FREQUENCY_HZ = 24_000_000L
        private const val MAX_FREQUENCY_HZ = 1_766_000_000L

        private const val VCO_MIN_KHZ = 1_770_000L
        private const val VCO_MAX_KHZ = 3_540_000L

        private val INIT_REGISTERS = byteArrayOf(
            0x83.toByte(),
            0x32.toByte(),
            0x75.toByte(),
            0xC0.toByte(),
            0x40.toByte(),
            0xD6.toByte(),
            0x6C.toByte(),
            0xF5.toByte(),
            0x63.toByte(),
            0x75.toByte(),
            0x68.toByte(),
            0x6C.toByte(),
            0x83.toByte(),
            0x80.toByte(),
            0x00.toByte(),
            0x0F.toByte(),
            0x00.toByte(),
            0xC0.toByte(),
            0x30.toByte(),
            0x48.toByte(),
            0xCC.toByte(),
            0x60.toByte(),
            0x00.toByte(),
            0x54.toByte(),
            0xAE.toByte(),
            0x4A.toByte(),
            0xC0.toByte()
        )
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

    private var detectedAddress = 0

    private var initialized = false

    private var currentFrequencyHz = 0L

    private var xtalFrequencyHz =
        DEFAULT_XTAL_HZ

    private var hasLock = false

    /*
     * Shadow register cache.
     *
     * Index 0 corresponds to register 0x05.
     */
    private val registers =
        ByteArray(NUM_REGS)

    fun detect(): DetectionResult? {

        if (!control.setI2cRepeater(true)) {
            return null
        }

        try {

            val r820Registers =
                readRegisters(
                    R820T_I2C_ADDR,
                    REG_START,
                    5
                )

            if (r820Registers != null) {

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
                        tunerType,
                        R820T_I2C_ADDR,
                        r820Registers
                    )
                }
            }

            val r828dRegisters =
                readRegisters(
                    R828D_I2C_ADDR,
                    REG_START,
                    5
                )

            if (r828dRegisters != null) {

                detectedType =
                    TunerType.R828D

                detectedAddress =
                    R828D_I2C_ADDR

                return DetectionResult(
                    TunerType.R828D,
                    R828D_I2C_ADDR,
                    r828dRegisters
                )
            }

            detectedType =
                TunerType.UNKNOWN

            detectedAddress = 0

            return null

        } finally {

            control.setI2cRepeater(false)
        }
    }

    fun initialize(): Boolean {

        if (detectedAddress == 0) {

            val result =
                detect()
                    ?: return false

            detectedType =
                result.type

            detectedAddress =
                result.i2cAddress
        }

        if (detectedAddress == 0) {
            return false
        }

        if (
            !control.setI2cRepeater(true)
        ) {
            return false
        }

        try {

            registers.fill(0)

            for (i in INIT_REGISTERS.indices) {
                registers[i] =
                    INIT_REGISTERS[i]
            }

            if (
                !writeRegisters(
                    detectedAddress,
                    REG_START,
                    INIT_REGISTERS
                )
            ) {
                return false
            }

            /*
             * The rtl-sdr driver initializes the
             * tuner as a digital-TV receiver before
             * subsequent tuning operations.
             */
            if (
                !writeRegisterMasked(
                    0x0C,
                    0x00,
                    0x0F
                )
            ) {
                return false
            }

            /*
             * Version field.
             */
            if (
                !writeRegisterMasked(
                    0x13,
                    0x00,
                    0x3F
                )
            ) {
                return false
            }

            initialized = true
            hasLock = false

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    fun setFrequency(
        frequencyHz: Long
    ): Boolean {

        if (!initialized) {
            return false
        }

        if (
            frequencyHz <
            MIN_FREQUENCY_HZ
        ) {
            return false
        }

        if (
            frequencyHz >
            MAX_FREQUENCY_HZ
        ) {
            return false
        }

        if (
            !control.setI2cRepeater(true)
        ) {
            return false
        }

        try {

            hasLock = false

            /*
             * RTL-SDR normally adds the configured
             * IF frequency to the requested tuner
             * frequency.
             *
             * At this stage PocketSDR keeps IF at
             * zero, so LO = requested frequency.
             */
            val loFrequencyHz =
                frequencyHz

            if (
                !setMux(loFrequencyHz)
            ) {
                return false
            }

            if (
                !setPll(loFrequencyHz)
            ) {
                return false
            }

            if (!hasLock) {
                return false
            }

            currentFrequencyHz =
                frequencyHz

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    private data class FrequencyRange(
        val startMHz: Int,
        val openDrain: Int,
        val rfMuxPloy: Int,
        val tfC: Int,
        val xtalCap20p: Int,
        val xtalCap10p: Int,
        val xtalCap0p: Int
    )

    /*
     * R820T/R82xx frequency-band table.
     *
     * These values come from the rtl-sdr R82xx
     * tuner implementation.
     */
    private val frequencyRanges =
        arrayOf(

            FrequencyRange(
                0,
                0x08,
                0x02,
                0xDF,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                50,
                0x08,
                0x02,
                0xBE,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                55,
                0x08,
                0x02,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                60,
                0x08,
                0x02,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                65,
                0x00,
                0x02,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                70,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                75,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                80,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                90,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                100,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                110,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                120,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                140,
                0x00,
                0x41,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                180,
                0x00,
                0x40,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                200,
                0x00,
                0x40,
                0x8B,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                240,
                0x00,
                0x00,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                280,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                310,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                360,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                380,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                450,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                485,
                0x00,
                0x40,
                0x00,
                0x02,
                0x00,
                0x00
            ),

            FrequencyRange(
                650,
                0x00,
                0x40,
                0x00,
                0x00,
                0x00,
                0x00
            )
        )

    private fun setMux(
        frequencyHz: Long
    ): Boolean {

        val frequencyMHz =
            (frequencyHz / 1_000_000L)
                .toInt()

        var selected =
            frequencyRanges[0]

        for (i in 0 until frequencyRanges.size - 1) {

            if (
                frequencyMHz <
                frequencyRanges[i + 1].startMHz
            ) {
                selected =
                    frequencyRanges[i]
                break
            }

            selected =
                frequencyRanges[i + 1]
        }

        /*
         * Open drain.
         */
        if (
            !writeRegisterMasked(
                0x17,
                selected.openDrain,
                0x08
            )
        ) {
            return false
        }

        /*
         * RF MUX + Poly MUX.
         */
        if (
            !writeRegisterMasked(
                0x1A,
                selected.rfMuxPloy,
                0xC3
            )
        ) {
            return false
        }

        /*
         * Tracking filter band.
         */
        if (
            !writeRegister(
                0x1B,
                selected.tfC
            )
        ) {
            return false
        }

        /*
         * XTAL capacitor / drive.
         *
         * PocketSDR starts with high-cap 0pF,
         * matching the rtl-sdr initial selection.
         */
        if (
            !writeRegisterMasked(
                0x10,
                selected.xtalCap0p,
                0x0B
            )
        ) {
            return false
        }

        /*
         * Clear LNA / mixer gain forcing.
         */
        if (
            !writeRegisterMasked(
                0x08,
                0x00,
                0x3F
            )
        ) {
            return false
        }

        if (
            !writeRegisterMasked(
                0x09,
                0x00,
                0x3F
            )
        ) {
            return false
        }

        return true
    }

    private fun setPll(
        frequencyHz: Long
    ): Boolean {

        if (
            frequencyHz <
            25_000_000L
        ) {
            return false
        }

        if (
            frequencyHz >
            1_770_000_000L
        ) {
            return false
        }

        /*
         * R820T uses a 28.8 MHz crystal.
         */
        var pllReferenceHz =
            xtalFrequencyHz

        /*
         * The current PocketSDR target is the
         * common 28.8 MHz R820T/R820T2.
         */
        var refDiv2 = 0

        if (
            xtalFrequencyHz >
            24_000_000L
        ) {
            pllReferenceHz /=
                2L

            refDiv2 =
                0x10
        }

        if (
            !writeRegisterMasked(
                0x10,
                refDiv2,
                0x10
            )
        ) {
            return false
        }

        /*
         * PLL autotune = 128 kHz.
         */
        if (
            !writeRegisterMasked(
                0x1A,
                0x00,
                0x0C
            )
        ) {
            return false
        }

        /*
         * VCO current = 100.
         */
        if (
            !writeRegisterMasked(
                0x12,
                0x80,
                0xE0
            )
        ) {
            return false
        }

        val frequencyKHz =
            (frequencyHz + 500L) /
                1000L

        var mixDiv = 2

        var divNum = 0

        while (mixDiv <= 64) {

            val vcoKHz =
                frequencyKHz *
                    mixDiv.toLong()

            if (
                vcoKHz >=
                VCO_MIN_KHZ &&
                vcoKHz <
                VCO_MAX_KHZ
            ) {
                break
            }

            mixDiv =
                mixDiv shl 1

            divNum++
        }

        if (mixDiv > 64) {
            return false
        }

        /*
         * R820T VCO divider.
         */
        if (
            !writeRegisterMasked(
                0x10,
                divNum shl 5,
                0xE0
            )
        ) {
            return false
        }

        /*
         * VCO frequency.
         */
        val vcoFrequencyHz =
            frequencyHz *
                mixDiv.toLong()

        /*
         * Fixed-point PLL calculation:
         *
         * vco_div =
         * (pll_ref + 65536*vco_freq)
         * /
         * (2*pll_ref)
         *
         * nint = integer part
         * sdm  = fractional part
         */
        val numerator =
            pllReferenceHz +
                65_536L *
                vcoFrequencyHz

        val denominator =
            2L *
                pllReferenceHz

        if (denominator <= 0L) {
            return false
        }

        val vcoDiv =
            numerator /
                denominator

        val nint =
            vcoDiv /
                65_536L

        val sdm =
            vcoDiv %
                65_536L

        /*
         * R820T valid NINT range.
         */
        if (
            nint < 13L ||
            nint > 76L
        ) {
            return false
        }

        val ni =
            ((nint - 13L) / 4L)
                .toInt()

        val si =
            (nint -
                4L *
                ni.toLong() -
                13L)
                .toInt()

        val nintRegister =
            ni or
                (si shl 6)

        if (
            !writeRegister(
                0x14,
                nintRegister
            )
        ) {
            return false
        }

        /*
         * Power SDM.
         */
        val pwSdm =
            if (sdm == 0L) {
                0x08
            } else {
                0x00
            }

        if (
            !writeRegisterMasked(
                0x12,
                pwSdm,
                0x08
            )
        ) {
            return false
        }

        /*
         * Fractional SDM.
         *
         * R820T expects:
         *   R15 = SDM low byte
         *   R16 = SDM high byte
         */
        if (
            !writeRegister(
                0x15,
                (sdm and 0xFFL)
                    .toInt()
            )
        ) {
            return false
        }

        if (
            !writeRegister(
                0x16,
                ((sdm shr 8) and 0xFFL)
                    .toInt()
            )
        ) {
            return false
        }

        /*
         * Read PLL lock status.
         *
         * R82xx reports lock through register
         * 0x02, bit 6 after bit-reversal.
         */
        var locked =
            false

        repeat(2) {

            val data =
                readRegisters(
                    detectedAddress,
                    0x00,
                    3
                )

            if (data == null) {
                return false
            }

            if (
                data.size >= 3 &&
                (data[2].toInt() and 0x40) != 0
            ) {
                locked = true
                return@repeat
            }

            /*
             * If not locked on the first attempt,
             * increase VCO current and retry.
             */
            if (it == 0) {

                if (
                    !writeRegisterMasked(
                        0x12,
                        0x60,
                        0xE0
                    )
                ) {
                    return false
                }
            }
        }

        if (!locked) {
            hasLock = false
            return false
        }

        hasLock = true

        /*
         * PLL autotune = 8 kHz.
         */
        if (
            !writeRegisterMasked(
                0x1A,
                0x08,
                0x08
            )
        ) {
            return false
        }

        return true
    }

    private fun writeRegister(
        register: Int,
        value: Int
    ): Boolean {

        if (
            register <
            REG_START
        ) {
            return false
        }

        val index =
            register -
                REG_START

        if (
            index !in registers.indices
        ) {
            return false
        }

        val byteValue =
            (value and 0xFF)
                .toByte()

        registers[index] =
            byteValue

        return control.i2cWrite(
            detectedAddress,
            byteArrayOf(
                (register and 0xFF)
                    .toByte(),
                byteValue
            )
        )
    }

    private fun writeRegisterMasked(
        register: Int,
        value: Int,
        mask: Int
    ): Boolean {

        if (
            register <
            REG_START
        ) {
            return false
        }

        val index =
            register -
                REG_START

        if (
            index !in registers.indices
        ) {
            return false
        }

        val current =
            registers[index]
                .toInt() and 0xFF

        val newValue =
            (current and mask.inv()) or
                (value and mask)

        if (
            newValue == current
        ) {
            return true
        }

        return writeRegister(
            register,
            newValue
        )
    }

    private fun readRegisters(
        address: Int,
        startRegister: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) {
            return null
        }

        if (
            !control.i2cWrite(
                address,
                byteArrayOf(
                    (startRegister and 0xFF)
                        .toByte()
                )
            )
        ) {
            return null
        }

        val raw =
            control.i2cRead(
                address,
                length
            ) ?: return null

        if (
            raw.size != length
        ) {
            return null
        }

        /*
         * R82xx read data is bit-reversed.
         */
        val result =
            ByteArray(length)

        for (i in raw.indices) {
            result[i] =
                bitReverse(
                    raw[i]
                )
        }

        return result
    }

    private fun writeRegisters(
        address: Int,
        startRegister: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) {
            return false
        }

        /*
         * rtl-sdr configures max_i2c_msg_len = 8.
         *
         * One byte is used for the register number,
         * so maximum payload = 7 bytes.
         */
        val maxPayload = 7

        var offset = 0

        while (
            offset <
            data.size
        ) {

            val count =
                minOf(
                    maxPayload,
                    data.size -
                        offset
                )

            val packet =
                ByteArray(
                    count + 1
                )

            packet[0] =
                (
                    startRegister +
                        offset
                    ).toByte()

            for (i in 0 until count) {

                packet[i + 1] =
                    data[
                        offset + i
                    ]
            }

            if (
                !control.i2cWrite(
                    address,
                    packet
                )
            ) {
                return false
            }

            for (i in 0 until count) {

                val register =
                    startRegister +
                        offset +
                        i

                val index =
                    register -
                        REG_START

                if (
                    index in
                    registers.indices
                ) {
                    registers[index] =
                        data[
                            offset + i
                        ]
                }
            }

            offset += count
        }

        return true
    }

    private fun detectR82xxType(
        registers: ByteArray
    ): TunerType {

        if (
            registers.isEmpty()
        ) {
            return TunerType.UNKNOWN
        }

        /*
         * The current USB dongle target is the
         * R820T/R820T2 family.
         *
         * R828D has its own I2C address and is
         * detected separately.
         */
        var nonZeroCount = 0

        for (value in registers) {

            if (
                (value.toInt() and 0xFF) != 0
            ) {
                nonZeroCount++
            }
        }

        if (
            nonZeroCount < 2
        ) {
            return TunerType.UNKNOWN
        }

        return TunerType.R820T2
    }

    private fun bitReverse(
        input: Byte
    ): Byte {

        val value =
            input.toInt() and 0xFF

        var result = 0

        for (i in 0 until 8) {

            result =
                (result shl 1) or
                    ((value shr i) and 0x01)
        }

        return result.toByte()
    }

    fun getTunerType():
        TunerType =
        detectedType

    fun getI2cAddress():
        Int =
        detectedAddress

    fun getCurrentFrequencyHz():
        Long =
        currentFrequencyHz

    fun getXtalFrequencyHz():
        Long =
        xtalFrequencyHz

    fun setXtalFrequencyHz(
        frequencyHz: Long
    ) {

        if (
            frequencyHz > 0L
        ) {
            xtalFrequencyHz =
                frequencyHz
        }
    }

    fun isInitialized():
        Boolean =
        initialized

    fun hasPllLock():
        Boolean =
        hasLock

    fun markInitialized() {
        initialized = true
    }

    fun reset() {

        initialized = false

        hasLock = false

        currentFrequencyHz = 0L

        detectedType =
            TunerType.UNKNOWN

        detectedAddress = 0

        registers.fill(0)

        try {
            control.setI2cRepeater(false)
        } catch (_: Exception) {
        }
    }
}
