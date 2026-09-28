/**
 * NanzBeatles Project (C) 2026
 */
package com.nanzbeatles.nanas.alarm

/** Source pool the alarm picks songs from. */
enum class AlarmSource(val displayName: String) {
    DOWNLOADS("Unduhan"),
    CACHED("Cache"),
    PLAYLIST("Daftar Putar"),
    ;

    companion object {
        fun fromName(name: String?): AlarmSource =
            values().firstOrNull { it.name == name } ?: DOWNLOADS
    }
}
