package com.example.overlay.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color as AndroidGraphicsColor
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
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
import androidx.compose.ui.graphics.asAndroidPath // Import for asAndroidPath()
import android.graphics.Path as AndroidGraphicsPath // Import Android Graphics Path
import android.graphics.RectF // For bounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

enum class DrawMode {
    FREEHAND, CIRCLE
}

class ScreenCircleActivity : ComponentActivity() {
    private val TAG = "ScreenCircleActivity"

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "mediaProjectionLauncher result received, resultCode: ${result.resultCode}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            val mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(result.resultCode, result.data!!)
            Log.d(TAG, "MediaProjection obtained, current drawMode: $drawMode")
            val bitmap = when (drawMode) {
                DrawMode.FREEHAND -> captureAndCropScreen(mediaProjection, lastPath, this)
                DrawMode.CIRCLE -> captureAndCropScreen(mediaProjection, lastCenter, lastRadius, this)
            }
            if (bitmap != null) {
                Log.d(TAG, "Bitmap captured and cropped successfully")
                val intent = Intent(this, OverlayService::class.java).apply {
                    putExtra("freehandBitmap", bitmap)
                    putExtra("drawMode", drawMode.name)
                    if (drawMode == DrawMode.CIRCLE) {
                        putExtra("centerX", lastCenter.x)
                        putExtra("centerY", lastCenter.y)
                        putExtra("radius", lastRadius)
                    }
                }
                startService(intent)
                // DO NOT FINISH HERE - Wait for Gemini result to be shown
                // finish()
            } else {
                Log.w(TAG, "Failed to capture or crop screen")
                finish() // Finish if capture failed
            }
        } else {
            Log.w(TAG, "MediaProjection permission denied or data is null")
            finish() // Finish if permission denied
        }
    }

    private var lastPath: Path = Path()
    private var lastPoints: List<Offset> = emptyList()
    private var lastCenter: Offset = Offset.Zero
    private var lastRadius: Float = 0f
    private var drawMode: DrawMode = DrawMode.CIRCLE // Default to circle drawing
    private var showDialog by mutableStateOf(false)
    private var geminiResult by mutableStateOf("")

    private val geminiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "geminiReceiver.onReceive called in ScreenCircleActivity")
            intent?.getStringExtra("result")?.let { result ->
                geminiResult = result
                showDialog = true
                Log.d(TAG, "Gemini result displayed in dialog")
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Should suppress lint for the else block on older APIs
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        drawMode = intent.getStringExtra("drawMode")?.let { DrawMode.valueOf(it) } ?: DrawMode.CIRCLE
        Log.d(TAG, "Resolved drawMode: $drawMode")

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geminiReceiver, IntentFilter("com.example.overlay.GEMINI_RESULT"), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("Deprecation")
            registerReceiver(geminiReceiver, IntentFilter("com.example.overlay.GEMINI_RESULT"))
        }
        Log.d(TAG, "geminiReceiver registered")

        setContent {
            Log.d(TAG, "setContent called")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                when (drawMode) {
                    DrawMode.FREEHAND -> FreehandDrawScreen { path, points ->
                        Log.d(TAG, "FreehandDrawScreen onLoopDrawn callback")
                        lastPath = path
                        lastPoints = points
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                    DrawMode.CIRCLE -> CircleDrawScreen { center, radius ->
                        Log.d(TAG, "CircleDrawScreen onCircleDrawn callback")
                        lastCenter = center
                        lastRadius = radius
                        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }
                }
                if (showDialog) {
                    GeminiResultDialog(
                        result = geminiResult,
                        onDismiss = { 
                            Log.d(TAG, "GeminiResultDialog onDismiss - finishing activity")
                            showDialog = false 
                            finish() // Finish activity when dialog is dismissed
                        }
                    )
                }
            }
        }
        Log.d(TAG, "Activity created with drawMode: $drawMode")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        unregisterReceiver(geminiReceiver)
        Log.d(TAG, "Activity destroyed")
    }
}

@Composable
fun FreehandDrawScreen(onLoopDrawn: (Path, List<Offset>) -> Unit) {
    val TAG = "FreehandDrawScreenComposable"
    Log.d(TAG, "FreehandDrawScreen composable executed")
    var path by remember { mutableStateOf(Path()) }
    var points by remember { mutableStateOf(mutableStateListOf<Offset>()) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        Log.d(TAG, "Drag start at $offset")
                        isDrawing = true
                        path = Path().apply { moveTo(offset.x, offset.y) }
                        points.clear()
                        points.add(offset)
                    },
                    onDrag = { change, _ ->
                        if (isDrawing) {
                            val newPoint = change.position
                            path.lineTo(newPoint.x, newPoint.y)
                            points.add(newPoint)
                        }
                    },
                    onDragEnd = {
                        Log.d(TAG, "Drag end, points: ${points.size}")
                        isDrawing = false
                        if (points.size > 1) {
                            val startPoint = points.first()
                            path.lineTo(startPoint.x, startPoint.y) // Close the path
                            points.add(startPoint) 
                            onLoopDrawn(path, points.toList())
                        }
                    }
                )
            }
    ) {
        drawPath(
            path = path,
            color = Color.Yellow.copy(alpha = 0.3f), 
            style = androidx.compose.ui.graphics.drawscope.Fill 
        )
        drawPath(
            path = path,
            color = Color.Yellow,
            style = Stroke(width = 15f)
        )
    }
}

