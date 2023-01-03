package com.contusfly.chat

import com.contus.flycommons.Constants

object MapUtils {

    /**
     * Get the image uri for the particular location
     *
     * @param latitude  Latitude of the location
     * @param longitude Longitude of the location
     * @return String Uri of the location
     */
    fun getMapImageUri(latitude: Double, longitude: Double): String {
        return ("https://maps.googleapis.com/maps/api/staticmap?center=" + latitude + "," + longitude
                + "&zoom=13&size=300x200&markers=color:red|" + latitude + "," + longitude + "&key="
                + Constants.ANDROID_KEY)
    }
}