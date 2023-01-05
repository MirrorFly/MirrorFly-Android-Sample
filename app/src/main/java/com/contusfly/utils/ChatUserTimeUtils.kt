package com.contusfly.utils

import android.widget.TextView
import com.contus.flycommons.LogMessage
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object ChatUserTimeUtils {

    /**
     * Sets the formatted duration in the text view with the chat format
     *
     * @param totalDuration Total duration for formatter
     * @param textTimer     View to display
     */
    fun setFormattedTimeTextView(totalDuration: Int, textTimer: TextView) {
        val formatted: String = if (totalDuration < 60) {
            if (totalDuration < 10) "00:0$totalDuration" else "00:$totalDuration"
        } else {
            val sec = totalDuration % 60
            val min = totalDuration / 60
            if (min < 10 && sec < 10) "0$min:0$sec" else if (min < 10 && sec >= 10) "0$min:$sec" else if (min >= 10 && sec < 10) "$min:0$sec" else "$min:$sec"
        }
        textTimer.text = formatted
    }

    /**
     * Gets the formatted time.
     *
     * @param timeConsume Timestamp
     * @return String The formatted time
     */
    fun getFormattedTime(timeConsume: Int): String? {
        return if (timeConsume < 60) {
                if (timeConsume < 10) "00:0$timeConsume" else "00:$timeConsume"
            } else {
                val sec = timeConsume % 60
                val min = timeConsume / 60
                if (min < 10 && sec < 10) "0$min:0$sec" else if (min < 10 && sec >= 10) "0$min:$sec" else if (min >= 10 && sec < 10) "$min:0$sec" else "$min:$sec"
            }
    }

    /**
     * Get the date from the timestamp with the dd/MM/yyyy format
     *
     * @param timeStamp Time stamp of the message
     * @return String Formatted date
     */
    fun getDateFromTimestamp(timeStamp: Long): String? {
        val now = Date(timeStamp / 1000)
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(now.time)
    }

    /**
     * Check the time is equal with today or yesterday in the chat view.
     *
     * @param timestamp Timestamp of the chat
     * @param day       Today or yesterday
     * @return boolean True if equals with the day
     */
    fun equalsWithYesterday(timestamp: Long, day: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) //
        // Time part has
        // discarded
        val cal = Calendar.getInstance()
        if (day == Constants.YESTERDAY) cal.add(Calendar.DATE, -1)
        /**
         * Get yesterday's Date without time part
         */
        val yesterday: Date
        try {
            yesterday = dateFormat.parse(dateFormat.format(cal.time))
            val srcDate = Date(timestamp)
            val srcDateWithoutTime = dateFormat.parse(dateFormat.format(srcDate))
            /**
             * Checks src date equals yesterday.
             */
            return yesterday == srcDateWithoutTime
        } catch (e: ParseException) {
            LogMessage.e(e)
        }
        return false
    }
}