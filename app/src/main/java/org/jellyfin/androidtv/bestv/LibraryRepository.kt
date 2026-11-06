/* JellyLive optional media library. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.model.*
import org.jellyfin.androidtv.auth.repository.*
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.model.api.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** All requests use the SDK's shared, origin-scoped compatibility and authentication stack. */
class LibraryRepository : KoinComponent {
    val api by inject<ApiClient>()
    val sessions by inject<SessionRepository>()
    val servers by inject<ServerRepository>()
    private val authentication by inject<AuthenticationRepository>()
    val connected get() = sessions.currentSession.value != null && api.baseUrl != null
    val serverName get() = servers.currentServer.value?.name ?: "Jellyfin"
    val address get() = servers.currentServer.value?.address.orEmpty()

    suspend fun restore() { if (!connected) withTimeout(20_000) { sessions.restoreSession(false) } }
    suspend fun login(address: String, username: String, password: String) = withTimeout(25_000) {
        val result = servers.addServer(address).first { it is ConnectedState || it is UnableToConnectState }
        val id = (result as? ConnectedState)?.id ?: error("无法连接服务器，请检查地址和网络")
        val server = servers.getServer(id) ?: error("服务器不可用")
        when (val state = authentication.authenticate(server, CredentialAuthenticateMethod(username, password))
            .first { it !is AuthenticatingState }) {
            AuthenticatedState -> Unit
            is ServerVersionNotSupported -> error("服务器版本不受支持")
            is ApiClientErrorLoginState -> error("登录未成功，请检查账号、密码或服务器授权")
            else -> error("登录未成功，请检查服务器和账号")
        }
    }
    suspend fun discovery(): List<Server> {
        servers.loadStoredServers()
        try { withTimeout(2500) { servers.loadDiscoveryServers() } } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        return (servers.storedServers.value + servers.discoveredServers.value).distinctBy { it.address }
    }
    private val legacyProvider get() = serverName.contains("fnmedia",true) || serverName.contains("飞牛影视")
    private fun userPath()="/Users/${requireNotNull(sessions.currentSession.value).userId}"
    private suspend inline fun <reified T> legacyRead(path: String, query: Map<String,Any?> = emptyMap()): T =
        api.request(HttpMethod.GET,path,queryParameters=query).createContent()
    suspend fun views() = withContext(Dispatchers.IO) {
        // fnmedia's observed compatible API uses the per-user routes. Keep newer Jellyfin routes unchanged.
        if(legacyProvider) legacyRead<BaseItemDtoQueryResult>("${userPath()}/Views").items else api.userViewsApi.getUserViews().content.items
    }
    suspend fun resume() = withContext(Dispatchers.IO) {
        if(legacyProvider) legacyRead<BaseItemDtoQueryResult>("${userPath()}/Items/Resume",mapOf("Fields" to ItemRepository.browseFields.joinToString(",") {it.serialName},"Limit" to 12,"MediaTypes" to "Video","IncludeItemTypes" to "Movie,Episode","ExcludeActiveSessions" to true)).items
        else api.itemsApi.getResumeItems(fields=ItemRepository.browseFields, limit=12, mediaTypes=listOf(MediaType.VIDEO),
            includeItemTypes=listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE), excludeActiveSessions=true).content.items
    }
    /** fnmedia's empty root is not an empty library: enumerate its actual user views. */
    private suspend fun legacyBrowse(parent: UUID?,kind: BaseItemKind?,start: Int,search: String?,alphabetical: Boolean,genre: String?,limit: Int): BaseItemDtoQueryResult {
        suspend fun fetch(id: UUID,offset: Int,count: Int): BaseItemDtoQueryResult = legacyRead("${userPath()}/Items",mapOf(
            "ParentId" to id,"Recursive" to true,"Limit" to count,"StartIndex" to offset,
            "IncludeItemTypes" to (kind?.serialName ?: "Movie,Series,Video"),"SearchTerm" to search,"Genres" to genre,
            "Fields" to ItemRepository.browseFields.joinToString(",") {it.serialName},"SortBy" to if(alphabetical) "SortName" else "DateCreated",
            "SortOrder" to if(alphabetical) "Ascending" else "Descending","EnableUserData" to true))
        if(parent!=null)return fetch(parent,start,limit)
        val roots=views().map {it.id}.distinct()
        if(roots.isEmpty())return BaseItemDtoQueryResult(items=emptyList(),totalRecordCount=0,startIndex=start)
        if(roots.size==1)return fetch(roots.first(),start,limit)
        // Merge independently sorted view prefixes before applying the global page offset.
        val results=roots.map {fetch(it,0,start+limit)}
        val merged=results.flatMap {it.items}.distinctBy {it.id}
        val ordered=if(alphabetical)merged.sortedBy {it.sortName ?: it.name.orEmpty()}else merged.sortedByDescending {it.dateCreated}
        return BaseItemDtoQueryResult(items=ordered.drop(start).take(limit),totalRecordCount=results.sumOf {it.totalRecordCount},startIndex=start)
    }
    suspend fun items(parent: UUID?=null, kind: BaseItemKind?=null, start: Int=0, search: String?=null,
                      alphabetical: Boolean=false, genre: String?=null) = withContext(Dispatchers.IO) {
        if(legacyProvider) legacyBrowse(parent,kind,start,search,alphabetical,genre,60)
        else api.itemsApi.getItems(parentId=parent, recursive=true, limit=60, startIndex=start,
            includeItemTypes=kind?.let { listOf(it) } ?: listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO),
            searchTerm=search, genres=genre?.let { listOf(it) }, fields=ItemRepository.browseFields,
            sortBy=listOf(if(alphabetical) ItemSortBy.SORT_NAME else ItemSortBy.DATE_CREATED),
            sortOrder=listOf(if(alphabetical) SortOrder.ASCENDING else SortOrder.DESCENDING), enableUserData=true).content
    }
    suspend fun searchMedia(query: String): List<BaseItemDto> = withContext(Dispatchers.IO) {
        if(legacyProvider)legacyBrowse(null,null,0,query,false,null,12).items
        else api.itemsApi.getItems(searchTerm=query,limit=12,recursive=true,includeItemTypes=listOf(BaseItemKind.MOVIE,BaseItemKind.SERIES,BaseItemKind.VIDEO),fields=ItemRepository.browseFields).content.items.take(12)
    }
    suspend fun detail(id: UUID) = withContext(Dispatchers.IO) {
        if(legacyProvider) legacyRead<BaseItemDto>("${userPath()}/Items/$id") else api.userLibraryApi.getItem(id).content
    }
    suspend fun seasons(series: UUID) = withContext(Dispatchers.IO) { api.tvShowsApi.getSeasons(seriesId=series).content.items }
    suspend fun episodes(series: UUID, season: UUID?=null) = withContext(Dispatchers.IO) {
        api.tvShowsApi.getEpisodes(seriesId=series, seasonId=season, fields=ItemRepository.itemFields, isMissing=false).content.items
    }
    suspend fun nextEpisode(series: UUID) = withContext(Dispatchers.IO) {
        api.tvShowsApi.getNextUp(seriesId=series, limit=1, fields=ItemRepository.itemFields).content.items.firstOrNull()
    }
    /** Source adapter: only actual channels of the connected server; no discovered-server assumption. */
    suspend fun liveChannels(): List<BaseItemDto> = withContext(Dispatchers.IO) {
        api.liveTvApi.getLiveTvChannels(enableUserData=true).content.items
    }
    fun image(item: BaseItemDto, backdrop: Boolean=false): String? {
        val image = (if(backdrop) item.itemBackdropImages.firstOrNull() else null) ?: item.itemImages[ImageType.PRIMARY]
        return image?.getUrl(api, maxWidth=if(backdrop) 1280 else 480)
    }
}
