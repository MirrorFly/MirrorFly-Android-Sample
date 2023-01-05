package com.contusfly.utils

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import com.contusfly.chat.RealPathUtil
import com.contusfly.utils.Constants.Companion.TAG
import com.contusflysdk.media.AsynTaskImageCompression
import java.io.*


class ImageCompressor {

    companion object {
        private const val MAX_HEIGHT = 1280.0f
        private const val MAX_WIDTH = 1280.0f

        fun sampleAndResize(imagefile: File, context: Context): File? {
            var uri = Uri.fromFile(imagefile)
            val imagePath = RealPathUtil.getRealPath(context, uri)
            var scaledBitmap: Bitmap? = null
            val options = BitmapFactory.Options()

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
            options.inJustDecodeBounds = true
            val bmp: Bitmap
            BitmapFactory.decodeFile(imagePath, options)
            var actualHeight = options.outHeight.toFloat()
            var actualWidth = options.outWidth.toFloat()

//      max Height and width values of the compressed image is taken as 816x612
            var imgRatio = actualWidth / actualHeight
            val maxRatio = MAX_WIDTH / MAX_HEIGHT

//      width and height values are set maintaining the aspect ratio of the image
            if (actualHeight > MAX_HEIGHT || actualWidth > MAX_WIDTH) {
                if (imgRatio < maxRatio) {
                    imgRatio = MAX_HEIGHT / actualHeight
                    actualWidth = imgRatio * actualWidth
                    actualHeight = MAX_HEIGHT
                } else if (imgRatio > maxRatio) {
                    imgRatio = MAX_WIDTH / actualWidth
                    actualHeight = imgRatio * actualHeight
                    actualWidth = MAX_WIDTH
                } else {
                    actualHeight = MAX_HEIGHT
                    actualWidth = MAX_WIDTH
                }
            }

//      setting inSampleSize value allows to load a scaled down version of the original image
            options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight)

//      inJustDecodeBounds set to false to load the actual bitmap
            options.inJustDecodeBounds = false

//      this options allow android to claim the bitmap memory if it runs low on memory
            options.inPurgeable = true
            options.inInputShareable = true
            options.inTempStorage = ByteArray(16 * 1024)
            bmp = try {
                //          load the bitmap from its path
                BitmapFactory.decodeFile(imagePath, options)
            } catch (exception: OutOfMemoryError) {
                LogMessage.e(TAG, exception)
                return null
            }
            scaledBitmap = try {
                Bitmap.createBitmap(
                    actualWidth.toInt(),
                    actualHeight.toInt(),
                    Bitmap.Config.ARGB_8888
                )
            } catch (exception: OutOfMemoryError) {
                com.contus.flycommons.LogMessage.e(exception)
                return null
            }
            val ratioX = actualWidth / options.outWidth
            val ratioY = actualHeight / options.outHeight
            val middleX = actualWidth / 2.0f
            val middleY = actualHeight / 2.0f
            val scaleMatrix = Matrix()
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)
            val canvas = Canvas(scaledBitmap!!)
            canvas.setMatrix(scaleMatrix)
            canvas.drawBitmap(
                bmp,
                middleX - bmp.width / 2f,
                middleY - bmp.height / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )

//      check the rotation of the image and display it properly
            val exif: ExifInterface
            try {
                exif = ExifInterface(imagePath!!)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 0
                )
                val matrix = Matrix()
                if (orientation == 6) {
                    matrix.postRotate(90f)
                } else if (orientation == 3) {
                    matrix.postRotate(180f)
                } else if (orientation == 8) {
                    matrix.postRotate(270f)
                }
                scaledBitmap = Bitmap.createBitmap(
                    scaledBitmap, 0, 0,
                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                    true
                )
            } catch (e: IOException) {
                com.contus.flycommons.LogMessage.e(e)
                return null
            }
            var out: FileOutputStream? = null
            var file = File(imagePath)
            try {
                file.createNewFile()
                out = FileOutputStream(file)

//          write the compressed bitmap at the destination specified by filename.
                scaledBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    AsynTaskImageCompression.getCompressionQuality(),
                    out
                )

            } catch (e: FileNotFoundException) {
                LogMessage.e(TAG, e)
                return null
            }
            return file
        }

        fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Float,
            reqHeight: Float
        ): Int {
            val height = options.outHeight.toFloat()
            val width = options.outWidth.toFloat()
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val heightRatio = Math.round(height / reqHeight)
                val widthRatio = Math.round(width / reqWidth)
                inSampleSize = Math.min(heightRatio, widthRatio)
            }
            val totalPixels = width * height
            val totalReqPixelsCap = reqWidth * reqHeight * 2
            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++
            }
            return inSampleSize
        }


    }
}