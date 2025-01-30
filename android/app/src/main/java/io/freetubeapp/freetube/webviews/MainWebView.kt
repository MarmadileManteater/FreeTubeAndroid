package io.freetubeapp.freetube.webviews

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.freetubeapp.freetube.javascript.dispatchEvent

class MainWebView @JvmOverloads constructor(
  givenContext: Context, attrs: AttributeSet? = null
) :
  BackgroundPlayWebView(givenContext, attrs) {
  init {
    setBackgroundColor(Color.TRANSPARENT)

    @SuppressLint("SetJavaScriptEnabled")
    settings.javaScriptEnabled = true

    // this is the 🥃 special sauce that makes local api streaming a possibility
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = true
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = true
    // allow playlist ▶auto-play in background
    settings.mediaPlaybackRequiresUserGesture = false
    webViewClient = object: WebViewClient() {

      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        // TODO refactor this to work for video streaming
        /*
        // LEFTOVER iOS WORKAROUND CODE
        if (request!!.requestHeaders.containsKey("x-user-agent")) {
          with (URL(request!!.url.toString()).openConnection() as HttpURLConnection) {
            requestMethod = request.method
            val isClient5 = request.requestHeaders.containsKey("x-youtube-client-name") && request.requestHeaders["x-youtube-client-name"] == "5"
            // map headers
            for (header in request!!.requestHeaders) {
              fun getReal(key: String, value: String): Array<String>? {
                if (key == "x-user-agent") {
                  return arrayOf("User-Agent", value)
                }
                if (key == "User-Agent") {
                  return null
                }
                if (key == "x-fta-request-id") {
                  return null
                }
                if (isClient5) {
                  if (key == "referrer") {
                    return null
                  }
                  if (key == "origin") {
                    return null
                  }
                  if (key == "Sec-Fetch-Site") {
                    return null
                  }
                  if (key == "Sec-Fetch-Mode") {
                    return null
                  }
                  if (key == "Sec-Fetch-Dest") {
                    return null
                  }
                  if (key == "sec-ch-ua") {
                    return null
                  }
                  if (key == "sec-ch-ua-mobile") {
                    return null
                  }
                  if (key == "sec-ch-ua-platform") {
                    return null
                  }
                }
                return arrayOf(key, value)
              }
              val real = getReal(header.key, header.value)
              if (real !== null) {
                setRequestProperty(real[0], real[1])
              }
            }
            if (request.requestHeaders.containsKey("x-fta-request-id")) {
              if (pendingRequestBodies.containsKey(request.requestHeaders["x-fta-request-id"])) {
                val body = pendingRequestBodies[request.requestHeaders["x-fta-request-id"]]
                pendingRequestBodies.remove(request.requestHeaders["x-fta-request-id"])
                outputStream.write(body!!.toByteArray())
              }
            }
            // 🧝‍♀️ magic
            return WebResourceResponse(this.contentType, this.contentEncoding, inputStream!!)
          }
        }
        */
        return super.shouldInterceptRequest(view, request)
      }
      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request!!.url!!.scheme == "file") {
          // don't send file url requests to a web browser (it will crash the app)
          return true
        }
        val regex = """^https?:\/\/((www\.)?youtube\.com(\/embed)?|youtu\.be)\/.*$"""

        if (Regex(regex).containsMatchIn(request!!.url!!.toString())) {
          dispatchEvent("youtube-link", "link", request!!.url!!.toString())
          return true
        }
        // send all requests to a real web browser
        val intent = Intent(Intent.ACTION_VIEW, request!!.url)
        context.startActivity(intent)
        return true
      }
    }

  }
}
