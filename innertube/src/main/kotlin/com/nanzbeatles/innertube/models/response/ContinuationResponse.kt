package com.nanzbeatles.innertube.models.response

import com.nanzbeatles.innertube.models.Continuation
import com.nanzbeatles.innertube.models.MusicShelfRenderer
import kotlinx.serialization.Serializable

@Serializable
data class ContinuationResponse(
    val onResponseReceivedActions: List<ResponseAction>?,
    val continuationContents: ContinuationContents?,
) {

    @Serializable
    data class ResponseAction(
        val appendContinuationItemsAction: ContinuationItems?,
    )

    @Serializable
    data class ContinuationItems(
        val continuationItems: List<MusicShelfRenderer.Content>?,
    )

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: SectionListContinuation?,
        val musicPlaylistShelfContinuation: MusicPlaylistShelfContinuation?,
        val gridContinuation: GridContinuation?,
        val musicShelfContinuation: MusicShelfRenderer?,
    ) {
        @Serializable
        data class SectionListContinuation(
            val contents: List<MusicShelfRenderer.Content> = emptyList(),
            val continuations: List<Continuation>?,
        )

        @Serializable
        data class MusicPlaylistShelfContinuation(
            val contents: List<MusicShelfRenderer.Content> = emptyList(),
            val continuations: List<Continuation>?,
        )

        @Serializable
        data class GridContinuation(
            val items: List<com.nanzbeatles.innertube.models.GridRenderer.Item> = emptyList(),
            val continuations: List<Continuation>?,
        )
    }
}
