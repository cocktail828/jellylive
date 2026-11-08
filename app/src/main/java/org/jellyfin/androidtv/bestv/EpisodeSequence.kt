package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** Playback order uses actual regular season/episode numbers, never the API response order. */
object EpisodeSequence {
    fun next(current: BaseItemDto, items: List<BaseItemDto>): BaseItemDto? {
        if(current.type!=BaseItemKind.EPISODE||current.seriesId==null||(current.parentIndexNumber ?: 0)<=0||(current.indexNumber ?: 0)<=0)return null
        val order=items.filter {it.type==BaseItemKind.EPISODE&&it.seriesId==current.seriesId&&(it.parentIndexNumber ?: 0)>0&&(it.indexNumber ?: 0)>0}
            .distinctBy {it.id}.sortedWith(compareBy<BaseItemDto> {it.parentIndexNumber}.thenBy {it.indexNumber}.thenBy {it.id})
        return order.firstOrNull {(it.parentIndexNumber!!>current.parentIndexNumber!!)||(it.parentIndexNumber==current.parentIndexNumber&&it.indexNumber!!>current.indexNumber!!)}
    }
}
