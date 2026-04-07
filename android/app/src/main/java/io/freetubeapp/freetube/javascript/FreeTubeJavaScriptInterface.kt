package io.freetubeapp.freetube.javascript

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState.STATE_PAUSED
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import androidx.documentfile.provider.DocumentFile
import io.freetubeapp.freetube.helpers.AmbiguousFileUri
import io.freetubeapp.freetube.helpers.ApplicationMethods
import io.freetubeapp.freetube.helpers.ApplicationState
import io.freetubeapp.freetube.helpers.MediaSessionFacade
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.WriteMode
import io.freetubeapp.freetube.helpers.readBytes
import io.freetubeapp.freetube.helpers.readText
import io.freetubeapp.freetube.helpers.writeBytes
import io.freetubeapp.freetube.helpers.writeText
import io.freetubeapp.freetube.webviews.FreeTubeWebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


class FreeTubeJavaScriptInterface(
  private val context: Context,
  private val webView: FreeTubeWebView,
  private val state: ApplicationState,
  private val methods: ApplicationMethods
) {
  private val coroutineScope = CoroutineScope(Dispatchers.Main)
  private val mediaSession: MediaSessionFacade = MediaSessionFacade(
    context,
    CHANNEL_ID,
    { event ->
      webView.dispatchEvent(event)
    },
    { position ->
      webView.dispatchEvent("media-seek", "position", position)
    }
  )
  val jsCommunicator: AsyncJSCommunicator = AsyncJSCommunicator(webView)

  companion object {
    private const val DATA_DIRECTORY = "data://"
    private const val CHANNEL_ID = "media_controls"
  }

  // region Media Notifications
  /**
   * creates (or updates) a media session
   * @param title the track name / video title
   * @param artist the author / channel name
   * @param duration the duration in milliseconds of the video
   * @param thumbnail a URL to the thumbnail for the video
   */
  @JavascriptInterface
  fun createMediaSession(title: String, artist: String, duration: Long = 0, thumbnail: String? = null) {
    mediaSession
      .setMetadata(title, artist, duration, thumbnail)
      .setState(STATE_PAUSED, 0)
      .push()
  }

  /**
   * updates the state of the active media session
   * @param state the state; should be an Int (as a string because the java bridge)
   * @param position the position; should be a Long (as a string because the java bridge)
   */
  @JavascriptInterface
  fun updateMediaSessionState(state: String?, position: String? = null) {
    mediaSession
      .setState(
        state?.toInt(),
        position?.toLong()
      )
  }

  /**
   * updates the metadata of the active media session
   * @param trackName the video title
   * @param artist the channel name
   * @param duration the length of the video in milliseconds
   * @param art the URL to the video thumbnail
   */
  @JavascriptInterface
  fun updateMediaSessionData(trackName: String, artist: String, duration: Long, art: String? = null) {
    mediaSession
      .setMetadata(
        trackName,
        artist,
        duration,
        art
      )
  }

  /**
   * cancels the active media notification
   */
  @JavascriptInterface
  fun cancelMediaNotification() {
    mediaSession.cancel()
  }

  // endregion

  // region File Helpers
  /**
   * @param directory a shortened directory uri
   * @return a full directory uri
   */
  @JavascriptInterface
  fun getDirectory(directory: String): String {
    val path =  if (directory == DATA_DIRECTORY) {
      // this is the directory cordova gave us access to before
      context.getExternalFilesDir(null)!!.parent
    } else {
      directory
    }
    return path
  }

  fun getFileNameFromUri(uri: String): String {
    var result: String? = null
    val cursor = context.contentResolver.query(Uri.parse(uri),  null, null, null, null)
    try {
      if (cursor != null && cursor.moveToFirst()) {
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index != -1) {
          result = cursor.getString(index)
        }
      }
    } finally {
      cursor!!.close()
    }

    if (result == null) {
      result = uri.split(Regex("(/)|(%2F)")).last()
    }

    return result
  }

  @JavascriptInterface
  fun revokePermissionForTree(treeUri: String) {
    context.revokeUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  }

  @JavascriptInterface
  fun listFilesInTree(tree: String): String {
    val directory = DocumentFile.fromTreeUri(context, Uri.parse(tree))
    val files = directory!!.listFiles().joinToString(",") { file ->
      "{ \"uri\": \"${file.uri}\", \"fileName\": \"${file.name}\", \"isFile\": ${file.isFile}, \"isDirectory\": ${file.isDirectory} }"
    }
    return "[$files]"
  }

  @JavascriptInterface
  fun createFileInTree(tree: String, fileName: String): String {
    val directory = DocumentFile.fromTreeUri(context, Uri.parse(tree))
    return directory!!.createFile("*/*", fileName)!!.uri.toString()
  }
  // endregion

  // region IO
  @JavascriptInterface
  fun listFilesInDataDir(): String {
    return "[${File(getDirectory(DATA_DIRECTORY)).listFiles()?.map {
      file ->
      "{ \"uri\": \"${DATA_DIRECTORY}${file.name}\", \"fileName\": \"${file.name}\", \"isFile\": ${file.isFile}, \"isDirectory\": ${file.isDirectory} }"
    }!!.joinToString(",")}]"
  }

  /**
   * reads a file from storage
   */
  @JavascriptInterface
  fun readFile(uri: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject ->
      AmbiguousFileUri(uri)
        .ifContentUri {
          uri ->
            resolve(
              context.contentResolver
                .readBytes(uri)
                .toString(Charset.forName("utf-8"))
            )
        }
        .ifDataUri {
          fileName ->
          val path = getDirectory(DATA_DIRECTORY)
          val file = File(path, fileName)
          if (file.exists()) {
            resolve(File(path, fileName).readText())
          } else {
            resolve("")
          }
        }
        .catch {
          ex ->
            reject(ex.stackTraceToString())
        }
    }).addJsCommunicator(jsCommunicator)
  }

  /**
   * writes a file to storage
   */
  @OptIn(ExperimentalEncodingApi::class)
  @JavascriptInterface
  fun writeFile(uri: String, content: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject ->
        AmbiguousFileUri(uri)
          .ifContentUri {
            uri ->
              val bytes = if (content.startsWith("data:")) {
                Base64.decode(content.split("base64,")[1])
              } else {
                content.toByteArray()
              }
              context.contentResolver.writeBytes(
                uri,
                bytes
              )
              resolve("")
          }
          .ifDataUri {
            fileName ->
              val path = getDirectory(DATA_DIRECTORY)
              File(path, fileName).writeText(content)
              resolve("")
          }
          .catch {
            ex ->
              reject(ex.stackTraceToString())
          }
    }).addJsCommunicator(jsCommunicator)
  }

  @OptIn(ExperimentalEncodingApi::class)
  @JavascriptInterface
  fun appendFile(uri: String, content: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject ->
        AmbiguousFileUri(uri)
          .ifContentUri {
              uri ->
                val bytes = if (content.startsWith("data:")) {
                  Base64.decode(content.split("base64,")[1])
                } else {
                  content.toByteArray()
                }
                context.contentResolver.writeBytes(
                  uri,
                  bytes,
                  WriteMode.Append
                )
                resolve("")
          }
          .ifDataUri {
              fileName ->
                val path = getDirectory(DATA_DIRECTORY)
                File(path, fileName).writeText(content, WriteMode.Append)
                resolve("")
          }
          .catch {
              ex ->
                reject(ex.stackTraceToString())
          }
    }).addJsCommunicator(jsCommunicator)
  }
  // endregion

  // region Dialogs
  /**
   * requests a save dialog, resolves a js promise when done, resolves with `USER_CANCELED` if the user cancels
   * @return a js promise id
   */
  @JavascriptInterface
  fun requestSaveDialog(fileName: String, fileType: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject
      ->
      methods.launchIntent(
        Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(fileType)
        .putExtra(Intent.EXTRA_TITLE, fileName)
      ).then {
          if (it!!.resultCode == Activity.RESULT_CANCELED) {
            resolve("USER_CANCELED")
          }
          try {
            val payload = JSONObject()
            payload.put("uri", it.data!!.data)
            resolve(payload)
          } catch (ex: Exception) {
            reject(ex.toString())
          }
        }
    }).addJsCommunicator(jsCommunicator)
  }

  @JavascriptInterface
  fun requestOpenDialog(fileTypes: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject ->
        methods.launchIntent(
          Intent(Intent.ACTION_GET_CONTENT)
          .setType("*/*")
          .putExtra(Intent.EXTRA_MIME_TYPES, fileTypes.split(",").toTypedArray())
        ).then {
            if (it!!.resultCode == Activity.RESULT_CANCELED) {
              resolve("USER_CANCELED")
            }
            try {
              val uri = it.data!!.data
              val mimeType = context.contentResolver.getType(uri!!)
              val fileName = getFileNameFromUri(uri.toString())
              val payload = JSONObject()
              payload.put("uri", uri)
              payload.put("type", mimeType)
              payload.put("fileName", fileName)
              resolve(payload)
            } catch (ex: Exception) {
              reject(ex.toString())
            }
          }
    }).addJsCommunicator(jsCommunicator)
  }

  @JavascriptInterface
  fun requestDirectoryAccessDialog(): String {
    return Promise(coroutineScope, {
      resolve,
      reject ->
      methods.launchIntent(
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
      ).then {
          if (it!!.resultCode == Activity.RESULT_CANCELED) {
            resolve("USER_CANCELED")
          }
          try {
            val uri = it.data!!.data!!
            context.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            resolve(uri)
          } catch (ex: Exception) {
            reject(ex.toString())
          }
        }
    }).addJsCommunicator(jsCommunicator)
  }

  // endregion

  // region System

  @JavascriptInterface
  fun openExternalLink(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
  }

  @JavascriptInterface
  fun getLogs(): String {
    var logs = "["
    for (message in state.consoleMessages) {
      logs += "${message},"
    }
    // get rid of trailing comma
    if (logs.length > 1) {
      logs = logs.substring(0, logs.length - 1)
    }
    logs += "]"
    return logs
  }

  @JavascriptInterface
  fun restart() {
    methods.restart()
  }

  /**
   * hides the splashscreen
   */
  @JavascriptInterface
  fun hideSplashScreen() {
    state.showSplashScreen = false
  }

  @JavascriptInterface
  fun enableKeepScreenOn() {
    methods.setKeepScreenOn(true)

  }

  @JavascriptInterface
  fun disableKeepScreenOn() {
    methods.setKeepScreenOn(false)
  }

  /**
   * used on the JS side for async js communication
   */
  @JavascriptInterface
  fun getSyncMessage(promise: String): String {
    return jsCommunicator.getSyncMessage(promise)
  }

  /**
   *
   */
  @JavascriptInterface
  fun themeSystemUi(navigationHex: String, statusHex: String, navigationDarkMode: Boolean  = true,  statusDarkMode: Boolean = true) {
    methods.themeSystemUi(navigationHex, statusHex, navigationDarkMode, statusDarkMode)
  }

  @JavascriptInterface
  fun getSystemTheme(): String {
    return if (state.darkMode) {
      "dark"
    } else {
      "light"
    }
  }

  @JavascriptInterface
  fun isAppPaused(): Boolean {
    return state.paused
  }

  @JavascriptInterface
  fun enterPromptMode() {
    webView.isVerticalScrollBarEnabled = false
    state.isInAPrompt = true
  }

  @JavascriptInterface
  fun exitPromptMode() {
    webView.isVerticalScrollBarEnabled = true
    state.isInAPrompt = false
  }

  @JavascriptInterface
  fun setScale(scale: Int) {
    webView.setScale(scale / 100.0, context)
  }

  // endregion

  // region Data Extraction

  private fun getBotGuardScript(videoId: String, sessionContext: String, includeDebugMessage: Boolean = true): String {
    val script = context.assets.readText("botGuardScript.js")
    val functionName = script.split("export{")[1].split(" as default};")[0]
    val exportSection = "export{${functionName} as default};"
    val then = if (includeDebugMessage) {
      "(TOKEN_RESULT) => { console.log(`Your potoken is \${TOKEN_RESULT}`); Android.returnToken(TOKEN_RESULT) }"
    } else {
      "(TOKEN_RESULT) => { Android.returnToken(TOKEN_RESULT) }"
    }
    val bakedScript =
      script.replace(exportSection, "; ${functionName}(\"$videoId\", $sessionContext).then($then)")
    return bakedScript
  }

  @JavascriptInterface
  fun generatePOToken(videoId: String, sessionContext: String): String {
    return Promise(coroutineScope, {
      resolve,
      reject
      ->
        webView.post {
          try {
            val bgScript = getBotGuardScript(videoId, sessionContext)
            val bgWv = webView.generateBgWebview()
            bgWv.jsInterface.onReturnToken {
              run {
                webView.post {
                  resolve(it)
                  bgWv.destroy()
                }
              }
            }
            webView.post {
              bgWv.loadDataWithBaseURL(
                "https://www.youtube.com/",
                "<script>\n" +
                  "window.ofetch = window.fetch\n" +
                  "window.fetch = async (url, data) => {\n" +
                  "  if (url.startsWith('https://www.google.com/')) {\n" +
                  "    return new Promise((resolve, _) => {" +
                  "    const script = document.createElement('script')\n" +
                  "    script.src = url\n" +
                  "    script.async = true\n" +
                  "    document.body.appendChild(script)\n" +
                  "     script.addEventListener('load', () => {\n" +
                  "       resolve({ text: () => '() => {}' })\n" +
                  "     })\n" +
                  "    })\n" +
                  "  }\n" +
                  "  const id = crypto.randomUUID()\n" +
                  "  if (data && 'body' in data) {" +
                  "    Android.queueBody(id, data.body)\n" +
                  "    data.headers['x-fta-request-id'] = id\n" +
                  "  }" +
                  "  return await window.ofetch(url, data)\n" +
                  "}</script><script>${bgScript}</script>",
                "text/html",
                "utf-8",
                null
              )
            }
          } catch (exception: Exception) {
            reject(exception.message!!)
          }
        }
    }).addJsCommunicator(jsCommunicator)
  }

  @JavascriptInterface
  fun runDecipherScript(id: String, code: String): String {
    webView.post {
      webView.generateSigWebview()
        .onLoad = {
          // pass data to other webview
          jsInterface.jsCommunicator.resolve(id, code)
          // dispatch event to read data
          dispatchEvent("message", "id", id)
          // TODO figure out when to clean up web views
          postDelayed({
            destroy()
          }, 10000)
      }
    }
    return id
  }

  // endregion
}
