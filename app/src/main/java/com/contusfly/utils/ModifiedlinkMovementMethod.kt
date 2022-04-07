package com.contusfly.utils

import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import com.contus.flycommons.LogMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class ModifiedlinkMovementMethod(var context: Context) : LinkMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            Log.d(TAG, "onTouchEvent: one")
            var x = event.x.toInt()
            var y = event.y.toInt()
            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY
            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val link = buffer.getSpans(off, off + 1, ClickableSpan::class.java)
            if (link.isNotEmpty()) {
                if (action == MotionEvent.ACTION_UP) link[0].onClick(widget)
                return true
            } else {
                Selection.removeSelection(buffer)
            }
        }
        return false
    }

    companion object {
        private val TAG = ModifiedlinkMovementMethod::class.java.simpleName
        fun expand(url: String?): String {
            val s3 = ""
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                val stream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(stream))
                val responseStringBuilder = StringBuilder()
                var line: String
                while (reader.readLine().also { line = it } != null) {
                    responseStringBuilder.append(line)
                }
                Log.d(TAG, "expand: s3" + connection.url.toString())
            } catch (e: MalformedURLException) {
                Log.d(TAG, "expand: error$e")
                LogMessage.e(TAG, e)
            } catch (e: IOException) {
                LogMessage.e(TAG, e)
                Log.d(TAG, "expand: error$e")
            }
            return s3
        }
    }
}