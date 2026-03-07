package com.example.simpletimeapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews
import android.widget.Toast

class ControlWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIFI = "com.example.simpletimeapp.WIDGET_WIFI"
        const val ACTION_DATA = "com.example.simpletimeapp.WIDGET_DATA"
        const val ACTION_GPS = "com.example.simpletimeapp.WIDGET_GPS"
        const val ACTION_BRIGHTNESS = "com.example.simpletimeapp.WIDGET_BRIGHTNESS"
        const val ACTION_SHUTDOWN = "com.example.simpletimeapp.WIDGET_SHUTDOWN"
        const val ACTION_UPDATE_WIDGET = "com.example.simpletimeapp.UPDATE_WIDGET"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_WIFI -> {
                Toast.makeText(context, "WiFi开关 - 请在应用中操作", Toast.LENGTH_SHORT).show()
                openApp(context)
            }
            ACTION_DATA -> {
                Toast.makeText(context, "移动数据 - 请在应用中操作", Toast.LENGTH_SHORT).show()
                openApp(context)
            }
            ACTION_GPS -> {
                toggleGPS(context)
            }
            ACTION_BRIGHTNESS -> {
                toggleBrightness(context)
            }
            ACTION_SHUTDOWN -> {
                Toast.makeText(context, "一键关机 - 请在应用中操作", Toast.LENGTH_SHORT).show()
                openApp(context)
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, ControlWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    private fun openApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }

    private fun toggleGPS(context: Context) {
        try {
            val locationMode = Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE)
            val isGpsEnabled = locationMode != Settings.Secure.LOCATION_MODE_OFF

            val newMode = if (isGpsEnabled) {
                Settings.Secure.LOCATION_MODE_OFF
            } else {
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            }

            try {
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.LOCATION_MODE, newMode)
                Toast.makeText(context, if (isGpsEnabled) "GPS已关闭" else "GPS已开启", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(context, "需要权限，请打开应用设置", Toast.LENGTH_SHORT).show()
                openApp(context)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "GPS操作失败", Toast.LENGTH_SHORT).show()
        }

        // 更新Widget状态
        updateAllWidgets(context)
    }

    private fun toggleBrightness(context: Context) {
        try {
            val currentBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )

            val autoBrightnessMode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val newBrightness = when {
                autoBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> 255 // 从自动切换到全亮
                currentBrightness >= 180 -> 5 // 全亮 -> 全暗
                currentBrightness >= 30 -> 255 // 自定义 -> 全亮
                else -> 128 // 全暗 -> 自定义
            }

            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)

            val label = when {
                newBrightness >= 180 -> "全亮"
                newBrightness >= 30 -> "自定义"
                else -> "全暗"
            }
            Toast.makeText(context, "亮度: $label", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "需要权限，请打开应用设置", Toast.LENGTH_SHORT).show()
            openApp(context)
        } catch (e: Exception) {
            Toast.makeText(context, "亮度调节失败", Toast.LENGTH_SHORT).show()
        }

        // 更新Widget状态
        updateAllWidgets(context)
    }

    private fun updateAllWidgets(context: Context) {
        val intent = Intent(context, ControlWidgetProvider::class.java)
        intent.action = ACTION_UPDATE_WIDGET
        context.sendBroadcast(intent)
    }
}

private fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val views = RemoteViews(context.packageName, R.layout.widget_control)

    // 设置WiFi按钮点击
    val wifiIntent = Intent(context, ControlWidgetProvider::class.java)
    wifiIntent.action = ControlWidgetProvider.ACTION_WIFI
    val wifiPendingIntent = PendingIntent.getBroadcast(
        context, 0, wifiIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetWifiButton, wifiPendingIntent)

    // 设置数据按钮点击
    val dataIntent = Intent(context, ControlWidgetProvider::class.java)
    dataIntent.action = ControlWidgetProvider.ACTION_DATA
    val dataPendingIntent = PendingIntent.getBroadcast(
        context, 1, dataIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetDataButton, dataPendingIntent)

    // 设置GPS按钮点击
    val gpsIntent = Intent(context, ControlWidgetProvider::class.java)
    gpsIntent.action = ControlWidgetProvider.ACTION_GPS
    val gpsPendingIntent = PendingIntent.getBroadcast(
        context, 2, gpsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetGpsButton, gpsPendingIntent)

    // 设置亮度按钮点击
    val brightnessIntent = Intent(context, ControlWidgetProvider::class.java)
    brightnessIntent.action = ControlWidgetProvider.ACTION_BRIGHTNESS
    val brightnessPendingIntent = PendingIntent.getBroadcast(
        context, 3, brightnessIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetBrightnessButton, brightnessPendingIntent)

    // 设置关机按钮点击
    val shutdownIntent = Intent(context, ControlWidgetProvider::class.java)
    shutdownIntent.action = ControlWidgetProvider.ACTION_SHUTDOWN
    val shutdownPendingIntent = PendingIntent.getBroadcast(
        context, 4, shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widgetShutdownButton, shutdownPendingIntent)

    // 更新Widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}