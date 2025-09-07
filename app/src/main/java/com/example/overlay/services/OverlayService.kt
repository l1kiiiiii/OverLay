//Overlay Service
package com.example.overlay.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private var floatButton: ComposeView? = null
    private var isDrawingMode = false
    private var drawingView: ComposeView? = null
    private lateinit var generativeModel: GenerativeModel
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

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
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = BuildConfig.API_KEY,
            ).also { Log.d("OverlayService", "Gemini model initialized") }

            floatButton = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent { FloatingButtonUI() }
                setOnTouchListener { _, _ -> true }
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
                onClick = {
                    if (!isDrawingMode) startDrawingMode() else stopDrawingMode()
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Draw Circle")
            }
        }
    }

    private fun startDrawingMode() {
        if (isDrawingMode) return
        isDrawingMode = true
        drawingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                CircleDrawScreen(onCircleDrawn = { center, radius, bitmap ->
                    bitmap?.let { processWithGemini(it) } ?:
                    Log.w("OverlayService", "No bitmap captured")
                    stopDrawingMode() // Stop drawing mode after a circle is drawn and processed
                })
            }
            setOnTouchListener { _, _ -> true }
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

    private fun processWithGemini(bitmap: Bitmap) {
        coroutineScope.launch {
            try {
                val response = generativeModel.generateContent("Identify objects or fields in the image")
                val textResult = response.text
                Log.d("GeminiResponse", "Objects/Fields: $textResult")
            } catch (e: Exception) {
                Log.e("GeminiError", "API call failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        savedStateRegistryController.performSave(Bundle())

        try {
            floatButton?.let { windowManager.removeView(it) }
            drawingView?.let { windowManager.removeView(it) }
            Log.d("OverlayService", "Service destroyed")
        } catch (e: Exception) {
            Log.e("OverlayService", "Cleanup failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}