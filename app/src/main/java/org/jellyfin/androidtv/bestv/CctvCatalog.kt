package org.jellyfin.androidtv.bestv

import org.json.JSONObject
import org.json.JSONArray

data class CctvAlbum(val id:String,val title:String,val category:String,val year:Int?=null,val count:Int?=null,val genre:String="",val channel:String="",val brief:String="",val imageUrl:String="") {
    fun encode()=JSONObject().put("id",id).put("title",title).put("category",category).put("year",year).put("count",count).put("genre",genre).put("channel",channel).put("brief",brief).put("imageUrl",imageUrl).toString()
    companion object { fun decode(raw:String):CctvAlbum {val j=JSONObject(raw);return CctvAlbum(j.getString("id"),j.getString("title"),j.getString("category"),j.optString("year").toIntOrNull(),j.optString("count").toIntOrNull(),j.optString("genre"),j.optString("channel"),j.optString("brief"),j.optString("imageUrl"))} }
}
data class CctvEpisode(val guid:String,val title:String,val part:Int)
data class CctvStream(val url:String,val duration:Long)
data class CctvAlbumPage(val items:List<CctvAlbum>,val total:Int)
data class CctvEpisodePage(val items:List<CctvEpisode>,val total:Int,val cursor:Int)
object CctvCatalog {
    private fun root(raw:String):JSONObject {val j=JSONObject(raw);require(!j.has("errcode")||j.optInt("errcode")==0){j.optString("msg","节目来源暂不可用")};return j}
    fun albums(raw:String,category:String):CctvAlbumPage {val d=root(raw).getJSONObject("data");val list=d.getJSONArray("list");return CctvAlbumPage((0 until list.length()).mapNotNull {i->val j=list.getJSONObject(i);val id=j.optString("id");require(id.isNotBlank()){"节目缺少专辑编号"};CctvAlbum(id,j.optString("title"),j.optString("fc").ifBlank {category},j.optString("year").toIntOrNull(),j.optString("count").toIntOrNull(),j.optString("sc"),j.optString("channel"),j.optString("brief"),j.optString("image2").ifBlank {j.optString("image").ifBlank {j.optString("img")}})},d.optInt("total"))}
    fun episodes(raw:String,start:Int=0):CctvEpisodePage {val d=root(raw).getJSONObject("data");val a=d.getJSONArray("list");return CctvEpisodePage((0 until a.length()).map {i->val j=a.getJSONObject(i);val guid=j.optString("guid");require(guid.matches(Regex("[a-fA-F0-9]{32}"))){"无效节目编号"};CctvEpisode(guid,j.optString("title"),j.optInt("part").takeIf {it>0}?:start+i+1)},d.optInt("total"),d.optInt("last_part"))}
    fun stream(raw:String):CctvStream {val j=root(raw);require(j.optString("ack")=="yes"&&j.optString("status")=="001"){"节目暂不可播放"};require(j.optString("is_preview") !in listOf("1","true")){"节目仅提供预览"};require(j.optString("is_protected") !in listOf("1","true")&&j.optString("play")!="0"&&j.optString("is_invalid_copyright")!="1"){"节目受播放限制"};val url=j.optString("hls_url");val uri=java.net.URI(url);require(uri.scheme in listOf("http","https")&&!uri.host.isNullOrBlank()){"节目没有可用播放地址"};val seconds=j.optJSONObject("video")?.optString("totalLength")?.toDoubleOrNull()?:0.0;require(seconds.isFinite()&&seconds>=0){"节目时长无效"};return CctvStream(url,(seconds*1000).toLong())}
}
data class CctvEntry(val album:CctvAlbum,val episode:CctvEpisode?=null,val position:Long=0,val duration:Long=0,val completed:Boolean=false,val watchedAt:Long=0,val favoriteAt:Long=0) {val resumePosition:Long get()=if(completed)0 else position.coerceAtLeast(0)}
data class CctvState(val entries:Map<String,CctvEntry> = emptyMap()) {
    fun favorites()=entries.values.filter {it.favoriteAt>0}.sortedByDescending {it.favoriteAt}
    fun history()=entries.values.filter {it.watchedAt>0}.sortedByDescending {it.watchedAt}
    fun favorite(album:CctvAlbum,value:Boolean,now:Long=System.currentTimeMillis()):CctvState {val e=(entries[album.id]?:CctvEntry(album)).copy(album=album,favoriteAt=if(value)now else 0);return copy(entries=entries+(album.id to e))}
    fun watch(album:CctvAlbum,episode:CctvEpisode,position:Long,duration:Long,completed:Boolean,now:Long=System.currentTimeMillis())=copy(entries=entries+(album.id to (entries[album.id]?:CctvEntry(album)).copy(album=album,episode=episode,position=position.coerceAtLeast(0),duration=duration.coerceAtLeast(0),completed=completed,watchedAt=now)))
    fun encode():String {val a=JSONArray();entries.values.forEach {e->a.put(JSONObject().put("album",JSONObject(e.album.encode())).put("episode",e.episode?.let {JSONObject().put("guid",it.guid).put("title",it.title).put("part",it.part)}).put("position",e.position).put("duration",e.duration).put("completed",e.completed).put("watchedAt",e.watchedAt).put("favoriteAt",e.favoriteAt))};return JSONObject().put("entries",a).toString()}
    companion object {fun decode(raw:String):CctvState {val a=JSONObject(raw).getJSONArray("entries");val entries=(0 until a.length()).map {i->val j=a.getJSONObject(i);val album=CctvAlbum.decode(j.getJSONObject("album").toString());val ep=j.optJSONObject("episode")?.let {CctvEpisode(it.getString("guid"),it.getString("title"),it.getInt("part"))};album.id to CctvEntry(album,ep,j.getLong("position"),j.getLong("duration"),j.getBoolean("completed"),j.getLong("watchedAt"),j.getLong("favoriteAt"))};return CctvState(entries.toMap())}}
}

/** A failed replacement keeps the last successful query and cannot advance its page under new facets. */
data class CctvBrowseCursor(val query:Map<String,String>?=null,val nextPage:Int=1) {
    fun page(filters:Map<String,String>,refresh:Boolean=false)=if(refresh||query!=filters)1 else nextPage
}
