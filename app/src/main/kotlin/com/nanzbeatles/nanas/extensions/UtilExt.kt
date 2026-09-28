/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.extensions

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }
