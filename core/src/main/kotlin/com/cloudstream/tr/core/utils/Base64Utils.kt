package com.cloudstream.tr.core.utils

import android.annotation.SuppressLint

object Base64Utils {
    @SuppressLint("NewApi")
    fun decode(str: String): ByteArray {
        return try {
            val res = android.util.Base64.decode(str, android.util.Base64.DEFAULT)
            res ?: java.util.Base64.getDecoder().decode(str)
        } catch (_: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
