package io.freetubeapp.freetube.helpers

import android.content.Intent
import android.view.WindowInsetsController
import androidx.activity.result.ActivityResult
import androidx.core.view.WindowInsetsControllerCompat

data class ApplicationMethods(
  val restart: () -> Unit,
  val launchIntent: (Intent) -> Promise<ActivityResult?, Exception>,
  val setKeepScreenOn: (Boolean) -> Unit,
  val themeSystemUi: (String, String, Boolean, Boolean) -> Unit,
  val getWindowInsetsController: () -> WindowInsetsControllerCompat
)
