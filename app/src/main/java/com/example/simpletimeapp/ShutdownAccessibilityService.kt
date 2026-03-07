package com.example.simpletimeapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShutdownAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ShutdownService"
        private var instance: ShutdownAccessibilityService? = null
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
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val rootNode = rootInActiveWindow ?: return@postDelayed

                    // 华为EMUI电源菜单
                    findAndClickButton(rootNode, "关机")
                    findAndClickButton(rootNode, "关闭")

                    // Android原生电源菜单
                    findAndClickButton(rootNode, "Power off")
                    findAndClickButton(rootNode, "Shut down")

                    // 确认对话框
                    Handler(Looper.getMainLooper()).postDelayed({
                        findAndClickButton(rootInActiveWindow, "关机")
                        findAndClickButton(rootInActiveWindow, "确认")
                    }, 500)

                } catch (e: Exception) {
                    Log.e(TAG, "处理电源菜单失败: ${e.message}")
                }
            }, 300)
        }
    }

    private fun findAndClickButton(rootNode: AccessibilityNodeInfo?, text: String) {
        if (rootNode == null) return

        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "点击按钮: $text")
                return
            }
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
    fun triggerShutdown() {
        isShutdownPending = true

        // 模拟长按电源键
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        Log.d(TAG, "触发电源菜单")
    }
}