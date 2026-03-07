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

        // 检测当前亮度状态（必须在检查权限前执行，以显示当前状态）
        detectCurrentBrightnessState()
        
        // 立即更新亮度按钮显示
        updateBrightnessButton()

        // 检查并申请亮度调节权限
        checkBrightnessPermission()

        // 启动时间更新
        startDynamicTimeUpdate()

        // 设置按钮点击事件
        setupButtons()

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
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
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
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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
        // 尝试多种关机方法
        val methods = listOf(
            // 方法1: Android 8.0+ PowerManager.shutdown()
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.javaClass.getMethod("shutdown", Boolean::class.java, String::class.java, Boolean::class.java)
                        .invoke(pm, false, "user_requested", false)
                    true
                } else false
            },
            // 方法2: 使用系统关机对话框（需要系统签名或Root）
            {
                val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
                intent.putExtra("android.intent.extra.KEY_CONFIRM", false)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                true
            },
            // 方法3: 使用PowerManager重启（模拟关机）
            {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.javaClass.getMethod("reboot", String::class.java).invoke(pm, "shutdown")
                true
            },
            // 方法4: 发送关机广播
            {
                val shutdownIntent = Intent(Intent.ACTION_SHUTDOWN)
                shutdownIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                sendBroadcast(shutdownIntent)
                true
            },
            // 方法5: 使用Root权限（如果设备已Root）
            {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
                true
            }
        )

        var success = false
        for ((index, method) in methods.withIndex()) {
            try {
                if (method()) {
                    success = true
                    break
                }
            } catch (e: Exception) {
                // 继续尝试下一个方法
            }
        }

        if (!success) {
            // 所有方法都失败，提供手动关机指导
            AlertDialog.Builder(this)
                .setTitle("⚠️ 需要系统权限")
                .setMessage("自动关机需要系统级权限。\n\n请尝试以下方法之一：\n1. 确保设备已Root后重试\n2. 长按电源键手动关机\n3. 将此应用安装为系统应用")
                .setPositiveButton("打开电源菜单") { _, _ ->
                    try {
                        // 尝试打开电源菜单
                        Runtime.getRuntime().exec("input keyevent 26") // KEYCODE_POWER
                    } catch (e: Exception) {
                        Toast.makeText(this, "无法打开电源菜单，请手动长按电源键", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WRITE_SETTINGS) {
            // 用户从设置页面返回，重新检测亮度状态
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(this)) {
                    Toast.makeText(this, "权限已获取，可以调节亮度了", Toast.LENGTH_SHORT).show()
                    detectCurrentBrightnessState()
                    updateBrightnessButton()
                } else {
                    Toast.makeText(this, "未获取权限，亮度调节功能受限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startDynamicTimeUpdate()
        updateStatusIcons()
        // 重新检测亮度状态（可能用户在设置中修改了）
        detectCurrentBrightnessState()
        updateBrightnessButton()
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
