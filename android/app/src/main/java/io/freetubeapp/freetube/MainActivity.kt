package io.freetubeapp.freetube

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar.LayoutParams
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewpager.widget.ViewPager
import io.freetubeapp.freetube.databinding.ActivityMainBinding
import io.freetubeapp.freetube.helpers.ApplicationState
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.isDarkMode
import io.freetubeapp.freetube.helpers.toYtUrl
import io.freetubeapp.freetube.javascript.FreeTubeJavaScriptInterface
import io.freetubeapp.freetube.javascript.dispatchEvent
import io.freetubeapp.freetube.webviews.BackgroundPlayWebView
import io.freetubeapp.freetube.webviews.BotGuardWebView
import io.freetubeapp.freetube.webviews.ConsoleLogChromeClient
import io.freetubeapp.freetube.webviews.FreeTubeWebView
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

  private val keepGoingService: Intent
    get() {
      return Intent(this, KeepAliveService::class.java)
    }

  val state = ApplicationState()
  lateinit var webView: FreeTubeWebView

  // region Callbacks
  private val activityResultListeners: MutableList<(ActivityResult?) -> Unit> = mutableListOf()
  private val activityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    for (listener in activityResultListeners) {
      listener(it)
    }
    // clear the listeners
    activityResultListeners.removeAll{ true }
  }
  // endregion

  // region Overridden methods

  @SuppressLint("SetJavaScriptEnabled")
  @Suppress("DEPRECATION")
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

    MediaControlsReceiver.notifyMediaSessionListeners = {
        action ->
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

    webView = FreeTubeWebView(this, state)
    ActivityMainBinding.inflate(layoutInflater).apply {
      setContentView(root)
      root.addView(webView)
    }

    val url = intent?.toYtUrl()
    val postfix = if (url != null) {
      "?intent=${URLEncoder.encode(url)}"
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
  @SuppressLint("MissingSuperCall")
  override fun onNewIntent(intent: Intent?) {
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

  // endregion

  private fun listenForActivityResults(listener: (ActivityResult?) -> Unit) {
    activityResultListeners.add(listener)
  }

  fun launchIntent(intent: Intent): Promise<ActivityResult?, Exception> {
    return Promise({
        resolve,
        reject ->
      try {
        listenForActivityResults {
          resolve(it)
        }
        activityResultLauncher.launch(intent)
      } catch (exception: Exception) {
        reject(exception)
      }
    })
  }
}
