package com.example.simpletimeapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val timeTextView: TextView = findViewById(R.id.timeTextView)
        val confirmButton: Button = findViewById(R.id.confirmButton)
        
        // 显示当前时间
        updateTime(timeTextView)
        
        // 确认按钮点击事件
        confirmButton.setOnClickListener {
            updateTime(timeTextView)
            confirmButton.text = "已确认 ✓"
        }
    }
    
    private fun updateTime(textView: TextView) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        textView.text = "当前时间：$currentTime"
    }
}
