package com.example.overlay.services

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.hypot
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.createBitmap

class ScreenCircleActivity : ComponentActivity() {
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val mediaProjection = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(result.resultCode, result.data!!)
            val bitmap = captureAndCropScreen(mediaProjection, lastCenter, lastRadius, this)
            if (bitmap != null) {
                startService(Intent(this, OverlayService::class.java).apply {
                    putExtra("circleBitmap", bitmap)
                })
            } else {
                Log.w("ScreenCircleActivity", "Failed to capture or crop screen")
            }
        } else {
            Log.w("ScreenCircleActivity", "MediaProjection permission denied")
        }
    }

    private var lastCenter: Offset = Offset.Zero
    private var lastRadius: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        window.setBackgroundDrawable(AndroidGraphicsColor.parseColor("#66000000").toDrawable())

        setContent {
            Box {
                CircleDrawScreen { center, radius, _ ->
                    lastCenter = center
                    lastRadius = radius
                    val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            }
        }
        Log.d("ScreenCircleActivity", "Activity created")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, OverlayService::class.java))
        Log.d("ScreenCircleActivity", "Activity destroyed")
    }
}

@Composable
fun CircleDrawScreen(onCircleDrawn: (Offset, Float, Bitmap?) -> Unit) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableFloatStateOf(0f) }
    var isDrawing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
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
                    isDrawing = false
                    onCircleDrawn(center, radius, null) // Bitmap will be handled by captureAndCropScreen
                }
            )
        }) {
        if (isDrawing || radius > 0) {
            drawCircle(
                color = Color.Red,
                center = center,
                radius = radius.coerceAtLeast(1.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
            )
        }
    }
}

@SuppressLint("ServiceCast")
private fun captureAndCropScreen(
    mediaProjection: MediaProjection?,
    center: Offset,
    radius: Float,
    context: Context
): Bitmap? {
    val displayMetrics = context.resources.displayMetrics
    val width = displayMetrics.widthPixels
    val height = displayMetrics.heightPixels
    val density = displayMetrics.densityDpi

    val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    val virtualDisplay = mediaProjection?.createVirtualDisplay(
        "ScreenCapture",
        width, height, density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface, null, null
    )

    try {
        val image = imageReader.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = createBitmap(width + rowPadding / pixelStride, height)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Crop the circle
            return cropCircleFromBitmap(bitmap, center, radius)
        }
    } catch (e: Exception) {
        Log.e("ScreenCircleActivity", "Capture failed: ${e.message}")
    } finally {
        imageReader.close()
        virtualDisplay?.release()
        mediaProjection?.stop()
    }
    return null
}

private fun cropCircleFromBitmap(bitmap: Bitmap, center: Offset, radius: Float): Bitmap {
    // Adjust srcRect to stay within bitmap bounds
    val adjustedCenterX = center.x.coerceIn(0f, bitmap.width.toFloat())
    val adjustedCenterY = center.y.coerceIn(0f, bitmap.height.toFloat())
    val adjustedRadius = radius.coerceAtMost(
        minOf(adjustedCenterX, bitmap.width - adjustedCenterX, adjustedCenterY, bitmap.height - adjustedCenterY)
    )

    val output = createBitmap((adjustedRadius * 2).toInt(), (adjustedRadius * 2).toInt())
    val canvas = android.graphics.Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rect = RectF(0f, 0f, adjustedRadius * 2, adjustedRadius * 2)
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(adjustedRadius, adjustedRadius, adjustedRadius, paint)
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    val srcRect = Rect(
        (adjustedCenterX - adjustedRadius).toInt().coerceIn(0, bitmap.width),
        (adjustedCenterY - adjustedRadius).toInt().coerceIn(0, bitmap.height),
        (adjustedCenterX + adjustedRadius).toInt().coerceIn(0, bitmap.width),
        (adjustedCenterY + adjustedRadius).toInt().coerceIn(0, bitmap.height)
    )
    canvas.drawBitmap(bitmap, srcRect, rect, paint)
    return output
}