package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType

/** Filter the complete scope before paging; never derive facets or totals from the visible page. */
object LibraryBrowse {
    enum class Sort(val label: String) { ADDED("最近添加"), WATCHED("最近观看"), YEAR("上映年份"), RATING("评分"), NAME("名称") }
    enum class State(val label: String) { ALL("全部状态"), UNSEEN("未看"), WATCHING("观看中"), FINISHED("已看") }
    data class Query(val kind: BaseItemKind?=null, val recent: Boolean=false, val state: State=State.ALL,
        val genre: String?=null, val year: Int?=null, val resolution: Int?=null, val region: String?=null, val sort: Sort=Sort.ADDED, val ascending: Boolean=false)
    data class Page(val items: List<BaseItemDto>,val start: Int,val total: Int,val size: Int){
        val previous get()=start>0
        val next get()=start+items.size<total
        val range get()="${if(total==0)0 else start+1}–${start+items.size} / $total"
    }
    fun page(items: List<BaseItemDto>,start: Int,size: Int): Page {
        require(size>0)
        val last=if(items.isEmpty())0 else (items.size-1)/size*size
        val offset=(start.coerceAtLeast(0)/size*size).coerceAtMost(last)
        return Page(items.drop(offset).take(size),offset,items.size,size)
    }
    fun seen(item: BaseItemDto) = item.userData?.let { it.played==true || (it.playCount ?: 0)>0 || it.lastPlayedDate!=null || (it.playbackPositionTicks ?: 0)>0 }==true
    fun watching(item: BaseItemDto) = item.userData?.played!=true && (item.userData?.playbackPositionTicks ?: 0)>0
    fun resolution(item: BaseItemDto): Int? {
        val streams=item.mediaStreams.orEmpty()+item.mediaSources.orEmpty().flatMap {it.mediaStreams.orEmpty()}
        return streams.filter {it.type==MediaStreamType.VIDEO}.mapNotNull {s->
            val w=s.width ?: 0;val h=s.height ?: 0
            when {w>=3840||h>=2160->2160;w>=1920||h>=1080->1080;w>=1280||h>=720->720;w>0&&h>0->480;else->null}
        }.maxOrNull()
    }
    fun regions(item: BaseItemDto)=item.productionLocations.orEmpty().map {it.trim()}.filter {it.isNotEmpty()}.distinct()
    fun top(item: BaseItemDto) = item.type in listOf(BaseItemKind.MOVIE,BaseItemKind.SERIES,BaseItemKind.VIDEO)
    fun search(items: List<BaseItemDto>,query: String,kind: BaseItemKind?=null): List<BaseItemDto> {
        val text=query.trim();if(text.isEmpty())return emptyList()
        return items.filter {top(it)&&(kind==null||it.type==kind)&&
            (it.name.orEmpty().contains(text,true)||it.originalTitle.orEmpty().contains(text,true))}
    }
    fun select(all: List<BaseItemDto>, q: Query): List<BaseItemDto> {
        val candidates=if(q.recent) all.filter { seen(it) && it.type in listOf(BaseItemKind.MOVIE,BaseItemKind.EPISODE,BaseItemKind.VIDEO) }
            .sortedByDescending { it.userData?.lastPlayedDate }.distinctBy { it.seriesId ?: it.id }
            else all.filter { top(it) }
        val filtered=candidates.filter { item ->
            (q.kind==null || item.type==q.kind) && (q.genre==null || q.genre in item.genres.orEmpty()) &&
                (q.year==null || LibraryMetadata.year(item)==q.year) && (q.resolution==null || resolution(item)==q.resolution) &&
                (q.region==null || q.region in regions(item)) && when(q.state) {
                    State.ALL -> true; State.UNSEEN -> !seen(item); State.WATCHING -> watching(item); State.FINISHED -> item.userData?.played==true
                }
        }
        val comparator: Comparator<BaseItemDto> = when(q.sort) {
            Sort.ADDED -> compareBy(nullsLast()) { it.dateCreated }
            Sort.WATCHED -> compareBy(nullsLast()) { it.userData?.lastPlayedDate }
            Sort.YEAR -> compareBy(nullsLast()) { LibraryMetadata.year(it) }
            Sort.RATING -> compareBy(nullsLast()) { it.communityRating }
            Sort.NAME -> compareBy { (it.sortName ?: it.name.orEmpty()).lowercase() }
        }
        // Unknown values always trail actual data in either direction.
        val known: (BaseItemDto)->Boolean = when(q.sort) {
            Sort.ADDED -> { {it.dateCreated!=null} }; Sort.WATCHED -> { {it.userData?.lastPlayedDate!=null} }
            Sort.YEAR -> { {LibraryMetadata.year(it)!=null} }; Sort.RATING -> { {LibraryMetadata.rating(it)!=null} }; Sort.NAME -> { {true} }
        }
        return filtered.sortedWith(compareByDescending<BaseItemDto> {known(it)}.then(if(q.ascending)comparator else comparator.reversed()).thenBy {it.id})
    }
}
