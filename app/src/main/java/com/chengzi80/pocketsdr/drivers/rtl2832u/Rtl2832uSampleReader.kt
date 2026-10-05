package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Rtl2832uSampleReader(
    private val connection: UsbDeviceConnection,
    private val device: Rtl2832uUsbDevice
) {

    companion object {

        private const val DEFAULT_BUFFER_SIZE =
            Rtl2832uSampleConfig.DEFAULT_BUFFER_SIZE

        private const val DEFAULT_TIMEOUT =
            Rtl2832uSampleConfig.DEFAULT_USB_TIMEOUT_MS
    }

    private val running =
        AtomicBoolean(false)

    private val totalBytes =
        AtomicLong(0L)

    private var workerThread: Thread? = null

    fun start(
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        timeout: Int = DEFAULT_TIMEOUT,
        onSamples: (ByteArray, Int) -> Unit,
        onError: (String) -> Unit
    ): Boolean {

        if (!running.compareAndSet(false, true)) {
            return false
        }

        totalBytes.set(0L)

        workerThread =
            Thread {

                val buffer =
                    ByteArray(bufferSize)

                try {

                    while (running.get()) {

                        val count =
                            connection.bulkTransfer(
                                device.bulkInEndpoint,
                                buffer,
                                buffer.size,
                                timeout
                            )

                        if (!running.get()) {
                            break
                        }

                        if (count > 0) {

                            totalBytes.addAndGet(
                                count.toLong()
                            )

                            /*
                             * bulkTransfer() 返回的数据
                             * 就是 RTL2832U 输出的原始
                             * unsigned 8-bit IQ 数据。
                             */
                            val samples =
                                buffer.copyOf(count)

                            try {

                                onSamples(
                                    samples,
                                    count
                                )

                            } catch (e: Exception) {

                                onError(
                                    "IQ数据回调异常：${e.message}"
                                )

                                break
                            }

                        } else if (count == 0) {

                            /*
                             * USB timeout。
                             *
                             * timeout 并不一定意味着设备
                             * 出错，所以继续读取。
                             */
                            continue

                        } else {

                            onError(
                                "USB Bulk读取失败：$count"
                            )

                            break
                        }
                    }

                } catch (e: InterruptedException) {

                    /*
                     * stop() 主动中断线程时属于正常退出。
                     */
                    if (running.get()) {

                        onError(
                            "IQ读取线程被中断：${e.message}"
                        )
                    }

                } catch (e: Exception) {

                    if (running.get()) {

                        onError(
                            "读取SDR数据失败：${e.message}"
                        )
                    }

                } finally {

                    running.set(false)
                }

            }.apply {

                name =
                    "PocketSDR-RTL2832U-IQ-Reader"

                priority =
                    Thread.NORM_PRIORITY

                start()
            }

        return true
    }

    fun stop() {

        if (!running.compareAndSet(
                true,
                false
            )
        ) {
            return
        }

        try {

            workerThread?.interrupt()

        } catch (_: Exception) {
        }

        workerThread = null
    }

    fun isRunning(): Boolean {
        return running.get()
    }

    fun getTotalBytes(): Long {
        return totalBytes.get()
    }

    fun resetStatistics() {
        totalBytes.set(0L)
    }
}
