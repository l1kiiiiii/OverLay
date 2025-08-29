package com.example.overlay

import android.graphics.Color as AndroidGraphicsColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

class ScreenCircleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }

        window.setBackgroundDrawable(ColorDrawable(AndroidGraphicsColor.parseColor("#66000000")))

        setContent {
            Box {
                CircleDrawScreen(onCircleDrawn = { center: Offset, radius: Float ->
                    // TODO: Implement screenshot + OCR here
                })
            }
        }
    }
}


@Composable
fun CircleDrawScreen(onCircleDrawn: (Offset, Float) -> Unit) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(0f) }
    var isDrawing by remember { mutableStateOf(false) }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    isDrawing = true
                    center = offset
                    radius = 0f
                },
                onDrag = { change, _ ->
                    if (isDrawing) {
                        val newRadius = hypot(change.position.x - center.x, change.position.y - center.y)
                        radius = newRadius
                    }
                },
                onDragEnd = {
                    isDrawing = false
                    onCircleDrawn(center, radius)
                }
            )
        }) {
        if (isDrawing || radius > 0) {
            drawCircle(
                color = Color.Red,
                center = center,
                radius = radius.coerceAtLeast(1.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
            )
        }
    }
}