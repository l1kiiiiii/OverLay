package com.example.overlay.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidGraphicsColor
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

// Constants for communication with OverlayService (ensure these match in OverlayService.kt)
const val ACTION_START_MEDIA_PROJECTION = "com.example.overlay.services.ACTION_START_MEDIA_PROJECTION"
const val EXTRA_RESULT_CODE = "com.example.overlay.services.EXTRA_RESULT_CODE"
const val EXTRA_RESULT_DATA = "com.example.overlay.services.EXTRA_RESULT_DATA"
const val EXTRA_DRAW_MODE = "com.example.overlay.services.EXTRA_DRAW_MODE"
const val EXTRA_PATH_POINTS = "com.example.overlay.services.EXTRA_PATH_POINTS" // FloatArray of x,y pairs
const val EXTRA_CIRCLE_CENTER_X = "com.example.overlay.services.EXTRA_CIRCLE_CENTER_X"
const val EXTRA_CIRCLE_CENTER_Y = "com.example.overlay.services.EXTRA_CIRCLE_CENTER_Y"
const val EXTRA_CIRCLE_RADIUS = "com.example.overlay.services.EXTRA_CIRCLE_RADIUS"

enum class DrawMode {
    FREEHAND, CIRCLE
}

class ScreenCircleActivity : ComponentActivity() {
    private val TAG = "ScreenCircleActivity"

    private val mediaProjectionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "mediaProjectionPermissionLauncher result received, resultCode: ${result.resultCode}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            Log.d(TAG, "MediaProjection permission GRANTED. Delegating to OverlayService.")
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                action = ACTION_START_MEDIA_PROJECTION
                putExtra(EXTRA_RESULT_CODE, result.resultCode)
                putExtra(EXTRA_RESULT_DATA, result.data)
                putExtra(EXTRA_DRAW_MODE, drawMode.name)

                when (drawMode) {
                    DrawMode.FREEHAND -> {
                        // Convert List<Offset> to FloatArray for parceling
                        val pointsArray = FloatArray(lastPoints.size * 2)
                        for (i in lastPoints.indices) {
                            pointsArray[i * 2] = lastPoints[i].x
                            pointsArray[i * 2 + 1] = lastPoints[i].y
                        }
                        putExtra(EXTRA_PATH_POINTS, pointsArray)
                        Log.d(TAG, "Passing ${lastPoints.size} points for FREEHAND")
                    }
                    DrawMode.CIRCLE -> {
                        putExtra(EXTRA_CIRCLE_CENTER_X, lastCenter.x)
                        putExtra(EXTRA_CIRCLE_CENTER_Y, lastCenter.y)
                        putExtra(EXTRA_CIRCLE_RADIUS, lastRadius)
                        Log.d(TAG, "Passing center: $lastCenter, radius: $lastRadius for CIRCLE")
                    }
                }
            }
            startService(serviceIntent)
            // Activity will remain open to show GeminiResultDialog later
            // finish() // DO NOT finish here yet
        } else {
            Log.w(TAG, "MediaProjection permission DENIED or data is null")
            finish() // Finish if permission denied or no data
        }
    }

    private var lastPathForDrawing: Path = Path() // For drawing on canvas only
    private var lastPoints: List<Offset> = emptyList() // For sending to service
    private var lastCenter: Offset = Offset.Zero
    private var lastRadius: Float = 0f
    private var drawMode: DrawMode = DrawMode.CIRCLE
    private var showDialog by mutableStateOf(false)
    private var geminiResult by mutableStateOf("")

    private val geminiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "geminiReceiver.onReceive called in ScreenCircleActivity")
            intent?.getStringExtra("result")?.let { result ->
                geminiResult = result
                showDialog = true
                Log.d(TAG, "Gemini result received, showing dialog: $result")
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        drawMode = intent.getStringExtra("drawMode")?.let { DrawMode.valueOf(it) } ?: DrawMode.CIRCLE
        Log.d(TAG, "Resolved drawMode: $drawMode from intent")

        // System UI visibility adjustments
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
        window.setBackgroundDrawable(ColorDrawable(AndroidGraphicsColor.parseColor("#66000000")))

        // Register BroadcastReceiver for Gemini results
        val intentFilter = IntentFilter("com.example.overlay.GEMINI_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geminiReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(geminiReceiver, intentFilter)
        }
        Log.d(TAG, "geminiReceiver registered")

        setContent {
            Log.d(TAG, "setContent called for drawMode: $drawMode")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                when (drawMode) {
                    DrawMode.FREEHAND -> FreehandDrawScreen { path, points ->
                        Log.d(TAG, "FreehandDrawScreen onLoopDrawn callback, points: ${points.size}")
                        lastPathForDrawing = path // For redrawing on canvas
                        lastPoints = points       // For sending to service
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                    DrawMode.CIRCLE -> CircleDrawScreen { center, radius ->
                        Log.d(TAG, "CircleDrawScreen onCircleDrawn callback, center: $center, radius: $radius")
                        lastCenter = center
                        lastRadius = radius
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                }
                if (showDialog) {
                    GeminiResultDialog(
                        result = geminiResult,
                        onDismiss = {
                            Log.d(TAG, "GeminiResultDialog onDismiss - finishing activity")
                            showDialog = false
                            finish()
                        }
                    )
                }
            }
        }
        Log.d(TAG, "Activity UI setup complete")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        unregisterReceiver(geminiReceiver)
        Log.d(TAG, "geminiReceiver unregistered, Activity destroyed")
    }
}

