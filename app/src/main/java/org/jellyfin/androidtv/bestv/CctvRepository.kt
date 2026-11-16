package org.jellyfin.androidtv.bestv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
class CctvRepository {
    val client:OkHttpClient get()=publicClient
    private suspend fun get(url:String,params:Map<String,String>):String=withContext(Dispatchers.IO) {val u=url.toHttpUrl().newBuilder();params.forEach {(k,v)->u.addQueryParameter(k,v)};client.newCall(Request.Builder().url(u.build()).build()).execute().use {check(it.isSuccessful){"节目来源请求失败（${it.code}）"};it.body?.string()?:error("节目来源未返回内容")}}
    suspend fun albums(category:String,page:Int,filters:Map<String,String>):CctvAlbumPage=CctvCatalog.albums(get("https://api.cntv.cn/list/getVideoAlbumList",mapOf("fc" to category,"p" to page.toString(),"n" to "30","serviceId" to "tvcctv","topv" to "1","t" to "json")+filters),category)
    suspend fun episodes(albumId:String):List<CctvEpisode> {require(albumId.startsWith("VIDA"));val items=linkedMapOf<String,CctvEpisode>();var cursor=0;repeat(1000) {val page=CctvCatalog.episodes(get("https://api.cntv.cn/NewVideo/getVideoListByAlbumIdNew",mapOf("id" to albumId,"serviceId" to "tvcctv","pub" to "1","mode" to "0","part" to cursor.toString(),"n" to "100","sort" to "asc")));page.items.forEach {items[it.guid]=it};if(items.size>=page.total)return items.values.sortedBy {it.part};check(page.items.isNotEmpty()){"选集目录尚未完整，请重试"};check(page.cursor>cursor){"选集分页未前进，请重试"};cursor=page.cursor};error("选集数量超出可处理范围")}
    suspend fun stream(guid:String)=CctvCatalog.stream(get("https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do",mapOf("pid" to guid)))
    companion object {private val publicClient=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).addInterceptor {chain->chain.proceed(chain.request().newBuilder().header("Referer","https://tv.cctv.com/").header("User-Agent","Mozilla/5.0 JellyLive").build())}.build()}
}
