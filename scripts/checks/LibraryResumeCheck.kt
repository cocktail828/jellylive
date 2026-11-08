package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.api.client.util.ApiSerializer
import org.jellyfin.sdk.model.api.*
import kotlinx.serialization.json.*
import java.util.UUID

private fun resumeItem(id: Int,type: BaseItemKind=BaseItemKind.MOVIE,played: Boolean=false,position: Long=0,series: UUID?=null): BaseItemDto {
 val uuid="00000000-0000-0000-0000-${id.toString().padStart(12,'0')}"
 return ApiSerializer.json.decodeFromJsonElement<BaseItemDto>(buildJsonObject {
  put("Id",uuid);put("Type",type.serialName);put("Name","Media $id");if(series!=null)put("SeriesId",series.toString())
  put("UserData",buildJsonObject {put("PlayCount",0);put("IsFavorite",false);put("Key",id.toString());put("ItemId",uuid);put("Played",played);put("PlaybackPositionTicks",position)})
 })
}
fun main(){
 var checks=0
 fun verify(name: String,ok: Boolean){check(ok){name};checks++;println("PASS $name")}
 for(code in listOf(400,404,405,501))verify("unsupported resume route falls back ($code)",LibraryResume.needsCatalogFallback(code))
 for(code in listOf(401,403,408,429,500,503))verify("real request failure is preserved ($code)",!LibraryResume.needsCatalogFallback(code))
 verify("failed-open zero never erases server progress",LibraryResume.position(60000,100,0,200)==60000L)
 verify("latest positive local progress wins",LibraryResume.position(60000,100,25000,200)==25000L)
 verify("newer remote progress wins",LibraryResume.position(60000,300,25000,200)==60000L)
 verify("invalid negative progress stays absent",LibraryResume.position(-1,100,-2,200)==0L)
 val a=resumeItem(1);val b=resumeItem(2);val completed=resumeItem(3,played=true,position=9000000)
 val server=resumeItem(4,position=600000000)
 val prefix="pos_server1_user1_"
 val values=mapOf<String,Any>(prefix+a.id to 25000L,"seen_"+prefix+a.id to 100L,prefix+b.id to 60000L,"seen_"+prefix+b.id to 200L,
  "pos_server2_user1_"+a.id to 999L,"seen_pos_server2_user1_"+a.id to 9999L,
  "pos_server1_user2_"+a.id to 999L,"seen_pos_server1_user2_"+a.id to 9999L,
  prefix+completed.id to 0L,"seen_"+prefix+completed.id to 300L,prefix+"bad-id" to 100L,"seen_"+prefix+"bad-id" to 400L)
 verify("local IDs scope server and user",LibraryResume.localIds(values,prefix)==listOf(b.id,a.id))
 verify("local ID limit follows last watch order",LibraryResume.localIds(values,prefix,1)==listOf(b.id))
 verify("local zero progress is absent",completed.id !in LibraryResume.localIds(values,prefix))
 verify("missing timestamp is absent",LibraryResume.localIds(mapOf(prefix+a.id to 100L),prefix).isEmpty())
 verify("negative position is absent",LibraryResume.localIds(mapOf(prefix+a.id to -1L,"seen_"+prefix+a.id to 100L),prefix).isEmpty())
 val positions=mapOf(a.id to 25000L,b.id to 60000L,completed.id to 1000L)
 val seen=mapOf(a.id to 100L,b.id to 200L)
 val position:(BaseItemDto)->Long={positions[it.id] ?: (it.userData?.playbackPositionTicks ?: 0)/10000}
 val lastSeen:(BaseItemDto)->Long={seen[it.id] ?: 0}
 fun select(remote: List<BaseItemDto>,local: List<BaseItemDto>)=LibraryResume.select(remote,local,position,lastSeen)
 verify("short local playback appears despite empty server row",select(emptyList(),listOf(a))==listOf(a))
 verify("local progress sorts by latest watch",select(emptyList(),listOf(a,b))==listOf(b,a))
 verify("server resume remains available without local progress",select(listOf(server),emptyList())==listOf(server))
 verify("same media appears once",select(listOf(a),listOf(a)).size==1)
 verify("fresh completed state overrides stale remote resume",select(listOf(a),listOf(resumeItem(1,played=true))).isEmpty())
 verify("completed media never returns as continue watching",select(listOf(completed),listOf(completed)).isEmpty())
 verify("zero progress never returns",select(listOf(resumeItem(8)),emptyList()).isEmpty())
 verify("non-video types never enter resume",select(emptyList(),listOf(resumeItem(1,type=BaseItemKind.TV_CHANNEL),resumeItem(2,type=BaseItemKind.SERIES))).isEmpty())
 val series=UUID.fromString("00000000-0000-0000-0000-000000000999")
 val ep1=resumeItem(1,type=BaseItemKind.EPISODE,series=series);val ep2=resumeItem(2,type=BaseItemKind.EPISODE,series=series)
 verify("same series keeps latest watched episode",select(emptyList(),listOf(ep1,ep2))==listOf(ep2))
 verify("row obeys display limit",LibraryResume.select(emptyList(),listOf(a,b),position,lastSeen,1)==listOf(b))
 println("$checks library resume checks passed")
}
