package org.jellyfin.androidtv.bestv
import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.*
import kotlinx.serialization.json.*
private fun item(id: Int, extra: String="", type: String="Movie"): BaseItemDto {
 val raw="""{"Id":"00000000-0000-0000-0000-${id.toString().padStart(12,'0')}","Type":"$type","Name":"Movie ${id.toString().padStart(3,'0')}"$extra}"""
 val obj=ApiSerializer.json.parseToJsonElement(raw).jsonObject
 val normal=buildJsonObject {obj.forEach { (k,v)->put(k,v) };obj["UserData"]?.jsonObject?.let {user->put("UserData",buildJsonObject {put("PlayCount",0);put("IsFavorite",false);put("Key",id.toString());put("ItemId",obj.getValue("Id"));put("Played",false);put("PlaybackPositionTicks",0);user.forEach { (k,v)->put(k,v) }})}}
 return ApiSerializer.json.decodeFromJsonElement<BaseItemDto>(normal)
}
fun main(){
 var checks=0
 fun verify(name: String,ok: Boolean){check(ok){name};checks++;println("PASS $name")}
 val all=(1..86).map {item(it,",\"CommunityRating\":${it/10.0},\"Genres\":[\"${if(it%2==0)"Drama" else "Action"}\"],\"ProductionYear\":${2000+it%3}")}
 val q=LibraryBrowse.Query()
 verify("complete filtered scope before page",LibraryBrowse.select(all,q.copy(genre="Drama")).size==43)
 verify("rating sort reaches item outside first page",LibraryBrowse.select(all,q.copy(sort=LibraryBrowse.Sort.RATING)).first().id==all.last().id)
 verify("year and genre combine",LibraryBrowse.select(all,q.copy(genre="Drama",year=2001)).all {it.productionYear==2001&&"Drama" in it.genres.orEmpty()})
 verify("paging preserves exact total",LibraryBrowse.select(all,q).drop(60).take(60).size==26)
 val unseen=item(101);val watching=item(102,",\"UserData\":{\"PlaybackPositionTicks\":10000,\"Played\":false}")
 val finished=item(103,",\"UserData\":{\"Played\":true,\"LastPlayedDate\":\"2026-11-01T00:00:00Z\"}")
 val states=listOf(unseen,watching,finished)
 verify("unseen excludes finished and partial",LibraryBrowse.select(states,q.copy(state=LibraryBrowse.State.UNSEEN))==listOf(unseen))
 verify("watching excludes finished",LibraryBrowse.select(states,q.copy(state=LibraryBrowse.State.WATCHING))==listOf(watching))
 verify("finished filter",LibraryBrowse.select(states,q.copy(state=LibraryBrowse.State.FINISHED))==listOf(finished))
 verify("recent includes finished",LibraryBrowse.select(states,q.copy(recent=true)).contains(finished))
 val episodes=listOf(item(201,",\"SeriesId\":\"00000000-0000-0000-0000-000000000999\",\"UserData\":{\"LastPlayedDate\":\"2026-11-01T00:00:00Z\"}","Episode"),item(202,",\"SeriesId\":\"00000000-0000-0000-0000-000000000999\",\"UserData\":{\"LastPlayedDate\":\"2026-11-02T00:00:00Z\"}","Episode"))
 verify("recent aggregates latest actual episode",LibraryBrowse.select(episodes,q.copy(recent=true)).single().id==episodes.last().id)
 verify("episodes excluded from movie/series home",LibraryBrowse.select(episodes,q).isEmpty())
 for(asc in listOf(false,true))verify("unknown rating remains last ($asc)",LibraryBrowse.select(listOf(unseen)+all,q.copy(sort=LibraryBrowse.Sort.RATING,ascending=asc)).last().id==unseen.id)
 val uhd=item(301,",\"MediaStreams\":[{\"Type\":\"Video\",\"IsInterlaced\":false,\"IsDefault\":false,\"IsForced\":false,\"IsHearingImpaired\":false,\"IsExternal\":false,\"IsTextSubtitleStream\":false,\"SupportsExternalStream\":false,\"Index\":0,\"Width\":3840,\"Height\":1606}],\"ProductionLocations\":[\"France\"]")
 val hd=item(302,",\"MediaStreams\":[{\"Type\":\"Video\",\"IsInterlaced\":false,\"IsDefault\":false,\"IsForced\":false,\"IsHearingImpaired\":false,\"IsExternal\":false,\"IsTextSubtitleStream\":false,\"SupportsExternalStream\":false,\"Index\":0,\"Width\":1920,\"Height\":808}],\"ProductionLocations\":[\"China\"]")
 verify("cropped 4K uses actual width",LibraryBrowse.resolution(uhd)==2160)
 verify("cropped 1080 uses actual width",LibraryBrowse.resolution(hd)==1080)
 verify("unknown resolution absent",LibraryBrowse.resolution(unseen)==null)
 verify("resolution filters actual stream",LibraryBrowse.select(listOf(uhd,hd,unseen),q.copy(resolution=2160))==listOf(uhd))
 verify("region filters explicit production location",LibraryBrowse.select(listOf(uhd,hd),q.copy(region="China"))==listOf(hd))
 verify("absent region has no invented value",LibraryBrowse.regions(unseen).isEmpty())
 val first=LibraryBrowse.page(all,0,6);verify("first page range and disabled previous",first.range=="1–6 / 86"&&!first.previous&&first.next)
 val last=LibraryBrowse.page(all,84,6);verify("last page range and disabled next",last.range=="85–86 / 86"&&last.previous&&!last.next)
 verify("outdated page clamps after filtering",LibraryBrowse.page(all.take(2),84,6).range=="1–2 / 2")
 verify("empty range never claims item one",LibraryBrowse.page(emptyList(),60,6).range=="0–0 / 0")
 verify("large text page contains five posters",LibraryBrowse.page(all,0,5).items.size==5)
 val namedMovie=item(401,",\"OriginalTitle\":\"Film Alias\"")
 val namedSeries=item(402,type="Series")
 val namedLive=item(403,type="TvChannel")
 val namedAudio=item(404,type="Audio")
 val names=listOf(namedMovie,namedSeries,namedLive,namedAudio)
 verify("media search excludes live and audio",LibraryBrowse.search(names,"Movie").size==2)
 verify("movie search preserves category",LibraryBrowse.search(names,"Movie",BaseItemKind.MOVIE)==listOf(namedMovie))
 verify("series search preserves category",LibraryBrowse.search(names,"Movie",BaseItemKind.SERIES)==listOf(namedSeries))
 verify("search original title case insensitive",LibraryBrowse.search(names,"film ALIAS")==listOf(namedMovie))
 verify("blank search does not dump catalog",LibraryBrowse.search(names,"  ").isEmpty())
 verify("collection search cannot access outside snapshot",LibraryBrowse.search(listOf(namedSeries),"Film Alias").isEmpty())
 println("$checks library browse checks passed")
}
