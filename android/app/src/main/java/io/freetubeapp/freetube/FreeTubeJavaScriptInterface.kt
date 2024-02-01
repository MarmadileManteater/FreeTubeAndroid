package io.freetubeapp.freetube

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import java.net.URL
class FreeTubeJavaScriptInterface {
  private var context: MainActivity
  private var mediaSession: MediaSession?
  private var lastPosition: Long
  private var lastState: Int
  companion object {
    private const val CHANNEL_ID = "media_controls"
    private const val NOTIFICATION_ID = 1
  }
  constructor(main: MainActivity) {
    context = main
    mediaSession = null
    lastPosition = 0
    lastState = PlaybackState.STATE_PLAYING
  }
  @JavascriptInterface
  fun createToast(text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
  }
  private fun setState(session: MediaSession, state: Int, position: Long? = null) {
    lastState = state
    var statePosition: Long
    if (position == null) {
      statePosition = lastPosition
    } else {
      statePosition = position
    }
    session.setPlaybackState(
      PlaybackState.Builder()
        .setState(state, statePosition, 1.0f)
        .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
        PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_SEEK_TO)
        .build()
    )
  }

  @SuppressLint("MissingPermission")
  @RequiresApi(Build.VERSION_CODES.O)
  private fun setMetadata(session: MediaSession, trackName: String, artist: String, duration: Long, art: String?, pushNotification: Boolean = true) {
    val mediaStyle = Notification.MediaStyle()
        .setMediaSession(session.sessionToken)
    var notification: Notification? = null

    if (pushNotification) {
      var neutralAction = arrayOf("Play", "play")
      var neutralIcon = androidx.media3.ui.R.drawable.exo_icon_play
      if (lastState == PlaybackState.STATE_PLAYING) {
        neutralAction = arrayOf("Pause", "pause")
        neutralIcon = androidx.media3.ui.R.drawable.exo_icon_pause
      }
      Notification.Builder(context, CHANNEL_ID)
        .setStyle(mediaStyle)
        .setSmallIcon(R.drawable.ic_media_notification_icon)
        .addAction(
          Notification.Action.Builder(
            androidx.media3.ui.R.drawable.exo_ic_skip_previous,
            "Back",
            PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction("previous"), PendingIntent.FLAG_IMMUTABLE)
          ).build()
        )
        .addAction(
          Notification.Action.Builder(
            neutralIcon,
            neutralAction[0],
            PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction(neutralAction[1]), PendingIntent.FLAG_IMMUTABLE)
          ).build()
        )
        .addAction(
          Notification.Action.Builder(
            androidx.media3.ui.R.drawable.exo_ic_skip_next,
            "Next",
            PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction("next"), PendingIntent.FLAG_IMMUTABLE)
          ).build()
        )
        .build()
    }

    if (art != null) {
      // todo move this to a function and add try catch
      val url = URL(art)
      val connection = url.openConnection()
      connection.doInput = true
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
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
  }


  @SuppressLint("MissingPermission")
  @RequiresApi(Build.VERSION_CODES.O)
  @JavascriptInterface
  fun createMediaSession(title: String, artist: String, duration: Long = 0, thumbnail: String? = null) {
    val notificationManager = NotificationManagerCompat.from(context)
    val channel = NotificationChannel(CHANNEL_ID, "Media Controls", NotificationManager.IMPORTANCE_MIN)
    channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    notificationManager.createNotificationChannel(channel)
    var session: MediaSession

    // don't create multiple sessions for one channel
    // it messes with custom actions
    if (mediaSession == null) {
      // add the callbacks && listeners

      session = MediaSession(context, CHANNEL_ID)
      session.isActive = true
      mediaSession = session
      session.setCallback(object : MediaSession.Callback() {
        override fun onSkipToNext() {
          super.onSkipToNext()
          context.runOnUiThread {
            context.webView.loadUrl("javascript: window.notifyMediaSessionListeners('next')")
          }
        }

        override fun onSkipToPrevious() {
          super.onSkipToPrevious()
          context.runOnUiThread {
            context.webView.loadUrl("javascript: window.notifyMediaSessionListeners('previous')")
          }
        }
        override fun onSeekTo(pos: Long) {
          super.onSeekTo(pos)
          lastPosition = pos
          context.runOnUiThread {
            context.webView.loadUrl(String.format("javascript: window.notifyMediaSessionListeners('seek', %s)", pos))
          }
        }

        override fun onPlay() {
          super.onPlay()
          context.runOnUiThread {
            context.webView.loadUrl("javascript: window.notifyMediaSessionListeners('play')")
          }
        }

        override fun onPause() {
          super.onPause()
          context.runOnUiThread {
            context.webView.loadUrl("javascript: window.notifyMediaSessionListeners('pause')")
          }
        }
      })
    } else {
      session = mediaSession!!
    }
    val mediaStyle = Notification.MediaStyle().setMediaSession(session.sessionToken)
    val notificationIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(context,  MainActivity::class.java)

    var neutralAction = arrayOf("Play", "play")
    var neutralIcon = androidx.media3.ui.R.drawable.exo_icon_play
    val notification = Notification.Builder(context, CHANNEL_ID)
      .setStyle(mediaStyle)
      .setSmallIcon(R.drawable.ic_media_notification_icon)
      .addAction(
        Notification.Action.Builder(
          androidx.media3.ui.R.drawable.exo_ic_skip_previous,
          "Back",
          PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction("previous"), PendingIntent.FLAG_IMMUTABLE)
        ).build()
      )
      .addAction(
        Notification.Action.Builder(
          neutralIcon,
          neutralAction[0],
          PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction(neutralAction[1]), PendingIntent.FLAG_IMMUTABLE)
        ).build()
      )
      .addAction(
        Notification.Action.Builder(
          androidx.media3.ui.R.drawable.exo_ic_skip_next,
          "Next",
          PendingIntent.getBroadcast(context, 1, Intent(context.applicationContext, MediaControlsReceiver::class.java).setAction("next"), PendingIntent.FLAG_IMMUTABLE)
        ).build()
      )
      .setContentIntent(
        PendingIntent.getActivity(
          context, 1, notificationIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
      )
      .build()
    // use the set metadata function without pushing a notification
    setMetadata(session, title, artist, duration, thumbnail, false)
    setState(session, PlaybackState.STATE_PLAYING)

    // hush hush ide, this is a special kind of notification which doesn't seem to require permissions
    notificationManager.notify(NOTIFICATION_ID, notification)
  }

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

  @SuppressLint("NewApi")
  @JavascriptInterface
  fun updateMediaSessionData(trackName: String, artist: String, duration: Long, art: String? = null) {
    setMetadata(mediaSession!!, trackName, artist, duration, art)
  }
}
