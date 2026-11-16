package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType

/** Filter the complete scope before paging; never derive facets or totals from the visible page. */
object LibraryBrowse {
    enum class Sort(val label: String) { ADDED("最近添加"), WATCHED("最近观看"), YEAR("发布时间"), RATING("评分"), NAME("名称"), DURATION("时长") }
    enum class State(val label: String) { ALL("全部"), UNSEEN("未看"), WATCHING("在看"), FINISHED("已看") }
    data class Query(val kind: BaseItemKind?=null, val recent: Boolean=false, val state: State=State.ALL,
        val minimumRating: Double?=null, val unknownYear: Boolean=false, val favorites: Boolean=false, val genre: String?=null, val year: Int?=null, val resolution: Int?=null, val region: String?=null, val sort: Sort=Sort.NAME, val ascending: Boolean=sort==Sort.NAME)
    data class Page(val items: List<BaseItemDto>,val start: Int,val total: Int,val size: Int){
        val previous get()=start>0
        val next get()=start+items.size<total
        val range get()="${if(total==0)0 else start+1}-${start+items.size}"
    }
    fun page(items: List<BaseItemDto>,start: Int,size: Int): Page {
        require(size>0)
        val last=if(items.isEmpty())0 else (items.size-1)/size*size
        val offset=(start.coerceAtLeast(0)/size*size).coerceAtMost(last)
        return Page(items.drop(offset).take(size),offset,items.size,size)
    }
    fun seen(item: BaseItemDto) = item.userData?.let { it.played==true || (it.playCount ?: 0)>0 || it.lastPlayedDate!=null || (it.playbackPositionTicks ?: 0)>0 }==true || watching(item)
    fun watching(item: BaseItemDto) = item.userData?.played!=true && ((item.userData?.playbackPositionTicks ?: 0)>0 ||
        (item.recursiveItemCount ?: 0)>0 && (item.userData?.unplayedItemCount ?: item.recursiveItemCount ?: 0) in 1 until (item.recursiveItemCount ?: 0))
    fun progress(item: BaseItemDto): Double? {
        if(item.userData?.played==true)return 1.0
        val position=item.userData?.playbackPositionTicks ?: 0
        if(item.type==BaseItemKind.SERIES){
            val total=item.recursiveItemCount?.takeIf {it>0} ?: return null
            val remaining=item.userData?.unplayedItemCount ?: return null
            if(remaining==total&&position>0)return null
            return ((total-remaining).toDouble()/total).coerceIn(0.0,1.0)
        }
        if(position>0)return LibraryMetadata.durationTicks(item)?.let {(position.toDouble()/it).coerceIn(0.0,1.0)}
        return if(item.userData?.played==false&&!seen(item))0.0 else null
    }
    fun resolution(item: BaseItemDto): Int? {
        val streams=item.mediaStreams.orEmpty()+item.mediaSources.orEmpty().flatMap {it.mediaStreams.orEmpty()}
        return streams.filter {it.type==MediaStreamType.VIDEO}.mapNotNull {s->
            val w=s.width ?: 0;val h=s.height ?: 0
            when {w>=3840||h>=2160->2160;w>=1920||h>=1080->1080;w>=1280||h>=720->720;w>0&&h>0->480;else->null}
        }.maxOrNull()
    }
    /** Highest available source tier; unknown sizes stay absent, including series without episode data. */
    fun quality(item:BaseItemDto,episodes:List<BaseItemDto> = emptyList()):String? {
        val items=listOf(item)+if(item.type==BaseItemKind.SERIES)episodes.filter {it.seriesId==item.id} else emptyList()
        return items.flatMap {it.mediaStreams.orEmpty()+it.mediaSources.orEmpty().flatMap {source->source.mediaStreams.orEmpty()}}
            .filter {it.type==MediaStreamType.VIDEO}.mapNotNull {stream->
                val w=stream.width?:0;val h=stream.height?:0
                when {w>=7680||h>=4320->4320;w>=3840||h>=2160->2160;w>=2560||h>=1440->1440;
                    w>=1920||h>=1080->1080;w>=1280||h>=720->720;h>0->h;else->null}
            }.maxOrNull()?.let {when(it){4320->"8K";2160->"4K";else->"${it}p"}}
    }
    fun regions(item: BaseItemDto)=item.productionLocations.orEmpty().map {it.trim()}.filter {it.isNotEmpty()}.distinct()
    fun top(item: BaseItemDto) = item.type in listOf(BaseItemKind.MOVIE,BaseItemKind.SERIES,BaseItemKind.VIDEO)
    fun search(items: List<BaseItemDto>,query: String,kind: BaseItemKind?=null): List<BaseItemDto> {
        val text=query.trim();if(text.isEmpty())return emptyList()
        return items.filter {top(it)&&(kind==null||it.type==kind)&&
            (it.name.orEmpty().contains(text,true)||it.originalTitle.orEmpty().contains(text,true))}
    }
    fun select(all: List<BaseItemDto>, q: Query): List<BaseItemDto> {
        // Episodes are server data too. Aggregate the available episodes without turning the
        // latest episode's played flag into a completed whole series.
        val episodes=all.filter {it.type==BaseItemKind.EPISODE&&it.seriesId!=null}.groupBy {it.seriesId}
        val titles=all.filter {top(it)}.map {series->
            val children=episodes[series.id].orEmpty()
            if(series.type!=BaseItemKind.SERIES||children.isEmpty())series else {
                val latest=children.maxByOrNull {it.userData?.lastPlayedDate ?: java.time.LocalDateTime.MIN}
                val data=series.userData ?: latest?.userData
                val serverTotal=series.recursiveItemCount?.takeIf {it>0}
                val serverRemaining=series.userData?.unplayedItemCount?.takeIf {serverTotal!=null&&it in 0..serverTotal}
                val completeChildren=children.all {it.userData!=null}&&(serverTotal==null||serverTotal==children.size)
                val watched=children.count {it.userData?.played==true}
                val total=serverTotal ?: children.size.takeIf {completeChildren}
                val remaining=serverRemaining ?: (children.size-watched).takeIf {completeChildren}
                series.copy(recursiveItemCount=total,userData=data?.copy(
                    played=series.userData?.played==true || (series.userData==null&&completeChildren&&watched==children.size),
                    unplayedItemCount=remaining,
                    playbackPositionTicks=series.userData?.playbackPositionTicks?.takeIf {it>0} ?: if(children.any {watching(it)})1 else 0,
                    lastPlayedDate=listOfNotNull(series.userData?.lastPlayedDate,latest?.userData?.lastPlayedDate).maxOrNull(),
                    isFavorite=series.userData?.isFavorite==true))
            }
        }
        val candidates=if(q.recent)(titles.filter {seen(it)} + all.filter {it.type==BaseItemKind.EPISODE && seen(it) && titles.none {series->series.id==it.seriesId}})
            .sortedByDescending {it.userData?.lastPlayedDate}.distinctBy {it.seriesId ?: it.id}
            else titles
        val filtered=candidates.filter { item ->
            (q.kind==null || item.type==q.kind || q.kind==BaseItemKind.SERIES&&item.type==BaseItemKind.EPISODE) &&
                (!q.favorites || item.userData?.isFavorite==true) &&
                (!q.unknownYear || LibraryMetadata.year(item)==null) &&
                (q.minimumRating==null || item.communityRating?.takeIf {it.isFinite()&&it>0}?.let {it>=q.minimumRating}==true) && (q.genre==null || q.genre in item.genres.orEmpty()) &&
                (q.year==null || LibraryMetadata.year(item)==q.year) && (q.resolution==null || resolution(item)==q.resolution) &&
                (q.region==null || q.region in regions(item)) && when(q.state) {
                    State.ALL -> true; State.UNSEEN -> item.userData?.played==false && !seen(item) && !watching(item); State.WATCHING -> watching(item); State.FINISHED -> item.userData?.played==true
                }
        }
        val comparator: Comparator<BaseItemDto> = when(q.sort) {
            Sort.ADDED -> compareBy(nullsLast()) { it.dateCreated }
            Sort.WATCHED -> compareBy(nullsLast()) { it.userData?.lastPlayedDate }
            Sort.YEAR -> compareBy(nullsLast()) { LibraryMetadata.releaseDate(it) }
            Sort.RATING -> compareBy(nullsLast()) { it.communityRating }
            Sort.DURATION -> compareBy(nullsLast()) { it.runTimeTicks }
            Sort.NAME -> compareBy { (it.sortName ?: it.name.orEmpty()).lowercase() }
        }
        // Unknown values always trail actual data in either direction.
        val known: (BaseItemDto)->Boolean = when(q.sort) {
            Sort.ADDED -> { {it.dateCreated!=null} }; Sort.WATCHED -> { {it.userData?.lastPlayedDate!=null} }
            Sort.YEAR -> { {LibraryMetadata.releaseDate(it)!=null} }; Sort.RATING -> { {LibraryMetadata.rating(it)!=null} }; Sort.NAME -> { {true} }; Sort.DURATION -> { {(it.runTimeTicks ?: 0)>0} }
        }
        return filtered.sortedWith(compareByDescending<BaseItemDto> {known(it)}.then(if(q.ascending)comparator else comparator.reversed()).thenBy {(it.sortName ?: it.name.orEmpty()).lowercase()}.thenBy {it.id})
    }
}
