package io.freetubeapp.freetube.javascript

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.session.PlaybackState.STATE_PAUSED
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.result.ActivityResult
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.MediaControlsReceiver
import io.freetubeapp.freetube.R
import io.freetubeapp.freetube.helpers.Promise
import io.freetubeapp.freetube.helpers.hexToColour
import io.freetubeapp.freetube.helpers.readBytes
import io.freetubeapp.freetube.helpers.readText
import io.freetubeapp.freetube.helpers.writeBytes
import io.freetubeapp.freetube.helpers.writeText
import io.freetubeapp.freetube.webviews.BotGuardWebView
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.UUID.*
import java.util.concurrent.ThreadPoolExecutor


class FreeTubeJavaScriptInterface {
  private val context: MainActivity
  private val webView: WebView
  private val bgWebView: WebView
  private val mainExecutor: ThreadPoolExecutor
  private val contentResolver: ContentResolver
  private var mediaSession: MediaSession?
  private var lastPosition: Long
  private var lastState: Int
  private var lastNotification: Notification? = null
  private var keepScreenOn: Boolean = false
  private val jsCommunicator: AsyncJSCommunicator

  // region Methods from MainActivity
  private val launchIntent: (Intent) -> Promise<ActivityResult?, Exception>
  private val revokeUriPermission : (uri: Uri, modeFlags: Int) -> Unit
  private val fromTreeUri: (uri: Uri) -> DocumentFile?
  // endregion

  companion object {
    private const val DATA_DIRECTORY = "data://"
    private const val CHANNEL_ID = "media_controls"
    private val NOTIFICATION_ID = (2..1000).random()
    private val NOTIFICATION_TAG = String.format("%s", randomUUID())
  }

  constructor(
    main: MainActivity,
    givenWebView: WebView,
    givenBotGuardWebView: BotGuardWebView,
    givenThreadPoolExecutor: ThreadPoolExecutor,
    givenContentResolver: ContentResolver,
    givenLaunchIntent: (Intent) -> Promise<ActivityResult?, Exception>,
    givenRevokeUriPermission: (uri: Uri, modeFlags: Int) -> Unit,
    givenFromTreeUri: (uri: Uri) -> DocumentFile?
  )  {
    context = main
    webView = givenWebView
    bgWebView = givenBotGuardWebView
    mainExecutor = givenThreadPoolExecutor
    contentResolver = givenContentResolver
    launchIntent = givenLaunchIntent
    revokeUriPermission = givenRevokeUriPermission
    fromTreeUri = givenFromTreeUri
    mediaSession = null
    lastPosition = 0
    lastState = PlaybackState.STATE_PLAYING
    jsCommunicator = AsyncJSCommunicator(main.webView)
  }

  // region Media Notifications

