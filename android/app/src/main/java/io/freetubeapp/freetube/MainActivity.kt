package io.freetubeapp.freetube

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.addCallback
import io.freetubeapp.freetube.activities.FreeTubeActivity
import io.freetubeapp.freetube.databinding.ActivityMainBinding
import io.freetubeapp.freetube.helpers.isDarkMode
import io.freetubeapp.freetube.helpers.toYtUrl
import io.freetubeapp.freetube.javascript.dispatchEvent
import io.freetubeapp.freetube.webviews.FreeTubeWebView
import java.net.URLEncoder
import java.nio.charset.Charset

class MainActivity: FreeTubeActivity() {
  private val keepGoingService: Intent
    get() {
      return Intent(this, KeepAliveService::class.java)
    }
  private lateinit var webView: FreeTubeWebView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

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

    webView = FreeTubeWebView(this)

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

  private fun urlEncode(url: String): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      URLEncoder.encode(url, Charset.defaultCharset())
    } else {
      @Suppress("DEPRECATION")
      URLEncoder.encode(url)
    }
  }
}
