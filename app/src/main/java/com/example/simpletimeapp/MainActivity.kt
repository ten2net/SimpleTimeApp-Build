package com.example.simpletimeapp

import android.app.Activity
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var timeTextView: TextView
    private lateinit var wifiButton: Button
    private lateinit var dataButton: Button
    private lateinit var gpsButton: Button
    private lateinit var shutdownButton: Button
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var brightnessLabel: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null

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
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        brightnessLabel = findViewById(R.id.brightnessLabel)

        // 初始化系统服务
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?

        // 启动时间更新
        startDynamicTimeUpdate()

        // 设置按钮点击事件
        setupButtons()

        // 设置亮度调节
        setupBrightnessControl()

        // 初始化状态显示
        updateStatusIcons()
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
    }

    private fun setupBrightnessControl() {
        // 获取当前亮度
        val currentBrightness = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        )
        brightnessSeekBar.progress = currentBrightness
        brightnessLabel.text = "亮度: ${currentBrightness / 255 * 100}%"

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setBrightness(progress)
                    brightnessLabel.text = "亮度: ${progress * 100 / 255}%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun toggleWifi() {
        try {
            wifiManager?.let {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // Android 10 以下可以直接开关
                    it.isWifiEnabled = !it.isWifiEnabled
                    Toast.makeText(this, if (it.isWifiEnabled) "WiFi已开启" else "WiFi已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    // Android 10+ 需要跳转到设置页面
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "WiFi操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMobileData() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 跳转到设置
                startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
            } else {
                // 尝试切换移动数据状态
                val method = telephonyManager?.javaClass?.getDeclaredMethod("setDataEnabled", Boolean::class.java)
                method?.isAccessible = true
                val isEnabled = telephonyManager?.javaClass?.getDeclaredMethod("getDataEnabled")?.invoke(telephonyManager) as? Boolean ?: false
                method?.invoke(telephonyManager, !isEnabled)
                Toast.makeText(this, if (!isEnabled) "移动数据已开启" else "移动数据已关闭", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // 跳转设置页面
            startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
        }
    }

    private fun toggleGPS() {
        try {
            val locationMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun setBrightness(value: Int) {
        try {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } catch (e: Exception) {
            Toast.makeText(this, "需要权限来调节亮度", Toast.LENGTH_SHORT).show()
        }
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
            // 尝试使用系统关机功能（需要特殊权限或ROOT）
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // 方法1: 使用系统关机广播
            val intent = Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN")
            intent.putExtra("android.intent.extra.KEY_CONFIRM", true)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // 方法2: 尝试使用PowerManager重启（部分设备支持）
                try {
                    val method = powerManager.javaClass.getMethod("reboot", String::class.java)
                    method.invoke(powerManager, null)
                } catch (e2: Exception) {
                    // 方法3: 使用su命令（需要ROOT权限）
                    try {
                        Runtime.getRuntime().exec("su -c reboot -p")
                    } catch (e3: Exception) {
                        Toast.makeText(this, "关机功能需要系统权限", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "关机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusIcons() {
        // 更新WiFi状态
        wifiManager?.let {
            val isWifiEnabled = it.isWifiEnabled
            wifiButton.text = if (isWifiEnabled) "WiFi\n已开启" else "WiFi\n已关闭"
            wifiButton.setBackgroundColor(if (isWifiEnabled) 0xFFE6F7FF.toInt() else 0xFFFFFFFF.toInt())
        }

        // 更新移动数据状态
        try {
            val isDataEnabled = telephonyManager?.javaClass
                ?.getDeclaredMethod("getDataEnabled")
                ?.invoke(telephonyManager) as? Boolean ?: false
            dataButton.text = if (isDataEnabled) "移动数据\n已开启" else "移动数据\n已关闭"
        } catch (e: Exception) {
            dataButton.text = "移动数据\n设置"
        }

        // 更新GPS状态
        try {
            val locationMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
            val isGpsEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF
            gpsButton.text = if (isGpsEnabled) "GPS\n已开启" else "GPS\n已关闭"
        } catch (e: Exception) {
            gpsButton.text = "GPS\n设置"
        }
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
