package com.chengzi80.pocketsdr.drivers.rtl2832u

import android.hardware.usb.UsbDeviceConnection

class Rtl2832uSampleReader(
    private val connection: UsbDeviceConnection,
    private val device: Rtl2832uUsbDevice
) {

    companion object {

        private const val DEFAULT_BUFFER_SIZE =
            16 * 16384

        private const val DEFAULT_TIMEOUT =
            1000
    }

    private var running =
        false

    private var workerThread:
            Thread? = null

    fun start(
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        timeout: Int = DEFAULT_TIMEOUT,
        onSamples: (ByteArray, Int) -> Unit,
        onError: (String) -> Unit
    ): Boolean {

        if (running) {
            return false
        }

        running = true

        workerThread =
            Thread {

                val buffer =
                    ByteArray(
                        bufferSize
                    )

                try {

                    while (running) {

                        val count =
                            connection.bulkTransfer(
                                device.bulkInEndpoint,
                                buffer,
                                buffer.size,
                                timeout
                            )

                        if (!running) {
                            break
                        }

                        if (count > 0) {

                            val samples =
                                buffer.copyOf(
                                    count
                                )

                            onSamples(
                                samples,
                                count
                            )

                        } else if (
                            count < 0
                        ) {

                            onError(
                                "USB Bulk 读取失败：$count"
                            )

                            break
                        }
                    }

                } catch (e: Exception) {

                    if (running) {

                        onError(
                            "读取 SDR 数据失败：${e.message}"
                        )
                    }

                } finally {

                    running = false
                }

            }.apply {

                name =
                    "PocketSDR-RTL2832U-Reader"

                start()
            }

        return true
    }

    fun stop() {

        running = false

        try {

            workerThread?.interrupt()

        } catch (_: Exception) {
        }

        workerThread = null
    }

    fun isRunning(): Boolean {

        return running
    }
}
