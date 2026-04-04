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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.freetubeapp.freetube.databinding.ActivityMainBinding
import io.freetubeapp.freetube.helpers.Promise
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

  private lateinit var keepAlive: Intent

  // region JS interfaces
  private lateinit var jsInterface: FreeTubeJavaScriptInterface
  // endregion

  // region Bindings
  private lateinit var binding: ActivityMainBinding
  lateinit var webView: FreeTubeWebView
  // endregion

  // region Callbacks
  private lateinit var activityResultListeners: MutableList<(ActivityResult?) -> Unit>
  private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
  // endregion

  // region State Information
  var consoleMessages: MutableList<JSONObject> = mutableListOf()
  var showSplashScreen: Boolean = true
  var darkMode: Boolean = false
  var paused: Boolean = false
  var isInAPrompt: Boolean = false
  // endregion

  // region Thread Pool Executor
  /*
   * Gets the number of available cores
   * (not always the same as the maximum number of cores)
   */
  private val numberOfCores = Runtime.getRuntime().availableProcessors()
  // Instantiates the queue of Runnables as a LinkedBlockingQueue
  private val workQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()
  // Sets the amount of time an idle thread waits before terminating
  private val keepAliveTime = 1
  // Sets the Time Unit to seconds
  private val keepAliveTimeUnit: TimeUnit = TimeUnit.SECONDS
  // Creates a thread pool manager
  var threadPoolExecutor = ThreadPoolExecutor(
    numberOfCores,  // Initial pool size
    numberOfCores,  // Max pool size
    keepAliveTime.toLong(),
    keepAliveTimeUnit,
    workQueue
  )
  // endregion

  private val onConsoleMessage = { messageData: JSONObject ->
    consoleMessages.add(messageData)
    webView.dispatchEvent("console-message", "data", messageData)
  }

  // region Overridden methods

  @SuppressLint("SetJavaScriptEnabled")
  @Suppress("DEPRECATION")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // this keeps android from shutting off the app to conserve battery
    keepAlive = Intent(this, KeepAliveService::class.java)
    startService(keepAlive)

    // allow fullscreen shaka player to use whole window width
    window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
      Configuration.UI_MODE_NIGHT_NO -> {
        darkMode = false
      }
      Configuration.UI_MODE_NIGHT_YES -> {
        darkMode = true
      }
    }

    val content: View = findViewById(android.R.id.content)
    content.viewTreeObserver.addOnPreDrawListener(
      object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          // Check whether the initial data is ready.
          return if (!showSplashScreen) {
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

    activityResultListeners = mutableListOf()

    activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      for (listener in activityResultListeners) {
        listener(it)
      }
      // clear the listeners
      activityResultListeners = mutableListOf()
    }

    MediaControlsReceiver.notifyMediaSessionListeners = {
        action ->
      webView.dispatchEvent("media-$action")
    }

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    webView = binding.webView
    jsInterface = webView.jsInterface
    webView.onConsoleMessage = onConsoleMessage

    // bind the back button to the web-view history
    onBackPressedDispatcher.addCallback {
      if (isInAPrompt) {
        webView.dispatchEvent("exit-prompt")
        jsInterface.exitPromptMode()
      } else {
        if (webView.canGoBack()) {
          webView.goBack()
        } else {
          moveTaskToBack(true)
        }
      }
    }

    if (intent!!.data !== null) {
      val url = intent!!.data.toString()
      val host = intent!!.data!!.host.toString()
      val intentPath = if (host != "youtube.com" && host != "youtu.be" && host != "m.youtube.com" && host != "www.youtube.com") {
        url.replace("${intent!!.data!!.host}", "youtube.com")
      } else {
        url
      }
      val intentEncoded = URLEncoder.encode(intentPath)
      webView.loadUrl("file:///android_asset/index.html?intent=${intentEncoded}")
    } else {
      webView.loadUrl("file:///android_asset/index.html")
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    when (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
      Configuration.UI_MODE_NIGHT_NO -> {
        darkMode = false
        webView.dispatchEvent("enabled-light-mode")
      }
      Configuration.UI_MODE_NIGHT_YES -> {
        darkMode = true
        webView.dispatchEvent("enabled-dark-mode")
      }
    }
  }

  /**
   * handles new intents which involve deep links (aka supported links)
   */
  @SuppressLint("MissingSuperCall")
  override fun onNewIntent(intent: Intent?) {
    if (intent!!.data !== null) {
      val uri = intent!!.data
      val isYT =
        uri!!.host!! == "www.youtube.com" || uri.host!! == "youtube.com" || uri.host!! == "m.youtube.com" || uri.host!! == "youtu.be"
      val url = if (!isYT) {
        uri.toString().replace(uri.host.toString(), "www.youtube.com")
      } else {
        uri
      }
      webView.dispatchEvent("youtube-link", "link", url.toString())
    }
  }

  override fun onPause() {
    super.onPause()
    paused = true
    webView.dispatchEvent("app-pause")
  }

  override fun onResume() {
    super.onResume()
    paused = false
    webView.dispatchEvent("app-resume")
  }

  override fun onDestroy() {
    // stop the keep alive service
    stopService(keepAlive)
    // cancel media notification (if there is one)
    jsInterface.cancelMediaNotification()
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
    return Promise(threadPoolExecutor, {
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
