package io.freetubeapp.freetube.helpers

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri

fun ContentResolver.readBytes(uri: Uri): ByteArray {
  val stream = openInputStream(uri)
  val content = stream!!.readBytes()
  stream.close()
  return content
}

fun ContentResolver.writeBytes(uri: Uri, bytes: ByteArray, writeMode: WriteMode = WriteMode.Truncate) {
  val mode = when (writeMode) {
      WriteMode.Truncate -> {
        "wt"
      }
      WriteMode.Append -> {
        "wa"
      }
      else -> {
        "w"
      }
  }
  val stream = openOutputStream(uri, mode)
  stream!!.write(bytes)
  stream.flush()
  stream.close()
}

fun ContentResolver.getFileName(uri: Uri): String {
  var result: String? = null
  val cursor = query(uri,  null, null, null, null)
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
    result = uri.toString().split(Regex("(/)|(%2F)")).last()
  }

  return result
}
