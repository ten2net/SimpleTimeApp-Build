package com.example.simpletimeapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
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
        Triple(5, "全暗", R.drawable.ic_brightness_low),  // 改为5而不是20，实现真正的全暗
        Triple(-1, "自动", R.drawable.ic_brightness_auto)
    )

    companion object {
        private const val REQUEST_WRITE_SETTINGS = 1001
        private const val REQUEST_SHUTDOWN_PERMISSION = 1002
    }

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

        // 设置按钮点击事件
        setupButtons()

        // 检查亮度权限（会触发亮度检测）
        checkBrightnessPermission()

        // 启动时间更新
        startDynamicTimeUpdate()

        // 初始化状态显示
        updateStatusIcons()
    }

    private fun checkBrightnessPermission() {
        // Android 6.0+ 需要检查 WRITE_SETTINGS 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                // 引导用户去设置页面授权
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("亮度调节功能需要修改系统设置权限，请在设置中开启。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivityForResult(intent, REQUEST_WRITE_SETTINGS)
                    }
                    .setNegativeButton("暂不设置") { _, _ ->
                        // 用户选择暂不设置，仍然尝试读取亮度（某些设备可能允许读取）
                        detectCurrentBrightnessState()
                        updateBrightnessButton()
                    }
                    .show()
            } else {
                // 已有权限，重新检测亮度确保准确
                detectCurrentBrightnessState()
                updateBrightnessButton()
            }
        }
    }

    private fun detectCurrentBrightnessState() {
        try {
            // 首先检查是否为自动亮度模式
            val autoBrightnessMode = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val isAutoMode = (autoBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)

            // 获取当前亮度值 (0-255)
            // 注意：这个值是系统亮度值，不是当前窗口亮度
            val currentBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )

            // 获取当前窗口亮度（如果有设置过）
            val windowBrightness = window.attributes.screenBrightness
            val effectiveBrightness = if (windowBrightness > 0) {
                (windowBrightness * 255).toInt()
            } else {
                currentBrightness
            }

            Log.d("Brightness", "系统亮度: $currentBrightness, 窗口亮度: $windowBrightness, 有效亮度: $effectiveBrightness, 自动模式: $isAutoMode")

            // 根据状态设置 brightnessState
            brightnessState = when {
                isAutoMode -> 3 // 自动模式
                effectiveBrightness >= 180 -> 0 // 全亮 (180-255)
                effectiveBrightness >= 30 -> 1  // 自定义 (30-179)
                else -> 2 // 全暗 (0-29)
            }

            Log.d("Brightness", "设置亮度状态索引: $brightnessState, 状态: ${brightnessStates[brightnessState].second}")

        } catch (e: SecurityException) {
            // 没有权限读取亮度，使用默认值
            Log.e("Brightness", "读取亮度失败(无权限): ${e.message}")
            brightnessState = 1 // 默认自定义
        } catch (e: Exception) {
            Log.e("Brightness", "读取亮度失败: ${e.message}")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                // 没有权限，引导用户去设置
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("亮度调节功能需要修改系统设置权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivityForResult(intent, REQUEST_WRITE_SETTINGS)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                toggleBrightness()
            }
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
                
                // 同时设置屏幕亮度（立即生效）
                val layoutParams = window.attributes
                layoutParams.screenBrightness = brightnessValue / 255.0f
                window.attributes = layoutParams
                
                Toast.makeText(this, "亮度: $brightnessLabel", Toast.LENGTH_SHORT).show()
            }
            updateBrightnessButton()
        } catch (e: Exception) {
            Toast.makeText(this, "亮度调节失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    val newState = !it.isWifiEnabled
                    it.isWifiEnabled = newState
                    Toast.makeText(this, if (newState) "WiFi已开启" else "WiFi已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    // Android 10+ 跳转设置
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "WiFi操作失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMobileData() {
        startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
    }

    private fun toggleGPS() {
        try {
            // 获取当前GPS状态
            val locationMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
            val isGpsEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF
            
            // 尝试直接切换GPS状态（需要WRITE_SECURE_SETTINGS权限）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // Android 4.4及以下可以尝试直接修改
                val newMode = if (isGpsEnabled) {
                    Settings.Secure.LOCATION_MODE_OFF
                } else {
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                }
                
                try {
                    Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, newMode)
                    Toast.makeText(this, if (isGpsEnabled) "GPS已关闭" else "GPS已开启", Toast.LENGTH_SHORT).show()
                    updateStatusIcons()
                    return
                } catch (e: SecurityException) {
                    // 没有权限，跳转到设置
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            } else {
                // Android 5.0+ 尝试切换
                val newMode = if (isGpsEnabled) {
                    Settings.Secure.LOCATION_MODE_OFF
                } else {
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                }
                
                try {
                    // 使用反射尝试修改（某些设备可能允许）
                    Settings.Secure.putInt(contentResolver, Settings.Secure.LOCATION_MODE, newMode)
                    Toast.makeText(this, if (isGpsEnabled) "GPS已关闭" else "GPS已开启", Toast.LENGTH_SHORT).show()
                    updateStatusIcons()
                } catch (e: SecurityException) {
                    // 没有权限，显示提示并跳转到设置
                    AlertDialog.Builder(this)
                        .setTitle("需要权限")
                        .setMessage("直接控制GPS需要特殊权限。是否跳转到设置手动开启/关闭？")
                        .setPositiveButton("去设置") { _, _ ->
                            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            // 出错时跳转到设置
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun showShutdownDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 确认关机")
            .setMessage("确定要关闭设备吗？")
            .setPositiveButton("关机") { _, _ ->
                shutdownDevice()
            }
            .setNegativeButton("取消", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun shutdownDevice() {
        // 方法1: 尝试直接关机（需要系统权限）
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.javaClass.getMethod("shutdown", Boolean::class.java, String::class.java, Boolean::class.java)
                    .invoke(pm, false, "user_requested", false)
                return
            }
        } catch (e: Exception) {
            // 继续尝试其他方法
        }

        // 方法2: 使用无障碍服务自动点击关机
        if (ShutdownAccessibilityService.isServiceEnabled()) {
            val serviceIntent = Intent(this, ShutdownAccessibilityService::class.java)
            startService(serviceIntent)
            
            // 延迟后触发电源菜单
            Handler(Looper.getMainLooper()).postDelayed({
                val service = ShutdownAccessibilityService.instance
                if (service != null) {
                    val result = service.triggerShutdown()
                    if (!result) {
                        // 触发失败，显示手动指导
                        showManualShutdownGuide()
                    }
                } else {
                    showManualShutdownGuide()
                }
            }, 100)
            return
        }

        // 方法3: 引导用户开启无障碍服务或手动关机
        showAccessibilityGuide()
    }

    private fun showAccessibilityGuide() {
        val isServiceEnabled = ShutdownAccessibilityService.isServiceEnabled()

        if (!isServiceEnabled) {
            AlertDialog.Builder(this)
                .setTitle("🔧 开启一键关机")
                .setMessage("一键关机需要开启无障碍服务辅助操作。\n\n开启后，应用将自动完成：\n1. 打开电源菜单\n2. 点击关机按钮\n3. 确认关机操作")
                .setPositiveButton("去开启") { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton("手动关机") { _, _ ->
                    showManualShutdownGuide()
                }
                .setNeutralButton("取消", null)
                .show()
        } else {
            showManualShutdownGuide()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到【一键关机服务】并开启", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置，请手动进入：设置 > 辅助功能 > 无障碍", Toast.LENGTH_LONG).show()
        }
    }

    private fun showManualShutdownGuide() {
        AlertDialog.Builder(this)
            .setTitle("📱 手动关机")
            .setMessage("请按住电源键 2-3 秒，然后点击\"关机\"按钮。")
            .setPositiveButton("模拟电源键") { _, _ ->
                try {
                    // 尝试模拟电源键（需要Root）
                    Runtime.getRuntime().exec("input keyevent 26")
                } catch (e: Exception) {
                    Toast.makeText(this, "模拟失败，请手动长按电源键", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("我知道了", null)
            .show()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WRITE_SETTINGS) {
            // 用户从设置页面返回，重新检测亮度状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = Settings.System.canWrite(this)
                if (hasPermission) {
                    Toast.makeText(this, "权限已获取，正在更新亮度状态...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未获取权限，亮度检测可能不准确", Toast.LENGTH_SHORT).show()
                }
                // 无论是否获取权限，都重新检测亮度
                detectCurrentBrightnessState()
                updateBrightnessButton()
                updateStatusIcons()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startDynamicTimeUpdate()
        // 重新检测亮度状态（可能用户在设置中修改了）
        detectCurrentBrightnessState()
        updateBrightnessButton()
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
