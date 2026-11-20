package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/** Identity bridge into the shared player; these IDs never reach a media server. */
object CctvPlayback {
    fun highestQuality(formats:List<Triple<Int,Int,Int>>):Int = formats.indices.maxWithOrNull(compareBy<Int> {formats[it].first.coerceAtLeast(0)}.thenBy {formats[it].second.coerceAtLeast(0)}.thenBy {formats[it].third.coerceAtLeast(0)}) ?: -1
    private fun id(guid: String)=UUID.nameUUIDFromBytes(("cctv:"+guid).toByteArray(Charsets.UTF_8))
    fun item(album: CctvAlbum,episode: CctvEpisode)=BaseItemDto(id=id(episode.guid),name=episode.title,seriesName=album.title,type=BaseItemKind.VIDEO)
    fun episode(itemId: UUID,episodes: List<CctvEpisode>)=episodes.firstOrNull {id(it.guid)==itemId}
    fun next(current: CctvEpisode,episodes: List<CctvEpisode>)=episodes.indexOfFirst {it.guid==current.guid}.takeIf {it>=0}?.let {episodes.getOrNull(it+1)}
}
