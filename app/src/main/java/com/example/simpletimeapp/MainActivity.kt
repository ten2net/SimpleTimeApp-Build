package com.example.simpletimeapp

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var timeTextView: TextView
    private lateinit var wifiButton: Button
    private lateinit var dataButton: Button
    private lateinit var gpsButton: Button
    private lateinit var shutdownButton: Button
    private lateinit var brightnessButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null

    // 亮度状态: 0=全亮, 1=用户自定义, 2=全暗, 3=系统自动
    private var brightnessState = 0
    private val brightnessStates = listOf(
        Triple(255, "全亮", R.drawable.ic_brightness_high),
        Triple(128, "自定义", R.drawable.ic_brightness_medium),
        Triple(20, "全暗", R.drawable.ic_brightness_low),
        Triple(-1, "自动", R.drawable.ic_brightness_auto)
    )

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateStatusIcons()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        timeTextView = findViewById(R.id.timeTextView)
        wifiButton = findViewById(R.id.wifiButton)
        dataButton = findViewById(R.id.dataButton)
        gpsButton = findViewById(R.id.gpsButton)
        shutdownButton = findViewById(R.id.shutdownButton)
        brightnessButton = findViewById(R.id.brightnessButton)

        // 初始化系统服务
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?

        // 获取当前亮度状态
        detectCurrentBrightnessState()

        // 启动时间更新
        startDynamicTimeUpdate()

        // 设置按钮点击事件
        setupButtons()

        // 初始化状态显示
        updateStatusIcons()
    }

    private fun detectCurrentBrightnessState() {
        try {
            // 检查是否为自动亮度
            val autoBrightness = Settings.System.getInt(contentResolver, 
                Settings.System.SCREEN_BRIGHTNESS_MODE, 
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            
            if (autoBrightness == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                brightnessState = 3 // 自动模式
                return
            }

            // 获取当前亮度值
            val currentBrightness = Settings.System.getInt(contentResolver, 
                Settings.System.SCREEN_BRIGHTNESS, 128)
            
            // 根据亮度值判断状态
            brightnessState = when {
                currentBrightness >= 200 -> 0 // 全亮
                currentBrightness >= 80 -> 1  // 自定义
                else -> 2 // 全暗
            }
        } catch (e: Exception) {
            brightnessState = 1 // 默认自定义
        }
    }

    private fun setupButtons() {
        // WiFi开关
        wifiButton.setOnClickListener {
            toggleWifi()
        }

        // 移动数据开关
        dataButton.setOnClickListener {
            toggleMobileData()
        }

        // GPS开关
        gpsButton.setOnClickListener {
            toggleGPS()
        }

        // 一键关机
        shutdownButton.setOnClickListener {
            showShutdownDialog()
        }

        // 亮度调节按钮
        brightnessButton.setOnClickListener {
            toggleBrightness()
        }
    }

    private fun toggleBrightness() {
        // 切换到下一个状态
        brightnessState = (brightnessState + 1) % brightnessStates.size
        val (brightnessValue, brightnessLabel, iconRes) = brightnessStates[brightnessState]
        
        try {
            if (brightnessValue == -1) {
                // 自动亮度模式
                Settings.System.putInt(contentResolver, 
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                Toast.makeText(this, "亮度: 自动调节", Toast.LENGTH_SHORT).show()
            } else {
                // 手动亮度模式
                Settings.System.putInt(contentResolver, 
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(contentResolver, 
                    Settings.System.SCREEN_BRIGHTNESS, 
                    brightnessValue)
                Toast.makeText(this, "亮度: $brightnessLabel", Toast.LENGTH_SHORT).show()
            }
            updateBrightnessButton()
        } catch (e: Exception) {
            Toast.makeText(this, "需要权限来调节亮度", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBrightnessButton() {
        val (_, label, iconRes) = brightnessStates[brightnessState]
        brightnessButton.text = "亮度\n$label"
        brightnessButton.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0)
        
        // 根据状态设置背景色
        val bgColor = when (brightnessState) {
            0 -> 0xFFFFF9E6.toInt() // 全亮 - 暖黄色
            1 -> 0xFFE6F7FF.toInt() // 自定义 - 淡蓝色
            2 -> 0xFFF0F0F0.toInt() // 全暗 - 灰色
            3 -> 0xFFE6FFE6.toInt() // 自动 - 淡绿色
            else -> 0xFFFFFFFF.toInt()
        }
        brightnessButton.setBackgroundColor(bgColor)
    }

    private fun toggleWifi() {
        try {
            wifiManager?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    it.isWifiEnabled = !it.isWifiEnabled
                    Toast.makeText(this, if (it.isWifiEnabled) "WiFi已开启" else "WiFi已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "WiFi操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMobileData() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
            } else {
                startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
        }
    }

    private fun toggleGPS() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun showShutdownDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认关机")
            .setMessage("确定要关机吗？")
            .setPositiveButton("关机") { _, _ ->
                shutdownDevice()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun shutdownDevice() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
            intent.putExtra("android.intent.extra.KEY_CONFIRM", true)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val method = powerManager.javaClass.getMethod("reboot", String::class.java)
                    method.invoke(powerManager, null)
                } catch (e2: Exception) {
                    try {
                        Runtime.getRuntime().exec("su -c reboot -p")
                    } catch (e3: Exception) {
                        Toast.makeText(this, "关机功能需要系统权限", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "关机失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusIcons() {
        // WiFi状态
        wifiManager?.let {
            val isWifiEnabled = it.isWifiEnabled
            wifiButton.text = if (isWifiEnabled) "WiFi\n已开启" else "WiFi\n已关闭"
            wifiButton.setBackgroundColor(if (isWifiEnabled) 0xFFE6F7FF.toInt() else 0xFFFFFFFF.toInt())
        }

        // 移动数据状态
        try {
            val isDataEnabled = telephonyManager?.javaClass
                ?.getDeclaredMethod("getDataEnabled")
                ?.invoke(telephonyManager) as? Boolean ?: false
            dataButton.text = if (isDataEnabled) "移动数据\n已开启" else "移动数据\n已关闭"
        } catch (e: Exception) {
            dataButton.text = "移动数据\n设置"
        }

        // GPS状态
        try {
            val locationMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
            val isGpsEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF
            gpsButton.text = if (isGpsEnabled) "GPS\n已开启" else "GPS\n已关闭"
        } catch (e: Exception) {
            gpsButton.text = "GPS\n设置"
        }

        // 更新亮度按钮
        updateBrightnessButton()
    }

    private fun updateTime() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        timeTextView.text = currentTime
    }

    private fun startDynamicTimeUpdate() {
        handler.removeCallbacks(updateTimeRunnable)
        handler.post(updateTimeRunnable)
    }

    override fun onResume() {
        super.onResume()
        startDynamicTimeUpdate()
        updateStatusIcons()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateTimeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
    }
}
