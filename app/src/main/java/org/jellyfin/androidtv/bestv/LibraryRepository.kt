/* JellyLive optional media library. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.model.*
import org.jellyfin.androidtv.auth.repository.*
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
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
    private val authenticationStore by inject<org.jellyfin.androidtv.auth.store.AuthenticationStore>()
    private val jellyfin by inject<org.jellyfin.sdk.Jellyfin>()
    val connected get() = sessions.currentSession.value != null && api.baseUrl != null
    val serverName get() = servers.currentServer.value?.name ?: "Jellyfin"
    val address get() = servers.currentServer.value?.address.orEmpty()

    suspend fun restore() { if (!connected) withTimeout(20_000) { sessions.restoreSession(false) } }
    suspend fun login(address: String, username: String, password: String) = withTimeout(25_000) {
        val previous=sessions.currentSession.value
        val result = servers.addServer(address).first { it is ConnectedState || it is UnableToConnectState }
        val id = (result as? ConnectedState)?.id ?: error("无法连接服务器，请检查地址和网络")
        val server = servers.getServer(id) ?: error("服务器不可用")
        // Stage credentials in an isolated client; publishing a new session is the last suspending step.
        val candidate=jellyfin.createApi(server.address,deviceInfo=api.deviceInfo)
        val authenticated=withContext(Dispatchers.IO){candidate.userApi.authenticateUserByName(username,password).content}
        val user=requireNotNull(authenticated.user){"服务器未返回账号信息"}
        val token=requireNotNull(authenticated.accessToken){"服务器未返回登录凭据"}
        coroutineContext.ensureActive();check(sessions.currentSession.value==previous){"媒体库已切换"}
        authenticationStore.putUser(server.id,user.id,AuthenticationStoreUser(name=user.name ?: username,imageTag=user.primaryImage?.tag,accessToken=token))
        check(sessions.switchCurrentSession(server.id,user.id)){"登录未成功，请检查服务器授权"}
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
        if(legacyProvider) {
            try {legacyRead<BaseItemDtoQueryResult>("${userPath()}/Items/Resume",mapOf("Fields" to ItemRepository.browseFields.joinToString(",") {it.serialName},"Limit" to 12,"MediaTypes" to "Video","IncludeItemTypes" to "Movie,Episode","ExcludeActiveSessions" to true)).items}
            catch(error: InvalidStatusException){
                if(!LibraryResume.needsCatalogFallback(error.status))throw error
                // Some identified fnmedia versions reject Resume while ordinary per-user
                // items contain valid progress. Do not mask auth/network/server failures.
                LibraryResume.select(catalog(refresh=true),emptyList(),{(it.userData?.playbackPositionTicks ?: 0)/10000},{0})
            }
        } else api.itemsApi.getResumeItems(fields=ItemRepository.browseFields, limit=12, mediaTypes=listOf(MediaType.VIDEO),
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
    private val catalogCache=CatalogCache<List<BaseItemDto>>()
    fun close(){catalogCache.close()}
    val sessionKey get() = "${api.baseUrl}|${sessions.currentSession.value?.userId}"
    /** Some compatible servers silently ignore Genres/Years/IsPlayed. Read a complete snapshot,
     * on IO, then apply all filters and totals together. A truncated scope is never presented. */
    suspend fun catalog(parent: UUID?=null, refresh: Boolean=false): List<BaseItemDto> = withContext(Dispatchers.IO) {
        val session=sessionKey
        val key="$session|$parent"
        catalogCache.get(key,refresh) {
        val roots=if(legacyProvider) parent?.let {listOf(it)} ?: views().map {it.id}.distinct() else listOf(parent)
        val collected=linkedMapOf<UUID,BaseItemDto>()
        for(root in roots) {
            // Two user views may legitimately contain the same title. Only repeated
            // pages within this root indicate a server paging failure.
            val rootSeen=hashSetOf<UUID>()
            var offset=0
            var expectedTotal=0
            do {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                check(sessionKey==session) {"媒体库已切换，请重新打开"}
                val result=if(legacyProvider) legacyRead<BaseItemDtoQueryResult>("${userPath()}/Items",mapOf(
                    "ParentId" to root,"Recursive" to true,"Limit" to 200,"StartIndex" to offset,
                    "Fields" to (ItemRepository.browseFields+setOf(ItemFields.MEDIA_STREAMS,ItemFields.MEDIA_SOURCES,ItemFields.PRODUCTION_LOCATIONS,ItemFields.ITEM_COUNTS)).joinToString(","){it.serialName},
                    "SortBy" to "SortName","SortOrder" to "Ascending","EnableUserData" to true))
                else api.itemsApi.getItems(parentId=root,recursive=true,limit=200,startIndex=offset,
                    includeItemTypes=listOf(BaseItemKind.MOVIE,BaseItemKind.SERIES,BaseItemKind.EPISODE,BaseItemKind.VIDEO),
                    fields=ItemRepository.browseFields+setOf(ItemFields.MEDIA_STREAMS,ItemFields.MEDIA_SOURCES,ItemFields.PRODUCTION_LOCATIONS,ItemFields.ITEM_COUNTS),
                    sortBy=listOf(ItemSortBy.SORT_NAME),sortOrder=listOf(SortOrder.ASCENDING),enableUserData=true).content
                expectedTotal=result.totalRecordCount
                if(expectedTotal<result.items.size)throw CatalogIncompleteException("服务器未提供可靠的目录总数")
                val before=rootSeen.size
                result.items.forEach {rootSeen.add(it.id);collected[it.id]=it}
                offset+=result.items.size
                if(result.items.isNotEmpty() && rootSeen.size<=before)throw CatalogIncompleteException("服务器未正确分页，请进入具体分类重试")
                if(collected.size>50_000)throw CatalogIncompleteException("内容较多，请进入具体媒体分类浏览")
                if(result.items.isEmpty()) {if(offset<result.totalRecordCount)throw CatalogIncompleteException("媒体列表未完整返回，请刷新重试");break}
            } while(offset<result.totalRecordCount)
            if(rootSeen.size<expectedTotal)throw CatalogIncompleteException("媒体列表未完整返回，请刷新重试")
        }
        check(sessionKey==session) {"媒体库已切换，请重新打开"}
        collected.values.toList()
        }
    }
    suspend fun searchMedia(query: String): List<BaseItemDto> = withContext(Dispatchers.IO) {
        if(legacyProvider)legacyBrowse(null,null,0,query,false,null,12).items
        else api.itemsApi.getItems(searchTerm=query,limit=12,recursive=true,includeItemTypes=listOf(BaseItemKind.MOVIE,BaseItemKind.SERIES,BaseItemKind.VIDEO),fields=ItemRepository.browseFields).content.items.take(12)
    }
    suspend fun detail(id: UUID) = withContext(Dispatchers.IO) {
        if(legacyProvider) legacyRead<BaseItemDto>("${userPath()}/Items/$id") else api.userLibraryApi.getItem(id).content
    }
    suspend fun setPlayed(id: UUID, played: Boolean) = withContext(Dispatchers.IO) {
        if(played) api.playStateApi.markPlayedItem(itemId=id) else api.playStateApi.markUnplayedItem(itemId=id)
        catalogCache.invalidate()
    }
    fun personImage(person: BaseItemPerson): String? = person.primaryImage?.getUrl(api,maxWidth=160)
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
