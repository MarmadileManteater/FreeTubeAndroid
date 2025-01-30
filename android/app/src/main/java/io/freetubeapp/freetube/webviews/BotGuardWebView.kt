package io.freetubeapp.freetube.webviews

import android.content.Context
import android.util.AttributeSet
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.readText
import io.freetubeapp.freetube.javascript.BotGuardJavascriptInterface


class BotGuardWebView @JvmOverloads constructor(
  givenContext: Context, attrs: AttributeSet? = null
) :
// no need to communicate window visibility to botguard
  BackgroundPlayWebView(givenContext, attrs) {
  private val context: MainActivity = givenContext as MainActivity
  private val jsInterface: BotGuardJavascriptInterface
  init {
    @Suppress("SetJavaScriptEnabled")
    settings.javaScriptEnabled = true

    jsInterface = BotGuardJavascriptInterface(context)
    addJavascriptInterface(jsInterface, "Android")
  }

  fun generatePOTokenFromVisitorData(visitorData: String): Promise<String, Exception> {
    return Promise(context.threadPoolExecutor, {
        resolve,
        reject ->
      val script = context.assets.readText("botGuardScript.js")
      try {
        val functionName = script.split("export{")[1].split(" as default};")[0]
        val exportSection = "export{${functionName} as default};"
        jsInterface.onReturnToken {
          run {
            context.runOnUiThread {
              resolve(it)
              loadUrl("about:blank")
            }
          }
        }
        val bakedScript =
          script.replace(exportSection, "; ${functionName}(\"${visitorData}\").then((TOKEN_RESULT) => { console.log(`Your potoken is \${TOKEN_RESULT}`) ; Android.returnToken(TOKEN_RESULT) })")
        context.runOnUiThread {
          loadDataWithBaseURL(
            "https://www.youtube.com",
            "<script>${bakedScript}</script>",
            "text/html",
            "utf-8",
            null
          )
        }
      } catch (exception: Exception) {
        reject(exception)
      }
    })
  }
}
