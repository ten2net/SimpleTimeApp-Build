package com.example.simpletimeapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShutdownAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ShutdownService"
        var instance: ShutdownAccessibilityService? = null
            private set
        var isShutdownPending = false

        fun isServiceEnabled(): Boolean {
            return instance != null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")

        // 配置服务
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isShutdownPending) return

        val eventType = event?.eventType ?: return
        val packageName = event.packageName?.toString() ?: return

        Log.d(TAG, "事件: $eventType, 包名: $packageName")

        // 监听电源菜单弹窗
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val rootNode = rootInActiveWindow
                    if (rootNode == null) {
                        Log.d(TAG, "无法获取窗口内容")
                        return@postDelayed
                    }

                    // 尝试查找并点击关机按钮
                    val clicked = tryClickShutdownButton(rootNode)
                    
                    if (clicked) {
                        Log.d(TAG, "成功点击关机按钮")
                        isShutdownPending = false
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "处理电源菜单失败: ${e.message}")
                }
            }, 500)
        }
    }

    private fun tryClickShutdownButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // 尝试多种关机按钮文本
        val shutdownTexts = listOf("关机", "关闭", "Power off", "Shut down", "关机", "Turn off")
        
        for (text in shutdownTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                // 尝试点击节点本身或其可点击的父节点
                val clickableNode = findClickableParent(node)
                if (clickableNode != null) {
                    val result = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (result) {
                        Log.d(TAG, "点击按钮成功: $text")
                        
                        // 延迟后尝试点击确认按钮
                        Handler(Looper.getMainLooper()).postDelayed({
                            confirmShutdown()
                        }, 800)
                        
                        return true
                    }
                }
            }
        }
        
        // 如果没找到文本按钮，尝试遍历所有可点击按钮
        return tryClickByTraversal(rootNode)
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return null
    }

    private fun tryClickByTraversal(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        
        // 遍历所有子节点，查找可能是关机按钮的节点
        val childCount = rootNode.childCount
        for (i in 0 until childCount) {
            val child = rootNode.getChild(i) ?: continue
            
            // 检查是否是按钮类节点
            if (child.className?.contains("Button") == true || 
                child.className?.contains("TextView") == true) {
                
                val text = child.text?.toString() ?: ""
                if (text.contains("关机") || text.contains("关闭") || 
                    text.contains("Power") || text.contains("Shut")) {
                    
                    val clickableNode = if (child.isClickable) child else findClickableParent(child)
                    if (clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        Log.d(TAG, "通过遍历点击成功: $text")
                        Handler(Looper.getMainLooper()).postDelayed({ confirmShutdown() }, 800)
                        return true
                    }
                }
            }
            
            // 递归遍历
            if (tryClickByTraversal(child)) {
                return true
            }
        }
        
        return false
    }

    private fun confirmShutdown() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // 尝试点击确认按钮
            val confirmTexts = listOf("确认", "确定", "OK", "Confirm", "关机")
            for (text in confirmTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(text)
                for (node in nodes) {
                    val clickableNode = findClickableParent(node)
                    if (clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        Log.d(TAG, "点击确认按钮: $text")
                        isShutdownPending = false
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "确认关机失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isShutdownPending = false
    }

    // 触发关机流程
    fun triggerShutdown(): Boolean {
        return try {
            isShutdownPending = true
            
            // 使用全局操作打开电源对话框（Android 5.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val result = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                Log.d(TAG, "GLOBAL_ACTION_POWER_DIALOG 结果: $result")
                result
            } else {
                // 低版本使用广播方式
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发电源菜单失败: ${e.message}")
            isShutdownPending = false
            false
        }
    }
}