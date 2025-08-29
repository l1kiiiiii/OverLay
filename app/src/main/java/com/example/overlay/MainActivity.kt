package com.example.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.overlay.ui.theme.OverLayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OverLayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OverlayUI(
                        onAskAI = { /* Handle Ask AI button click */ },
                        onClose = { /* Handle Close button click */ }
                    )
                }
            }
        }
    }
}

