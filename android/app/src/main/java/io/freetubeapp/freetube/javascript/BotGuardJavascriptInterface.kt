package io.freetubeapp.freetube.javascript

import android.webkit.JavascriptInterface

class BotGuardJavascriptInterface {
  private var poToken: String? = null
  private var tokenListeners: MutableList<(String) -> Unit> = mutableListOf()
  val pendingRequestBodies: MutableMap<String, String> = mutableMapOf()

  @JavascriptInterface
  fun queueBody(id: String, body: String) {
    pendingRequestBodies[id] = body
  }

  @JavascriptInterface
  fun returnToken(token: String) {
    notify(token)
    poToken = token
  }

  fun notify(token: String) {
     tokenListeners.forEach {
       it(token)
     }
    tokenListeners = mutableListOf()
  }

  fun onReturnToken(callback: (String) -> Unit) {
    val poToken = poToken
    if (poToken != null) {
      callback(poToken)
    } else {
      tokenListeners.add(callback)
    }
  }
}
