package com.example.overlay.services

import android.annotation.SuppressLint // Required for SuppressLint
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
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

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val TAG = "OverlayService"

    private lateinit var windowManager: WindowManager
    private var floatButton: ComposeView? = null
    private lateinit var generativeModel: GenerativeModel
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private lateinit var savedStateRegistryController: SavedStateRegistryController

    private val geminiResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "geminiResultReceiver.onReceive called")
            intent?.getStringExtra("result")?.let {
                Log.d(TAG, "Gemini Result Received: $it")
            }
        }
    }

    override val lifecycle: Lifecycle get() {
        Log.d(TAG, "get lifecycle called")
        return lifecycleRegistry
    }
    override val viewModelStore: ViewModelStore get() {
        Log.d(TAG, "get viewModelStore called")
        return _viewModelStore
    }
    override val savedStateRegistry: SavedStateRegistry get() {
        Log.d(TAG, "get savedStateRegistry called")
        return savedStateRegistryController.savedStateRegistry
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Suppress lint for the else block below
    override fun onCreate() {
        Log.d(TAG, "onCreate called")
        super.onCreate()
        savedStateRegistryController = SavedStateRegistryController.create(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geminiResultReceiver, IntentFilter("com.example.overlay.GEMINI_RESULT"), RECEIVER_NOT_EXPORTED)
        } else {
            // The @SuppressLint above should cover this for the lint warning
            registerReceiver(geminiResultReceiver, IntentFilter("com.example.overlay.GEMINI_RESULT"))
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }

            windowManager.addView(floatButton, params)
            Log.d(TAG, "Floating button added")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ${e.message}")
        }
    }

    @Composable
    fun FloatingButtonUI() {
        Log.d(TAG, "FloatingButtonUI called")
        Box(contentAlignment = Alignment.TopEnd) {
            FloatingActionButton(
                onClick = {
                    Log.d(TAG, "FloatingButton onClick")
                    try {
                        val intent = Intent(this@OverlayService, ScreenCircleActivity::class.java).apply {
                            putExtra("drawMode", DrawMode.CIRCLE.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        this@OverlayService.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                Log.e("OverlayService", "Failed to start ScreenCircleActivity: ${e.message}")
                Toast.makeText(
                    this@OverlayService,
                    "Cannot open drawing screen. Please check app configuration.",
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        modifier = Modifier.padding(16.dp)
        ) {
                Icon(Icons.Filled.Add, contentDescription = "Draw Circle")
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
                val textResult = response.text
                Log.d("$TAG-GeminiResponse", "Processed result: $textResult") // Changed tag for Gemini specific log

                val geminiIntent = Intent("com.example.overlay.GEMINI_RESULT").apply {
                    putExtra("result", textResult)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND) // Ensures a foreground receiver can get it
                    setPackage(packageName) // Make the intent explicit to this app
                }
                sendBroadcast(geminiIntent)
            } catch (e: Exception) {
                Log.e("$TAG-GeminiError", "Failed to process with Gemini: ${e.message}") // Changed tag for Gemini specific log
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        savedStateRegistryController.performSave(Bundle())
        coroutineScope.cancel()
        if (::windowManager.isInitialized) {
            floatButton?.let { windowManager.removeView(it) }
        }
        unregisterReceiver(geminiResultReceiver)
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        val bitmap: Bitmap? = intent?.getParcelableExtra("freehandBitmap")
        val drawModeFromIntent = intent?.getStringExtra("drawMode")?.let { DrawMode.valueOf(it) } ?: DrawMode.CIRCLE // Renamed to avoid conflict
        val centerX = intent?.getFloatExtra("centerX", 0f) ?: 0f
        val centerY = intent?.getFloatExtra("centerY", 0f) ?: 0f
        val radius = intent?.getFloatExtra("radius", 0f) ?: 0f

        if (bitmap != null) {
            Log.d(TAG, "Bitmap received in onStartCommand, processing with Gemini...")
            val centerOffset = if (drawModeFromIntent == DrawMode.CIRCLE) Offset(centerX, centerY) else null // Renamed to avoid conflict
            processWithGemini(bitmap, drawModeFromIntent, centerOffset, radius)
        } else {
            Log.d(TAG, "No bitmap received in onStartCommand")
        }
        return START_STICKY
    }
}
