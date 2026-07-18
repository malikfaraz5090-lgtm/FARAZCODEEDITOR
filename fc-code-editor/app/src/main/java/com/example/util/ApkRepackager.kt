package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.android.apksig.ApkSigner
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ApkRepackager {

    private const val TAG = "ApkRepackager"

    // 100-character placeholder string exactly matching the one in runner's strings.xml
    private const val PLACEHOLDER_STRING = "FC_PREVIEWER_APP_NAME_PLACEHOLDER_FOR_DYNAMIC_REPLACING_BY_EDITOR_APP_100_CHARS_PADDING_SPACES_X_X_X"

    /**
     * Patch and sign the APK on device.
     * Reads runner-debug.apk from assets, edits resources and icon, bundles standalone files, and signs it.
     */
    fun buildPatchedApk(
        context: Context,
        appName: String,
        appVersion: String,
        companyName: String,
        selectedOrientation: String,
        iconShape: String,
        enableSplash: Boolean,
        splashColor: Int,
        customIconBase64: String?,
        iconColor: Int,
        iconSymbol: String,
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        outputApkFile: File
    ): Boolean {
        var tempUnsignedFile: File? = null
        try {
            Log.d(TAG, "Starting APK build process for app: $appName")
            
            // 1. Create a temporary file for the unsigned patched APK
            tempUnsignedFile = File.createTempFile("unsigned_patched", ".apk", context.cacheDir)
            
            // 2. Open the template runner-debug.apk from assets
            val apkInputStream = context.assets.open("runner-debug.apk")
            
            var configWritten = false

            // 3. Prepare ZIP streams
            ZipInputStream(apkInputStream).use { zipIn ->
                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempUnsignedFile))).use { zipOut ->
                    
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        System.err.println("ENTRY_NAME_READ: '$name'")
                        
                        if (name.isEmpty()) {
                            readAllBytes(zipIn)
                            entry = zipIn.nextEntry
                            continue
                        }
                        
                        // We will skip existing signature files in META-INF to avoid conflicts
                        if (name.startsWith("META-INF/")) {
                            readAllBytes(zipIn) // ALWAYS CONSUME BYTES TO PREVENT ALIGNMENT CORRUPTION!
                            entry = zipIn.nextEntry
                            continue
                        }
                        
                        // Create a new ZIP entry
                        // We must use DEFLATED for apk zip entries except uncompressed resources if needed,
                        // but standard ZipOutputStream defaults to DEFLATED which is fine.
                        val newEntry = ZipEntry(name)
                        try {
                            zipOut.putNextEntry(newEntry)
                        } catch (ze: java.util.zip.ZipException) {
                            Log.e(TAG, "Duplicate entry error for: '$name'", ze)
                            System.err.println("--- DUPLICATE ENTRY ERROR: '$name' ---")
                            throw ze
                        }
                        
                        when {
                            // Inject custom standalone offline HTML
                            name == "assets/index.html" -> {
                                readAllBytes(zipIn) // Consume original
                                zipOut.write(htmlCode.toByteArray(Charsets.UTF_8))
                            }
                            
                            // Inject custom standalone offline CSS
                            name == "assets/style.css" -> {
                                readAllBytes(zipIn) // Consume original
                                zipOut.write(cssCode.toByteArray(Charsets.UTF_8))
                            }
                            
                            // Inject custom standalone offline JS
                            name == "assets/script.js" -> {
                                readAllBytes(zipIn) // Consume original
                                zipOut.write(jsCode.toByteArray(Charsets.UTF_8))
                            }
                            
                            // Inject custom standalone offline config
                            name == "assets/app_config.json" -> {
                                readAllBytes(zipIn) // Consume original
                                val configJson = JSONObject().apply {
                                    put("name", appName)
                                    put("version", appVersion)
                                    put("company", companyName)
                                    put("orientation", selectedOrientation)
                                    put("iconShape", iconShape)
                                    put("enableSplash", enableSplash)
                                    put("splashColor", splashColor)
                                    put("customIconBase64", customIconBase64 ?: "")
                                    put("iconColor", iconColor)
                                    put("iconSymbol", iconSymbol)
                                }
                                zipOut.write(configJson.toString().toByteArray(Charsets.UTF_8))
                                configWritten = true
                            }
                            
                            // Patch the resources.arsc to change the App Name
                            name == "resources.arsc" -> {
                                val arscBytes = readAllBytes(zipIn)
                                val patchedArscBytes = patchAppNameInArsc(arscBytes, appName)
                                zipOut.write(patchedArscBytes)
                            }
                            
                            // Replace App Launcher Icons if a custom icon is provided
                            !customIconBase64.isNullOrEmpty() && (name.contains("ic_launcher.png") || name.contains("ic_launcher_round.png")) -> {
                                val iconBytes = resizeAndGenerateIconPng(customIconBase64, name)
                                val originalBytes = readAllBytes(zipIn) // Consume original always
                                if (iconBytes != null) {
                                    zipOut.write(iconBytes)
                                } else {
                                    // Fallback to copying existing icon
                                    zipOut.write(originalBytes)
                                }
                            }
                            
                            else -> {
                                // Copy all other files exactly as they are
                                val bytes = readAllBytes(zipIn)
                                System.err.println("ENTRY_BYTES_READ: '$name' size=${bytes.size}")
                                zipOut.write(bytes)
                            }
                        }
                        
                        zipOut.closeEntry()
                        entry = zipIn.nextEntry
                    }
                    
                    if (!configWritten) {
                        // 4. Manually add assets/app_config.json to the unsigned APK if not written in-line yet
                        val configEntry = ZipEntry("assets/app_config.json")
                        zipOut.putNextEntry(configEntry)
                        
                        val configJson = JSONObject().apply {
                            put("name", appName)
                            put("version", appVersion)
                            put("company", companyName)
                            put("orientation", selectedOrientation)
                            put("iconShape", iconShape)
                            put("enableSplash", enableSplash)
                            put("splashColor", splashColor)
                            put("customIconBase64", customIconBase64 ?: "")
                            put("iconColor", iconColor)
                            put("iconSymbol", iconSymbol)
                        }
                        
                        zipOut.write(configJson.toString().toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }
            }
            
            Log.d(TAG, "Unsigned patched APK built successfully. Size: ${tempUnsignedFile.length()} bytes. Now signing...")
            
            // 5. Sign the unsigned APK using Google's apksig library
            signApk(context, tempUnsignedFile, outputApkFile)
            Log.d(TAG, "Signed Patched APK written successfully to: ${outputApkFile.absolutePath}")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build patched and signed APK", e)
            e.printStackTrace()
            return false
        } finally {
            // Clean up temporary unsigned file
            try {
                tempUnsignedFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Patches the app name placeholder in resources.arsc with the custom app name.
     */
    private fun patchAppNameInArsc(arscBytes: ByteArray, customAppName: String): ByteArray {
        val targetBytes = PLACEHOLDER_STRING.toByteArray(Charsets.UTF_8)
        
        // Prepare replacement bytes: custom app name + null terminator + zero padding up to 100 bytes
        val replacementBytes = ByteArray(100)
        val nameBytes = customAppName.toByteArray(Charsets.UTF_8)
        
        // Copy name bytes (up to 99 bytes to preserve at least one null terminator)
        val copyLen = minOf(nameBytes.size, 99)
        System.arraycopy(nameBytes, 0, replacementBytes, 0, copyLen)
        
        // The remaining bytes of replacementBytes are initialized to 0x00 by default in Kotlin
        
        // Find the index of the placeholder in the resources.arsc bytes
        val index = findBytePattern(arscBytes, targetBytes)
        if (index != -1) {
            Log.d(TAG, "Placeholder found in resources.arsc at index: $index. Patching...")
            val resultBytes = arscBytes.clone()
            System.arraycopy(replacementBytes, 0, resultBytes, index, replacementBytes.size)
            return resultBytes
        } else {
            Log.w(TAG, "Placeholder string was NOT found in resources.arsc. APK might retain default name.")
            return arscBytes
        }
    }

    /**
     * Decodes, scales, and generates PNG bytes for launcher icons according to mipmap bucket resolution.
     */
    private fun resizeAndGenerateIconPng(customIconBase64: String, entryName: String): ByteArray? {
        return try {
            val decodedBytes = Base64.decode(customIconBase64, Base64.DEFAULT)
            val srcBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: return null
            
            val targetSize = when {
                entryName.contains("-mdpi") -> 48
                entryName.contains("-hdpi") -> 72
                entryName.contains("-xhdpi") -> 96
                entryName.contains("-xxhdpi") -> 144
                entryName.contains("-xxxhdpi") -> 192
                else -> 192
            }
            
            val scaledBitmap = Bitmap.createScaledBitmap(srcBitmap, targetSize, targetSize, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            
            // Clean up bitmaps
            if (scaledBitmap != srcBitmap) {
                scaledBitmap.recycle()
            }
            srcBitmap.recycle()
            
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing custom icon for entry: $entryName", e)
            null
        }
    }

    /**
     * Signs an unsigned APK using Google's apksig library.
     */
    private fun signApk(context: Context, unsignedApk: File, signedApk: File) {
        // Load the embedded PKCS12 keystore from assets
        val keyStore = KeyStore.getInstance("PKCS12")
        context.assets.open("signer.p12").use { ksStream ->
            keyStore.load(ksStream, "fccodepassword".toCharArray())
        }
        
        val entry = keyStore.getEntry("fccode", KeyStore.PasswordProtection("fccodepassword".toCharArray()))
                as KeyStore.PrivateKeyEntry
        
        val privateKey: PrivateKey = entry.privateKey
        val certificateChain = entry.certificateChain.map { it as X509Certificate }
        
        // Build Signer Config
        val signerConfig = ApkSigner.SignerConfig.Builder("fccode", privateKey, certificateChain).build()
        
        // Configure ApkSigner
        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(24) // Matches the runner module minSdk
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            
        // Run signature signing
        apkSigner.sign()
        Log.d(TAG, "APK signed successfully with v1, v2, and v3 schemes!")
    }

    /**
     * Utility to read all bytes of a ZIP entry.
     */
    private fun readAllBytes(inputStream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len = inputStream.read(buffer)
        while (len > 0) {
            output.write(buffer, 0, len)
            len = inputStream.read(buffer)
        }
        return output.toByteArray()
    }

    /**
     * Simple byte array search pattern matching (KMP/Naive search).
     */
    private fun findBytePattern(src: ByteArray, pattern: ByteArray): Int {
        var i = 0
        while (i <= src.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (src[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                return i
            }
            i++
        }
        return -1
    }
}