@Composable
fun CircleDrawScreen(onCircleDrawn: (center: Offset, radius: Float) -> Unit) {
    val TAG = "CircleDrawScreenComposable"
    Log.d(TAG, "CircleDrawScreen composable executed")
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
                        }
                    },
                    onDragEnd = {
                        Log.d(TAG, "Drag end, radius: $radius")
                        isDrawing = false
                        if (radius > 0) {
                            onCircleDrawn(center, radius)
                        }
                    }
                )
            }
    ) {
        if (isDrawing || radius > 0) {
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
    Log.d(TAG, "GeminiResultDialog composable executed with result: $result")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini Result") },
        text = { Text(result) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@SuppressLint("ServiceCast")
private fun captureAndCropScreen(
    mediaProjection: MediaProjection?,
    composePath: Path, 
    context: Context
): Bitmap? {
    val TAG = "ScreenCircleActivity"
    Log.d(TAG, "captureAndCropScreen (Path version) called")
    if (mediaProjection == null) {
        Log.w(TAG, "MediaProjection is null in captureAndCropScreen (Path)")
        return null
    }

    val displayMetrics = context.resources.displayMetrics
    val width = displayMetrics.widthPixels
    val height = displayMetrics.heightPixels
    val density = displayMetrics.densityDpi

    val imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
    val virtualDisplay = mediaProjection.createVirtualDisplay(
        "ScreenCapture",
        width, height, density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface, null, null
    )
    Log.d(TAG, "VirtualDisplay created for Path capture")

    var fullBitmap: Bitmap? = null
    var croppedBitmap: Bitmap? = null

    try {
        val image = imageReader.acquireLatestImage() ?: run {
            Log.w(TAG, "acquireLatestImage returned null in Path capture")
            return null
        }
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        fullBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(buffer)
        image.close()
        Log.d(TAG, "Full screen bitmap created from ImageReader for Path capture")

        val bounds = RectF()
        val androidPath = composePath.asAndroidPath() 
        androidPath.computeBounds(bounds, true) 
        Log.d(TAG, "Computed bounds for Path: $bounds")

        val left = bounds.left.toInt().coerceIn(0, width)
        val top = bounds.top.toInt().coerceIn(0, height)
        val right = bounds.right.toInt().coerceIn(0, width)
        val bottom = bounds.bottom.toInt().coerceIn(0, height)

        if (left < right && top < bottom) {
            croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                left,
                top,
                (right - left).coerceAtLeast(1), 
                (bottom - top).coerceAtLeast(1) 
            )
            Log.d(TAG, "Bitmap cropped for Path capture")
        } else {
            Log.w(TAG, "Invalid crop dimensions for Path capture: [$left, $top, $right, $bottom]")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Capture failed in captureAndCropScreen (Path): ${e.message}", e)
    } finally {
        imageReader.close()
        virtualDisplay?.release()
        mediaProjection.stop()
        if (fullBitmap != null && fullBitmap != croppedBitmap) {
            fullBitmap.recycle()
        }
        Log.d(TAG, "captureAndCropScreen (Path version) finished")
    }
    return croppedBitmap
}

@SuppressLint("ServiceCast")
private fun captureAndCropScreen(
    mediaProjection: MediaProjection?,
    center: Offset,
    radius: Float,
    context: Context
): Bitmap? {
    val TAG = "ScreenCircleActivity"
    Log.d(TAG, "captureAndCropScreen (Circle version) called with center: $center, radius: $radius")
    if (mediaProjection == null) {
        Log.w(TAG, "MediaProjection is null in captureAndCropScreen (Circle)")
        return null
    }

    val displayMetrics = context.resources.displayMetrics
    val width = displayMetrics.widthPixels
    val height = displayMetrics.heightPixels
    val density = displayMetrics.densityDpi

    val imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
    val virtualDisplay = mediaProjection.createVirtualDisplay(
        "ScreenCapture",
        width, height, density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface, null, null
    )
    Log.d(TAG, "VirtualDisplay created for Circle capture")

    var fullBitmap: Bitmap? = null
    var croppedBitmap: Bitmap? = null

    try {
        val image = imageReader.acquireLatestImage() ?: run {
            Log.w(TAG, "acquireLatestImage returned null in Circle capture")
            return null
        }
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        fullBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(buffer)
        image.close()
        Log.d(TAG, "Full screen bitmap created from ImageReader for Circle capture")

        val left = (center.x - radius).toInt().coerceIn(0, width)
        val top = (center.y - radius).toInt().coerceIn(0, height)
        val right = (center.x + radius).toInt().coerceIn(0, width)
        val bottom = (center.y + radius).toInt().coerceIn(0, height)
        Log.d(TAG, "Crop dimensions for Circle: [$left, $top, $right, $bottom]")

        if (left < right && top < bottom) {
            croppedBitmap = Bitmap.createBitmap(
                fullBitmap,
                left,
                top,
                (right - left).coerceAtLeast(1), 
                (bottom - top).coerceAtLeast(1)  
            )
            Log.d(TAG, "Bitmap cropped for Circle capture")
        } else {
            Log.w(TAG, "Invalid crop dimensions for Circle capture: [$left, $top, $right, $bottom]")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Capture failed in captureAndCropScreen (Circle): ${e.message}", e)
    } finally {
        imageReader.close()
        virtualDisplay?.release()
        mediaProjection.stop()
        if (fullBitmap != null && fullBitmap != croppedBitmap) {
            fullBitmap.recycle()
        }
        Log.d(TAG, "captureAndCropScreen (Circle version) finished")
    }
    return croppedBitmap
}
