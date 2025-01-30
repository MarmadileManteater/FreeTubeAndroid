package io.freetubeapp.freetube.webviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet

@SuppressLint("SetJavaScriptEnabled")
class MainWebView @JvmOverloads constructor(
  givenContext: Context, attrs: AttributeSet? = null
) :
  BackgroundPlayWebView(givenContext, attrs) {
  init {
    setBackgroundColor(Color.TRANSPARENT)
    settings.javaScriptEnabled = true

    // this is the 🥃 special sauce that makes local api streaming a possibility
    settings.allowUniversalAccessFromFileURLs = true
    settings.allowFileAccessFromFileURLs = true
    // allow playlist ▶auto-play in background
    settings.mediaPlaybackRequiresUserGesture = false
  }
}
