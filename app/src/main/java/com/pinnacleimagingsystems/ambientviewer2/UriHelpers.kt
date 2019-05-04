package com.pinnacleimagingsystems.ambientviewer2

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun Uri.toDisplayName(contentResolver: ContentResolver): String {
    var displayName: String? = null

    if (scheme == "content") {
        contentResolver.query(
                this,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0)
            }
        }
    }

    if (displayName == null) {
        displayName = lastPathSegment
    }

    return displayName!!
}