  /**
   * retrieves actions for the media controls
   * @param state the current state of the media controls (ex PlaybackState.STATE_PLAYING or PlaybackState.STATE_PAUSED
   */
  private fun getActions(state: Int = lastState): Array<Notification.Action> {
    var neutralAction = arrayOf("Pause", "pause")
    var neutralIcon = androidx.media3.ui.R.drawable.exo_icon_pause
    if (state == PlaybackState.STATE_PAUSED) {
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
    if (mediaSession != null) {
      return Notification.MediaStyle()
        .setMediaSession(mediaSession!!.sessionToken).setShowActionsInCompactView(0, 1, 2)
    } else {
      return null
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
        var actions = getActions(state)
        val notification = getMediaControlsNotification(actions)
        pushNotification(notification!!)
      }
    }
    lastState = state
    var statePosition: Long
    if (position == null) {
      statePosition = lastPosition
    } else {
      statePosition = position
    }
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
    var session: MediaSession

    // don't create multiple sessions or multiple channels
    if (mediaSession == null) {
      notificationManager.createNotificationChannel(channel)
      // add the callbacks && listeners

      session = MediaSession(context, CHANNEL_ID)
      session.isActive = true
      mediaSession = session
      session.setCallback(object : MediaSession.Callback() {
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
    } else {
    }
    if (position != null) {
      lastPosition = position.toLong()!!
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

  @JavascriptInterface
  fun getFileNameFromUri(uri: String): String {
    var result: String? = null
    val cursor = contentResolver.query(Uri.parse(uri),  null, null, null, null)
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
    revokeUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  }

  @JavascriptInterface
  fun listFilesInTree(tree: String): String {
    val directory = fromTreeUri(Uri.parse(tree))
    val files = directory!!.listFiles().joinToString(",") { file ->
      "{ \"uri\": \"${file.uri}\", \"fileName\": \"${file.name}\", \"isFile\": ${file.isFile}, \"isDirectory\": ${file.isDirectory} }"
    }
    return "[$files]"
  }

  @JavascriptInterface
  fun createFileInTree(tree: String, fileName: String): String {
    val directory = fromTreeUri(Uri.parse(tree))
    return directory!!.createFile("*/*", fileName)!!.uri.toString()
  }

  @JavascriptInterface
  fun createDirectoryInTree(tree: String, fileName: String): String {
    val directory = fromTreeUri(Uri.parse(tree))
    return directory!!.createDirectory(fileName)!!.uri.toString()
  }

  @JavascriptInterface
  fun deleteFileInTree(fileUri: String): Boolean {
    val file = fromTreeUri(Uri.parse(fileUri))
    return file!!.delete()
  }

  // endregion

  // region IO
  /**
   * reads a file from storage
   */
  @JavascriptInterface
  fun readFile(basedir: String, filename: String): String {
    return Promise(mainExecutor, {
      resolve,
      reject ->
      try {
        if (basedir.startsWith("content://")) {
          resolve(
            contentResolver
            .readBytes(Uri.parse(basedir))
            .toString(Charset.forName("utf-8"))
          )
        } else {
          val path = getDirectory(basedir)
          resolve(File(path, filename).readText())
        }
      } catch (ex: Exception) {
        reject(ex.stackTraceToString())
      }
    }).addJsCommunicator(jsCommunicator)
  }

  /**
   * writes a file to storage
   */
  @JavascriptInterface
  fun writeFile(basedir: String, filename: String, content: String): String {
    return Promise(mainExecutor, {
      resolve,
      reject ->
      try {
        if (basedir.startsWith("content://")) {
          // urls created by save dialog
          contentResolver.writeBytes(
            Uri.parse(basedir),
            content.toByteArray()
          )
          resolve("true")
        } else {
          val path = getDirectory(basedir)
          File(path, filename).writeText(content)
          resolve("true")
        }
      } catch (ex: Exception) {
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
    return Promise(mainExecutor, {
      resolve,
      reject
      ->
      launchIntent(
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
    return Promise(mainExecutor, {
      resolve,
      reject ->
        launchIntent(
          Intent(Intent.ACTION_GET_CONTENT)
          .setType("*/*")
          .putExtra(Intent.EXTRA_MIME_TYPES, fileTypes.split(",").toTypedArray())
        ).then {
            if (it!!.resultCode == Activity.RESULT_CANCELED) {
              resolve("USER_CANCELED")
            }
            try {
              val uri = it.data!!.data
              val mimeType = contentResolver.getType(uri!!)
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
    return Promise(mainExecutor, {
      resolve,
      reject ->
      launchIntent(
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
      ).then {
          if (it!!.resultCode == Activity.RESULT_CANCELED) {
            resolve("USER_CANCELED")
          }
          try {
            val uri = it.data!!.data!!
            contentResolver.takePersistableUriPermission(
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
  fun getLogs(): String {
    var logs = "["
    for (message in context.consoleMessages) {
      logs += "${message},"
    }
    // get rid of trailing comma
    logs = logs.substring(0, logs.length - 1)
    logs += "]"
    return logs
  }

  @JavascriptInterface
  fun restart() {
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
    context.showSplashScreen = false
  }

  @JavascriptInterface
  fun enableKeepScreenOn() {
    if (!keepScreenOn) {
      keepScreenOn = true
      context.runOnUiThread {
        context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }

  @JavascriptInterface
  fun disableKeepScreenOn() {
    if (keepScreenOn) {
      keepScreenOn = false
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
    context.runOnUiThread {
      val windowInsetsController =
        WindowCompat.getInsetsController(context.window, context.window.decorView)
      windowInsetsController.isAppearanceLightNavigationBars = !navigationDarkMode
      windowInsetsController.isAppearanceLightStatusBars = !statusDarkMode
      context.window.navigationBarColor = navigationHex.hexToColour()
      context.window.statusBarColor = statusHex.hexToColour()
    }
  }

  @JavascriptInterface
  fun getSystemTheme(): String {
    if (context.darkMode) {
      return "dark"
    } else {
      return "light"
    }
  }

  @JavascriptInterface
  fun isAppPaused(): Boolean {
    return context.paused
  }

  @JavascriptInterface
  fun enterPromptMode() {
    webView.isVerticalScrollBarEnabled = false
    context.isInAPrompt = true
  }

  @JavascriptInterface
  fun exitPromptMode() {
    webView.isVerticalScrollBarEnabled = true
    context.isInAPrompt = false
  }

  @JavascriptInterface
  fun setScale(scale: Int) {
    webView.setScale(scale / 100.0)
  }

  // endregion

  // region Data Extraction

  @JavascriptInterface
  fun generatePOTokenFromVisitorData(visitorData: String): String {
    return Promise(mainExecutor, {
      resolve,
      reject ->
        val bgWv = bgWebView
        val script = context.assets.readText("botGuardScript.js")
        try {
          val functionName = script.split("export{")[1].split(" as default};")[0]
          val exportSection = "export{${functionName} as default};"
          context.bgJsInterface.onReturnToken {
            run {
              context.runOnUiThread {
                resolve(it)
                bgWv.loadUrl("about:blank")
              }
            }
          }
          val bakedScript =
            script.replace(exportSection, "; ${functionName}(\"${visitorData}\").then((TOKEN_RESULT) => { console.log(`Your potoken is \${TOKEN_RESULT}`) ; Android.returnToken(TOKEN_RESULT) })")
          context.runOnUiThread {
            bgWv.loadDataWithBaseURL(
              "https://www.youtube.com",
              "<script>${bakedScript}</script>",
              "text/html",
              "utf-8",
              null
            )
          }
        } catch (exception: Exception) {
          reject(exception.message!!)
        }
    }).addJsCommunicator(jsCommunicator)
  }

  // endregion

  /**
  @JavascriptInterface
  fun queueFetchBody(id: String, body: String) {
    if (body != "undefined") {
      context.pendingRequestBodies[id] = body
    }
  }
  */
}
