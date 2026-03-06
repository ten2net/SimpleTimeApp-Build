package com.example.simpletimeapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var timeTextView: TextView
    private lateinit var confirmButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 每秒更新一次
    
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, updateInterval)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        timeTextView = findViewById(R.id.timeTextView)
        confirmButton = findViewById(R.id.confirmButton)
        
        // 启动动态时间更新
        startDynamicTimeUpdate()
        
        // 确认按钮点击事件
        confirmButton.setOnClickListener {
            confirmButton.text = "已确认 ✓"
            // 3秒后恢复按钮文字
            handler.postDelayed({
                confirmButton.text = "确认"
            }, 3000)
        }
    }
    
    override fun onResume() {
        super.onResume()
        startDynamicTimeUpdate()
    }
    
    override fun onPause() {
        super.onPause()
        stopDynamicTimeUpdate()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopDynamicTimeUpdate()
    }
    
    private fun startDynamicTimeUpdate() {
        handler.removeCallbacks(updateTimeRunnable)
        handler.post(updateTimeRunnable)
    }
    
    private fun stopDynamicTimeUpdate() {
        handler.removeCallbacks(updateTimeRunnable)
    }
    
    private fun updateTime() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        timeTextView.text = "当前时间：$currentTime"
    }
}
