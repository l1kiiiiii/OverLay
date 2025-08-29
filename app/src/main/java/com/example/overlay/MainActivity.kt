package com.example.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.sp
import com.example.overlay.OverlayUI
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

@Composable

fun OverlayUI(
    onAskAI: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.Companion
            .size(300.dp, 200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A2E)),
        color = Color.Companion.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Companion.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "AI Assistant Icon",
                tint = Color(0xFF4E79E6),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.Companion.height(16.dp))

            Text(
                text = "AI Assistant",
                color = Color.Companion.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Companion.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Get instant help and creative ideas.",
                color = Color(0xFFB0B0C0),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = onAskAI,   // ✅ trigger callback
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E79E6)),
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Ask AI Icon",
                    tint = Color.Companion.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.Companion.width(8.dp))
                Text("Ask AI", color = Color.Companion.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Companion.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Close", color = Color.Companion.White, fontSize = 16.sp)
            }
        }
    }
}