@Composable
fun FreehandDrawScreen(onLoopDrawn: (Path, List<Offset>) -> Unit) {
    val TAG = "FreehandDrawScreenComposable"
    var currentPath by remember { mutableStateOf(Path()) }
    var currentPoints by remember { mutableStateOf(mutableStateListOf<Offset>()) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        Log.d(TAG, "Drag start at $offset")
                        isDrawing = true
                        currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        currentPoints.clear()
                        currentPoints.add(offset)
                    },
                    onDrag = { change, _ ->
                        if (isDrawing) {
                            val newPoint = change.position
                            currentPath.lineTo(newPoint.x, newPoint.y)
                            currentPoints.add(newPoint)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        Log.d(TAG, "Drag end, points: ${currentPoints.size}")
                        isDrawing = false
                        if (currentPoints.size > 1) {
                            val startPoint = currentPoints.first()
                            currentPath.lineTo(startPoint.x, startPoint.y) // Close the path for drawing
                            val finalPoints = currentPoints.toList() // Create immutable list for callback
                            onLoopDrawn(currentPath, finalPoints)
                        }
                    }
                )
            }
    ) {
        drawPath(
            path = currentPath,
            color = Color.Yellow.copy(alpha = 0.3f),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        drawPath(
            path = currentPath,
            color = Color.Yellow,
            style = Stroke(width = 15f)
        )
    }
}

@Composable
fun CircleDrawScreen(onCircleDrawn: (center: Offset, radius: Float) -> Unit) {
    val TAG = "CircleDrawScreenComposable"
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(0f) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        Log.d(TAG, "Drag start at $offset")
                        isDrawing = true
                        center = offset
                        radius = 0f
                    },
                    onDrag = { change, _ ->
                        if (isDrawing) {
                            val newRadius = hypot(change.position.x - center.x, change.position.y - center.y)
                            radius = newRadius
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        Log.d(TAG, "Drag end, radius: $radius")
                        isDrawing = false
                        if (radius > 0f) {
                            onCircleDrawn(center, radius)
                        }
                    }
                )
            }
    ) {
        if (isDrawing || radius > 0f) {
            drawCircle(
                color = Color.Red.copy(alpha = 0.3f),
                center = center,
                radius = radius.coerceAtLeast(1.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            drawCircle(
                color = Color.Red,
                center = center,
                radius = radius.coerceAtLeast(1.dp.toPx()),
                style = Stroke(width = 5f)
            )
        }
    }
}

@Composable
fun GeminiResultDialog(result: String, onDismiss: () -> Unit) {
    val TAG = "GeminiResultDialogComposable"
    // Log only a snippet if result is too long to avoid cluttering Logcat
    val logResult = if (result.length > 100) result.substring(0, 100) + "..." else result
    Log.d(TAG, "GeminiResultDialog composable executed with result: $logResult")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini Result") },
        text = { Text(result) }, // Consider Scrollable Text for long results
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// captureAndCropScreen methods are removed from here as they will be moved to OverlayService

