package io.freetubeapp.freetube.helpers

import android.view.ViewTreeObserver

fun ViewTreeObserver.addOnPreDraw(listener: ViewTreeObserver.OnPreDrawListener.() -> Boolean) {
  addOnPreDrawListener (
    object: ViewTreeObserver.OnPreDrawListener {
      override fun onPreDraw(): Boolean {
        return listener()
      }
    }
  )
}

fun ViewTreeObserver.removeOnPreDraw(listener: ViewTreeObserver.OnPreDrawListener) {
  removeOnPreDrawListener(listener)
}
