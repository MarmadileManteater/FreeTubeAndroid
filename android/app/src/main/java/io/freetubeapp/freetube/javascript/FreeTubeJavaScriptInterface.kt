package io.freetubeapp.freetube.javascript

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_KEY_EVENT
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.session.PlaybackState.STATE_PAUSED
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.MediaControlsReceiver
import io.freetubeapp.freetube.R
import io.freetubeapp.freetube.helpers.AmbiguousFileUri
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.WriteMode
import io.freetubeapp.freetube.helpers.hexToColour
import io.freetubeapp.freetube.helpers.readBytes
import io.freetubeapp.freetube.helpers.readText
import io.freetubeapp.freetube.helpers.writeBytes
import io.freetubeapp.freetube.helpers.writeText
import io.freetubeapp.freetube.webviews.FreeTubeWebView
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


class FreeTubeJavaScriptInterface(private val context: MainActivity, val webView: FreeTubeWebView) {
  private var mediaSession: MediaSession?
  private var lastPosition: Long
  private var lastState: Int
  private var lastNotification: Notification? = null
  private var keepScreenOn: Boolean = false
  val jsCommunicator: AsyncJSCommunicator

  companion object {
    private const val DATA_DIRECTORY = "data://"
    private const val CHANNEL_ID = "media_controls"
    private val NOTIFICATION_ID = (2..1000).random()
    private val NOTIFICATION_TAG = String.format("%s", randomUUID())
  }

  init {
    mediaSession = null
    lastPosition = 0
    lastState = PlaybackState.STATE_PLAYING
    jsCommunicator = AsyncJSCommunicator(webView)
  }

  // region Media Notifications

  /**
   * retrieves actions for the media controls
   * @param state the current state of the media controls (ex PlaybackState.STATE_PLAYING or PlaybackState.STATE_PAUSED
   */
  private fun getActions(state: Int = lastState): Array<Notification.Action> {
    var neutralAction = arrayOf("Pause", "pause")
    var neutralIcon = androidx.media3.ui.R.drawable.exo_icon_pause
    if (state == STATE_PAUSED) {
      neutralAction = arrayOf("Play", "play")
      neutralIcon = androidx.media3.ui.R.drawable.exo_icon_play
    }
    return arrayOf(
      Notification.Action.Builder(
        androidx.media3.ui.R.drawable.exo_ic_skip_previous,
        "Back",
        PendingIntent.getBroadcast(context, 1, Intent(context, MediaControlsReceiver::class.java).setAction("previous"), PendingIntent.FLAG_IMMUTABLE)
      ).build(),
      Notification.Action.Builder(
        neutralIcon,
        neutralAction[0],
        PendingIntent.getBroadcast(context, 1, Intent(context, MediaControlsReceiver::class.java).setAction(neutralAction[1]), PendingIntent.FLAG_IMMUTABLE)
      ).build(),
      Notification.Action.Builder(
        androidx.media3.ui.R.drawable.exo_ic_skip_next,
        "Next",
        PendingIntent.getBroadcast(context, 1, Intent(context, MediaControlsReceiver::class.java).setAction("next"), PendingIntent.FLAG_IMMUTABLE)
      ).build()
    )
  }

  /**
   * retrieves the media style for the media controls notification
   */
  private fun getMediaStyle(): Notification.MediaStyle? {
    return if (mediaSession != null) {
      Notification.MediaStyle()
        .setMediaSession(mediaSession!!.sessionToken).setShowActionsInCompactView(0, 1, 2)
    } else {
      null
    }
  }

  /**
   * Gets a fresh media controls notification given the current `mediaSession`
   * @param actions a list of actions for the media controls (defaults to `getActions()`)
   */
  @RequiresApi(Build.VERSION_CODES.O)
  private fun getMediaControlsNotification(actions: Array<Notification.Action> = getActions()): Notification? {
    val mediaStyle = getMediaStyle()
    if (mediaStyle != null) {
      // when clicking the notification, launch the app as if the user tapped on it in their launcher (open an existing instance if able)
      val notificationIntent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClass(context,  MainActivity::class.java)

      // always reuse notification
      if (lastNotification != null) {
        lastNotification!!.actions = actions
        return lastNotification
      }
      lastNotification = Notification.Builder(context, CHANNEL_ID)
        .setStyle(getMediaStyle())
        .setSmallIcon(R.drawable.ic_media_notification_icon)
        .addAction(
          actions[0]
        )
        .addAction(
          actions[1]
        )
        .addAction(
          actions[2]
        )
        .setContentIntent(
          PendingIntent.getActivity(
            context, 1, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )
        )
        .setDeleteIntent(
          PendingIntent.getBroadcast(context, 1, Intent(context, MediaControlsReceiver::class.java).setAction("pause"), PendingIntent.FLAG_IMMUTABLE)
        )
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .build()
      return lastNotification
    } else {
      return null
    }
  }

