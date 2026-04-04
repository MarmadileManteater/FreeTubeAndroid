package io.freetubeapp.freetube.webviews

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.javascript.FreeTubeJavaScriptInterface
import io.freetubeapp.freetube.javascript.dispatchEvent
import org.json.JSONObject

class FreeTubeWebView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null
) : BackgroundPlayWebView(context, attrs) {
  // TODO fix the coupling here with context as MainActivity
  val jsInterface = FreeTubeJavaScriptInterface(context as MainActivity, this)

  var onConsoleMessage: (JSONObject) -> Unit = {}

  init {
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
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request!!.url!!.scheme == "file") {
          // don't send file url requests to a web browser (it will crash the app)
          return true
        }
        val regex = """^https?:\/\/((www\.)?youtube\.com(\/embed)?|youtu\.be)\/.*$"""

        if (Regex(regex).containsMatchIn(request.url!!.toString())) {
          dispatchEvent("youtube-link", "link", request.url!!.toString())
          return true
        }
        // send all requests to a real web browser
        context.startActivity(
          Intent(Intent.ACTION_VIEW, request.url)
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

          // hide system ui
          viewGroup.fitsSystemWindows = false
          windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
          windowInsetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

          viewGroup.addView(view)
          fullscreenView = view
          dispatchEvent("start-fullscreen")
        }
      }

      override fun onHideCustomView() {
        val viewGroup = (parent as ViewGroup)

        // show system ui
        viewGroup.fitsSystemWindows = true
        windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())

        viewGroup.removeView(fullscreenView)
        dispatchEvent("end-fullscreen")
      }
    }
  }
}
