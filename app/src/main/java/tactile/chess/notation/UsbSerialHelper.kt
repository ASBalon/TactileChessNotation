package tactile.chess.notation

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class UsbSerialHelper(
    private val context: Context,
    private val onSquareReceived: (String) -> Unit
) : SerialInputOutputManager.Listener {
    private var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    fun connect(device: UsbDevice) {
        try {
            // Find the driver for the device
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                ?: throw IOException("No compatible driver found")

            // Open connection
            connection = usbManager.openDevice(device)
                ?: throw IOException("Failed to open device connection")

            // Get the first port
            port = driver.ports[0]
            port?.open(connection)
            port?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Start IO manager for reading data
            ioManager = SerialInputOutputManager(port, this)
            Executors.newSingleThreadExecutor().submit(Runnable {
                ioManager?.start()
            })

        } catch (e: Exception) {
            disconnect()
            e.printStackTrace()
        }
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        port?.close()
        port = null
        connection?.close()
        connection = null
    }

    override fun onNewData(data: ByteArray) {
        val square = String(data).trim()
        if (square.length == 2) {
            onSquareReceived(square)
        }
    }

    override fun onRunError(e: Exception?) {
        e?.printStackTrace()
        disconnect()
    }
}