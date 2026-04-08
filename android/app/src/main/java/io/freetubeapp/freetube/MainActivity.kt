package io.freetubeapp.freetube

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.freetubeapp.freetube.databinding.ActivityMainBinding
import io.freetubeapp.freetube.helpers.ApplicationMethods
import io.freetubeapp.freetube.helpers.ApplicationState
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.hexToColour
import io.freetubeapp.freetube.helpers.isDarkMode
import io.freetubeapp.freetube.helpers.toYtUrl
import io.freetubeapp.freetube.javascript.dispatchEvent
import io.freetubeapp.freetube.webviews.FreeTubeWebView
import java.net.URLEncoder
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

  private val keepGoingService: Intent
    get() {
      return Intent(this, KeepAliveService::class.java)
    }
  private val activityResultListeners: MutableList<(ActivityResult?) -> Unit> = mutableListOf()
  private val activityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    for (listener in activityResultListeners) {
      listener(it)
    }
    // clear the listeners
    activityResultListeners.removeAll { true }
  }
  private val state = ApplicationState()
  private lateinit var webView: FreeTubeWebView
  private lateinit var windowInsetsController: WindowInsetsControllerCompat

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

    // this keeps android from shutting off the app to conserve battery
    startService(keepGoingService)

    // allow fullscreen shaka player to use whole window width
    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    state.darkMode = resources.configuration.isDarkMode()

    val content: View = findViewById(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(
      object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          // Check whether the initial data is ready.
          return if (!state.showSplashScreen) {
            // The content is ready. Start drawing.
            content.viewTreeObserver.removeOnPreDrawListener(this)
            true
          } else {
            // The content isn't ready. Suspend.
            false
          }
        }
      }
    )

    MediaControlsReceiver.notifyMediaSessionListeners = { action ->
      webView.dispatchEvent("media-$action")
    }

    // bind the back button to the web-view history
    onBackPressedDispatcher.addCallback {
      if (state.isInAPrompt) {
        webView.dispatchEvent("exit-prompt")
        webView.jsInterface.exitPromptMode()
      } else {
        if (webView.canGoBack()) {
          webView.goBack()
        } else {
          moveTaskToBack(true)
        }
      }
    }

    webView = FreeTubeWebView(
      this,
      windowInsetsController,
      state,
      ApplicationMethods(
        restart = { restart() },
        launchIntent = { intent -> launchIntent(intent) },
        setKeepScreenOn = { newState -> setKeepScreenOn(newState) },
        themeSystemUi = { navigationHex, statusHex, navigationDarkMode,  statusDarkMode ->
          themeSystemUI(navigationHex, statusHex, navigationDarkMode, statusDarkMode)
        }
      )
    )

    ActivityMainBinding.inflate(layoutInflater).apply {
      setContentView(root)
      root.addView(webView)
    }

    val url = intent?.toYtUrl()
    val postfix = if (url != null) {
      "?intent=${urlEncode(url)}"
    } else {
      ""
    }
    webView.loadUrl("file:///android_asset/index.html$postfix")
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    state.darkMode = newConfig.isDarkMode()
    val colorString = if (state.darkMode) { "dark" } else { "light" }
    webView.dispatchEvent("enabled-$colorString-mode")
  }

  /**
   * handles new intents which involve deep links (aka supported links)
   */
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    val url = intent?.toYtUrl()
    if (url != null) {
      webView.dispatchEvent("youtube-link", "link", url)
    }
  }

  override fun onPause() {
    super.onPause()
    state.paused = true
    webView.dispatchEvent("app-pause")
  }

  override fun onResume() {
    super.onResume()
    state.paused = false
    webView.dispatchEvent("app-resume")
  }

  override fun onDestroy() {
    // stop the keep alive service
    stopService(keepGoingService)
    // cancel media notification (if there is one)
    webView.jsInterface.cancelMediaNotification()
    // clean up the web view
    webView.destroy()
    // call `super`
    super.onDestroy()
  }

  private fun listenForActivityResults(listener: (ActivityResult?) -> Unit) {
    activityResultListeners.add(listener)
  }

  private fun restart() {
    finish()
    startActivity(Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setClass(this,  MainActivity::class.java))
  }

  private fun launchIntent(intent: Intent): Promise<ActivityResult?, Exception> {
    return Promise { resolve, reject ->
      try {
        listenForActivityResults {
          resolve(it)
        }
        activityResultLauncher.launch(intent)
      } catch (exception: Exception) {
        reject(exception)
      }
    }
  }

  private fun setKeepScreenOn(newState: Boolean) {
    if (state.keepScreenOn != newState) {
      state.keepScreenOn = newState
      runOnUiThread {
        if (state.keepScreenOn) {
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }
    }
  }

  private fun themeSystemUI(navigationHex: String, statusHex: String, navigationDarkMode: Boolean,  statusDarkMode: Boolean) {
    runOnUiThread {
      windowInsetsController.isAppearanceLightNavigationBars = !navigationDarkMode
      windowInsetsController.isAppearanceLightStatusBars = !statusDarkMode
      window.navigationBarColor = navigationHex.hexToColour()
      window.statusBarColor = statusHex.hexToColour()

      val bitmap = createBitmap(24, 24)
      bitmap.eraseColor(navigationHex.hexToColour())
      val canvas = Canvas(bitmap)
      canvas.drawColor(navigationHex.hexToColour())
      val bitmapDrawable = bitmap.toDrawable(resources)
      window.setBackgroundDrawable(bitmapDrawable)
    }
  }

  private fun urlEncode(url: String): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      URLEncoder.encode(url, Charset.defaultCharset())
    } else {
      @Suppress("DEPRECATION")
      URLEncoder.encode(url)
    }
  }
}
