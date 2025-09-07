package com.example.overlay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.overlay.services.OverlayService
import androidx.compose.runtime.remember
import com.example.overlay.ui.theme.OverLayTheme
import androidx.core.net.toUri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


    class MainActivity : ComponentActivity() {
        private val OVERLAY_PERMISSION_REQUEST_CODE = 123

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()

            // Combine the logic from both onCreate methods
            checkAndRequestOverlayPermission()

            setContent {
                OverLayTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OverlayUI(
                            onAskAI = {
                                startService(Intent(this@MainActivity, OverlayService::class.java))
                            },
                            onClose = { finish() }
                        )
                    }
                }
            }
            Log.d("MainActivity", "Activity created and service potentially started")
        }

        private fun checkAndRequestOverlayPermission() {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            } else {
                startOverlayService()
            }
        }

        @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        startOverlayService()
                    } else {
                        Log.w("MainActivity", "Overlay permission was not granted.")
                    }
                }
            }
        }

        private fun startOverlayService() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.e("MainActivity", "Cannot start OverlayService: permission not granted.")
                return
            }
            Log.d("MainActivity", "Starting OverlayService...")
            val intent = Intent(this, OverlayService::class.java)
            startService(intent)
        }

        override fun onDestroy() {
            super.onDestroy()
            stopService(Intent(this@MainActivity, OverlayService::class.java))
            Log.d("MainActivity", "Activity destroyed and service stopped")
        }
    }
@Composable
fun OverlayUI(onAskAI: () -> Unit, onClose: () -> Unit) {
    var showPrompt by remember { mutableStateOf(false) }
    OverLayTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .size(300.dp, 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A2E))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "AI Assistant Icon",
                    tint = Color(0xFF4E79E6),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AI Assistant",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Get instant help and creative ideas.",
                    color = Color(0xFFB0B0C0),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = {
                        showPrompt = true
                        onAskAI()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E79E6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Ask AI Icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask AI", color = Color.White, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Close", color = Color.White, fontSize = 16.sp)
                }
            }

            if (showPrompt) {
                AlertDialog(
                    onDismissRequest = { showPrompt = false },
                    title = { Text("Draw a Circle") },
                    text = { Text("Please draw a circle on the screen to scan objects or fields.") },
                    confirmButton = {
                        TextButton(onClick = { showPrompt = false }) { Text("OK") }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OverlayUIPreview() {
    OverLayTheme {
        OverlayUI(onAskAI = {}, onClose = {})
    }
}

