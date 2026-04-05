package io.freetubeapp.freetube.helpers

import android.content.Intent
import androidx.activity.result.ActivityResult

data class ApplicationMethods(
  val restart: () -> Unit,
  val launchIntent: (Intent) -> Promise<ActivityResult?, Exception>,
  val setKeepScreenOn: (Boolean) -> Unit,
  val themeSystemUi: (String, String, Boolean, Boolean) -> Unit
)
