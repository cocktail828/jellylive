package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale

object LibraryMetadata {
    fun year(item: BaseItemDto): Int? = item.productionYear ?: item.premiereDate?.year
    fun releaseDate(item: BaseItemDto) = item.premiereDate ?: item.productionYear?.let {java.time.LocalDateTime.of(it,1,1,0,0)}
    fun rating(item: BaseItemDto): String? = item.communityRating
        ?.takeIf { it.isFinite() && it > 0 }
        ?.let { String.format(Locale.ROOT, "%.1f", it) }
}
