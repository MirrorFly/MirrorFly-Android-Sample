package com.contusfly.utils

import android.content.Context
import com.contusfly.R
import com.contusfly.constants.MobileApplication
import com.contusfly.BuildConfig

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object SharedPreferenceManager {

    private val sharedPreferences = MobileApplication.getContext().getSharedPreferences(Constants.SHAREDPREFERENCE_STORAGE_NAME, Context.MODE_PRIVATE)
    var editor = sharedPreferences.edit()


    /**
     * Set Boolean in preference.
     *
     * @param key   the key
     * @param value the value
     */
    fun setBoolean(key: String?, value: Boolean) {
        editor.putBoolean(key, value)
        editor.commit()
    }

    /**
     * Get Boolean from preference.
     *
     * @param key the key
     * @return boolean Value from preference
     */
    fun getBoolean(key: String?): Boolean {
        return sharedPreferences.getBoolean(key, false)
    }

    /**
     * Set String in preference.
     *
     * @param key   the key
     * @param value the value
     */
    fun setString(key: String?, value: String?) {
        editor.putString(key, value)
        editor.commit()
    }

    /**
     * Get the string from preference.
     *
     * @param key the key
     * @return String Value from preference
     */
    fun getString(key: String?): String {
        return sharedPreferences.getString(key, Constants.EMPTY_STRING).toString()
    }

    /**
     * Set int in preference.
     *
     * @param key   the key
     * @param value the value
     */
    fun setInt(key: String?, value: Int) {
        editor.putInt(key, value)
        editor.commit()
    }

    /**
     * Get the int from preference.
     *
     * @param key the key
     * @return Int Value from preference
     */
    fun getInt(key: String?): Int {
        return sharedPreferences.getInt(key, 0)
    }

    fun getCurrentUserJid(): String {
        return getString("username") + "@" + BuildConfig.XMPP_DOMAIN
    }

    /**
     * Clear all preference.
     */
    fun clearAllPreference() {
        val versionName: String = getString(Constants.APP_VERSION).toString()
        val token: String = getString(Constants.FIRE_BASE_TOKEN).toString()
        editor.clear()
        editor.commit()
        setString(Constants.APP_VERSION, versionName)
        setString(Constants.FIRE_BASE_TOKEN, token)
    }
}