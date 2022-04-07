package com.contusfly.utils

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class ChatMsgTime {

    /**
     * hh:mm aa DateFormat
     */
    private var hhmmaaDateFormat: SimpleDateFormat? = null

    /**
     * h:mm aa DateFormat
     */
    private var hmmaaDateFormat: SimpleDateFormat? = null

    /**
     * hh:mm DateFormat
     */
    private var hhmmDateFormat: SimpleDateFormat? = null

    /**
     * h:mm DateFormat
     */
    private var hmmDateFormat: SimpleDateFormat? = null

    /**
     * Gets the day sent msg. The epoche time getting timestamp using date object getting the
     * current time using date format get the time format 12 or 24 getting the date format and
     * dateHourFormat using simpleDate format getting today time using calendar instance
     *
     * @param context    Instance of the startupActivityContext
     * @param epocheTime Timestamp of the Message
     * @return String Formatted date
     */
    fun getDaySentMsg(context: Context?, epocheTime: Long): String? {
        val dateHourFormat: SimpleDateFormat
        val timeLong = epocheTime / 1000
        val now = Date(timeLong)
        val cal = Calendar.getInstance()
        cal.time = now
        val hours = cal[Calendar.HOUR_OF_DAY]
        val format = if (DateFormat.is24HourFormat(context)) 24 else 12
        dateHourFormat = setDateHourFormat(format, hours)
        return dateHourFormat.format(timeLong)
    }

    /**
     * Set the date and time format
     *
     * @param format Hour format value
     * @param hours  Hour of the day
     * @return SimpleDateFormat Formatted date
     */
    fun setDateHourFormat(format: Int, hours: Int): SimpleDateFormat {
        val dateHourFormat: SimpleDateFormat
        if (format == 12) {
            if (hours < 10) dateHourFormat = getHhmmaaDateFormat() else dateHourFormat = getHmmaaDateFormat()
        } else {
            if (hours < 10) dateHourFormat = getHhmmDateFormat() else dateHourFormat = getHmmDateFormat()
        }
        return dateHourFormat
    }

    private fun getHhmmaaDateFormat(): SimpleDateFormat {
        if (hhmmaaDateFormat == null) {
            hhmmaaDateFormat = SimpleDateFormat("hh:mm aa", Locale.getDefault())
        }
        return hhmmaaDateFormat as SimpleDateFormat
    }

    private fun getHmmaaDateFormat(): SimpleDateFormat {
        if (hmmaaDateFormat == null) {
            hmmaaDateFormat = SimpleDateFormat("h:mm aa", Locale.getDefault())
        }
        return hmmaaDateFormat as SimpleDateFormat
    }

    private fun getHhmmDateFormat(): SimpleDateFormat {
        if (hhmmDateFormat == null) {
            hhmmDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        }
        return hhmmDateFormat as SimpleDateFormat
    }

    private fun getHmmDateFormat(): SimpleDateFormat {
        if (hmmDateFormat == null) {
            hmmDateFormat = SimpleDateFormat("H:mm", Locale.getDefault())
        }
        return hmmDateFormat as SimpleDateFormat
    }
}