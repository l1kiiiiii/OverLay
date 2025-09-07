package com.example.overlay.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle // Required for SavedStateRegistryController
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.overlay.network.GeminiApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
// Imports for Lifecycle, ViewModelStore, SavedStateRegistry
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

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private var floatButton: ComposeView? = null
    private var isDrawingMode = false
    private var drawingView: ComposeView? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val geminiApiClient by lazy { GeminiApiClient.getInstance(applicationContext) }

    // Lifecycle, ViewModelStore, SavedStateRegistry implementation
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Initialize SavedStateRegistryController
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null) // Restore state if any

        // Dispatch lifecycle events
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            floatButton = ComposeView(this).apply {
                // Set the owners
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent { FloatingButtonUI() }
                setOnTouchListener { _, event -> true }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }

            windowManager.addView(floatButton, params)
            Log.d("OverlayService", "Floating button added")
        } catch (e: Exception) {
            Log.e("OverlayService", "Initialization failed: ${e.message}")
        }
    }

    @Composable
    fun FloatingButtonUI() {
        Box(contentAlignment = Alignment.TopEnd) {
            FloatingActionButton(
                onClick = { if (!isDrawingMode) startDrawingMode() else stopDrawingMode() },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Draw Loop")
            }
        }
    }

    @Composable
    fun FreehandDrawScreen(onLoopDrawn: (Path, List<Offset>) -> Unit) {
        var path by remember { mutableStateOf(Path()) }
        var points by remember { mutableStateOf(mutableStateListOf<Offset>()) }
        var isDrawing by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDrawing = true
                        path = Path()
                        path.moveTo(offset.x, offset.y)
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
                        isDrawing = false
                        if (points.size > 1) {
                            path.lineTo(points.first().x, points.first().y) // Close the loop
                            onLoopDrawn(path, points.toList()) // Pass a copy of points
                        }
                    }
                )
            }) {
            drawPath(
                path = path,
                color = Color.Red,
                style = Stroke(width = 5f)
            )
        }
    }

    private fun startDrawingMode() {
        if (isDrawingMode) return
        isDrawingMode = true
        drawingView = ComposeView(this).apply {
            // Set the owners
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { FreehandDrawScreen { path, points ->
                // This is a placeholder for capture logic
                // For now, just stop drawing mode
                stopDrawingMode()
            } }
            setOnTouchListener { _, event -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            windowManager.addView(drawingView, params)
            Log.d("OverlayService", "Drawing mode started")
        } catch (e: Exception) {
            Log.e("OverlayService", "Failed to start drawing mode: ${e.message}")
            isDrawingMode = false
        }
    }

    private fun stopDrawingMode() {
        if (!isDrawingMode) return
        isDrawingMode = false
        drawingView?.let {
            try {
                windowManager.removeView(it)
                Log.d("OverlayService", "Drawing mode stopped")
            } catch (e: Exception) {
                Log.e("OverlayService", "Failed to stop drawing mode: ${e.message}")
            }
            drawingView = null
        }
    }

    private fun processWithGemini(bitmap: Bitmap, center: Offset, radius: Float) { // Added center and radius, though not used by GeminiApiClient yet
        coroutineScope.launch {
            val result = geminiApiClient.identifyObjects(bitmap)
            result.onSuccess { textResult ->
                Log.d("GeminiResponse", "Objects/Fields: $textResult")
                val intent = Intent("com.example.overlay.GEMINI_RESULT")
                intent.putExtra("result", textResult)
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }.onFailure { e ->
                Log.e("GeminiError", "API call failed: ${e.message}")
                val intent = Intent("com.example.overlay.GEMINI_RESULT")
                intent.putExtra("result", "Error: ${e.message}")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Dispatch lifecycle events
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        // Clear ViewModelStore
        _viewModelStore.clear()
        // Save SavedStateRegistry state
        savedStateRegistryController.performSave(Bundle()) // Pass a Bundle

        try {
            coroutineScope.cancel()
            floatButton?.let { windowManager.removeView(it) }
            drawingView?.let { windowManager.removeView(it) }
            Log.d("OverlayService", "Service destroyed")
        } catch (e: Exception) {
            Log.e("OverlayService", "Cleanup failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getParcelableExtra<Bitmap>("freehandBitmap")?.let {
             // Assuming you might want to pass center/radius if available from the intent later
            processWithGemini(it, Offset.Zero, 0f)
        }
        return START_STICKY
    }
}
