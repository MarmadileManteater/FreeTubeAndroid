package io.freetubeapp.freetube

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import io.freetubeapp.freetube.databinding.ActivityMainBinding
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.hexToColour
import io.freetubeapp.freetube.javascript.FreeTubeJavaScriptInterface
import io.freetubeapp.freetube.javascript.dispatchEvent
import io.freetubeapp.freetube.webviews.BotGuardWebView
import io.freetubeapp.freetube.webviews.MainWebView
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

  // region Keep Alive service
  private lateinit var keepAliveService: KeepAliveService
  private lateinit var keepAliveIntent: Intent
  // endregion

  // region JS interfaces
  private lateinit var jsInterface: FreeTubeJavaScriptInterface
  // endregion

  // region Bindings
  private lateinit var binding: ActivityMainBinding
  private lateinit var webView: MainWebView
  private lateinit var bgWebView: BotGuardWebView
  private lateinit var content: View
  private var fullscreenView: View? = null
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
  private var keepScreenOn: Boolean = false
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

  // region Overridden methods

  @SuppressLint("SetJavaScriptEnabled")
  @Suppress("DEPRECATION")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
      Configuration.UI_MODE_NIGHT_NO -> {
        darkMode = false
      }
      Configuration.UI_MODE_NIGHT_YES -> {
        darkMode = true
      }
    }

    content = findViewById(android.R.id.content)
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

    // this keeps android from shutting off the app to conserve battery
    keepAliveService = KeepAliveService()
    keepAliveIntent = Intent(this, keepAliveService.javaClass)
    startService(keepAliveIntent)

    // this gets the controller for hiding and showing the system bars
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val windowInsetsController =
      WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    webView = binding.webView

    // bind the back button to the web-view history
    onBackPressedDispatcher.addCallback {
      if (isInAPrompt) {
        webView.dispatchEvent("exit-prompt")
        jsInterface.exitPromptMode()
      } else {
        if (webView.canGoBack()) {
          webView.goBack()
        } else {
          this@MainActivity.moveTaskToBack(true)
        }
      }
    }

    bgWebView = binding.botGuardWebView

    jsInterface = FreeTubeJavaScriptInterface(
      this,
      webView,
      bgWebView,
      threadPoolExecutor,
      contentResolver,
      {
          intent: Intent ->
            launchIntent(intent)
      },
      {
          intent,
          intentClass
        ->
        intent.setClass(this, intentClass)
      },
      {
          uri: Uri,
          modeFlags: Int ->
            revokeUriPermission(uri, modeFlags)
      },
      {
          uri: Uri
          ->
            DocumentFile.fromTreeUri(this, uri)
      },
      {
        navigationHex: String,
        statusHex: String,
        navigationDarkMode: Boolean,
        statusDarkMode: Boolean
        ->
          themeSystemUi(navigationHex, statusHex, navigationDarkMode, statusDarkMode)
      },
      {
        getLogs()
      },
      {
        restart()
      },
      {
        hideSplashScreen()
      },
      {
        enableKeepScreenOn()
      },
      {
        disableKeepScreenOn()
      }
    )
    webView.addJavascriptInterface(jsInterface, "Android")
    webView.webChromeClient = object: WebChromeClient() {

      override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val messageData = JSONObject()
        messageData.put("content", consoleMessage.message())
        messageData.put("level", consoleMessage.messageLevel())
        messageData.put("timestamp", System.currentTimeMillis())
        messageData.put("id", UUID.randomUUID())
        messageData.put("key", "${messageData["id"]}-${messageData["timestamp"]}")
        messageData.put("sourceId", consoleMessage.sourceId())
        messageData.put("lineNumber", consoleMessage.lineNumber())
        consoleMessages.add(messageData)
        webView.dispatchEvent("console-message", "data", messageData)
        return super.onConsoleMessage(consoleMessage);
      }

      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        fullscreenView = view!!
        view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        this@MainActivity.binding.root.addView(view)
        webView.visibility = View.GONE
        this@MainActivity.binding.root.fitsSystemWindows = false
        webView.dispatchEvent("start-fullscreen")
      }

      override fun onHideCustomView() {
        webView.visibility = View.VISIBLE
        this@MainActivity.binding.root.removeView(fullscreenView)
        fullscreenView = null
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        this@MainActivity.binding.root.fitsSystemWindows = true
        webView.dispatchEvent("end-fullscreen")
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
        uri!!.host!! == "www.youtube.com" || uri!!.host!! == "youtube.com" || uri!!.host!! == "m.youtube.com" || uri!!.host!! == "youtu.be"
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
    stopService(keepAliveIntent)
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

  private fun launchIntent(intent: Intent): Promise<ActivityResult?, Exception> {
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

  private fun themeSystemUi(navigationHex: String, statusHex: String, navigationDarkMode: Boolean  = true,  statusDarkMode: Boolean = true) {
    runOnUiThread {
      val windowInsetsController =
        WindowCompat.getInsetsController(window, window.decorView)
      windowInsetsController.isAppearanceLightNavigationBars = !navigationDarkMode
      windowInsetsController.isAppearanceLightStatusBars = !statusDarkMode
      window.navigationBarColor = navigationHex.hexToColour()
      window.statusBarColor = statusHex.hexToColour()
    }
  }

  private fun getLogs(): String {
    var logs = "["
    for (message in consoleMessages) {
      logs += "${message},"
    }
    // get rid of trailing comma
    logs = logs.substring(0, logs.length - 1)
    logs += "]"
    return logs
  }

  private fun restart() {
    finish()
    startActivity(
      Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClass(this, MainActivity::class.java)
    )
  }

  private fun hideSplashScreen() {
    showSplashScreen = false
  }

  private fun enableKeepScreenOn() {
    if (!keepScreenOn) {
      keepScreenOn = true
      runOnUiThread {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  private fun disableKeepScreenOn() {
    if (keepScreenOn) {
      keepScreenOn = false
      runOnUiThread {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }
}
