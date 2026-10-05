package com.chengzi80.pocketsdr.drivers.rtl2832u

class R82xxTuner(
    private val control: Rtl2832uUsbControl
) {

    companion object {
        private const val R820T_I2C_ADDR = 0x34
        private const val R828D_I2C_ADDR = 0x74

        private const val REG_START = 0x05

        private const val DEFAULT_XTAL_HZ = 28_800_000L

        private const val MIN_FREQUENCY_HZ = 24_000_000L
        private const val MAX_FREQUENCY_HZ = 1_766_000_000L

        /*
         * rtl-sdr 官方 R82xx 初始化寄存器。
         *
         * 从寄存器 0x05 开始：
         *
         * 05 - 1f
         *
         * 共 27 个寄存器。
         */
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

    private var detectedType = TunerType.UNKNOWN
    private var detectedAddress = 0

    private var initialized = false

    private var currentFrequencyHz = 0L

    private var xtalFrequencyHz = DEFAULT_XTAL_HZ

    fun detect(): DetectionResult? {

        if (!control.setI2cRepeater(true)) {
            return null
        }

        try {

            val r820Registers = readRegisters(
                address = R820T_I2C_ADDR,
                startRegister = REG_START,
                length = 5
            )

            if (r820Registers != null) {

                val tunerType = detectR82xxType(
                    r820Registers
                )

                if (tunerType != TunerType.UNKNOWN) {

                    detectedType = tunerType
                    detectedAddress = R820T_I2C_ADDR

                    return DetectionResult(
                        type = tunerType,
                        i2cAddress = R820T_I2C_ADDR,
                        registers = r820Registers
                    )
                }
            }

            val r828dRegisters = readRegisters(
                address = R828D_I2C_ADDR,
                startRegister = REG_START,
                length = 5
            )

            if (r828dRegisters != null) {

                detectedType = TunerType.R828D
                detectedAddress = R828D_I2C_ADDR

                return DetectionResult(
                    type = TunerType.R828D,
                    i2cAddress = R828D_I2C_ADDR,
                    registers = r828dRegisters
                )
            }

            detectedType = TunerType.UNKNOWN
            detectedAddress = 0

            return null

        } finally {

            control.setI2cRepeater(false)
        }
    }

    /**
     * 初始化 R82xx。
     *
     * 这里首先写入官方 rtl-sdr 使用的
     * R82xx 初始寄存器表。
     */
    fun initialize(): Boolean {

        if (detectedAddress == 0) {

            val result = detect()
                ?: return false

            detectedType = result.type
            detectedAddress = result.i2cAddress
        }

        if (detectedAddress == 0) {
            return false
        }

        if (!control.setI2cRepeater(true)) {
            return false
        }

        try {

            if (!writeRegisters(
                    address = detectedAddress,
                    startRegister = REG_START,
                    data = INIT_REGISTERS
                )
            ) {
                return false
            }

            /*
             * rtl-sdr 初始化后会进入数字电视标准配置。
             *
             * PocketSDR 后续会针对 SDR 接收用途重新
             * 配置带宽、IF 和 AGC。
             */

            initialized = true

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    /**
     * 设置中心频率。
     *
     * 当前先完成频率参数计算和范围检查。
     *
     * 真正的 R82xx PLL 寄存器编码会在下一步接入。
     */
    fun setFrequency(
        frequencyHz: Long
    ): Boolean {

        if (!initialized) {
            return false
        }

        if (
            frequencyHz < MIN_FREQUENCY_HZ ||
            frequencyHz > MAX_FREQUENCY_HZ
        ) {
            return false
        }

        if (!control.setI2cRepeater(true)) {
            return false
        }

        try {

            /*
             * R82xx 实际 LO 频率还需要加上 IF。
             *
             * 当前 RTL2832U SDR 接收链路暂时使用
             * 0 Hz IF 框架，后续会根据 RTL2832U
             * 的实际 IF 设置调整。
             */
            val loFrequencyHz = frequencyHz

            if (!configurePll(loFrequencyHz)) {
                return false
            }

            currentFrequencyHz = frequencyHz

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    /**
     * 当前版本的 PLL 配置入口。
     *
     * 不直接写未经验证的 PLL 参数。
     *
     * 下一阶段会把官方 r82xx_set_pll()
     * 的整数分频、VCO、reference divider、
     * fractional PLL 和 lock detection 完整移植进来。
     */
    private fun configurePll(
        frequencyHz: Long
    ): Boolean {

        if (frequencyHz <= 0L) {
            return false
        }

        if (xtalFrequencyHz <= 0L) {
            return false
        }

        /*
         * 这里只计算基础参数，暂不写 PLL。
         *
         * 防止未经验证的 PLL 参数直接写入
         * 用户的实体 SDR。
         */
        val ratio =
            frequencyHz.toDouble() /
                xtalFrequencyHz.toDouble()

        val integerPart =
            ratio.toLong()

        val fractionalPart =
            (
                (ratio - integerPart.toDouble()) *
                    65536.0
                ).toLong()
                .coerceIn(
                    0L,
                    65535L
                )

        if (integerPart <= 0L) {
            return false
        }

        @Suppress("UNUSED_VARIABLE")
        val calculatedIntegerPart =
            integerPart

        @Suppress("UNUSED_VARIABLE")
        val calculatedFractionalPart =
            fractionalPart

        return true
    }

    /**
     * 读取 R82xx 寄存器。
     */
    private fun readRegisters(
        address: Int,
        startRegister: Int,
        length: Int
    ): ByteArray? {

        if (length <= 0) {
            return null
        }

        if (!control.i2cWrite(
                address,
                byteArrayOf(
                    (startRegister and 0xFF).toByte()
                )
            )
        ) {
            return null
        }

        val data =
            control.i2cRead(
                address,
                length
            ) ?: return null

        if (data.size != length) {
            return null
        }

        return data
    }

    /**
     * 连续写入 R82xx 寄存器。
     *
     * rtl-sdr 对 R82xx 使用最大 8 字节 I2C
     * message 长度。
     */
    private fun writeRegisters(
        address: Int,
        startRegister: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) {
            return false
        }

        val maxChunk = 8

        var offset = 0

        while (offset < data.size) {

            val count =
                minOf(
                    maxChunk,
                    data.size - offset
                )

            val packet =
                ByteArray(count + 1)

            packet[0] =
                (
                    (startRegister + offset) and
                        0xFF
                    ).toByte()

            for (i in 0 until count) {

                packet[i + 1] =
                    data[offset + i]
            }

            if (!control.i2cWrite(
                    address,
                    packet
                )
            ) {
                return false
            }

            offset += count
        }

        return true
    }

    /**
     * 当前只负责判断 R82xx I2C 通信是否有响应。
     *
     * R820T / R820T2 的严格型号识别后续处理。
     */
    private fun detectR82xxType(
        registers: ByteArray
    ): TunerType {

        if (registers.isEmpty()) {
            return TunerType.UNKNOWN
        }

        var nonZeroCount = 0

        for (value in registers) {

            if (
                (value.toInt() and 0xFF) != 0
            ) {
                nonZeroCount++
            }
        }

        if (nonZeroCount < 2) {
            return TunerType.UNKNOWN
        }

        return TunerType.R820T2
    }

    fun getTunerType(): TunerType {
        return detectedType
    }

    fun getI2cAddress(): Int {
        return detectedAddress
    }

    fun getCurrentFrequencyHz(): Long {
        return currentFrequencyHz
    }

    fun getXtalFrequencyHz(): Long {
        return xtalFrequencyHz
    }

    fun setXtalFrequencyHz(
        frequencyHz: Long
    ) {

        if (frequencyHz > 0L) {
            xtalFrequencyHz = frequencyHz
        }
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    fun markInitialized() {
        initialized = true
    }

    fun reset() {

        initialized = false

        currentFrequencyHz = 0L

        detectedType = TunerType.UNKNOWN

        detectedAddress = 0

        try {
            control.setI2cRepeater(false)
        } catch (_: Exception) {
        }
    }
}
