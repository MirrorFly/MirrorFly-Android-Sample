package com.contusfly.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.contusfly.BuildConfig
import com.contusfly.TAG
import com.contusfly.constants.MobileApplication


/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
object SharedPreferenceManager {

    private val masterKey: MasterKey = MasterKey.Builder(MobileApplication.getContext())
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val nonEncryptedPreferences: SharedPreferences =  MobileApplication.getContext().getSharedPreferences(Constants.SHAREDPREFERENCE_STORAGE_NAME, Context.MODE_PRIVATE)

    val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
            MobileApplication.getContext(),
            Constants.SHAREDPREFERENCE_ENCRYPTED_STORAGE_NAME,
            masterKey, // masterKey created above
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).apply {
            if (nonEncryptedPreferences.all.isNotEmpty() && this.all.isEmpty()) {
                // migrate non encrypted shared preferences
                // to encrypted shared preferences and clear them once finished.
                nonEncryptedPreferences.copyTo(this)
                nonEncryptedPreferences.clear()
            }
        }

    private var editor = sharedPreferences.edit()


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
        return getString("username") + "@" + com.contus.flycommons.Constants.getDomain()
    }

    /**
     * Clear all preference.
     */
    fun clearAllPreference() {
        val versionName: String = getString(Constants.APP_VERSION).toString()
        val token: String = getString(Constants.FIRE_BASE_TOKEN).toString()
        val askPermission = getBoolean(Constants.ASK_PERMISSION)
        editor.clear()
        editor.commit()
        setString(Constants.APP_VERSION, versionName)
        setString(Constants.FIRE_BASE_TOKEN, token)
        setBoolean(Constants.ASK_PERMISSION, askPermission)
    }
}

private fun SharedPreferences.copyTo(dest: SharedPreferences) {
    for (entry in all.entries) {
        val key = entry.key
        val value: Any? = entry.value
        dest.set(key, value)
    }
}

private fun SharedPreferences.set(key: String, value: Any?) {
    when (value) {
        is String? -> edit { it.putString(key, value) }
        is Int -> edit { it.putInt(key, value.toInt()) }
        is Boolean -> edit { it.putBoolean(key, value) }
        is Float -> edit { it.putFloat(key, value.toFloat()) }
        is Long -> edit { it.putLong(key, value.toLong()) }
        else -> {
            LogMessage.v(TAG, "Unsupported Type: $value")
        }
    }
}

private fun SharedPreferences.clear() {
    edit { it.clear() }
}

inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
    val editor = this.edit()
    operation(editor)
    editor.apply()
}

