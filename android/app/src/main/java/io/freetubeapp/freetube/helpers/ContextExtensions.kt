package io.freetubeapp.freetube.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_KEY_EVENT
import android.media.session.MediaSession
import android.os.Build
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.core.app.NotificationManagerCompat

fun Context.buildMediaSession(channelId: String, dispatchEvent: (String) -> Unit, updatePos: (Long) -> Unit): MediaSession {
  val notificationManager = NotificationManagerCompat.from(this)
  val channel = notificationManager.getNotificationChannel(channelId, "Media Controls")
    ?: NotificationChannel(channelId, "Media Controls", NotificationManager.IMPORTANCE_MIN)

  channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
  notificationManager.createNotificationChannel(channel)

  // add the callbacks && listeners
  val session = MediaSession(this, channelId)
  session.isActive = true

  session.setCallback(object : MediaSession.Callback() {
    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
      val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        mediaButtonIntent.extras?.getParcelable(EXTRA_KEY_EVENT, KeyEvent::class.java)
      } else {
        mediaButtonIntent.extras?.getParcelable(EXTRA_KEY_EVENT)
      }
      return if (keyEvent == null) {
        super.onMediaButtonEvent(mediaButtonIntent)
      } else {
        when (keyEvent.keyCode) {
          KEYCODE_MEDIA_PLAY -> {
            dispatchEvent("media-play")
          }
          KEYCODE_MEDIA_PAUSE -> {
            dispatchEvent("media-pause")
          }
          KEYCODE_MEDIA_NEXT -> {
            dispatchEvent("media-next")
          }
          KEYCODE_MEDIA_PREVIOUS -> {
            dispatchEvent("media-previous")
          }
        }
        false
      }
    }

    override fun onSkipToNext() {
      super.onSkipToNext()
      dispatchEvent("media-next")
    }

    override fun onSkipToPrevious() {
      super.onSkipToPrevious()
      dispatchEvent("media-previous")
    }

    override fun onSeekTo(pos: Long) {
      super.onSeekTo(pos)
      updatePos(pos)
    }

    override fun onPlay() {
      super.onPlay()
      dispatchEvent("media-play")
    }

    override fun onPause() {
      super.onPause()
      dispatchEvent("media-pause")
    }
  })

  return session
}
