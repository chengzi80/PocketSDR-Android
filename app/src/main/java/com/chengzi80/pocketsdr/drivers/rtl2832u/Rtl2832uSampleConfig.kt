package com.chengzi80.pocketsdr.drivers.rtl2832u

data class Rtl2832uSampleConfig(
    val sampleRateHz: Long = 2_048_000L,
    val frequencyHz: Long = 100_000_000L,
    val bufferSize: Int = 16384,
    val usbTimeoutMs: Int = 1000
) {

    companion object {

        const val DEFAULT_SAMPLE_RATE_HZ = 2_048_000L

        const val MIN_SAMPLE_RATE_HZ = 225_001L

        const val MAX_SAMPLE_RATE_HZ = 3_200_000L

        const val DEFAULT_FREQUENCY_HZ = 100_000_000L

        const val MIN_FREQUENCY_HZ = 24_000_000L

        const val MAX_FREQUENCY_HZ = 1_766_000_000L

        const val DEFAULT_BUFFER_SIZE = 16384

        const val DEFAULT_USB_TIMEOUT_MS = 1000

        fun default(): Rtl2832uSampleConfig {
            return Rtl2832uSampleConfig()
        }
    }

    fun isValid(): Boolean {

        if (
            sampleRateHz < MIN_SAMPLE_RATE_HZ ||
            sampleRateHz > MAX_SAMPLE_RATE_HZ
        ) {
            return false
        }

        if (
            frequencyHz < MIN_FREQUENCY_HZ ||
            frequencyHz > MAX_FREQUENCY_HZ
        ) {
            return false
        }

        if (bufferSize < 512) {
            return false
        }

        if (usbTimeoutMs <= 0) {
            return false
        }

        return true
    }
}
