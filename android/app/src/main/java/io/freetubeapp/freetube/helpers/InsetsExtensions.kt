package io.freetubeapp.freetube.helpers

import android.graphics.Insets
import org.json.JSONObject

fun Insets.toJSON(): JSONObject {
  val json = JSONObject()
  json.put("top", top)
  json.put("bottom", bottom)
  json.put("left", left)
  json.put("right", right)

  return json
}

operator fun Insets.div(denominator: Float): Insets {
  return Insets.of(
    (left / denominator).toInt(),
    (top / denominator).toInt(),
    (right / denominator).toInt(),
    (bottom / denominator).toInt()
  )
}

operator fun Insets.plus(insets: Insets): Insets {
  return Insets.of(
    left + insets.left,
    top + insets.top,
    right + insets.right,
    bottom + insets.bottom
  )
}

fun Insets.addSystemBars(insets: Insets): Insets {
  return Insets.of(
    left,
    if (top != 0) {
      top
    } else {
      insets.top
    },
    right,
    bottom + insets.bottom
  )
}
