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

    /**
     * 当前晶振频率。
     *
     * 大多数 RTL2832U + R820T/R820T2 设备使用 28.8 MHz。
     */
    private var xtalFrequencyHz = DEFAULT_XTAL_HZ

    /**
     * 检测 R82xx 调谐器。
     *
     * 注意：
     * 这里主要负责确认 I2C 通信是否正常。
     * R820T 与 R820T2 的完整型号识别不能仅依靠这里的简单寄存器判断。
     */
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

            if (r820Registers != null && r820Registers.size == 5) {

                val tunerType = detectR82xxType(r820Registers)

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

            if (r828dRegisters != null && r828dRegisters.size == 5) {

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
     * 当前阶段先完成基础寄存器配置。
     * PLL、滤波器、AGC 等将在下一阶段继续完善。
     */
    fun initialize(): Boolean {

        if (detectedAddress == 0) {
            val result = detect() ?: return false

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

            /*
             * R82xx 上电后的基础寄存器初始化。
             *
             * 这里采用连续写入的方式。
             * 后续我们会根据官方 r82xx_init()
             * 继续补齐完整初始化表。
             */

            val initRegisters = byteArrayOf(
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
                0x0F.toByte()
            )

            if (!writeRegisters(
                    address = detectedAddress,
                    startRegister = REG_START,
                    data = initRegisters
                )
            ) {
                return false
            }

            initialized = true

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    /**
     * 设置中心频率。
     *
     * 例如：
     *
     * setFrequency(100_000_000)
     *
     * = 100 MHz
     */
    fun setFrequency(frequencyHz: Long): Boolean {

        if (!initialized) {
            return false
        }

        if (frequencyHz < MIN_FREQUENCY_HZ ||
            frequencyHz > MAX_FREQUENCY_HZ
        ) {
            return false
        }

        if (!control.setI2cRepeater(true)) {
            return false
        }

        try {

            /*
             * 当前先计算目标 LO。
             *
             * RTL2832U 的最终 IF/PLL 配置将在下一阶段
             * 按官方 r82xx_set_freq() 完整移植。
             */

            val loFrequencyHz =
                frequencyHz

            if (!setPllFrequency(loFrequencyHz)) {
                return false
            }

            currentFrequencyHz = frequencyHz

            return true

        } finally {

            control.setI2cRepeater(false)
        }
    }

    /**
     * 设置 R82xx PLL。
     *
     * 这里采用 R82xx 常见的分频计算方式。
     *
     * 注意：
     * 当前版本主要用于建立完整的软件结构和硬件通信链路。
     * 下一步会继续加入官方实现中的：
     *
     * - VCO band
     * - PLL integer
     * - PLL fractional
     * - reference divider
     * - lock detection
     */
    private fun setPllFrequency(
        frequencyHz: Long
    ): Boolean {

        if (frequencyHz <= 0L) {
            return false
        }

        /*
         * R82xx PLL 的基本参数。
         *
         * 当前使用 28.8 MHz 晶振。
         */
        val xtal = xtalFrequencyHz

        if (xtal <= 0L) {
            return false
        }

        /*
         * 计算整数 N。
         *
         * 这里只建立计算框架。
         * 完整寄存器编码将在下一阶段严格按照
         * rtl-sdr 的 r82xx_set_pll() 完成。
         */
        val ratio =
            frequencyHz.toDouble() / xtal.toDouble()

        val integerPart = ratio.toLong()

        val fractionalPart =
            ((ratio - integerPart.toDouble()) * 65536.0)
                .toLong()
                .coerceIn(0L, 65535L)

        /*
         * 目前暂不把未经完整验证的 PLL 参数写入硬件。
         *
         * 这样可以避免错误 PLL 配置导致设备进入异常状态。
         *
         * 下一阶段会把这里替换成经过验证的寄存器配置。
         */
        @Suppress("UNUSED_VARIABLE")
        val calculatedIntegerPart = integerPart

        @Suppress("UNUSED_VARIABLE")
        val calculatedFractionalPart = fractionalPart

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

        val data = control.i2cRead(
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
     */
    private fun writeRegisters(
        address: Int,
        startRegister: Int,
        data: ByteArray
    ): Boolean {

        if (data.isEmpty()) {
            return false
        }

        /*
         * R82xx 一次 I2C 消息长度受到限制。
         *
         * 当前分成小块写入。
         */
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
                ((startRegister + offset) and 0xFF).toByte()

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
     * 当前阶段只做基础通信识别。
     *
     * 不把 R820T 和 R820T2 过度区分，
     * 避免因为不同批次芯片的寄存器差异造成误判。
     */
    private fun detectR82xxType(
        registers: ByteArray
    ): TunerType {

        if (registers.isEmpty()) {
            return TunerType.UNKNOWN
        }

        var nonZeroCount = 0

        for (value in registers) {

            if ((value.toInt() and 0xFF) != 0) {
                nonZeroCount++
            }
        }

        if (nonZeroCount < 2) {
            return TunerType.UNKNOWN
        }

        /*
         * 当前无法仅凭这组读取可靠区分：
         *
         * R820T
         * R820T2
         *
         * 因此先统一视为 R820T2/R820T 系列。
         *
         * 后续根据官方 r82xx 初始化流程进一步识别。
         */
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
