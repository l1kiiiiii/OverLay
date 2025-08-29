package com.example.overlay.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
//import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.example.overlay.OverlayUI
import kotlin.jvm.java
import com.example.overlay.R

@Suppress("DEPRECATION")
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingComposeView:    ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        showFloatingButton()
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        val channelId = "overlay_channel"
        val channel = NotificationChannel(
            channelId,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Assistant Running")
            .setContentText("Floating button is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(1, notification)
    }

    private fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingComposeView = ComposeView(this).apply {
            setContent {
                OverlayUI(
                    onAskAI = {
                        val intent = Intent(this@OverlayService, CircleOverlayService::class.java)
                        startService(intent)
                    },
                    onClose = { stopSelf() }
                )
            }
        }


        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        windowManager.addView(floatingComposeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingComposeView?.let { windowManager.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingButtonUI(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFF6200EE),
        contentColor = Color.White
    ) {
        Icon(Icons.Default.Bolt, contentDescription = "AI Button")
    }
}

@Preview
@Composable
fun FloatingButtonUIPreview() {
    FloatingButtonUI(onClick = {})
}

