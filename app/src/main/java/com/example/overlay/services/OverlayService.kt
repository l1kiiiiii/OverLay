package com.example.overlay.services

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas // For drawing path on bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overlay.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import com.example.overlay.MainActivity
import com.example.overlay.R
import android.graphics.Path as AndroidGraphicsPath // For constructing path for cropping


class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val TAG = "OverlayService"
    private val NOTIFICATION_CHANNEL_ID = "OverlayServiceChannel"
    private val NOTIFICATION_ID = 1

    private lateinit var windowManager: WindowManager
    private var floatButton: ComposeView? = null
    private lateinit var generativeModel: GenerativeModel
    private val coroutineScope = CoroutineScope(Dispatchers.Main) // Use Dispatchers.IO for capture if needed

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private val geminiResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "geminiResultReceiver.onReceive called")
            intent?.getStringExtra("result")?.let {
                Log.d(TAG, "Gemini Result Received by service: $it")
                // Optionally, the service itself could also react to its own Gemini results
            }
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        Log.d(TAG, "onCreate called")
        super.onCreate()

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Overlay Service Initializing")
            .setContentText("Setting up overlay features.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            Log.d(TAG, "Service started in foreground with media projection type from onCreate.")
        } else {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Service started in foreground from onCreate.")
        }

        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val intentFilter = IntentFilter("com.example.overlay.GEMINI_RESULT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geminiResultReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(geminiResultReceiver, intentFilter)
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = BuildConfig.API_KEY
            ).also { Log.d(TAG, "Gemini model initialized") }

            floatButton = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent { FloatingButtonUI() }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.END; x = 0; y = 100 }
            windowManager.addView(floatButton, params)
            Log.d(TAG, "Floating button added")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed in onCreate: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Overlay Service Channel", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel created.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")

        ensureForegroundState()

        if (intent?.action == ACTION_START_MEDIA_PROJECTION) {
            Log.d(TAG, "Received ACTION_START_MEDIA_PROJECTION")
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
            val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
            val drawModeName = intent.getStringExtra(EXTRA_DRAW_MODE)
            val drawMode = try { drawModeName?.let { DrawMode.valueOf(it) } } catch (e: IllegalArgumentException) { null }

            if (resultCode == RESULT_OK && resultData != null && drawMode != null) {
                Log.d(TAG, "Media projection permission data received. Attempting to start projection.")
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                Log.d(TAG, "MediaProjection obtained in service.")

                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped")
                        mediaProjection = null
                    }
                }, null)

                val capturedBitmap = when (drawMode) {
                    DrawMode.FREEHAND -> {
                        val pathPointsArray = intent.getFloatArrayExtra(EXTRA_PATH_POINTS)
                        if (pathPointsArray != null && pathPointsArray.size >= 4) { // Need at least 2 points (4 floats)
                            val androidPath = convertFloatArrayToAndroidPath(pathPointsArray)
                            captureAndCropScreen(androidPath, this)
                        } else {
                            Log.e(TAG, "Invalid or missing path points for FREEHAND mode.")
                            null
                        }
                    }
                    DrawMode.CIRCLE -> {
                        val centerX = intent.getFloatExtra(EXTRA_CIRCLE_CENTER_X, -1f)
                        val centerY = intent.getFloatExtra(EXTRA_CIRCLE_CENTER_Y, -1f)
                        val radius = intent.getFloatExtra(EXTRA_CIRCLE_RADIUS, -1f)
                        if (centerX != -1f && centerY != -1f && radius > 0) {
                            captureAndCropScreen(Offset(centerX, centerY), radius, this)
                        } else {
                            Log.e(TAG, "Invalid or missing circle parameters for CIRCLE mode.")
                            null
                        }
                    }
                }

                if (capturedBitmap != null) {
                    Log.d(TAG, "Bitmap captured by service, processing with Gemini.")
                    val centerForGemini = if (drawMode == DrawMode.CIRCLE) Offset(intent.getFloatExtra(EXTRA_CIRCLE_CENTER_X, 0f), intent.getFloatExtra(EXTRA_CIRCLE_CENTER_Y, 0f)) else null
                    val radiusForGemini = if (drawMode == DrawMode.CIRCLE) intent.getFloatExtra(EXTRA_CIRCLE_RADIUS, 0f) else null
                    processWithGemini(capturedBitmap, drawMode, centerForGemini, radiusForGemini)
                } else {
                    Log.w(TAG, "Failed to capture bitmap in service.")
                    // Optionally send a failure broadcast back to activity
                }
                 // mediaProjection?.stop() // Stop immediately after one capture - or manage lifecycle elsewhere if needed for multiple captures

            } else {
                Log.w(TAG, "Invalid data for ACTION_START_MEDIA_PROJECTION. ResultCode: $resultCode, Data: $resultData, DrawMode: $drawModeName")
            }
        } else if (intent?.hasExtra("freehandBitmap") == true || intent?.hasExtra("circleBitmap") == true) {
            // This is the old way of receiving bitmap, kept for a moment for transition if needed, but should be removed.
            Log.d(TAG, "Received bitmap directly (old path) - this should be transitioned out.")
            val bitmap: Bitmap? = intent.getParcelableExtra("freehandBitmap") ?: intent.getParcelableExtra("circleBitmap")
            val drawModeFromIntent = intent.getStringExtra("drawMode")?.let { DrawMode.valueOf(it) } ?: DrawMode.CIRCLE
            val centerX = intent.getFloatExtra("centerX", 0f)
            val centerY = intent.getFloatExtra("centerY", 0f)
            val radius = intent.getFloatExtra("radius", 0f)

            if (bitmap != null) {
                processWithGemini(bitmap, drawModeFromIntent, if (drawModeFromIntent == DrawMode.CIRCLE) Offset(centerX, centerY) else null, radius)
            }
        } else {
            Log.d(TAG, "onStartCommand with no specific action or old bitmap extra.")
        }

        return START_STICKY
    }

    private fun ensureForegroundState() {
        // This can be called to make sure the service is in foreground, e.g. if restarted by system
        // For media projection, it's critical to be foreground with the correct type.
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Overlay Service Active")
            .setContentText("Screen capture active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "Ensured service is in foreground from onStartCommand/ensureForegroundState.")
    }

    private fun convertFloatArrayToAndroidPath(pointsArray: FloatArray): AndroidGraphicsPath {
        val path = AndroidGraphicsPath()
        if (pointsArray.size >= 2) {
            path.moveTo(pointsArray[0], pointsArray[1])
            for (i in 2 until pointsArray.size step 2) {
                path.lineTo(pointsArray[i], pointsArray[i + 1])
            }
            // path.close() // Close if it's meant to be a closed loop for cropping bounds
        }
        return path
    }

    @Composable
    fun FloatingButtonUI() {
        Box(contentAlignment = Alignment.TopEnd) {
            FloatingActionButton(
                onClick = {
                    Log.d(TAG, "FloatingButton onClick - launching ScreenCircleActivity")
                    try {
                        val intent = Intent(this@OverlayService, ScreenCircleActivity::class.java).apply {
                            // Default to CIRCLE, or could be configurable
                            putExtra("drawMode", DrawMode.CIRCLE.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "Failed to start ScreenCircleActivity: ${e.message}")
                        Toast.makeText(this@OverlayService, "Cannot open drawing screen.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Draw Circle or Freehand")
            }
        }
    }

    private fun processWithGemini(bitmap: Bitmap, drawMode: DrawMode, center: Offset? = null, radius: Float? = null) {
        Log.d(TAG, "processWithGemini called with drawMode: $drawMode")
        coroutineScope.launch {
            try {
                val content = when (drawMode) {
                    DrawMode.FREEHAND -> content {
                        image(bitmap)
                        text("Identify objects or fields in the image within the drawn freehand region.")
                    }
                    DrawMode.CIRCLE -> content {
                        image(bitmap)
                        text("Identify objects or fields in the image within the drawn circle centered at (${center?.x}, ${center?.y}) with radius $radius.")
                    }
                }
                val response = generativeModel.generateContent(content)
                val textResult = response.text ?: "No text result from Gemini."
                Log.d("$TAG-GeminiResponse", "Processed result: $textResult")

                val geminiIntent = Intent("com.example.overlay.GEMINI_RESULT").apply {
                    putExtra("result", textResult)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    setPackage(packageName)
                }
                sendBroadcast(geminiIntent)
                Log.d(TAG, "Gemini result broadcast sent.")
            } catch (e: Exception) {
                Log.e("$TAG-GeminiError", "Failed to process with Gemini: ${e.message}", e)
                 val errorIntent = Intent("com.example.overlay.GEMINI_RESULT").apply {
                    putExtra("result", "Error processing with Gemini: ${e.message}")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    setPackage(packageName)
                }
                sendBroadcast(errorIntent)
            } finally {
                 // Recycle bitmap after Gemini is done with it
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                    Log.d(TAG, "Bitmap recycled after Gemini processing.")
                }
            }
        }
    }

    // Screen Capture Logic (moved into Service)
    @SuppressLint("WrongConstant") // For ImageReader.newInstance format
    private fun captureAndCropScreen(androidPath: AndroidGraphicsPath, context: Context): Bitmap? {
        Log.d(TAG, "Service: captureAndCropScreen (Path version) called")
        if (mediaProjection == null) {
            Log.w(TAG, "Service: MediaProjection is null in captureAndCropScreen (Path)")
            return null
        }

        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ServiceScreenCapture_Path", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null
        )
        Log.d(TAG, "Service: VirtualDisplay created for Path capture")

        var fullBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null

        try {
            val image = imageReader.acquireLatestImage() ?: run {
                Log.w(TAG, "Service: acquireLatestImage returned null (Path)")
                virtualDisplay?.release()
                imageReader.close()
                mediaProjection?.stop() // Critical: stop projection if image is null
                return null
            }
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            fullBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            fullBitmap.copyPixelsFromBuffer(buffer)
            image.close()
            Log.d(TAG, "Service: Full screen bitmap created (Path)")

            // Ensure path is closed for accurate bounds calculation if it represents a loop
            if (!androidPath.isEmpty) androidPath.close() 

            val bounds = RectF()
            androidPath.computeBounds(bounds, true)
            Log.d(TAG, "Service: Computed bounds for Path: $bounds")

            val cropLeft = bounds.left.toInt().coerceIn(0, fullBitmap.width)
            val cropTop = bounds.top.toInt().coerceIn(0, fullBitmap.height)
            // Ensure cropWidth and cropHeight are positive and within bounds
            val cropWidth = (bounds.width().toInt()).coerceAtLeast(1).coerceAtMost(fullBitmap.width - cropLeft)
            val cropHeight = (bounds.height().toInt()).coerceAtLeast(1).coerceAtMost(fullBitmap.height - cropTop)

            if (cropWidth > 0 && cropHeight > 0 && cropLeft + cropWidth <= fullBitmap.width && cropTop + cropHeight <= fullBitmap.height) {
                croppedBitmap = Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                Log.d(TAG, "Service: Bitmap cropped for Path capture [$cropLeft, $cropTop, $cropWidth, $cropHeight]")
            } else {
                Log.w(TAG, "Service: Invalid crop dimensions for Path capture. Bounds: $bounds, Full: ${fullBitmap.width}x${fullBitmap.height}, CropRect: $cropLeft, $cropTop, $cropWidth, $cropHeight")
                croppedBitmap =
                    fullBitmap.config?.let { fullBitmap.copy(it, true) } // Fallback: use full bitmap or handle error
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service: Capture failed (Path): ${e.message}", e)
        } finally {
            imageReader.close()
            virtualDisplay?.release()
            // Do not stop mediaProjection here if you plan to reuse it immediately.
            // Stop it when the service decides it's done with projections (e.g., in onDestroy or after a timeout).
            // For this single use case, stopping it after capture is safer.
            mediaProjection?.stop()
            Log.d(TAG, "MediaProjection stopped after path capture.")

            if (fullBitmap != null && fullBitmap != croppedBitmap && !fullBitmap.isRecycled) {
                fullBitmap.recycle()
            }
            Log.d(TAG, "Service: captureAndCropScreen (Path) finished")
        }
        return croppedBitmap
    }

    @SuppressLint("WrongConstant")
    private fun captureAndCropScreen(center: Offset, radius: Float, context: Context): Bitmap? {
        Log.d(TAG, "Service: captureAndCropScreen (Circle) called with center: $center, radius: $radius")
        if (mediaProjection == null) {
            Log.w(TAG, "Service: MediaProjection is null (Circle)")
            return null
        }

        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ServiceScreenCapture_Circle", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, null
        )
        Log.d(TAG, "Service: VirtualDisplay created (Circle)")

        var fullBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null

        try {
            val image = imageReader.acquireLatestImage() ?: run {
                Log.w(TAG, "Service: acquireLatestImage returned null (Circle)")
                virtualDisplay?.release()
                imageReader.close()
                mediaProjection?.stop() // Critical: stop projection
                return null
            }
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            fullBitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            fullBitmap.copyPixelsFromBuffer(buffer)
            image.close()
            Log.d(TAG, "Service: Full screen bitmap created (Circle)")

            val cropLeft = (center.x - radius).toInt().coerceIn(0, fullBitmap.width)
            val cropTop = (center.y - radius).toInt().coerceIn(0, fullBitmap.height)
            val cropRight = (center.x + radius).toInt().coerceAtMost(fullBitmap.width)
            val cropBottom = (center.y + radius).toInt().coerceAtMost(fullBitmap.height)

            val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)
            val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

            if (cropWidth > 0 && cropHeight > 0 && cropLeft + cropWidth <= fullBitmap.width && cropTop + cropHeight <= fullBitmap.height) {
                croppedBitmap = Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
                Log.d(TAG, "Service: Bitmap cropped for Circle capture [$cropLeft, $cropTop, $cropWidth, $cropHeight]")
            } else {
                Log.w(TAG, "Service: Invalid crop dimensions for Circle. CropRect: $cropLeft, $cropTop, $cropWidth, $cropHeight")
                croppedBitmap =
                    fullBitmap.config?.let { fullBitmap.copy(it, true) } // Fallback or error handling
            }
        } catch (e: Exception) {
            Log.e(TAG, "Service: Capture failed (Circle): ${e.message}", e)
        } finally {
            imageReader.close()
            virtualDisplay?.release()
            mediaProjection?.stop() // Stop after this capture
            Log.d(TAG, "MediaProjection stopped after circle capture.")

            if (fullBitmap != null && fullBitmap != croppedBitmap && !fullBitmap.isRecycled) {
                fullBitmap.recycle()
            }
            Log.d(TAG, "Service: captureAndCropScreen (Circle) finished")
        }
        return croppedBitmap
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        mediaProjection?.stop() // Ensure media projection is stopped
        Log.d(TAG, "MediaProjection explicitly stopped in onDestroy.")
        stopForeground(true)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        // Consider when to call performSave for savedStateRegistryController
        coroutineScope.cancel()
        if (::windowManager.isInitialized && floatButton != null) {
            try {
                windowManager.removeView(floatButton)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view: ${e.message}", e)
            }
        }
        unregisterReceiver(geminiResultReceiver)
        Log.d(TAG, "Service destroyed")
    }
}
