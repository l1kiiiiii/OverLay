package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class AssistantAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val source = event.source ?: return
                val bounds = android.graphics.Rect()
                findTextInNode(source, bounds)
            }
        }
    }

    private fun findTextInNode(node: AccessibilityNodeInfo, bounds: android.graphics.Rect) {
        node.getBoundsInScreen(bounds)
        // Placeholder for circle bounds (sync with CircleDrawScreen)
        val circleBounds = android.graphics.Rect(100, 100, 300, 300) // Replace with dynamic data
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