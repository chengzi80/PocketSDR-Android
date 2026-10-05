package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager

class Rtl2832uDriver(
    private val usbManager: UsbManager
) {

    private var connection: UsbDeviceConnection? = null
    private var usbDevice: Rtl2832uUsbDevice? = null

    private var control: Rtl2832uUsbControl? = null
    private var demodulator: Rtl2832uDemodulator? = null
    private var tuner: R82xxTuner? = null

    private var sampleReader: Rtl2832uSampleReader? = null

    private var sampleConfig =
        Rtl2832uSampleConfig.default()

    private var detectedTunerType =
        R82xxTuner.TunerType.UNKNOWN

    private var detectedTunerAddress = 0

    @Volatile
    private var lastError: String? = null

    val isOpen: Boolean
        get() = connection != null

    val isInitialized: Boolean
        get() =
            demodulator?.isInitialized() == true &&
                tuner?.isInitialized() == true

    val isReadingSamples: Boolean
        get() =
            sampleReader?.isRunning() == true

    val deviceInfo: Rtl2832uUsbDevice?
        get() = usbDevice

    fun open(
        device: Rtl2832uUsbDevice
    ): Boolean {

        close()

        lastError = null

        val conn =
            usbManager.openDevice(
                device.device
            ) ?: run {
                lastError =
                    "USB设备无法打开"
                return false
            }

        if (
            !conn.claimInterface(
                device.usbInterface,
                true
            )
        ) {

            conn.close()

            lastError =
                "无法占用USB接口"

            return false
        }

        connection = conn
        usbDevice = device

        val usbControl =
            Rtl2832uUsbControl(
                conn
            )

        control =
            usbControl

        demodulator =
            Rtl2832uDemodulator(
                usbControl
            )

        tuner =
            R82xxTuner(
                usbControl
            )

        return true
    }

    fun initialize(): Boolean {

        val demod =
            demodulator ?: run {
                lastError =
                    "RTL2832U Demodulator不存在"
                return false
            }

        val tunerDriver =
            tuner ?: run {
                lastError =
                    "R820T2 Tuner不存在"
                return false
            }

        /*
         * Step 1:
         * initialize RTL2832U.
         */
        if (
            !demod.initialize()
        ) {

            lastError =
                "RTL2832U初始化失败"

            return false
        }

        /*
         * Step 2:
         * detect R82xx tuner.
         */
        val detection =
            tunerDriver.detect()

                ?: run {

                    lastError =
                        "无法检测R820T/R820T2调谐器"

                    return false
                }

        detectedTunerType =
            detection.type

        detectedTunerAddress =
            detection.i2cAddress

        /*
         * Step 3:
         * initialize tuner.
         */
        if (
            !tunerDriver.initialize()
        ) {

            lastError =
                "R820T/R820T2初始化失败"

            return false
        }

        /*
         * Step 4:
         * configure default sample rate.
         */
        if (
            !demod.setSampleRate(
                sampleConfig.sampleRateHz
            )
        ) {

            lastError =
                "RTL2832U采样率设置失败"

            return false
        }

        /*
         * Step 5:
         * configure default frequency.
         */
        if (
            !tunerDriver.setFrequency(
                sampleConfig.frequencyHz
            )
        ) {

            lastError =
                "R820T2默认频率设置失败"

            return false
        }

        lastError = null

        return true
    }

    fun setFrequency(
        frequencyHz: Long
    ): Boolean {

        if (!isInitialized) {
            lastError =
                "SDR设备尚未初始化"
            return false
        }

        if (
            frequencyHz <
            Rtl2832uSampleConfig.MIN_FREQUENCY_HZ
        ) {

            lastError =
                "频率低于设备支持范围"

            return false
        }

        if (
            frequencyHz >
            Rtl2832uSampleConfig.MAX_FREQUENCY_HZ
        ) {

            lastError =
                "频率高于设备支持范围"

            return false
        }

        val tunerDriver =
            tuner ?: run {
                lastError =
                    "Tuner不存在"
                return false
            }

        if (
            !tunerDriver.setFrequency(
                frequencyHz
            )
        ) {

            lastError =
                "R820T2调频失败"

            return false
        }

        sampleConfig =
            sampleConfig.copy(
                frequencyHz =
                    frequencyHz
            )

        lastError = null

        return true
    }

    fun getFrequencyHz(): Long =
        tuner?.getCurrentFrequencyHz()
            ?: 0L

    fun setSampleRate(
        sampleRateHz: Long
    ): Boolean {

        if (!isInitialized) {
            lastError =
                "SDR设备尚未初始化"
            return false
        }

        if (
            sampleRateHz <
            Rtl2832uSampleConfig.MIN_SAMPLE_RATE_HZ
        ) {
            lastError =
                "采样率低于设备支持范围"
            return false
        }

        if (
            sampleRateHz >
            Rtl2832uSampleConfig.MAX_SAMPLE_RATE_HZ
        ) {
            lastError =
                "采样率高于设备支持范围"
            return false
        }

        if (
            sampleRateHz >
                300_000L &&
            sampleRateHz <=
                900_000L
        ) {
            lastError =
                "RTL2832U不支持300001~900000 Hz采样率"
            return false
        }

        val demod =
            demodulator ?: run {
                lastError =
                    "Demodulator不存在"
                return false
            }

        if (
            !demod.setSampleRate(
                sampleRateHz
            )
        ) {

            lastError =
                "RTL2832U采样率设置失败"

            return false
        }

        sampleConfig =
            sampleConfig.copy(
                sampleRateHz =
                    demod.getSampleRateHz()
            )

        lastError = null

        return true
    }

    fun getSampleRateHz(): Long =
        sampleConfig.sampleRateHz

    fun getSampleConfig():
        Rtl2832uSampleConfig =
        sampleConfig

    fun startSampleReading(
        onSamples: (
            ByteArray,
            Int
        ) -> Unit,

        onError: (
            String
        ) -> Unit
    ): Boolean {

        if (!isInitialized) {

            val message =
                lastError
                    ?: "SDR设备尚未初始化"

            onError(message)

            return false
        }

        val conn =
            connection ?: run {

                onError(
                    "USB设备连接不存在"
                )

                return false
            }

        val device =
            usbDevice ?: run {

                onError(
                    "USB设备信息不存在"
                )

                return false
            }

        if (
            sampleReader?.isRunning() == true
        ) {

            onError(
                "IQ读取已经在运行"
            )

            return false
        }

        val demod =
            demodulator ?: run {

                onError(
                    "RTL2832U Demodulator不存在"
                )

                return false
            }

        /*
         * Tune tuner.
         */
        if (
            !setFrequency(
                sampleConfig.frequencyHz
            )
        ) {

            onError(
                lastError
                    ?: "SDR中心频率设置失败"
            )

            return false
        }

        /*
         * Mandatory endpoint reset before
         * starting sample acquisition.
         */
        if (
            !demod.resetBuffer()
        ) {

            onError(
                "RTL2832U USB缓冲区重置失败"
            )

            return false
        }

        val reader =
            Rtl2832uSampleReader(
                connection = conn,
                device = device
            )

        sampleReader =
            reader

        val started =
            reader.start(
                bufferSize =
                    sampleConfig.bufferSize,

                timeout =
                    sampleConfig.usbTimeoutMs,

                onSamples = {
                        data,
                        count ->

                    onSamples(
                        data,
                        count
                    )
                },

                onError = {
                    error ->

                    lastError =
                        error

                    onError(
                        error
                    )
                }
            )

        if (!started) {

            sampleReader = null

            onError(
                "无法启动USB IQ读取线程"
            )

            return false
        }

        return true
    }

    fun stopSampleReading() {

        sampleReader?.stop()

        sampleReader = null
    }

    fun getTotalSampleBytes(): Long =
        sampleReader?.getTotalBytes()
            ?: 0L

    fun getTunerType():
        R82xxTuner.TunerType =
        detectedTunerType

    fun getTunerI2cAddress(): Int =
        detectedTunerAddress

    fun getTunerPllLock(): Boolean =
        tuner?.hasPllLock()
            ?: false

    fun getControl():
        Rtl2832uUsbControl? =
        control

    fun getConnection():
        UsbDeviceConnection? =
        connection

    fun getTuner():
        R82xxTuner? =
        tuner

    fun getLastError():
        String? =
        lastError

    fun resetDemodulator(): Boolean =
        demodulator?.reset()
            ?: false

    fun close() {

        try {
            sampleReader?.stop()
        } catch (_: Exception) {
        }

        sampleReader = null

        try {
            tuner?.reset()
        } catch (_: Exception) {
        }

        try {
            demodulator?.reset()
        } catch (_: Exception) {
        }

        try {

            val conn =
                connection

            val dev =
                usbDevice

            if (
                conn != null &&
                dev != null
            ) {

                conn.releaseInterface(
                    dev.usbInterface
                )
            }

        } catch (_: Exception) {
        }

        try {
            connection?.close()
        } catch (_: Exception) {
        }

        connection = null
        usbDevice = null
        control = null
        demodulator = null
        tuner = null

        detectedTunerType =
            R82xxTuner.TunerType.UNKNOWN

        detectedTunerAddress = 0

        lastError = null

        sampleConfig =
            Rtl2832uSampleConfig.default()
    }
}
