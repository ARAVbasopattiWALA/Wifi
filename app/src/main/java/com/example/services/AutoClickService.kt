package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoClickService : AccessibilityService() {

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected

        private val _screenContentFlow = MutableStateFlow("")
        val screenContentFlow: StateFlow<String> = _screenContentFlow

        @Volatile
        var instance: AutoClickService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val content = getScreenTextContent()
            _screenContentFlow.value = content
        }
    }

    override fun onInterrupt() {
        _isConnected.value = false
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        instance = null
    }

    fun clickAt(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke()
            }
        }, null)
    }

    fun typeText(text: String): Boolean {
        val node = findFocusedNode()
        return if (node != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            node.recycle()
            success
        } else {
            false
        }
    }

    fun clearField(): Boolean {
        val node = findFocusedNode()
        return if (node != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            node.recycle()
            success
        } else {
            false
        }
    }

    fun findFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) return focused
        return findFocusedNodeRecursive(root)
    }

    private fun findFocusedNodeRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNodeRecursive(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    fun getScreenTextContent(): String {
        val sb = StringBuilder()
        val root = rootInActiveWindow
        collectText(root, sb)
        root?.recycle()
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text
        if (!text.isNullOrEmpty()) {
            sb.append(text).append(" | ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb)
            child.recycle()
        }
    }
}
