package com.example.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import android.widget.FrameLayout
import androidx.compose.ui.Modifier

class CircleOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatButton: ComposeView
    private var isDrawingMode = false
    private var drawingView: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

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
                CircleDrawScreen(onCircleDrawn = { center, radius ->
                    // TODO: Integrate with AccessibilityService for text extraction
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

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatButton)
        drawingView?.let { windowManager.removeView(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}