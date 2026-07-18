package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FC Code Studio", appName)
  }

  @Test
  fun `test repackaging on robolectric`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempApkFile = java.io.File(context.cacheDir, "test_out.apk")
    if (tempApkFile.exists()) {
      tempApkFile.delete()
    }
    val success = com.example.util.ApkRepackager.buildPatchedApk(
      context = context,
      appName = "My Test App",
      appVersion = "1.0.0",
      companyName = "My Company",
      selectedOrientation = "Portrait",
      iconShape = "Squircle",
      enableSplash = true,
      splashColor = 0xFF1E1E1E.toInt(),
      customIconBase64 = null,
      iconColor = 0xFF00C853.toInt(),
      iconSymbol = "code",
      htmlCode = "<h1>Hello</h1>",
      cssCode = "",
      jsCode = "",
      outputApkFile = tempApkFile
    )
    println("--- REPACKAGING TEST RESULT: $success ---")
    assertTrue(success)
    assertTrue(tempApkFile.exists())
  }
}
