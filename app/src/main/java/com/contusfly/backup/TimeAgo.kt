package com.contusfly.backup

import android.content.Context
import android.util.Log
import com.contusfly.R
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

object TimeAgo {


    /**
     * Get the System Current Data
     *
     * @return Date today
     */
    private fun currentDate(): Date {
        val calendar: Calendar = Calendar.getInstance()
        return calendar.time
    }

    /**
     *  Given the date as a Relative Human Readable String
     *
     * @param dateInLong Long date in milliseconds
     * @param ctx Context android Context
     * @return String relative time
     */
    fun getTimeAgo(dateInLong: Long, ctx: Context): String {
        val curDate = currentDate()
        val now = curDate.time
        val dim = getTimeDistanceInMinutes(dateInLong)
        Log.d("TimeAgo ", "$dateInLong $now $dim")
        val timeAgo: String?
        timeAgo = when (dim) {
            0 -> {
                return ctx.resources.getString(R.string.date_util_just_now)
            }
            1 -> {
                "1 " + ctx.resources.getString(R.string.date_util_unit_minute)
            }
            in 2..59 -> {
                dim.toString() + " " + ctx.resources.getString(R.string.date_util_unit_minutes)
            }
            in 60..119 -> {
                val min = dim % 60
                "1 " + ctx.resources.getString(R.string.date_util_unit_hour) + " ${if (min == 1) " $min minute " else " $min minutes"} "
            }
            in 120..1439 -> {
                val min = dim % 60
                (dim / 60.toFloat()).roundToInt().toString() + " " + ctx.resources.getString(R.string.date_util_unit_hours) + " ${if (min == 1) " $min minute " else " $min minutes"} "
            }
            in 1440..2519 -> {
                "1 " + ctx.resources.getString(R.string.date_util_unit_day)
            }
            in 2520..43199 -> {
                (dim / 1440.toFloat()).roundToInt().toString() + " " + ctx.resources.getString(R.string.date_util_unit_days)
            }
            in 43200..86399 -> {
                "1 " + " " + ctx.resources.getString(R.string.date_util_unit_month)
            }
            in 86400..525599 -> {
                (dim / 43200.toFloat()).roundToInt().toString() + " " + ctx.resources.getString(R.string.date_util_unit_months)
            }
            in 525600..914399 -> {
                "1 " + " " + ctx.resources.getString(R.string.date_util_unit_year)
            }
            in 914400..1051199 -> {
                " 2 " + ctx.resources.getString(R.string.date_util_unit_years)
            }
            else -> {
                (dim / 525600.toFloat()).roundToInt().toString() + " " + ctx.resources.getString(R.string.date_util_unit_years)
            }
        }
        return timeAgo + " " + ctx.resources.getString(R.string.date_util_suffix)
    }

    /**
     * Returns the rounded difference between the current system time and given time in long
     *
     * @param time Long for which difference we need
     * @return Int difference between the time
     */
    private fun getTimeDistanceInMinutes(time: Long): Int {
        val timeDistance = currentDate().time - time
        return (abs(timeDistance) / 1000 / 60.toFloat()).roundToInt()
    }


}