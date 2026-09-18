package com.auramusic.musixmatch

import com.auramusic.musixmatch.models.ApiResponse
import com.auramusic.musixmatch.models.LyricsBody
import com.auramusic.musixmatch.models.RichSyncBody
import com.auramusic.musixmatch.models.RichSyncLine
import com.auramusic.musixmatch.models.SubtitleBody
import com.auramusic.musixmatch.models.TokenBody
import com.auramusic.musixmatch.models.Track
import com.auramusic.musixmatch.models.TrackSearchBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import java.net.URI
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

private const val API_URL = "https://apic.musixmatch.com/ws/1.1/"
private const val APP_ID = "web-desktop-app-v1.0"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val FALLBACK_SECRET = "IEJ5E8XFaHQvIQNfs7IC"

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
private val client = HttpClient(OkHttp) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(json)
        // Musixmatch serves its JSON API responses as text/plain.
        json(json, ContentType.Text.Plain)
    }
    install(ContentEncoding) { gzip(); deflate() }
}

/** Unofficial Musixmatch web client. The upstream private endpoints may change without notice. */
object Musixmatch {
    private val tokenLock = Mutex()
    private val secretLock = Mutex()
    private var secret: String? = null
    private var userToken: String? = null

    suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null): Result<String> =
        runCatching {
            require(title.isNotBlank() && artist.isNotBlank()) { "Title and artist are required" }
            val tracks = request("track.search", mapOf(
                "q_track" to title, "q_artist" to artist, "page_size" to "10",
                "s_track_rating" to "desc", "f_has_lyrics" to "1",
            )).decode<TrackSearchBody>().trackList.map { it.track }
            val track = tracks.maxByOrNull { score(it, title, artist, album, duration) }
                ?: error("No lyrics candidate")

            if (track.hasRichSync != 0) runCatching {
                request("track.richsync.get", mapOf(
                    "track_id" to track.id.toString(),
                    "f_richsync_length" to (track.length.takeIf { it > 0 } ?: duration).toString(),
                    "f_richsync_length_max_deviation" to "10",
                ))
                    .decode<RichSyncBody>().richsync.body.toEnhancedLrc()
            }.getOrNull()?.takeIf(String::isNotBlank)?.let { return@runCatching it }

            if (track.hasSubtitles != 0) runCatching {
                request("track.subtitle.get", mapOf(
                    "track_id" to track.id.toString(),
                    "subtitle_format" to "lrc",
                    "f_subtitle_length" to (track.length.takeIf { it > 0 } ?: duration).toString(),
                    "f_subtitle_length_max_deviation" to "10",
                ))
                    .decode<SubtitleBody>().subtitle.body
            }.getOrNull()?.takeIf(String::isNotBlank)?.let { return@runCatching it }

            request("track.lyrics.get", mapOf("track_id" to track.id.toString()))
                .decode<LyricsBody>().lyrics.body.takeIf(String::isNotBlank) ?: error("Lyrics are empty")
        }

    private suspend fun request(method: String, values: Map<String, String>): ApiResponse {
        repeat(2) { attempt ->
            val token = token()
            val response = signedGet(method, values + mapOf("usertoken" to token))
            val status = response.message.header.statusCode
            if (status != 401 && status != 402) {
                if (status !in 200..299) error("Musixmatch API returned status $status")
                return response
            }
            if (attempt == 0) {
                tokenLock.withLock { if (userToken == token) userToken = null }
                secretLock.withLock { secret = null }
            }
        }
        error("Musixmatch authorization failed")
    }

    private suspend fun token(): String = tokenLock.withLock {
        userToken?.let { return@withLock it }
        val response = signedGet("token.get", emptyMap())
        val status = response.message.header.statusCode
        check(status == 200) {
            "Musixmatch token request failed with status $status" +
                response.message.header.hint?.let { " ($it)" }.orEmpty()
        }
        val value = response.decode<TokenBody>().userToken
        require(value.isNotBlank() && !value.startsWith("UpgradeOnly")) { "Musixmatch token unavailable" }
        value.also { userToken = it }
    }

    private suspend fun signedGet(method: String, values: Map<String, String>): ApiResponse {
        val params = linkedMapOf(
            "app_id" to APP_ID,
            "format" to "json",
        ).apply { putAll(values) }
        val query = params.entries.joinToString("&") { (k, v) ->
            "${k.encodeURLParameter()}=${v.encodeURLParameter(spaceToPlus = false)}"
        }
        val unsigned = "$API_URL$method?$query"
        val normalized = unsigned.replace("%20", "+").replace(" ", "+")
        val key = getSecret()
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val signature = Base64.getEncoder().encodeToString(
            mac.doFinal((normalized + date).toByteArray(StandardCharsets.UTF_8))
        )
        val response = client.get(
            "$normalized&signature=${signature.encodeURLParameter()}&signature_protocol=sha256"
        ) {
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cookie", "mxm_bab=AB")
        }
        if (response.status.value == 401 || response.status.value == 402) {
            return ApiResponse(ApiResponse.Message(ApiResponse.Header(response.status.value), json.parseToJsonElement("{}")))
        }
        return response.body()
    }

    private suspend fun getSecret(): String = secretLock.withLock {
        secret?.let { return@withLock it }
        val extracted = runCatching {
            val pageUrl = URI("https://www.musixmatch.com/")
            val html = client.get("https://www.musixmatch.com/search") {
                header("User-Agent", USER_AGENT)
                header("Cookie", "mxm_bab=AB")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            }.bodyAsText()
            val appScript = Regex("src=[\\\"']([^\\\"']*/_next/static/chunks/pages/_app-[^\\\"']+\\.js)")
                .find(html)?.groupValues?.get(1)
                ?: error("Musixmatch app script was not found")
            val source = client.get(pageUrl.resolve(appScript).toString()) {
                header("User-Agent", USER_AGENT)
                header("Accept", "*/*")
            }.bodyAsText()
            val encoded = Regex("from\\(\\s*[\\\"'](.*?)[\\\"']\\s*\\.split")
                .find(source)?.groupValues?.get(1)
                ?: error("Musixmatch signature secret was not found")
            String(Base64.getDecoder().decode(encoded.reversed()), StandardCharsets.UTF_8)
        }.getOrNull()
        (extracted ?: FALLBACK_SECRET).also { secret = it }
    }

    private inline fun <reified T> ApiResponse.decode(): T = json.decodeFromJsonElement(message.body)

    private fun score(track: Track, title: String, artist: String, album: String?, duration: Int): Double {
        fun similarity(a: String, b: String): Double {
            val left = a.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter(String::isNotBlank).toSet()
            val right = b.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter(String::isNotBlank).toSet()
            return if (left.isEmpty() || right.isEmpty()) 0.0 else left.intersect(right).size.toDouble() / left.union(right).size
        }
        var result = similarity(track.name, title) * 5 + similarity(track.artistName, artist) * 4
        if (!album.isNullOrBlank() && !track.albumName.isNullOrBlank()) result += similarity(track.albumName, album) * 2
        if (duration > 0 && track.length > 0) result += (1.0 - abs(track.length - duration) / 30.0).coerceAtLeast(-1.0) * 2
        return result
    }

    internal fun convertRichSyncToLrc(lines: List<RichSyncLine>): String = lines.joinToString("\n") { line ->
        buildString {
            append(timestamp(line.ts, '[', ']'))
            if (line.l.isNotEmpty()) line.l.forEach { part ->
                // Musixmatch emits spaces as separate RichSync parts. Timestamping those
                // whitespace-only parts makes enhanced-LRC parsers collapse the words.
                if (part.c.isBlank()) append(part.c)
                else append(timestamp(line.ts + part.o, '<', '>')).append(part.c)
            }
            else append(line.x.orEmpty())
        }
    }

    private fun String.toEnhancedLrc(): String =
        convertRichSyncToLrc(json.decodeFromString<List<RichSyncLine>>(this))

    private fun timestamp(seconds: Double, open: Char, close: Char): String {
        val milliseconds = (seconds.coerceAtLeast(0.0) * 1000).toLong()
        return "%c%02d:%02d.%03d%c".format(
            Locale.US,
            open,
            milliseconds / 60_000,
            milliseconds / 1000 % 60,
            milliseconds % 1000,
            close,
        )
    }
}
