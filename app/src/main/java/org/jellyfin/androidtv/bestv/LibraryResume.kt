package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.ZoneOffset
import java.util.UUID

/** A server resume threshold must not hide progress saved on this TV. */
object LibraryResume {
    fun position(server: Long, lastPlayed: Long, local: Long, seen: Long): Long =
        if (local > 0 && seen > 0 && seen >= lastPlayed) local else server.coerceAtLeast(0)

    fun needsCatalogFallback(status: Int) = status in setOf(400, 404, 405, 501)

    fun localIds(values: Map<String, *>, prefix: String, limit: Int = 12): List<UUID> =
        values.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix) || (value as? Number)?.toLong()?.let { it > 0 } != true) return@mapNotNull null
            val seen = (values["seen_$key"] as? Number)?.toLong() ?: return@mapNotNull null
            if (seen <= 0) return@mapNotNull null
            val id = runCatching { UUID.fromString(key.removePrefix(prefix)) }.getOrNull() ?: return@mapNotNull null
            id to seen
        }.sortedByDescending { it.second }.take(limit).map { it.first }

    fun select(
        server: List<BaseItemDto>,
        local: List<BaseItemDto>,
        position: (BaseItemDto) -> Long,
        seen: (BaseItemDto) -> Long,
        limit: Int = 12,
    ): List<BaseItemDto> = (local + server).distinctBy { it.id }
        .filter { it.type in setOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE) && it.userData?.played != true && position(it) > 0 }
        .sortedByDescending { maxOf(seen(it), it.userData?.lastPlayedDate?.toInstant(ZoneOffset.UTC)?.toEpochMilli() ?: 0) }
        .distinctBy { if (it.type == BaseItemKind.EPISODE) it.seriesId ?: it.id else it.id }
        .take(limit)
}
