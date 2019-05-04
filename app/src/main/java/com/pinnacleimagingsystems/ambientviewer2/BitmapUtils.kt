package com.pinnacleimagingsystems.ambientviewer2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

fun Bitmap.asRotated(rotation: Int) = when (rotation % 360) {
    0 -> this
    else -> {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}

fun loadBitmap(file: String, maxSize: Int = 0): Bitmap {
    val exif = androidx.exifinterface.media.ExifInterface(file)
    val rotationDegrees = exif.rotationDegrees

    val options = BitmapFactory.Options()

    if (maxSize > 0) {
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file, options)

        val maxBitmapDimension = maxOf(options.outWidth, options.outHeight)

        options.inJustDecodeBounds = false
        options.inSampleSize = maxOf(1, maxBitmapDimension / maxSize)
    }

    val bitmap = BitmapFactory.decodeFile(file, options)

    return bitmap.asRotated(rotationDegrees)
}

fun loadThumbnailBitmap(file: String): Bitmap? {
    val exif = androidx.exifinterface.media.ExifInterface(file)
    val rotationDegrees = exif.rotationDegrees
    val bitmap = exif.thumbnailBitmap ?: return null

    return bitmap.asRotated(rotationDegrees)
}

