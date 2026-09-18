package com.auramusic.musixmatch.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiResponse(val message: Message) {
    @Serializable
    data class Message(
        val header: Header,
        val body: JsonElement = JsonObject(emptyMap()),
    )

    @Serializable
    data class Header(
        @SerialName("status_code") val statusCode: Int = 0,
        val hint: String? = null,
    )
}

@Serializable
data class TokenBody(@SerialName("user_token") val userToken: String)

@Serializable
data class TrackSearchBody(@SerialName("track_list") val trackList: List<TrackItem> = emptyList())

@Serializable
data class TrackItem(val track: Track)

@Serializable
data class Track(
    @SerialName("track_id") val id: Long,
    @SerialName("track_name") val name: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("track_length") val length: Int = 0,
    @SerialName("has_richsync") val hasRichSync: Int = 0,
    @SerialName("has_subtitles") val hasSubtitles: Int = 0,
    @SerialName("has_lyrics") val hasLyrics: Int = 0,
)

@Serializable
data class RichSyncBody(val richsync: RichSync)

@Serializable
data class RichSync(@SerialName("richsync_body") val body: String)

@Serializable
data class SubtitleBody(val subtitle: Subtitle)

@Serializable
data class Subtitle(@SerialName("subtitle_body") val body: String)

@Serializable
data class LyricsBody(val lyrics: Lyrics)

@Serializable
data class Lyrics(@SerialName("lyrics_body") val body: String)

@Serializable
data class RichSyncLine(
    val ts: Double,
    val te: Double? = null,
    val l: List<RichSyncPart> = emptyList(),
    val x: String? = null,
)

@Serializable
data class RichSyncPart(val c: String, val o: Double = 0.0)
