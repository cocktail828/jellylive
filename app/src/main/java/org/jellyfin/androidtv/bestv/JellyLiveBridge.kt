/* Connected Jellyfin live-source bridge. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.sdk.model.ServerVersion
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory

/** Callbacks run on main; fetched channel cache never contains an access token or signed stream URL. */
object JellyLiveBridge : KoinComponent {
    data class Snapshot(val serverKey: String,val serverName: String,val channels: List<JSONObject>)
    interface Callback<T> { fun success(value: T); fun failure(message: String) }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val main=Handler(Looper.getMainLooper())
    private val playGeneration=AtomicLong()
    private data class Owned(val api: ApiClient,val liveId: String?,val encodingSession: String?,val generation: Long,val closed: java.util.concurrent.atomic.AtomicBoolean=java.util.concurrent.atomic.AtomicBoolean(false))
    private val owned=mutableListOf<Owned>()
    private fun takeOwned(): List<Owned> = synchronized(owned) {owned.toList().also {owned.clear()}}
    private suspend fun close(resources: List<Owned>) {
        resources.forEach {resource ->
            if(!resource.closed.compareAndSet(false,true))return@forEach
            withContext(NonCancellable) {
                runCatching {withTimeout(5000) {resource.liveId?.let {resource.api.mediaInfoApi.closeLiveStream(it)}}}
                runCatching {withTimeout(5000) {resource.encodingSession?.let {resource.api.hlsSegmentApi.stopEncodingProcess(resource.api.deviceInfo.id,it)}}}
            }
        }
    }
    /** Invalidates pending negotiations, then closes leases with their original immutable API/session. */
    @JvmStatic fun releaseLiveStreams() {playGeneration.incrementAndGet();val resources=takeOwned();scope.launch {close(resources)}}
    @JvmStatic fun dataSourceFactory(): androidx.media3.datasource.HttpDataSource.Factory {
        val api=synchronized(owned){owned.lastOrNull()?.api} ?: get<ApiClient>()
        return androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(LibraryMediaHttp.client(api,get()))
    }
    @JvmStatic fun mediaDataSourceFactory(context: android.content.Context): androidx.media3.datasource.DataSource.Factory = androidx.media3.datasource.DefaultDataSource.Factory(context.applicationContext,dataSourceFactory())
    @JvmStatic fun isConnected() = LibraryRepository().connected
    @JvmStatic fun serverName(): String = LibraryRepository().serverName
    @JvmStatic fun serverKey(): String = LibraryRepository().sessions.currentSession.value?.serverId?.toString().orEmpty()
    @JvmStatic fun loadSource(callback: Callback<Snapshot>) {
        run(callback) {
            val repo=LibraryRepository()
            if(!repo.connected)repo.restore()
            check(repo.connected) {"请先连接 Jellyfin"}
            val session=repo.sessions.currentSession.value ?: error("请先连接 Jellyfin")
            val name=repo.serverName
            val channels=repo.liveChannels().filter { it.type==BaseItemKind.TV_CHANNEL && it.isFolder!=true }
            check(repo.sessions.currentSession.value==session) {"Jellyfin 连接已切换，请重新获取"}
            Snapshot(session.serverId.toString(),name,channels.mapIndexed {index,item -> JSONObject().put("ChannelCode",item.id.toString()).put("ChannelName",item.name ?: "频道 ${index+1}")
                .put("ChannelNo",item.channelNumber ?: (index+1).toString()).put("CategoryName","Jellyfin 直播").put("EpgId",item.id.toString()) })
        }
    }
    @JvmStatic fun loadChannels(callback: Callback<List<JSONObject>>) {
        loadSource(object: Callback<Snapshot> {
            override fun success(value: Snapshot) {callback.success(value.channels)}
            override fun failure(message: String) {callback.failure(message)}
        })
    }
    /** Optional search only queries the active authenticated session, never starts discovery/login. */
    @JvmStatic fun searchMedia(query: String,callback: Callback<List<JSONObject>>) {
        run(callback) {
            val repo=LibraryRepository()
            if(query.isBlank() || !repo.connected)return@run emptyList<JSONObject>()
            val session=repo.sessions.currentSession.value
            val items=repo.searchMedia(query.trim())
            check(repo.sessions.currentSession.value==session)
            items.take(12).map {item -> JSONObject().put("id",item.id.toString()).put("name",item.name.orEmpty()).put("type",item.type?.serialName.orEmpty())}
        }
    }
    @JvmStatic fun playbackUrl(context: android.content.Context, serverKey: String, channel: String, callback: Callback<String>) {
        val requestGeneration=playGeneration.incrementAndGet()
        val previous=takeOwned()
        scope.launch {
            var acquired=emptyList<Owned>()
            try {
                close(previous)
                val result=withTimeout(25000) {
                    val repo=LibraryRepository()
                    if(!repo.connected)repo.restore()
                    check(repo.connected && this@JellyLiveBridge.serverKey()==serverKey)
                    val session=repo.sessions.currentSession.value ?: error("未连接")
                    // Freeze origin, token, device and client before negotiation; cleanup never uses a changed global API.
                    val factory=get<OkHttpFactory>();val current=repo.api
                    val api=factory.create(current.baseUrl,current.accessToken,current.clientInfo,current.deviceInfo,current.httpClientOptions,factory)
                    val itemId=UUID.fromString(channel)
                    val profile=createDeviceProfile(context.applicationContext,get<UserPreferences>(),repo.servers.currentServer.value?.serverVersion ?: ServerVersion(10,10,0))
                    val response=api.mediaInfoApi.getPostedPlaybackInfo(itemId=itemId,data=PlaybackInfoDto(deviceProfile=profile,
                        enableDirectPlay=true,enableDirectStream=true,autoOpenLiveStream=true,allowVideoStreamCopy=true,allowAudioStreamCopy=true)).content
                    acquired=response.mediaSources.mapNotNull {source ->source.liveStreamId?.let {Owned(api,it,null,requestGeneration)}}.distinctBy {it.liveId}
                    val source=response.mediaSources.firstOrNull() ?: error("没有直播流")
                    check(repo.sessions.currentSession.value==session && this@JellyLiveBridge.serverKey()==serverKey && playGeneration.get()==requestGeneration)
                    val url=when {
                        source.supportsDirectPlay -> if(source.isRemote && source.path!=null) source.path!! else api.videosApi.getVideoStreamUrl(itemId=itemId,container=source.container,mediaSourceId=source.id,static=true,tag=source.eTag,liveStreamId=source.liveStreamId)
                        source.supportsDirectStream || source.supportsTranscoding -> source.transcodingUrl?.let {api.createUrl(it,ignorePathParameters=true)} ?: error("没有播放地址")
                        else -> error("没有兼容格式")
                    }
                    val encoding=if(!source.supportsDirectPlay) response.playSessionId else null
                    val active=Owned(api,source.liveStreamId,encoding,requestGeneration)
                    close(acquired.filter {it.liveId!=source.liveStreamId})
                    acquired=listOf(active)
                    synchronized(owned) {if(playGeneration.get()!=requestGeneration)error("播放请求已取消");owned.add(active)}
                    url
                }
                main.post {
                    if(playGeneration.get()==requestGeneration)callback.success(result)
                    else scope.launch {val stale=synchronized(owned){owned.filter {it.generation==requestGeneration}.also {owned.removeAll(it.toSet())}};close(stale)}
                }
            } catch(_: Exception) {
                val cleanup=acquired
                synchronized(owned){owned.removeAll(cleanup.toSet())}
                close(cleanup)
                if(playGeneration.get()==requestGeneration)main.post {if(playGeneration.get()==requestGeneration)callback.failure("Jellyfin 暂时不可用，请检查连接或服务器直播配置")}
            }
        }
    }
    private fun <T> run(callback: Callback<T>, action: suspend ()->T) {
        scope.launch {
            try {val result=withTimeout(25000) {action()};main.post {callback.success(result)}}
            catch(_: Exception) {main.post {callback.failure("Jellyfin 暂时不可用，请检查连接或服务器直播配置")}}
        }
    }
}
