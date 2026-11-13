package org.jellyfin.androidtv.bestv

import kotlinx.coroutines.*
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Saved account labels never expose tokens. Every request is cancellable and origin-isolated. */
class LibraryConnections : KoinComponent {
    data class Entry(val serverId:String,val userId:String,val name:String,val username:String,val address:String) {
        val key get()="$serverId/$userId"
        val label get()=listOf(name,username).filter {it.isNotBlank()}.joinToString(" · ")
    }
    interface Callback {fun success(entry:Entry);fun failure(message:String)}
    class Request internal constructor(private val job:Job){fun cancel(){job.cancel()}}
    private val store by inject<AuthenticationStore>()
    private val prefs by inject<AuthenticationPreferences>()
    private val sessions by inject<SessionRepository>()
    private val jellyfin by inject<Jellyfin>()
    private val api by inject<ApiClient>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    fun entries():List<Entry> = store.getServers().flatMap {(serverId,server)->server.users.filterValues {!it.accessToken.isNullOrBlank()}.map {(userId,user)->Entry(serverId.toString(),userId.toString(),server.name.ifBlank {"媒体库"},user.name,server.address)}}
    fun current():Entry? {
        val session=sessions.currentSession.value
        val server=session?.serverId?.toString() ?: prefs[AuthenticationPreferences.lastServerId]
        val user=session?.userId?.toString() ?: prefs[AuthenticationPreferences.lastUserId]
        return entries().firstOrNull {it.serverId==server&&it.userId==user}
    }
    fun currentKey():String=current()?.key.orEmpty()
    fun isActive(entry:Entry):Boolean=sessions.currentSession.value?.let {it.serverId.toString()==entry.serverId&&it.userId.toString()==entry.userId}==true
    fun connect(entry:Entry,callback:Callback):Request {
        val job=scope.launch {
            try {
                verifyAndActivate(entry)
                ensureActive();callback.success(entry)
            } catch(error:CancellationException) {
                if(error is TimeoutCancellationException)callback.failure("连接超时，请检查网络后重试。")
            } catch(error:Exception) {
                callback.failure(failureMessage(error))
            }
        }
        return Request(job)
    }
    interface StartupObserver {
        fun checking(entry:Entry?,attempt:Int)
        fun success(entry:Entry)
        fun failure(message:String)
    }
    private var startupJob:Job?=null
    private var startupStarted=false
    private var configuredPreset:LibraryBootstrap?=null
    private var ownerContext:android.content.Context?=null
    val canRetryDefault get()=configuredPreset!=null
    var startupAttempts=0
        private set
    val startupBusy get()=startupJob?.isActive==true
    fun prepare(context:android.content.Context){ownerContext=context.applicationContext}
    fun stopStartup(){startupJob?.cancel();startupJob=null}
    /** Once per launcher lifetime; defaults are eligible only until a manual/current selection wins. */
    fun startConfigured(context:android.content.Context,observer:StartupObserver) {
        if(startupStarted)return
        startupStarted=true;ownerContext=context.applicationContext
        startupJob=scope.launch(start=CoroutineStart.LAZY) {
            val local=context.getSharedPreferences("jellylive_library",android.content.Context.MODE_PRIVATE)
            var entry=current()
            val preset=if(entry==null)readPreset() else null
            val savedAddress=local.getString("last_address","").orEmpty();val savedUser=local.getString("last_username","").orEmpty()
            val consumed=local.getBoolean("bootstrap_consumed",false)
            val defaultAllowed=preset?.mayConnect(false,savedAddress,savedUser,consumed)==true ||
                (preset!=null&&local.getBoolean("bootstrap_retry_pending",false)&&(savedAddress.isBlank()||preset.matchesAddress(savedAddress))&&(savedUser.isBlank()||savedUser==preset.username))
            if(entry==null&&!defaultAllowed)return@launch
            if(entry==null)configuredPreset=preset
            if(entry==null)local.edit().putBoolean("bootstrap_consumed",true).putBoolean("bootstrap_retry_pending",true).apply()
            var message="无法连接媒体库，请检查网络后重试。"
            for(attempt in 1..3){
                ensureActive();startupAttempts=attempt;observer.checking(entry,attempt)
                try {
                    if(entry!=null)verifyAndActivate(entry)
                    else {entry=connectDefault()}
                    ensureActive();startupJob=null;observer.success(requireNotNull(entry));return@launch
                }catch(error:CancellationException){if(error !is TimeoutCancellationException)throw error;message="连接超时，请检查网络后重试。"}
                catch(error:Exception){message=failureMessage(error)}
                if(attempt<3)delay(attempt*1000L)
            }
            startupJob=null;observer.failure(message)
        }
        startupJob?.start()
    }
    fun retryDefault(callback:Callback):Request {
        stopStartup()
        return Request(scope.launch {
            try {val entry=connectDefault();ensureActive();callback.success(entry)}
            catch(error:CancellationException){if(error is TimeoutCancellationException)callback.failure("连接超时，请检查网络后重试。")}
            catch(error:Exception){callback.failure(failureMessage(error))}
        })
    }
    private suspend fun connectDefault():Entry {
        check(current()==null){"connection changed"}
        val preset=requireNotNull(configuredPreset)
        LibraryRepository().login(preset.address,preset.username,preset.password)
        ownerContext?.getSharedPreferences("jellylive_library",android.content.Context.MODE_PRIVATE)?.edit()?.putString("last_address",preset.address)?.putString("last_username",preset.username)?.putBoolean("bootstrap_retry_pending",false)?.apply()
        return requireNotNull(current())
    }
    private suspend fun readPreset():LibraryBootstrap?=withContext(Dispatchers.IO){
        try {ownerContext?.assets?.open("jellyfin-bootstrap.json")?.use {input->
            val value=org.json.JSONObject(LiveSourceStore.read(input,64*1024));val address=LibraryBootstrap.normalize(value.optString("address"));val username=value.optString("username").trim();val uri=java.net.URI(address)
            if(uri.scheme !in listOf("http","https")||uri.host.isNullOrBlank()||username.isBlank())null else LibraryBootstrap(address,username,value.optString("password"))
        }}catch(error:CancellationException){throw error}catch(_:Exception){null}
    }
    suspend fun verifyAndActivate(entry:Entry)=withTimeout(20000){
        val expected=sessions.currentSession.value
        val server=store.getServer(java.util.UUID.fromString(entry.serverId)) ?: error("missing")
        val user=store.getUser(java.util.UUID.fromString(entry.serverId),java.util.UUID.fromString(entry.userId)) ?: error("missing")
        org.jellyfin.androidtv.util.sdk.FnMediaCompatibility.rememberServer(server.address,server.name)
        val candidate=jellyfin.createApi(server.address,accessToken=requireNotNull(user.accessToken),deviceInfo=api.deviceInfo)
        try {withContext(Dispatchers.IO){candidate.userApi.getCurrentUser().content}}
        catch(error:Exception){
            if(LibraryFailure.from(error)!=LibraryFailure.SESSION)throw error
            val preset=readPreset()
            if(preset==null||!preset.matches(entry.address,entry.username)||currentKey()!=entry.key||sessions.currentSession.value!=expected)throw error
            // An expired token may be renewed only for the already selected matching account.
            LibraryRepository().login(preset.address,preset.username,preset.password)
            return@withTimeout
        }
        ensureActive();check(sessions.currentSession.value==expected){"connection changed"}
        if(!isActive(entry))check(sessions.switchCurrentSession(java.util.UUID.fromString(entry.serverId),java.util.UUID.fromString(entry.userId))){"verify"}
    }
    private fun failureMessage(error:Exception):String=when(LibraryFailure.from(error)){
        LibraryFailure.SESSION->"登录已失效，请重新登录。"
        LibraryFailure.PERMISSION->"当前账号没有访问权限，请检查服务器授权。"
        LibraryFailure.TIMEOUT->"连接超时，请检查网络后重试。"
        else->"无法连接媒体库，请检查网络后重试。"
    }
    fun close(){scope.cancel()}
}
