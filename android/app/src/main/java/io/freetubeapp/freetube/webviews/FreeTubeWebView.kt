package io.freetubeapp.freetube.webviews

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Insets
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import io.freetubeapp.freetube.R
import io.freetubeapp.freetube.activities.FreeTubeActivity
import io.freetubeapp.freetube.helpers.WindowInsetsControllerWrapper
import io.freetubeapp.freetube.helpers.addSystemBars
import io.freetubeapp.freetube.helpers.div
import io.freetubeapp.freetube.helpers.plus
import io.freetubeapp.freetube.javascript.FreeTubeJavaScriptInterface
import io.freetubeapp.freetube.javascript.consoleLog
import io.freetubeapp.freetube.javascript.dispatchEvent
import org.json.JSONObject

@SuppressLint("ViewConstructor")
class FreeTubeWebView (
  context: FreeTubeActivity
) : BackgroundPlayWebView(context, null) {
  private val windowInsetsControllerWrapper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowInsetsController != null) {
    WindowInsetsControllerWrapper(windowInsetsController)
  } else {
    WindowInsetsControllerWrapper(context.windowInsetsController)
  }
  val cornerRadius: Float
    get() {
      val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
      } else {
        null
      } ?: 0

      return radius / context.resources.displayMetrics.density
    }
  val insets: Insets
    get() {
      val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        rootWindowInsets.getInsets(WindowInsets.Type.displayCutout())
          .addSystemBars(rootWindowInsets.getInsets(WindowInsets.Type.systemBars()))
      } else {
        rootWindowInsets.systemWindowInsets
      }
      return insets / context.resources.displayMetrics.density
    }
  val jsInterface = FreeTubeJavaScriptInterface(context, this)

  val onConsoleMessage: (JSONObject) -> Unit = { messageData: JSONObject ->
    context.state.consoleMessages.add(messageData)
    dispatchEvent("console-message", "data", messageData)
  }

  init {
    layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
    setBackgroundColor(Color.TRANSPARENT)

    @SuppressLint("SetJavaScriptEnabled")
    settings.javaScriptEnabled = true
    // add the JavaScript interface
    addJavascriptInterface(jsInterface, "Android")

    // this is the 🥃 special sauce that makes local api streaming a possibility
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = true
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = true
    // allow playlist ▶auto-play in background
    settings.mediaPlaybackRequiresUserGesture = false

    webViewClient = object: WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        context.state.currentPage = url
        super.onPageFinished(view, url)
      }
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request?.url?.scheme == "file") {
          // don't send file url requests to a web browser (it will crash the app)
          return true
        }

        val regex = context.getString(R.string.youtube_regex)

        val urlString = request?.url?.toString()
        if (urlString != null && Regex(regex).containsMatchIn(urlString)) {
          dispatchEvent("youtube-link", "link", urlString)
          return true
        }
        // send all requests to a real web browser
        context.startActivity(
          Intent(Intent.ACTION_VIEW, request?.url)
        )
        return true
      }
    }

    var fullscreenView: View? = null
    webChromeClient = object: ConsoleLogChromeClient({ message ->
      onConsoleMessage(message)
    }) {
      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view != null) {
          val viewGroup = (parent as ViewGroup)

          windowInsetsControllerWrapper.hide(WindowInsetsCompat.Type.systemBars())
          windowInsetsControllerWrapper.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

          viewGroup.addView(view)
          fullscreenView = view
          dispatchEvent("start-fullscreen")
        }
      }

      override fun onHideCustomView() {
        val viewGroup = (parent as ViewGroup)

        windowInsetsControllerWrapper.show(WindowInsetsCompat.Type.systemBars())

        viewGroup.removeView(fullscreenView)
        dispatchEvent("end-fullscreen")
      }
    }
  }

  fun generateBgWebview(): BotGuardWebView {
    return BotGuardWebView(context, onConsoleMessage)
  }

  fun generateSigWebview(): SigWebView {
    val view = SigWebView(context, jsInterface.jsCommunicator, onConsoleMessage)
    val viewGroup = (parent as ViewGroup)
    viewGroup.addView(view)
    view.visibility = GONE
    return view
  }
}
