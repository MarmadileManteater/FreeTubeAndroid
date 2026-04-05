package io.freetubeapp.freetube.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState.STATE_PAUSED
import io.freetubeapp.freetube.MainActivity
import io.freetubeapp.freetube.MediaControlsReceiver
import io.freetubeapp.freetube.R


fun MediaSession.getMediaControlsIntent(context: Context, action: String): Intent {
  return Intent(
    context,
    MediaControlsReceiver::class.java
  ).setAction(action)
}

fun MediaSession.getAction(context: Context, icon: Int, label: String, action: String): Notification.Action {
  @Suppress("DEPRECATION")
  return Notification.Action.Builder(
    icon,
    label,
    PendingIntent.getBroadcast(
      context, 1,
      getMediaControlsIntent(context, action),
      PendingIntent.FLAG_IMMUTABLE
    )
  ).build()
}

@SuppressLint("PrivateResource")
fun MediaSession.getBack(context: Context): Notification.Action {
  return getAction(
    context,

    androidx.media3.ui.R.drawable.exo_ic_skip_previous,
    "Back",
    "previous"
  )
}

@SuppressLint("PrivateResource")
fun MediaSession.getNext(context: Context): Notification.Action {
  return getAction(
    context,

    androidx.media3.ui.R.drawable.exo_ic_skip_next,
    "Next",
    "next"
  )
}

@SuppressLint("PrivateResource")
fun MediaSession.getPause(context: Context): Notification.Action {
  return getAction(
    context,
    androidx.media3.ui.R.drawable.exo_icon_pause,
    "Pause",
    "pause"
  )
}

@SuppressLint("PrivateResource")
fun MediaSession.getPlay(context: Context): Notification.Action {
  return getAction(
    context,
    androidx.media3.ui.R.drawable.exo_icon_play,
    "Play",
    "play"
  )
}

fun MediaSession.getPlayPause(context: Context, state: Int): Notification.Action {
  return if (state == STATE_PAUSED) {
    getPause(context)
  } else {
    getPlay(context)
  }
}


fun MediaSession.getNotification(context: Context, channelId: String, state: Int): Notification {
  val style = Notification.MediaStyle()
    .setMediaSession(sessionToken).setShowActionsInCompactView(0, 1, 2)

  return Notification.Builder(context, channelId)
    .setStyle(style)
    .setSmallIcon(R.drawable.ic_media_notification_icon)
    .setContentIntent(
      PendingIntent.getActivity(
        context,
        1,
        Intent(Intent.ACTION_MAIN)
          .addCategory(Intent.CATEGORY_LAUNCHER)
          .setClass(context,  MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
    )
    .setDeleteIntent(
      PendingIntent.getBroadcast(
        context,
        1,
        getMediaControlsIntent(context, "pause"),
        PendingIntent.FLAG_IMMUTABLE
      )
    )
    .addAction(getBack(context))
    .addAction(getPlayPause(context, state))
    .addAction(getNext(context))
    .setVisibility(Notification.VISIBILITY_PUBLIC)
    .build()
}
