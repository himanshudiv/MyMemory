package com.example.mymemory.utils

import android.graphics.Bitmap

object BitmapScaler {

    // scale and maintain ration given a desired width
    //BitmapScaler.scaleToFitWidth(bitmap, 100);
    fun scaleToFitWidth(b: Bitmap, width: Int): Bitmap{
        val factor: Float = width/b.width.toFloat()
        return Bitmap.createScaledBitmap(b, width, (b.height * factor).toInt(), true)

    }
    // scale and maintain ration given a desired height
    fun scaleToFitHeight(b: Bitmap, height: Int): Bitmap{
        val factor: Float= height/b.height.toFloat()
        return Bitmap.createScaledBitmap(b, (b.width * factor).toInt(), height, true)

    }
}
