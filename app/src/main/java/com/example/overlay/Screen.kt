package com.example.overlay


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun OverlayUI() {
    Surface(
        modifier = Modifier
            .size(300.dp, 200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A2E)), // Dark background color approximating the image
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Placeholder for star icon (replace with actual Icon or Image asset)
            Icon(
                imageVector = Icons.Default.Star, // Replace with your star icon asset
                contentDescription = "AI Assistant Icon",
                tint = Color(0xFF4E79E6), // Blue color for the star
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "AI Assistant",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Subtitle
            Text(
                text = "Get instant help and creative ideas.",
                color = Color(0xFFB0B0C0), // Light gray for subtitle
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Ask AI Button
            Button(
                onClick = { /* Add AI interaction logic here */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E79E6)), // Blue button
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb, // Replace with appropriate icon
                    contentDescription = "Ask AI Icon",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask AI", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Close Button
            Button(
                onClick = { /* Add close logic here */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Close", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun OverlayUIPreview() {
    OverlayUI()
}