package com.example.leanbackpocmvvm.utils

import android.content.Context
import android.os.Build
import android.util.TypedValue

fun dpToPx(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}

fun isAndroidVersion9Supported(): Boolean {
    return Build.VERSION.SDK_INT == Build.VERSION_CODES.P
}