  /**
   * pushes a notification
   * @param notification the notification the be pushed (usually a media controls notification)
   */
  @SuppressLint("MissingPermission")
  private fun pushNotification(notification: Notification) {
    if (lastNotification !== null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // always set notifications to pause before sending another on android 13+
      setState(mediaSession!!, STATE_PAUSED)
    }
    val manager = NotificationManagerCompat.from(context)
    manager.notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
  }

  /**
   * sets the state of the media session
   * @param session the current media session
   * @param state the state of playback
   * @param position the position in milliseconds of playback
   */
  @SuppressLint("MissingPermission")
  private fun setState(session: MediaSession, state: Int, position: Long? = null) {

    if (state != lastState) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // need to reissue a notification if we want to update the actions
        val actions = getActions(state)
        val notification = getMediaControlsNotification(actions)
        pushNotification(notification!!)
      }
    }
    lastState = state
    val statePosition: Long = position ?: lastPosition
    session.setPlaybackState(
      PlaybackState.Builder()
        .setState(state, statePosition, 0.0f)
        .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_SEEK_TO)
        .build()
    )
  }

  /**
   * sets the metadata of the media session
   * @param session the current media session
   * @param trackName the video name
   * @param artist the channel name
   * @param duration duration in milliseconds
   */
  @SuppressLint("MissingPermission")
  @RequiresApi(Build.VERSION_CODES.O)
  private fun setMetadata(session: MediaSession, trackName: String, artist: String, duration: Long, art: String?, pushNotification: Boolean = true) {
    var notification: Notification? = null
    if (pushNotification) {
      notification = getMediaControlsNotification()
    }

    if (art != null) {
      // todo move this to a function and add try catch
      val connection = URL(art).openConnection()
      connection.connect()
      val input = connection.getInputStream()
      val bitmapArt = BitmapFactory.decodeStream(input)
      // todo
      session.setMetadata(
        MediaMetadata.Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, trackName)
          .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
          .putBitmap(MediaMetadata.METADATA_KEY_ART, bitmapArt)
          .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmapArt)
          .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
          .build()
      )
    } else {
      session.setMetadata(
        MediaMetadata.Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, trackName)
          .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
          .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
          .build()
      )
    }
    if (pushNotification && notification != null) {
      pushNotification(notification)
    }
  }

  /**
   * creates (or updates) a media session
   * @param title the track name / video title
   * @param artist the author / channel name
   * @param duration the duration in milliseconds of the video
   * @param thumbnail a URL to the thumbnail for the video
   */
  @SuppressLint("MissingPermission")
  @RequiresApi(Build.VERSION_CODES.O)
  @JavascriptInterface
  fun createMediaSession(title: String, artist: String, duration: Long = 0, thumbnail: String? = null) {
    val notificationManager = NotificationManagerCompat.from(context)
    val channel = notificationManager.getNotificationChannel(CHANNEL_ID, "Media Controls")
      ?: NotificationChannel(CHANNEL_ID, "Media Controls", NotificationManager.IMPORTANCE_MIN)

    channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    val session: MediaSession

    // don't create multiple sessions or multiple channels
    if (mediaSession == null) {
      notificationManager.createNotificationChannel(channel)
      // add the callbacks && listeners

      session = MediaSession(context, CHANNEL_ID)
      session.isActive = true
      mediaSession = session

      session.setCallback(object : MediaSession.Callback() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
          val keyEvent = mediaButtonIntent.extras!!.getParcelable(EXTRA_KEY_EVENT, KeyEvent::class.java)
          return if (keyEvent == null) {
            super.onMediaButtonEvent(mediaButtonIntent)
          } else {
            when (keyEvent.keyCode) {
              KEYCODE_MEDIA_PLAY -> {
                webView.dispatchEvent("media-play")
              }
              KEYCODE_MEDIA_PAUSE -> {
                webView.dispatchEvent("media-pause")
              }
              KEYCODE_MEDIA_NEXT -> {
                webView.dispatchEvent("media-next")
              }
              KEYCODE_MEDIA_PREVIOUS -> {
                webView.dispatchEvent("media-previous")
              }
            }
            false
          }
        }

        override fun onSkipToNext() {
          super.onSkipToNext()
          webView.dispatchEvent("media-next")
        }

        override fun onSkipToPrevious() {
          super.onSkipToPrevious()
          webView.dispatchEvent("media-previous")
        }

        override fun onSeekTo(pos: Long) {
          super.onSeekTo(pos)
          webView.dispatchEvent("media-seek", "position", pos)
        }

        override fun onPlay() {
          super.onPlay()
          webView.dispatchEvent("media-play")
        }

        override fun onPause() {
          super.onPause()
          webView.dispatchEvent("media-pause")
        }

      })
    } else {
      session = mediaSession!!
    }

    val notification = getMediaControlsNotification()
    // use the set metadata function without pushing a notification
    setMetadata(session, title, artist, duration, thumbnail, false)
    setState(session, PlaybackState.STATE_PLAYING)

    pushNotification(notification!!)
  }

  /**
   * updates the state of the active media session
   * @param state the state; should be an Int (as a string because the java bridge)
   * @param position the position; should be a Long (as a string because the java bridge)
   */
  @JavascriptInterface
  fun updateMediaSessionState(state: String?, position: String? = null) {
    var givenState = state?.toInt()
    if (state == null) {
      givenState = lastState
    }
    if (position != null) {
      lastPosition = position.toLong()
    }
    setState(mediaSession!!, givenState!!, position?.toLong())
  }

  /**
   * updates the metadata of the active media session
   * @param trackName the video title
   * @param artist the channel name
   * @param duration the length of the video in milliseconds
   * @param art the URL to the video thumbnail
   */
  @SuppressLint("NewApi")
  @JavascriptInterface
  fun updateMediaSessionData(trackName: String, artist: String, duration: Long, art: String? = null) {
    setMetadata(mediaSession!!, trackName, artist, duration, art)
  }

  /**
   * cancels the active media notification
   */
  @JavascriptInterface
  fun cancelMediaNotification() {
    val manager = NotificationManagerCompat.from(context)
    manager.cancelAll()
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
    return Promise(context.threadPoolExecutor, {
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
          resolve(File(path, fileName).readText())
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
    return Promise(context.threadPoolExecutor, {
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
    return Promise(context.threadPoolExecutor, {
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
    return Promise(context.threadPoolExecutor, {
      resolve,
      reject
      ->
      context.launchIntent(
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
    // TODO decouple
    return Promise(context.threadPoolExecutor, {
      resolve,
      reject ->
        // TODO decouple
        context.launchIntent(
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
    // TODO decouple
    return Promise(context.threadPoolExecutor, {
      resolve,
      reject ->
      // TODO decouple
      context.launchIntent(
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
    for (message in webView.state.consoleMessages) {
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
    // TODO decouple
    context.finish()
    context.startActivity(Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_LAUNCHER)
      .setClass(context,  MainActivity::class.java))
  }

  /**
   * hides the splashscreen
   */
  @JavascriptInterface
  fun hideSplashScreen() {
    webView.state.showSplashScreen = false
  }

  @JavascriptInterface
  fun enableKeepScreenOn() {
    if (!keepScreenOn) {
      keepScreenOn = true
      // TODO decouple
      context.runOnUiThread {
        context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  @JavascriptInterface
  fun disableKeepScreenOn() {
    if (keepScreenOn) {
      keepScreenOn = false
      // TODO decouple
      context.runOnUiThread {
        context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
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
    // TODO decouple
    context.runOnUiThread {
      // TODO decouple
      val windowInsetsController =
        WindowCompat.getInsetsController(context.window, context.window.decorView)
      windowInsetsController.isAppearanceLightNavigationBars = !navigationDarkMode
      windowInsetsController.isAppearanceLightStatusBars = !statusDarkMode
      // TODO decouple
      context.window.navigationBarColor = navigationHex.hexToColour()
      // TODO decouple
      context.window.statusBarColor = statusHex.hexToColour()
    }

    val bitmap = createBitmap(24, 24)
    bitmap.eraseColor(navigationHex.hexToColour())
    val canvas = Canvas(bitmap)
    canvas.drawColor(navigationHex.hexToColour())
    val bitmapDrawable = bitmap.toDrawable(context.resources)
    // TODO decouple
    context.window.setBackgroundDrawable(bitmapDrawable)
  }

  @JavascriptInterface
  fun getSystemTheme(): String {
    return if (webView.state.darkMode) {
      "dark"
    } else {
      "light"
    }
  }

  @JavascriptInterface
  fun isAppPaused(): Boolean {
    return webView.state.paused
  }

  @JavascriptInterface
  fun enterPromptMode() {
    webView.isVerticalScrollBarEnabled = false
    webView.state.isInAPrompt = true
  }

  @JavascriptInterface
  fun exitPromptMode() {
    webView.isVerticalScrollBarEnabled = true
    webView.state.isInAPrompt = false
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
    // TODO decouple
    return Promise(context.threadPoolExecutor, {
      resolve,
      reject
      ->
      // TODO decouple
        context.runOnUiThread {
          try {
            val bgScript = getBotGuardScript(videoId, sessionContext)
            val bgWv = webView.generateBgWebview()
            bgWv.jsInterface.onReturnToken {
              run {
                // TODO decouple
                context.runOnUiThread {
                  resolve(it)
                  bgWv.destroy()
                }
              }
            }
            // TODO decouple
            context.runOnUiThread {
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
