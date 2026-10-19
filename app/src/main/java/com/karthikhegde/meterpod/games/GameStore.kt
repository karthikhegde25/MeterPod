package com.karthikhegde.meterpod.games

import android.content.Context

/** Tiny SharedPreferences wrapper for the high scores / best times of the games (survives closing the app). */
object GameStore {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("meterpod_games", Context.MODE_PRIVATE)
    fun getInt(ctx: Context, key: String, def: Int = 0): Int = prefs(ctx).getInt(key, def)
    fun putInt(ctx: Context, key: String, value: Int) { prefs(ctx).edit().putInt(key, value).apply() }
}
