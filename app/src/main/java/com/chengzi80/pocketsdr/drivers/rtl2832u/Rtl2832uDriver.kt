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

    private var detectedTunerAddress =
        0

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

    /**
     * 打开 RTL2832U USB 设备。
     */
    fun open(
        device: Rtl2832uUsbDevice
    ): Boolean {

        close()

        val conn =
            usbManager.openDevice(
                device.device
            ) ?: return false

        if (
            !conn.claimInterface(
                device.usbInterface,
                true
            )
        ) {

            conn.close()

            return false
        }

        connection = conn

        usbDevice = device

        control =
            Rtl2832uUsbControl(
                conn
            )

        demodulator =
            Rtl2832uDemodulator(
                control!!
            )

        tuner =
            R82xxTuner(
                control!!
            )

        return true
    }

    /**
     * 初始化 RTL2832U + R82xx。
     */
    fun initialize(): Boolean {

        val demod =
            demodulator
                ?: return false

        val tunerDriver =
            tuner
                ?: return false

        /*
         * 初始化 RTL2832U Demodulator。
         */
        if (!demod.initialize()) {
            return false
        }

        /*
         * 检测 R82xx。
         */
        val detection =
            tunerDriver.detect()
                ?: return false

        detectedTunerType =
            detection.type

        detectedTunerAddress =
            detection.i2cAddress

        /*
         * 初始化 R82xx。
         */
        if (!tunerDriver.initialize()) {
            return false
        }

        /*
         * 把默认采样率真正写入 RTL2832U。
         */
        if (
            !demod.setSampleRate(
                sampleConfig.sampleRateHz
            )
        ) {
            return false
        }

        return true
    }

    /**
     * 设置中心频率。
     */
    fun setFrequency(
        frequencyHz: Long
    ): Boolean {

        if (!isInitialized) {
            return false
        }

        if (
            frequencyHz <
            Rtl2832uSampleConfig.MIN_FREQUENCY_HZ
        ) {
            return false
        }

        if (
            frequencyHz >
            Rtl2832uSampleConfig.MAX_FREQUENCY_HZ
        ) {
            return false
        }

        if (
            tuner?.setFrequency(
                frequencyHz
            ) != true
        ) {
            return false
        }

        sampleConfig =
            sampleConfig.copy(
                frequencyHz = frequencyHz
            )

        return true
    }

    /**
     * 获取当前中心频率。
     */
    fun getFrequencyHz(): Long {

        return tuner?.getCurrentFrequencyHz()
            ?: 0L
    }

    /**
     * 设置采样率。
     */
    fun setSampleRate(
        sampleRateHz: Long
    ): Boolean {

        if (!isInitialized) {
            return false
        }

        if (
            sampleRateHz <
            Rtl2832uSampleConfig.MIN_SAMPLE_RATE_HZ
        ) {
            return false
        }

        if (
            sampleRateHz >
            Rtl2832uSampleConfig.MAX_SAMPLE_RATE_HZ
        ) {
            return false
        }

        val demod =
            demodulator
                ?: return false

        if (
            !demod.setSampleRate(
                sampleRateHz
            )
        ) {
            return false
        }

        sampleConfig =
            sampleConfig.copy(
                sampleRateHz = sampleRateHz
            )

        return true
    }

    /**
     * 获取当前采样率。
     */
    fun getSampleRateHz(): Long {

        return sampleConfig.sampleRateHz
    }

    /**
     * 获取完整采样配置。
     */
    fun getSampleConfig():
        Rtl2832uSampleConfig {

        return sampleConfig
    }

    /**
     * 开始读取 IQ 数据。
     *
     * 顺序：
     *
     * 1. 检查设备
     * 2. 设置采样率
     * 3. 设置中心频率
     * 4. Reset USB endpoint
     * 5. 开始 Bulk IN
     */
    fun startSampleReading(
        onSamples: (ByteArray, Int) -> Unit,
        onError: (String) -> Unit
    ): Boolean {

        if (!isInitialized) {

            onError(
                "SDR设备尚未初始化"
            )

            return false
        }

        if (connection == null) {

            onError(
                "USB设备连接不存在"
            )

            return false
        }

        if (usbDevice == null) {

            onError(
                "USB设备信息不存在"
            )

            return false
        }

        if (
            sampleReader?.isRunning() == true
        ) {

            return false
        }

        val demod =
            demodulator

                ?: run {

                    onError(
                        "RTL2832U Demodulator不存在"
                    )

                    return false
                }

        /*
         * 重新确认采样率。
         */
        if (
            !demod.setSampleRate(
                sampleConfig.sampleRateHz
            )
        ) {

            onError(
                "RTL2832U采样率设置失败"
            )

            return false
        }

        /*
         * 设置中心频率。
         */
        if (
            !setFrequency(
                sampleConfig.frequencyHz
            )
        ) {

            onError(
                "SDR中心频率设置失败"
            )

            return false
        }

        /*
         * 官方 rtl-sdr 在开始读取 IQ
         * 之前必须 reset endpoint。
         */
        if (
            !demod.resetBuffer()
        ) {

            onError(
                "RTL2832U USB缓冲区重置失败"
            )

            return false
        }

        /*
         * 创建 IQ reader。
         */
        val reader =
            Rtl2832uSampleReader(
                connection = connection!!,
                device = usbDevice!!
            )

        sampleReader =
            reader

        val started =
            reader.start(
                bufferSize =
                    sampleConfig.bufferSize,

                timeout =
                    sampleConfig.usbTimeoutMs,

                onSamples =
                    onSamples,

                onError =
                    onError
            )

        if (!started) {

            sampleReader = null

            return false
        }

        return true
    }

    /**
     * 停止读取 IQ。
     */
    fun stopSampleReading() {

        sampleReader?.stop()

        sampleReader = null
    }

    /**
     * 获取已经读取的原始 IQ 字节数。
     */
    fun getTotalSampleBytes(): Long {

        return sampleReader?.getTotalBytes()
            ?: 0L
    }

    /**
     * 获取 Tuner 类型。
     */
    fun getTunerType():
        R82xxTuner.TunerType {

        return detectedTunerType
    }

    /**
     * 获取 Tuner I2C 地址。
     */
    fun getTunerI2cAddress(): Int {

        return detectedTunerAddress
    }

    /**
     * 获取底层 USB 控制对象。
     */
    fun getControl():
        Rtl2832uUsbControl? {

        return control
    }

    /**
     * 获取 USB Connection。
     */
    fun getConnection():
        UsbDeviceConnection? {

        return connection
    }

    /**
     * 获取 R82xx Tuner。
     */
    fun getTuner():
        R82xxTuner? {

        return tuner
    }

    /**
     * 重置 Demodulator。
     */
    fun resetDemodulator(): Boolean {

        return demodulator?.reset()
            ?: false
    }

    /**
     * 关闭设备。
     */
    fun close() {

        /*
         * 停止 IQ。
         */
        try {

            sampleReader?.stop()

        } catch (_: Exception) {
        }

        sampleReader = null

        /*
         * 重置 Tuner。
         */
        try {

            tuner?.reset()

        } catch (_: Exception) {
        }

        /*
         * 重置 Demodulator。
         */
        try {

            demodulator?.reset()

        } catch (_: Exception) {
        }

        /*
         * 释放 USB Interface。
         */
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

        /*
         * 关闭 USB Connection。
         */
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

        detectedTunerAddress =
            0

        sampleConfig =
            Rtl2832uSampleConfig.default()
    }
}
