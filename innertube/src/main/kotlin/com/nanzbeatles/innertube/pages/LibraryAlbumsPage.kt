package com.nanzbeatles.innertube.pages

import com.nanzbeatles.innertube.models.Album
import com.nanzbeatles.innertube.models.AlbumItem
import com.nanzbeatles.innertube.models.Artist
import com.nanzbeatles.innertube.models.ArtistItem
import com.nanzbeatles.innertube.models.MusicResponsiveListItemRenderer
import com.nanzbeatles.innertube.models.MusicTwoRowItemRenderer
import com.nanzbeatles.innertube.models.PlaylistItem
import com.nanzbeatles.innertube.models.SongItem
import com.nanzbeatles.innertube.models.YTItem
import com.nanzbeatles.innertube.models.oddElements
import com.nanzbeatles.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
