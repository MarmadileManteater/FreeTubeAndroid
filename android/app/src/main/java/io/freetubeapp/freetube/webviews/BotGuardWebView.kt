package io.freetubeapp.freetube.webviews

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.javascript.BotGuardJavascriptInterface
import io.freetubeapp.freetube.javascript.consoleLog
import io.freetubeapp.freetube.javascript.dispatchEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class BotGuardWebView @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null
) :
  // no need to communicate window visibility to botguard
  BackgroundPlayWebView(context, attrs) {
    val jsInterface = BotGuardJavascriptInterface()
    init {
      @SuppressLint("SetJavaScriptEnabled")
      settings.javaScriptEnabled = true
      @Suppress("DEPRECATION")
      settings.allowUniversalAccessFromFileURLs = true
      addJavascriptInterface(jsInterface, "Android")
      webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?
        ): WebResourceResponse? {
          if (request!!.url.toString().startsWith("data:text/html") || request.url.toString().startsWith("https://www.youtube.com/api/jnn/v1/GenerateIT")) {
            return super.shouldInterceptRequest(view, request)
          }
          with(URL(request.url.toString()).openConnection() as HttpURLConnection) {
            requestMethod = request.method
            // map headers
            for (header in request.requestHeaders) {
              setRequestProperty(header.key, header.value)
            }

            if (url.toString().startsWith("https://www.youtube.com/youtubei/")) {
              setRequestProperty("Referer", "https://www.youtube.com/")
              setRequestProperty("Origin", "https://www.youtube.com")
              setRequestProperty("Sec-Fetch-Site", "same-origin")
              setRequestProperty("Sec-Fetch-Mode", "same-origin")
              setRequestProperty("X-Youtube-Bootstrap-Logged-In", "false")
            }
            if (url.toString().startsWith("https://www.google.com/js/")) {
              setRequestProperty("referer", "https://www.google.com/")
              setRequestProperty("origin", "https://www.google.com")
              setRequestProperty("Sec-Fetch-Dest", "script")
              setRequestProperty("Sec-Fetch-Site", "cross-site")
              setRequestProperty("Accept-Language", "*")
            }
            if (request.requestHeaders.containsKey("x-fta-request-id")) {
              if (jsInterface.pendingRequestBodies.containsKey(request.requestHeaders["x-fta-request-id"])) {
                val body = jsInterface.pendingRequestBodies[request.requestHeaders["x-fta-request-id"]]
                jsInterface.pendingRequestBodies.remove(request.requestHeaders["x-fta-request-id"])
                outputStream.write(body!!.toByteArray())
              }
            }
            try {
              // 🧝‍♀️ magic
              return WebResourceResponse(this.contentType, this.contentEncoding, inputStream!!)
            } catch (ex: Exception) {
              consoleLog(ex.message!!, "error")
              return super.shouldInterceptRequest(view, request)
            }
          }
        }
      }
    }


  constructor(context: Context, onConsoleMessage: (JSONObject) -> Unit = {}): this(context, null) {
    webChromeClient = ConsoleLogChromeClient(onConsoleMessage)
  }
}
