package com.example.overlay.services

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.example.overlay.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AssistantAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val source = event.source ?: return
                val bounds = Rect()
                findTextInNode(source, bounds)
            }
        }
    }

    private fun findTextInNode(node: AccessibilityNodeInfo, bounds: Rect) {
        node.getBoundsInScreen(bounds)
        // Placeholder for circle bounds (sync with CircleDrawScreen)
        val circleBounds = Rect(100, 100, 300, 300) // Replace with dynamic data
        if (node.text != null && bounds.intersect(circleBounds)) {
            Log.d("AccessibilityService", "Found text in circle: ${node.text}")
            // TODO: Send text to OverlayService or activity
        }
        for (i in 0 until node.childCount) {
            findTextInNode(node.getChild(i) ?: continue, bounds)
        }
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Service interrupted")
    }
}

class CircleOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatButton: ComposeView
    private var isDrawingMode = false
    private var drawingView: ComposeView? = null
    private lateinit var generativeModel: GenerativeModel
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Initialize Gemini API
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = BuildConfig.API_KEY
        )

        floatButton = ComposeView(this).apply {
            setContent {
                FloatingButtonUI()
            }
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
    }

    @Composable
    fun FloatingButtonUI() {
        Box(contentAlignment = Alignment.TopEnd) {
            FloatingActionButton(
                onClick = {
                    if (!isDrawingMode) {
                        startDrawingMode()
                    } else {
                        stopDrawingMode()
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Draw Circle")
            }
        }
    }

    private fun startDrawingMode() {
        isDrawingMode = true
        drawingView = ComposeView(this).apply {
            setContent {
                CircleDrawScreen(onCircleDrawn = { center, radius, bitmap ->
                    bitmap?.let { processWithGemini(it, center, radius) }
                })
            }
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
        windowManager.addView(drawingView, params)
    }

    private fun stopDrawingMode() {
        isDrawingMode = false
        drawingView?.let { windowManager.removeView(it) }
        drawingView = null
    }

    private fun processWithGemini(bitmap: Bitmap, center: Offset, radius: Float) {
        coroutineScope.launch {
            try {
                // Convert bitmap to InputImage for Gemini (if required by the API)
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val response = generativeModel.generateContent("Identify objects or fields in the image")
                val textResult = response.text // Corrected: Access 'text' as a property
                Log.d("GeminiResponse", "Processed result: $textResult")
            } catch (e: Exception) {
                Log.e("GeminiError", "Failed to process with Gemini: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel() // Cancel the coroutine scope
        if (::windowManager.isInitialized) {
            if (::floatButton.isInitialized) {
                windowManager.removeView(floatButton)
            }
            drawingView?.let { windowManager.removeView(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}