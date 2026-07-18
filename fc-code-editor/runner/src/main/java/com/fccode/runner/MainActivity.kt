package com.fccode.runner

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        
        // 1. Try standalone offline assets loading first!
        var isStandalone = false
        var html = ""
        var css = ""
        var js = ""
        var appName = "FC Code"
        var appVersion = "1.0.0"
        var companyName = "FC Studio"
        var selectedOrientation = "Portrait"
        var iconShape = "Squircle"
        var enableSplash = true
        var splashColor = 0xFF1E1E1E.toInt()
        var customIconBase64 = ""
        var iconColor = 0xFF00C853.toInt()
        var iconSymbol = "code"

        try {
            assets.open("index.html").use { stream ->
                html = stream.bufferedReader().use { it.readText() }
                isStandalone = true
            }
            try {
                assets.open("style.css").use { stream ->
                    css = stream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) { e.printStackTrace() }
            try {
                assets.open("script.js").use { stream ->
                    js = stream.bufferedReader().use { it.readText() }
                }
            } catch (e: Exception) { e.printStackTrace() }

            // Read app_config.json for dynamic configurations
            try {
                assets.open("app_config.json").use { stream ->
                    val configText = stream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(configText)
                    appName = json.optString("name", appName)
                    appVersion = json.optString("version", appVersion)
                    companyName = json.optString("company", companyName)
                    selectedOrientation = json.optString("orientation", selectedOrientation)
                    iconShape = json.optString("iconShape", iconShape)
                    enableSplash = json.optBoolean("enableSplash", enableSplash)
                    splashColor = json.optInt("splashColor", splashColor)
                    customIconBase64 = json.optString("customIconBase64", customIconBase64)
                    iconColor = json.optInt("iconColor", iconColor)
                    iconSymbol = json.optString("iconSymbol", iconSymbol)
                }
            } catch (e: Exception) { e.printStackTrace() }
        } catch (e: Exception) {
            // Not standalone, use ContentProvider
            isStandalone = false
        }

        var dataLoaded = false

        if (isStandalone) {
            dataLoaded = true
        } else {
            // Query the main app's ContentProvider
            val projectId = intent.getIntExtra("project_id", -1)
            val providerUri = if (projectId != -1) {
                Uri.parse("content://com.example.fccode.provider/project/$projectId")
            } else {
                Uri.parse("content://com.example.fccode.provider/active")
            }
            val cursor: Cursor? = contentResolver.query(providerUri, null, null, null, null)
            
            if (cursor != null && cursor.moveToFirst()) {
                val htmlCol = cursor.getColumnIndex("html")
                val cssCol = cursor.getColumnIndex("css")
                val jsCol = cursor.getColumnIndex("js")
                val nameCol = cursor.getColumnIndex("name")
                val versionCol = cursor.getColumnIndex("version")
                val companyCol = cursor.getColumnIndex("company")
                val orientationCol = cursor.getColumnIndex("orientation")
                val iconShapeCol = cursor.getColumnIndex("iconShape")
                val enableSplashCol = cursor.getColumnIndex("enableSplashScreen")
                val splashColorCol = cursor.getColumnIndex("splashColor")
                val customIconCol = cursor.getColumnIndex("customIconBase64")
                val iconColorCol = cursor.getColumnIndex("iconColor")
                val iconSymbolCol = cursor.getColumnIndex("iconSymbol")
                
                html = if (htmlCol >= 0) cursor.getString(htmlCol) else ""
                css = if (cssCol >= 0) cursor.getString(cssCol) else ""
                js = if (jsCol >= 0) cursor.getString(jsCol) else ""
                appName = if (nameCol >= 0) cursor.getString(nameCol) else "FC Code"
                appVersion = if (versionCol >= 0) cursor.getString(versionCol) else "1.0.0"
                companyName = if (companyCol >= 0) cursor.getString(companyCol) else "FC Studio"
                selectedOrientation = if (orientationCol >= 0) cursor.getString(orientationCol) else "Portrait"
                iconShape = if (iconShapeCol >= 0) cursor.getString(iconShapeCol) else "Squircle"
                enableSplash = if (enableSplashCol >= 0) cursor.getInt(enableSplashCol) == 1 else true
                splashColor = if (splashColorCol >= 0) cursor.getInt(splashColorCol) else 0xFF1E1E1E.toInt()
                customIconBase64 = if (customIconCol >= 0) cursor.getString(customIconCol) else ""
                iconColor = if (iconColorCol >= 0) cursor.getInt(iconColorCol) else 0xFF00C853.toInt()
                iconSymbol = if (iconSymbolCol >= 0) cursor.getString(iconSymbolCol) else "code"
                
                dataLoaded = true
                cursor.close()
            }
        }
        
        if (dataLoaded) {
            title = appName
            
            // Set screen orientation dynamically
            requestedOrientation = when (selectedOrientation) {
                "Landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "Sensor / Auto" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            
            // Build App Launcher Icon Bitmap
            var appIconBitmap: android.graphics.Bitmap? = null
            if (customIconBase64.isNotEmpty()) {
                try {
                    val bytes = android.util.Base64.decode(customIconBase64, android.util.Base64.DEFAULT)
                    appIconBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (appIconBitmap == null) {
                val size = 192
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                
                // Draw background shape
                paint.color = iconColor
                val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
                val rx = when (iconShape) {
                    "Round" -> size / 2f
                    "Square" -> 12f
                    "Squircle" -> size * 0.3f
                    "Teardrop" -> size * 0.25f
                    else -> size * 0.2f
                }
                
                if (iconShape == "Teardrop") {
                    val path = android.graphics.Path()
                    path.addRoundRect(rect, floatArrayOf(rx, rx, rx, rx, rx, rx, 4f, 4f), android.graphics.Path.Direction.CW)
                    canvas.drawPath(path, paint)
                } else {
                    canvas.drawRoundRect(rect, rx, rx, paint)
                }
                
                // Draw bold stylized first letter of App Name
                paint.color = android.graphics.Color.WHITE
                paint.textAlign = android.graphics.Paint.Align.CENTER
                paint.textSize = size * 0.45f
                paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                
                val letter = if (appName.isNotEmpty()) appName.substring(0, 1).uppercase() else "F"
                val fontMetrics = paint.fontMetrics
                val x = size / 2f
                val y = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(letter, x, y, paint)
                
                appIconBitmap = bitmap
            }
            
            // Programmatic Fullscreen Container
            val container = android.widget.FrameLayout(this)
            container.addView(webView, android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
            
            if (enableSplash) {
                val splashLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setBackgroundColor(splashColor)
                    isClickable = true
                    isFocusable = true
                }
                
                // Icon View
                val iconSize = (110 * resources.displayMetrics.density).toInt()
                val iconView = android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(appIconBitmap)
                }
                val iconParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    bottomMargin = (24 * resources.displayMetrics.density).toInt()
                }
                splashLayout.addView(iconView, iconParams)
                
                // App Name
                val nameView = android.widget.TextView(this).apply {
                    text = appName
                    setTextColor(if (isColorDark(splashColor)) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                    textSize = 24f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                }
                val nameParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * resources.displayMetrics.density).toInt()
                }
                splashLayout.addView(nameView, nameParams)
                
                // App Version
                val versionView = android.widget.TextView(this).apply {
                    text = "Version $appVersion"
                    setTextColor(if (isColorDark(splashColor)) android.graphics.Color.LTGRAY else android.graphics.Color.GRAY)
                    textSize = 13f
                    gravity = android.view.Gravity.CENTER
                }
                val versionParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (28 * resources.displayMetrics.density).toInt()
                }
                splashLayout.addView(versionView, versionParams)
                
                // Loader Circular Progress
                val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(
                        if (isColorDark(splashColor)) android.graphics.Color.WHITE else 0xFF00C853.toInt()
                    )
                }
                val progressParams = android.widget.LinearLayout.LayoutParams(
                    (42 * resources.displayMetrics.density).toInt(),
                    (42 * resources.displayMetrics.density).toInt()
                ).apply {
                    bottomMargin = (40 * resources.displayMetrics.density).toInt()
                }
                splashLayout.addView(progressBar, progressParams)
                
                // Developer Footer
                val footerView = android.widget.TextView(this).apply {
                    text = "Developed by $companyName\nPowered by FC Studio"
                    setTextColor(if (isColorDark(splashColor)) android.graphics.Color.GRAY else android.graphics.Color.LTGRAY)
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                }
                val footerParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                splashLayout.addView(footerView, footerParams)
                
                container.addView(splashLayout, android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
                
                // Fade splash layout out beautifully after 2.2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    splashLayout.animate()
                        .alpha(0f)
                        .setDuration(450)
                        .withEndAction {
                            container.removeView(splashLayout)
                        }
                        .start()
                }, 2200)
            }
            
            setContentView(container)
            
            if (html.isNotEmpty()) {
                val combinedHtml = """
                    $html
                    <style>
                    $css
                    </style>
                    <script>
                    $js
                    </script>
                """.trimIndent()
                
                webView.loadDataWithBaseURL(
                    "https://local.code2apk.studio/",
                    combinedHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            } else {
                showFallback(webView, "Project is empty. Write some HTML code in 'FC Code' first!")
            }
        } else {
            showFallback(webView, "To run your app, open 'FC Code', choose a project, build it, and then run this app!")
        }
    }
    
    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 
                            0.587 * android.graphics.Color.green(color) + 
                            0.114 * android.graphics.Color.blue(color)) / 255
        return darkness >= 0.5
    }
    
    private fun showFallback(webView: WebView, message: String) {
        val errorHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body {
                        background-color: #121212;
                        color: #ffffff;
                        font-family: sans-serif;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 20px;
                        text-align: center;
                    }
                    h2 { color: #007ACC; }
                    p { color: #888; font-size: 14px; line-height: 1.6; }
                </style>
            </head>
            <body>
                <h2>FC Code Runner</h2>
                <p>$message</p>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }
}
