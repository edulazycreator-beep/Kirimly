package com.kirimly.app.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * App-wide string lookup that works in composables, ViewModels and services alike.
 * Strings live in res/values (English, default) and res/values-in (Indonesian).
 */
object AppText {
    lateinit var context: Context
}

fun tr(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) AppText.context.getString(id) else AppText.context.getString(id, *args)
