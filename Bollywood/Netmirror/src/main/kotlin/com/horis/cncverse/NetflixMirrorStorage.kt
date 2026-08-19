package com.horis.cncverse

import android.content.Context
import android.content.SharedPreferences

object NetflixMirrorStorage {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        this.context = context.applicationContext
        this.prefs = context.getSharedPreferences("NetflixMirrorPrefs", Context.MODE_PRIVATE)
    }

    fun saveCookie(cookie: String) {
        val editor = prefs.edit()
        editor.putString("nf_cookie", cookie)
        editor.putLong("nf_cookie_timestamp", System.currentTimeMillis())
        editor.apply()
    }

    fun getCookie(): Pair<String?, Long> {
        return Pair(
            prefs.getString("nf_cookie", null),
            prefs.getLong("nf_cookie_timestamp", 0L)
        )
    }

    fun clearCookie() {
        val editor = prefs.edit()
        editor.remove("nf_cookie")
        editor.remove("nf_cookie_timestamp")
        editor.apply()
    }

    fun saveApiBase(url: String) {
        val editor = prefs.edit()
        editor.putString("nf_api_base", url)
        editor.putLong("nf_api_base_timestamp", System.currentTimeMillis())
        editor.apply()
    }

    fun getApiBase(): Pair<String?, Long> {
        return Pair(
            prefs.getString("nf_api_base", null),
            prefs.getLong("nf_api_base_timestamp", 0L)
        )
    }

    fun clearApiBase() {
        val editor = prefs.edit()
        editor.remove("nf_api_base")
        editor.remove("nf_api_base_timestamp")
        editor.apply()
    }
}