package com.example.overlay.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.overlay.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SuppressLint("AccessibilityPolicy")
class AssistantAccessibilityService : AccessibilityService() {
    private val TAG = "AssistantAccessibilityService"
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private lateinit var generativeModel: GenerativeModel

    override fun onCreate() {
        Log.d(TAG, "onCreate called")
        super.onCreate()
        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-pro",
                apiKey = BuildConfig.API_KEY
            ).also { Log.d(TAG, "Gemini model initialized") }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "onAccessibilityEvent called")
        event?.let {
            Log.d(TAG, "Event: ${AccessibilityEvent.eventTypeToString(it.eventType)}")

            // Example: Trigger ScreenCircleActivity when a specific UI element is clicked
            if (it.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                Log.d(TAG, "Event type is TYPE_VIEW_CLICKED")
                val nodeInfo = it.source
                nodeInfo?.let { node ->
                    val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                    Log.d(TAG, "Clicked node text: '$text'")

                    // Example: Trigger circle drawing if a specific condition is met
                    if (text.contains("search", ignoreCase = true)) {
                        Log.d(TAG, "Clicked text contains 'search', launching ScreenCircleActivity")
                        val intent = Intent(this, ScreenCircleActivity::class.java).apply {
                            putExtra("drawMode", DrawMode.CIRCLE.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }

                    // Example: Analyze clicked text with Gemini
                    Log.d(TAG, "Launching coroutine for Gemini analysis")
                    coroutineScope.launch {
                        Log.d(TAG, "Coroutine for Gemini analysis started")
                        try {
                            val content = content {
                                text("Analyze the following UI element text: $text")
                            }
                            Log.d(TAG, "Calling generativeModel.generateContent")
                            val response = generativeModel.generateContent(content)
                            val result = response.text
                            Log.d(TAG, "Gemini analysis result: $result")

                            // Broadcast the result to be shown in ScreenCircleActivity
                            val geminiIntent = Intent("com.example.overlay.GEMINI_RESULT").apply {
                                putExtra("result", result)
                                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                // setPackage(packageName) // Optional: Keep it if OverlayService needs it for its receiver
                            }
                            sendBroadcast(geminiIntent)
                            Log.d(TAG, "GEMINI_RESULT broadcast sent")
                        } catch (e: Exception) {
                            Log.e(TAG, "Gemini processing failed: ${e.message}")
                        }
                        Log.d(TAG, "Coroutine for Gemini analysis finished")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt called")
        // Original log: Log.d("AssistantAccessibilityService", "Service interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        coroutineScope.cancel()
        Log.d(TAG, "Service destroyed") // Original log: Log.d("AssistantAccessibilityService", "Service destroyed")
    }

    override fun onServiceConnected() {
        Log.d(TAG, "onServiceConnected called")
        super.onServiceConnected()
        // Original log: Log.d("AssistantAccessibilityService", "Service connected")
        // Configure the service to listen for specific events
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = FEEDBACK_GENERIC
            notificationTimeout = 100
            Log.d(TAG, "Service info configured: eventTypes set, feedbackType set to GENERIC")
        }
    }
}
