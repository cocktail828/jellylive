package org.jellyfin.androidtv.bestv

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.repository.*
import org.jellyfin.sdk.api.client.*
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.UUID

/** A compatible HTTP boundary: obsolete SDK routes return HTML; legacy writes may have no JSON. */
private class StatusServer : ApiClient() {
    val user="00000000-0000-0000-0000-000000000001"
    val item=UUID.fromString("00000000-0000-0000-0000-000000000002")
    override var baseUrl: String?="http://status.invalid"
    override val accessToken: String?=null
    override val clientInfo: ClientInfo get()=error("unused")
    override val deviceInfo: DeviceInfo get()=error("unused")
    override val httpClientOptions=HttpClientOptions()
    override val webSocket: SocketApi get()=error("unused")
    override fun update(baseUrl: String?,accessToken: String?,clientInfo: ClientInfo,deviceInfo: DeviceInfo)=error("unused")
    var favorite=false
    var played=false
    var status=204
    var applyWrite=true
    var afterWrite: ()->Unit={}
    var writes=0
    var reads=0
    var modernWrites=0
    override suspend fun request(method: HttpMethod,pathTemplate: String,pathParameters: Map<String,Any?>,queryParameters: Map<String,Any?>,requestBody: Any?): RawResponse {
        val path=pathTemplate
        if(method==HttpMethod.GET && path=="/Users/$user/Items/$item") {
            reads++
            return RawResponse("""{"Id":"$item","Type":"Movie","UserData":{"ItemId":"$item","Key":"fixture","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":$favorite,"Played":$played}}""".toByteArray(),200,emptyMap())
        }
        val favoriteRoute=path=="/Users/$user/FavoriteItems/$item"
        val playedRoute=path=="/Users/$user/PlayedItems/$item"
        if(favoriteRoute || playedRoute) {
            check(method==HttpMethod.POST || method==HttpMethod.DELETE)
            writes++
            if(status in 200..299 && applyWrite) {
                if(favoriteRoute)favorite=method==HttpMethod.POST else played=method==HttpMethod.POST
            }
            afterWrite()
            // A 200 HTML acknowledgment must not be treated as saved unless readback agrees.
            return RawResponse(if(status==204)byteArrayOf() else "<html>ack</html>".toByteArray(),status,emptyMap())
        }
        if(path=="/UserFavoriteItems/{itemId}" || path=="/UserPlayedItems/{itemId}") {
            modernWrites++
            check(pathParameters["itemId"]==item)
            if(path.startsWith("/UserFavorite"))favorite=method==HttpMethod.POST else played=method==HttpMethod.POST
            return RawResponse("""{"ItemId":"$item","Key":"fixture","PlaybackPositionTicks":0,"PlayCount":0,"IsFavorite":$favorite,"Played":$played}""".toByteArray(),200,emptyMap())
        }
        return RawResponse("<html>unsupported route</html>".toByteArray(),200,emptyMap())
    }
}
fun main()=runBlocking {
    val api=StatusServer()
    val sessionFlow=MutableStateFlow<Session?>(Session(UUID.fromString(api.user),UUID.randomUUID(),"fixture"))
    val sessions=mockk<SessionRepository>();every {sessions.currentSession} returns sessionFlow
    val serverFlow=MutableStateFlow<Server?>(Server(UUID.randomUUID(),"fnmedia","http://status.invalid"))
    val servers=mockk<ServerRepository>();every {servers.currentServer} returns serverFlow
    startKoin {modules(module {single<ApiClient>{api};single<SessionRepository>{sessions};single<ServerRepository>{servers}})}
    val repo=LibraryRepository()
    var checks=0
    fun verify(name: String,value: Boolean){check(value){name};checks++;println("PASS $name")}
    try {
        verify("favorite survives empty legacy acknowledgment",repo.setFavorite(api.item,true).isFavorite && api.favorite && api.reads==1)
        api.status=200
        verify("unfavorite reads server state after non-JSON acknowledgment",!repo.setFavorite(api.item,false).isFavorite && !api.favorite)
        verify("mark played uses legacy route and reads actual state",repo.setPlayed(api.item,true).played && api.played)
        verify("mark unplayed uses DELETE and reads actual state",!repo.setPlayed(api.item,false).played && !api.played)
        api.applyWrite=false
        verify("ignored favorite write fails rather than faking success",runCatching {repo.setFavorite(api.item,true)}.isFailure)
        verify("ignored played write fails rather than faking success",runCatching {repo.setPlayed(api.item,true)}.isFailure)
        for(code in listOf(401,403,500)) {
            api.status=code;val reads=api.reads
            val error=runCatching {repo.setFavorite(api.item,true)}.exceptionOrNull()
            verify("HTTP $code preserved without success readback",error is InvalidStatusException && error.status==code && api.reads==reads)
        }
        api.status=204;api.applyWrite=true
        val oldSession=repo.sessionKey
        api.baseUrl="http://other.invalid";val writes=api.writes
        verify("stale session cannot write",runCatching {repo.setFavorite(api.item,true,oldSession)}.isFailure && api.writes==writes)
        api.baseUrl="http://status.invalid"
        api.afterWrite={api.baseUrl="http://other.invalid"};val reads=api.reads
        verify("session change after write prevents read from another account",runCatching {repo.setPlayed(api.item,true)}.isFailure && api.reads==reads)
        api.afterWrite={};api.baseUrl="http://status.invalid"
        serverFlow.value=serverFlow.value!!.copy(id=UUID.randomUUID(),name="Jellyfin")
        val legacyReads=api.reads
        verify("ordinary Jellyfin keeps SDK favorite endpoint",repo.setFavorite(api.item,true).isFavorite && api.modernWrites==1 && api.reads==legacyReads)
        verify("ordinary Jellyfin keeps SDK watched endpoint",!repo.setPlayed(api.item,false).played && api.modernWrites==2 && api.reads==legacyReads)
        println("$checks user status checks passed")
    } finally {repo.close();stopKoin()}
}
