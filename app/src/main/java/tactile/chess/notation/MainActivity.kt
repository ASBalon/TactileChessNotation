package tactile.chess.notation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast


class MainActivity : AppCompatActivity() {
    private val chessPieces = mutableMapOf<String, Char>()
    private lateinit var inputField: EditText
    private lateinit var chessBoardGrid: GridLayout
    private lateinit var moveDisplay: TextView
    private lateinit var notationDisplay: TextView
    private lateinit var clearButton: Button
    private val squareViews = Array(8) { arrayOfNulls<TextView>(8) }

    private var originSquare: String? = null
    private var destinationSquare: String? = null
    private var moveCount = 1
    private var whiteToMove = true
    private val moveHistory = mutableListOf<String>()

    private val highlightColor = Color.parseColor("#FFFACD")
    private val lightSquareColor = Color.parseColor("#F0D9B5")
    private val darkSquareColor = Color.parseColor("#B58863")

    // USB Serial Components
    private lateinit var usbManager: UsbManager
    private var usbSerialPort: UsbSerialPort? = null
    private var usbIoManager: SerialInputOutputManager? = null
    private val ACTION_USB_PERMISSION = "tactile.chess.notation.USB_PERMISSION"

    private lateinit var exportButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Elct201)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // Initialize views
        inputField = findViewById(R.id.inputField)
        chessBoardGrid = findViewById(R.id.chessBoardGrid)
        moveDisplay = findViewById(R.id.moveDisplay)
        notationDisplay = findViewById(R.id.notationDisplay)
        clearButton = findViewById(R.id.clearButton)
        exportButton = findViewById(R.id.exportButton)

        initializeBoard()
        createBoardUI()

        // Input handling
        inputField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.length == 2) {
                    processSquareInput(s.toString().uppercase())
                    s.clear()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearButton.setOnClickListener { resetSelection() }
        exportButton.setOnClickListener { exportGameToClipboard() }

        // USB initialization
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        setupUsbConnection()
    }

    private fun setupUsbConnection() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: run {
            requestUsbPermission(driver.device)
            return
        }

        try {
            usbSerialPort = driver.ports[0].apply {
                open(connection)
                setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                usbIoManager = SerialInputOutputManager(this, object : SerialInputOutputManager.Listener {
                    override fun onNewData(data: ByteArray) {
                        runOnUiThread {
                            val square = String(data).trim()
                            if (square.length == 2) processSquareInput(square)
                        }
                    }

                    override fun onRunError(e: Exception?) {
                        runOnUiThread {
                            // handle error by showing Toast
                        }
                    }
                })

                Executors.newSingleThreadExecutor().execute {
                    usbIoManager?.start()
                }
            }
        } catch (e: Exception) {
            disconnectUsb()
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun disconnectUsb() {
        usbIoManager?.stop()
        usbIoManager = null
        usbSerialPort?.close()
        usbSerialPort = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_USB_PERMISSION) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) {
                intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)?.let {
                    setupUsbConnection()
                }
            }
        }
    }

    override fun onDestroy() {
        disconnectUsb()
        super.onDestroy()
    }

    // Chess functions
    private fun processSquareInput(square: String) {
        if (!square.matches(Regex("[A-H][1-8]"))) {
            inputField.error = "Invalid square"
            return
        }

        if (originSquare == null) {
            if (chessPieces[square] == null) {
                inputField.error = "No piece at $square"
                return
            }
            originSquare = square
            moveDisplay.text = "From: $square"
            highlightSquare(square, highlightColor)
        } else {
            destinationSquare = square
            moveDisplay.text = "From: $originSquare To: $square"
            highlightSquare(square, highlightColor)

            handleSpecialMoves()
            updateNotation()

            resetSelection()
            whiteToMove = !whiteToMove
        }
    }

    private fun handleSpecialMoves() {
        val origin = originSquare!!
        val dest = destinationSquare!!
        val piece = chessPieces[origin]!!

        // Castling
        if (piece.lowercaseChar() == 'k' && Math.abs(origin[0] - dest[0]) == 2) {
            handleCastling(origin, dest)
            return
        }

        // Pawn promotion (auto-queen)
        if (piece.lowercaseChar() == 'p' && (dest[1] == '1' || dest[1] == '8')) {
            chessPieces[dest] = if (whiteToMove) 'Q' else 'q'
            chessPieces.remove(origin)
            return
        }

        // En passant
        if (piece.lowercaseChar() == 'p' && origin[0] != dest[0] && chessPieces[dest] == null) {
            val capturedPawnSquare = "${dest[0]}${origin[1]}"
            chessPieces.remove(capturedPawnSquare)
        }

        // Normal move
        chessPieces[dest] = piece
        chessPieces.remove(origin)
    }

    private fun handleCastling(origin: String, dest: String) {
        val isShortCastle = dest[0] > origin[0]
        val rookOriginFile = if (isShortCastle) 'H' else 'A'
        val rookDestFile = if (isShortCastle) 'F' else 'D'
        val rank = origin[1]

        chessPieces[dest] = chessPieces[origin]!!
        chessPieces.remove(origin)

        val rookOrigin = "$rookOriginFile$rank"
        val rookDest = "$rookDestFile$rank"
        chessPieces[rookDest] = chessPieces[rookOrigin]!!
        chessPieces.remove(rookOrigin)
    }

    private fun updateNotation() {
        val origin = originSquare!!
        val dest = destinationSquare!!
        val piece = chessPieces[dest] ?: return

        val destNotation = dest.lowercase()
        var moveText = when {
            // Castling notation
            piece.lowercaseChar() == 'k' && Math.abs(origin[0] - dest[0]) == 2 -> {
                if (dest[0] > origin[0]) "O-O" else "O-O-O"
            }
            // Pawn promotion
            piece.lowercaseChar() == 'p' && (dest[1] == '1' || dest[1] == '8') -> {
                "$destNotation=Q"
            }
            // Pawn capture
            piece.lowercaseChar() == 'p' && origin[0] != dest[0] -> {
                "${origin[0].lowercase()}x$destNotation"
            }
            // Piece capture
            chessPieces[origin]?.let { it.lowercaseChar() != 'p' } == true && chessPieces[dest] != null -> {
                "${piece.uppercaseChar()}x$destNotation"
            }
            // Pawn move (don't show P)
            piece.lowercaseChar() == 'p' -> {
                destNotation
            }
            // Piece move
            else -> {
                "${piece.uppercaseChar()}$destNotation"
            }
        }

        if (whiteToMove) {
            moveText = "$moveCount. $moveText"
            moveCount++
        } else {
            moveText = " $moveText"
        }

        moveHistory.add(moveText)
        notationDisplay.text = moveHistory.joinToString(" ")
        updateBoard()
    }

    private fun resetSelection() {
        originSquare?.let { highlightSquare(it, getSquareColor(it)) }
        destinationSquare?.let { highlightSquare(it, getSquareColor(it)) }
        originSquare = null
        destinationSquare = null
        moveDisplay.text = ""
    }

    private fun highlightSquare(square: String, color: Int) {
        val col = square[0] - 'A'
        val row = 8 - square[1].toString().toInt()
        squareViews[row][col]?.setBackgroundColor(color)
    }

    private fun getSquareColor(square: String): Int {
        val col = square[0] - 'A'
        val row = 8 - square[1].toString().toInt()
        return if ((row + col) % 2 == 0) lightSquareColor else darkSquareColor
    }

    private fun initializeBoard() {
        chessPieces.clear()

        // White pieces
        chessPieces["A1"] = 'R'
        chessPieces["B1"] = 'N'
        chessPieces["C1"] = 'B'
        chessPieces["D1"] = 'Q'
        chessPieces["E1"] = 'K'
        chessPieces["F1"] = 'B'
        chessPieces["G1"] = 'N'
        chessPieces["H1"] = 'R'
        for (c in 'A'..'H') {
            chessPieces["${c}2"] = 'P'
        }

        // Black pieces
        chessPieces["A8"] = 'r'
        chessPieces["B8"] = 'n'
        chessPieces["C8"] = 'b'
        chessPieces["D8"] = 'q'
        chessPieces["E8"] = 'k'
        chessPieces["F8"] = 'b'
        chessPieces["G8"] = 'n'
        chessPieces["H8"] = 'r'
        for (c in 'A'..'H') {
            chessPieces["${c}7"] = 'p'
        }
    }

    private fun createBoardUI() {
        chessBoardGrid.removeAllViews()
        chessBoardGrid.columnCount = 8
        chessBoardGrid.rowCount = 8

        val squareSize = dpToPx(36)

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val square = TextView(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = squareSize
                        height = squareSize
                        rowSpec = GridLayout.spec(row)
                        columnSpec = GridLayout.spec(col)
                    }
                    gravity = Gravity.CENTER
                    textSize = 20f // Slightly smaller text
                    setTextColor(Color.BLACK)
                    setBackgroundColor(
                        if ((row + col) % 2 == 0) lightSquareColor else darkSquareColor
                    )
                }
                squareViews[row][col] = square
                chessBoardGrid.addView(square)
            }
        }
        updateBoard()
    }

    private fun exportGameToClipboard() {
        if (moveHistory.isEmpty()) {
            Toast.makeText(this, "No moves to export", Toast.LENGTH_SHORT).show()
            return
        }

        val pgn = StringBuilder().apply {
            append("[Event \"Casual Game\"]\n")
            append("[Site \"Android Chess App\"]\n")
            append("[Date \"${java.text.SimpleDateFormat("yyyy.MM.dd").format(java.util.Date())}\"]\n")
            append("[Result \"*\"]\n\n")

            append(moveHistory.joinToString(" "))
        }.toString()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chess PGN", pgn)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "PGN copied to clipboard", Toast.LENGTH_LONG).show()
    }

    private fun updateBoard() {
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val file = 'A' + col
                val rank = 8 - row
                val position = "$file$rank"
                val piece = chessPieces[position]
                squareViews[row][col]?.text = piece?.let {
                    mapOf(
                        'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗",
                        'N' to "♘", 'P' to "♙", 'k' to "♚", 'q' to "♛",
                        'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟"
                    )[it]
                } ?: ""
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}