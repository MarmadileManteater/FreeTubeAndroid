package io.freetubeapp.freetube.helpers

import android.content.res.Configuration

fun Configuration.isDarkMode(): Boolean {
  when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
    Configuration.UI_MODE_NIGHT_NO -> {
      return false
    }
    Configuration.UI_MODE_NIGHT_YES -> {
      return true
    }
    else -> {
      return false
    }
  }
}
