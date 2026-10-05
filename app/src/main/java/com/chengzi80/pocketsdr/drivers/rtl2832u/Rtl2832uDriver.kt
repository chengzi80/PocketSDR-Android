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

    val isOpen: Boolean
        get() = connection != null

    val isInitialized: Boolean
        get() =
            demodulator?.isInitialized() == true &&
                tuner?.isInitialized() == true

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
     * 初始化整个 RTL2832U + R82xx 接收链路。
     */
    fun initialize(): Boolean {

        val demod =
            demodulator
                ?: return false

        val tunerDriver =
            tuner
                ?: return false

        /*
         * 第一步：
         * 初始化 RTL2832U Demodulator。
         */
        if (!demod.initialize()) {
            return false
        }

        /*
         * 第二步：
         * 检测 R82xx。
         */
        val detection =
            tunerDriver.detect()
                ?: return false

        /*
         * 第三步：
         * 初始化 R82xx。
         */
        if (!tunerDriver.initialize()) {
            return false
        }

        /*
         * 目前只是记录检测结果。
         *
         * 后续可以通过 Driver API 对外提供。
         */
        detectedTunerType =
            detection.type

        detectedTunerAddress =
            detection.i2cAddress

        return true
    }

    private var detectedTunerType =
        R82xxTuner.TunerType.UNKNOWN

    private var detectedTunerAddress =
        0

    /**
     * 获取 R82xx 类型。
     */
    fun getTunerType():
        R82xxTuner.TunerType {

        return detectedTunerType
    }

    /**
     * 获取 R82xx I2C 地址。
     */
    fun getTunerI2cAddress(): Int {

        return detectedTunerAddress
    }

    /**
     * 设置中心频率。
     *
     * 例如：
     *
     * 100_000_000L
     *
     * = 100 MHz
     */
    fun setFrequency(
        frequencyHz: Long
    ): Boolean {

        return tuner?.setFrequency(
            frequencyHz
        ) == true
    }

    /**
     * 获取当前中心频率。
     */
    fun getFrequencyHz(): Long {

        return tuner?.getCurrentFrequencyHz()
            ?: 0L
    }

    /**
     * 获取底层控制对象。
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
     * 获取 Tuner。
     */
    fun getTuner(): R82xxTuner? {

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

        detectedTunerAddress =
            0
    }
}
