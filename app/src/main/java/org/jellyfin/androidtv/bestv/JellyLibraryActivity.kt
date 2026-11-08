/* JellyLive media pages, based on supplied 11–24 drawings. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.jellyfin.androidtv.data.compat.StreamInfo
import org.jellyfin.androidtv.data.compat.VideoOptions
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.AsyncImageView
import org.jellyfin.androidtv.ui.playback.PlaybackManager
import org.jellyfin.androidtv.util.apiclient.ReportingHelper
import org.jellyfin.androidtv.util.apiclient.Response
import org.jellyfin.androidtv.util.applyTheme
import org.jellyfin.androidtv.util.profile.createDeviceProfile
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.*
import org.koin.android.ext.android.inject
import java.util.UUID

/** Optional library: backing out always leaves independent live available. */
class JellyLibraryActivity : FragmentActivity() {
    private val repo = LibraryRepository()
    private val playback by inject<PlaybackManager>()
    private val mediaHttpFactory by inject<org.jellyfin.sdk.api.okhttp.OkHttpFactory>()
    private val preferences by inject<UserPreferences>()
    private val reporting by inject<ReportingHelper>()
    private lateinit var ui: TvUi
    private lateinit var page: FrameLayout
    private var pageName = ""
    private data class SavedPage(val view: FrameLayout, val name: String, val focus: Int, val session: String)
    private val history = ArrayDeque<SavedPage>()
    private val gridReloads=java.util.WeakHashMap<FrameLayout,()->Unit>()
    private var task: Job? = null
    private var homeResumeJob: Job? = null
    private var gridRenderJob: Job? = null
    private var generation = 0
    private var interaction = 0
    private var dialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private val local by lazy { getSharedPreferences("jellylive_library", MODE_PRIVATE) }
    private var currentItem: BaseItemDto? = null
    private var seriesEpisodes = emptyList<BaseItemDto>()
    private var player: ExoPlayer? = null
    private var stream: StreamInfo? = null
    private var playingItem: BaseItemDto? = null
    private var playingSessionKey=""
    private var playingPositionPrefix=""
    private var pageSessionKey=""
    private var videoSurface: PlayerView? = null
    private var controls: FrameLayout? = null
    private var seek: SeekBar? = null
    private var clock: TextView? = null
    private var pause: Button? = null
    private var rewindAction: Button?=null
    private var forwardAction: Button?=null
    private var playbackFailure=false
    private var retryPosition=0L
    private var buffering: FrameLayout?=null
    private val showBuffering=Runnable {if(pageName=="player"&&!playbackFailure&&player?.playbackState==Player.STATE_BUFFERING)buffering?.visibility=View.VISIBLE}
    private var playerHeading: FrameLayout? = null
    private var playerWallClock: TextView?=null
    private var speedAction: Button? = null
    private var audioAction: Button? = null
    private var subtitleAction: Button? = null
    private var seekPreview: TextView? = null
    private var nextCard: FrameLayout? = null
    private var nextClock: TextView? = null
    private var nextSeconds=5
    private var nextCancelled=false
    private var manualEnd=false
    private var mediaSpeed=1f
    private var audioLanguage: String?=null
    private var subtitleLanguage: String?=null
    private var subtitleOff=true
    private val nextTick=object: Runnable {
        override fun run(){
            if(pageName!="player"||player?.playbackState!=Player.STATE_ENDED)return
            if(!getSharedPreferences("bestv_live",MODE_PRIVATE).getBoolean("auto_next_episode",true)){cancelAutoNext();return}
            if(dialog?.isShowing==true){nextCard?.visibility=View.GONE;handler.postDelayed(this,500);return}
            nextCard?.visibility=View.VISIBLE
            nextClock?.text="${time(nextSeconds*1000L)} 后播放"
            if(nextSeconds--<=0){playingItem?.let {nextEpisodeItem(it)}?.let {replacePlayback(it,0)};return}
            handler.postDelayed(this,1000)
        }
    }
    private var lastPlayerAction: View? = null
    private var overlay = MediaOverlay()
    private var consumedPlayerConfirm=false
    private var reportStarted = false
    private var lastReport = 0L
    private var lastPersist = 0L
    private var searchInput: EditText? = null
    private var searchResults: LinearLayout? = null
    private var searchJob: Job? = null
    private data class MediaSearchScope(val session: String,val parent: UUID?,val kind: BaseItemKind?)
    private var mediaSearchScope: MediaSearchScope?=null
    private var mediaSearchSerial=0
    private var bootstrap: LibraryBootstrap? = null
    private var bootstrapFailure = false
    private val hideControls = Runnable { if(overlay.expire(android.os.SystemClock.uptimeMillis()))applyControlsVisibility() }
    private val updatePlayer = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.duration.takeIf { it > 0 } ?: ((stream?.runTimeTicks ?: 0) / 10000)
            val shownPosition=overlay.target ?: p.currentPosition
            updateSeekAvailability()
            seek?.progress=if(duration>0)(shownPosition*10000/duration).toInt() else 0
            clock?.text="${time(shownPosition)} / ${if(duration>0)time(duration) else "--:--:--"}"
            playerWallClock?.text=android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())
            pause?.let {TvIcons.text(it,if(p.playWhenReady&&p.playbackState!=Player.STATE_ENDED)"Ⅱ" else "▷")}
            playingItem?.let { item ->
                if(System.currentTimeMillis()-lastPersist>=5000){rememberPosition(item,p.currentPosition);lastPersist=System.currentTimeMillis()}
                if (reportStarted && playingSessionKey==repo.sessionKey && System.currentTimeMillis() - lastReport > 15000) {
                    stream?.let { reporting.reportProgress(this@JellyLibraryActivity, null, item, it, p.currentPosition * 10000, !p.isPlaying) }
                    lastReport = System.currentTimeMillis()
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(state: Bundle?) {
        applyTheme()
        super.onCreate(state)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        ui = TvUi(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { back() } })
        fresh("loading", false)
        header("我的媒体库", "正在恢复连接…")
        restoreInitialConnection()
    }
    private fun restoreInitialConnection() {
        val expected = generation
        task = lifecycleScope.launch {
            bootstrap = withContext(Dispatchers.IO) {
                try {
                    assets.open("jellyfin-bootstrap.json").use { input ->
                        val value = org.json.JSONObject(LiveSourceStore.read(input, 64 * 1024))
                        val address = LibraryBootstrap.normalize(value.optString("address"))
                        val username = value.optString("username").trim()
                        val password = value.optString("password")
                        val uri = java.net.URI(address)
                        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() || username.isBlank()) null
                        else LibraryBootstrap(address, username, password)
                    }
                } catch (_: Exception) { null }
            }
            var restoreFailed = false
            try { repo.restore() }
            catch (error: CancellationException) { if (error !is TimeoutCancellationException) throw error; restoreFailed = true }
            catch (_: Exception) { restoreFailed = true }
            val preset = bootstrap
            val savedAddress = repo.address.ifEmpty { local.getString("last_address", "").orEmpty() }
            val savedUsername = local.getString("last_username", "").orEmpty()
            val consumed = local.getBoolean("bootstrap_consumed", false)
            val attempt = preset?.mayConnect(repo.connected, savedAddress, savedUsername, consumed) == true
            // This is an initial default, never a rule that reselects the owner's NAS after switching/logout.
            if (preset != null && !consumed) local.edit().putBoolean("bootstrap_consumed", true).apply()
            if (attempt && preset != null) {
                try {
                    repo.login(preset.address, preset.username, preset.password)
                    local.edit().putString("last_address", preset.address).putString("last_username", preset.username).apply()
                } catch (error: CancellationException) {
                    if (error !is TimeoutCancellationException) throw error
                    bootstrapFailure = true
                } catch (_: Exception) { bootstrapFailure = true }
            }
            if (expected != generation) return@launch
            when {
                intent.getBooleanExtra("connect", false) -> connection(false)
                repo.connected -> launchDestination()
                attempt -> connection(false)
                restoreFailed -> offline(false)
                else -> disconnected(false)
            }
        }
    }
    private fun fresh(name: String, push: Boolean=true): FrameLayout {
        task?.cancel(); searchJob?.cancel();gridRenderJob?.cancel(); generation++
        if (push && ::page.isInitialized) history.addLast(SavedPage(page,pageName,currentFocus?.id ?: View.NO_ID,pageSessionKey))
        pageName = name;pageSessionKey=repo.sessionKey; page = ui.canvas(); setContentView(page)
        return page
    }
    private fun header(title: String, subtitle: String="") { ui.header(page, title, subtitle) { back() }; ui.footer(page, "方向键移动  OK 确认  返回上一级") }
    private fun <T> load(block: suspend () -> T, success: (T) -> Unit) {
        task?.cancel()
        val expected = generation
        task = lifecycleScope.launch {
            try { val result = withTimeout(25000) { block() }; if (expected == generation) success(result) }
            catch (error: CancellationException) { if (error is TimeoutCancellationException && expected == generation) libraryError(error){load(block,success)} }
            catch (error: Exception) { if (expected == generation) libraryError(error){load(block,success)} }
        }
    }
    private fun libraryError(error: Throwable,retry: ()->Unit){
        val failure=LibraryFailure.from(error)
        fresh("library-error",false);header("我的媒体库")
        when(failure){
            LibraryFailure.SESSION->centerState("登录已过期","请重新登录，继续访问你的媒体库。",listOf("重新登录" to {connection()},"返回看直播" to {finish()}))
            LibraryFailure.PERMISSION->centerState("当前账号没有访问权限","可切换有权限的账号，或返回媒体库。",listOf("切换账号" to {connection()},"返回媒体库" to {home(false)}))
            LibraryFailure.MISSING->centerState("这部媒体已不在库中","刷新媒体库后重新选择内容。",listOf("刷新列表" to {refreshOrigin()},"返回媒体库" to {home(false)}))
            LibraryFailure.INCOMPLETE->centerState("媒体目录未完整返回","请刷新重试，或在服务器检查目录大小与分页设置。",listOf("重新加载" to retry,"返回媒体库" to {home(false)}))
            LibraryFailure.CONTENT->centerState("媒体数据暂时无法读取","服务器返回了无法识别的内容，请重试或检查连接。",listOf("重试" to retry,"连接设置" to {connection()}))
            LibraryFailure.SERVER->centerState("媒体服务器暂时出错","请稍后重试，或检查服务器运行状态。",listOf("重试" to retry,"连接设置" to {connection()}))
            else->centerState(if(failure==LibraryFailure.TIMEOUT)"连接超时" else "暂时联系不上你的 Jellyfin。","请检查服务器与网络，直播仍可独立使用。",listOf("重试" to retry,"连接设置" to {connection()}))
        }
    }
    private fun refreshOrigin(){
        if(history.lastOrNull()?.name=="grid"){
            backToSaved();gridReloads[page]?.invoke() ?: home(false,true)
        }else home(false,true)
    }
    private fun focus(v: View, stamp: Int=interaction) { v.post { if (stamp == interaction && v.isAttachedToWindow) v.requestFocus() } }
    private fun label(text: String, x: Float, y: Float, w: Float, h: Float, size: Int=14, muted: Boolean=false) =
        ui.text(page, text, size, if(muted) TvUi.MUTED else TvUi.INK, x, y, w, h)
    private fun centerState(title: String, subtitle: String, buttons: List<Pair<String, () -> Unit>>) {
        val panel=FrameLayout(this); panel.background=ui.surface(TvUi.PANEL,false); ui.at(page,panel,48f,103f,864f,347f)
        ui.text(panel,"▱",48,0xffb7a2ff.toInt(),408f,72f,48f,48f).gravity=Gravity.CENTER
        ui.title(panel,title,24f,142f,816f,46f).apply {gravity=Gravity.CENTER;maxLines=1}
        ui.text(panel,subtitle,14,TvUi.MUTED,24f,186f,816f,30f).gravity=Gravity.CENTER
        val width=160f
        val start=(864-(buttons.size*width+(buttons.size-1)*14))/2
        val actions=buttons.mapIndexed { i, (name, click) ->
            val button=ui.button(panel,name,start+i*(width+14),251f,width,38f,click)
            if(i==0) focus(button)
            button
        }
        actions.forEachIndexed {i,button->button.nextFocusLeftId=actions.getOrNull(i-1)?.id ?: button.id;button.nextFocusRightId=actions.getOrNull(i+1)?.id ?: button.id;button.nextFocusUpId=button.id;button.nextFocusDownId=button.id}
    }

    private fun disconnected(push: Boolean=true) {
        fresh("disconnected",push); header("我的媒体库")
        centerState("连接你的 Jellyfin，开启私人影院。","你仍然可以随时返回主页观看直播。",listOf("连接 Jellyfin" to { connection() },"▷ 返回看直播" to { finish() }))
    }
    private fun offline(push: Boolean=true) {
        fresh("offline",push); header("我的媒体库")
        centerState("暂时联系不上你的 Jellyfin。","请检查服务器或网络；独立直播仍可使用。",listOf("重新连接" to { home(false) },"连接设置" to { connection() },"▷ 返回看直播" to { finish() }))
    }
    private fun empty(title: String="我的媒体库", push: Boolean=false, retry: () -> Unit={home(false)}) {
        fresh("empty",push);header(title)
        centerState("连接成功，还没有媒体内容。","请在 Jellyfin 服务器添加媒体库，再回来刷新。",listOf("刷新媒体库" to retry,"▷ 返回看直播" to { finish() }))
    }
    private fun connection(push: Boolean=true) {
        fresh("connection",push); header("连接 Jellyfin","媒体库为可选功能，不影响直播")
        val summary=label(if(repo.connected) "已连接 · ${repo.serverName}" else "尚未连接",64f,101f,540f,30f,13)
        val addressLabel=label("服务器地址",64f,151f,540f,24f,14,true)
        val preset=bootstrap
        val initialAddress=repo.address.ifEmpty { local.getString("last_address","").orEmpty() }.ifEmpty { preset?.address.orEmpty() }
        val initialUsername=local.getString("last_username","").orEmpty().ifEmpty { if(preset?.matchesAddress(initialAddress)==true) preset.username else "" }
        val address=ui.input(page,"http://服务器地址:8096",initialAddress,64f,181f,540f,false)
        val usernameLabel=label("用户名",64f,237f,540f,24f,14,true)
        val username=ui.input(page,"输入用户名",initialUsername,64f,267f,540f,false)
        val passwordLabel=label("密码",64f,322f,540f,24f,14,true)
        val password=ui.input(page,"输入密码",if(preset?.matches(initialAddress,initialUsername)==true) preset.password else "",64f,352f,540f,true)
        val clearForeignPassword=object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(preset!=null && !preset.matches(address.text.toString(),username.text.toString())) password.setText("")
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        address.addTextChangedListener(clearForeignPassword);username.addTextChangedListener(clearForeignPassword)
        label("连接后可以",655f,164f,257f,32f,18)
        label("✓  浏览电影和剧集\n\n✓  同步继续观看进度\n\n✓  使用服务器提供的直播",655f,201f,257f,150f,14,true)
        val status=label("",64f,394f,540f,25f,12,true)
        if(bootstrapFailure) status.text="自动连接未成功，可检查网络后重试"
        lateinit var submit: Button
        submit=ui.button(page,"检测并连接",64f,420f,150f,39f) {
            val a=address.text.toString().trim(); val u=username.text.toString().trim()
            if (a.isEmpty() || u.isEmpty()) { status.text="请填写服务器地址和用户名"; return@button }
            val normalized=if(a.contains("://")) a else "http://$a"
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {status.text="请输入 http 或 https 服务器地址";return@button}
            submit.isEnabled=false; status.text="正在验证服务器并登录…"
            val stamp=generation
            task=lifecycleScope.launch {
                try {
                    repo.login(normalized,u,password.text.toString())
                    password.setText("");local.edit().putString("last_address",normalized).putString("last_username",u).putBoolean("bootstrap_consumed",true).apply()
                    if(stamp==generation) { history.clear();launchDestination() }
                } catch(e: CancellationException) { if (e is TimeoutCancellationException && stamp==generation) status.text="连接超时，请检查服务器和网络" }
                catch(e: Exception) { if(stamp==generation) status.text=e.message?.takeIf { it.length<100 } ?: "连接失败，请检查地址和账号" }
                finally { if(stamp==generation) submit.isEnabled=true }
            }
        }
        ui.button(page,"取消",228f,420f,100f,39f) {back()}
        val discover=ui.button(page,"发现局域网服务器",655f,374f,240f,39f) {
            status.text="正在发现局域网服务器…"
            val stamp=generation
            task=lifecycleScope.launch {
                try {
                    val found=repo.discovery()
                    if(stamp==generation) {
                        status.text=if(found.isEmpty()) "未发现服务器，也可手动输入地址" else "选择服务器后输入账号密码"
                        if(found.isNotEmpty()) choose("选择服务器",found.map { it.name },-1) { address.setText(found[it].address);username.requestFocus() }
                    }
                } catch (_: Exception) { if(stamp==generation) status.text="发现暂不可用，请手动输入地址" }
            }
        }
        address.nextFocusDownId=username.id;username.nextFocusDownId=password.id;password.nextFocusDownId=submit.id
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val formScroll=scrollContent(490f)
        TvImeForm(ui,page,formScroll,formScroll.getChildAt(0) as FrameLayout,listOf(address,username,password),listOf(addressLabel,usernameLabel,passwordLabel),summary,submit)
        listOf(address,username,password).forEach {field->field.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_NEXT}
        password.imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        password.setOnEditorActionListener {_,action,_->if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(password.windowToken,0);submit.requestFocus();true}else false}
        focus(address)
    }

    private fun <T: View> place(parent: FrameLayout,view: T,box: LibraryPageLayout.Box): T =
        ui.at(parent,view,box.x,box.y,box.width,box.height)
    private fun pageText(parent: FrameLayout,text: String,size: Int,color: Int,box: LibraryPageLayout.Box)=place(parent,ui.text(text,size,color),box)
    private fun focusCard(box: FrameLayout,action: ()->Unit): FrameLayout {
        box.id=View.generateViewId();box.isFocusable=true;box.isFocusableInTouchMode=true;box.isClickable=true
        box.background=ui.surface(TvUi.PANEL,false);box.clipToOutline=true
        box.setOnFocusChangeListener {_,focused->box.foreground=if(focused)android.graphics.drawable.GradientDrawable().apply {setColor(Color.TRANSPARENT);cornerRadius=ui.px(8f).toFloat();setStroke(ui.px(2f),Color.WHITE)}else null}
        box.setOnClickListener {action()};return box
    }
    /** Gallery 15: navigation cards and resume playback, with no category poster grid. */
    private fun home(push: Boolean=true,refresh: Boolean=false) {
        if(!repo.connected){disconnected(push);return}
        fresh("home",push);header("我的媒体库","${repo.serverName} · 已连接")
        val host=page;val session=repo.sessionKey;val initialInteraction=interaction
        val search=place(host,ui.button("⌕ 搜索媒体"){search()},LibraryPageLayout.HOME_SEARCH)
        pageText(host,"继续观看",17,TvUi.INK,LibraryPageLayout.HOME_RESUME_LABEL).apply {setTypeface(null,Typeface.BOLD)}
        val resumeArea=place(host,FrameLayout(this),LibraryPageLayout.HOME_RESUME_ROW)
        resumeStatus(resumeArea,"正在加载继续观看…")
        pageText(host,"探索媒体库",17,TvUi.INK,LibraryPageLayout.HOME_EXPLORE_LABEL).apply {setTypeface(null,Typeface.BOLD)}
        val illustrations=listOf(org.jellyfin.androidtv.R.drawable.library_entry_movies,org.jellyfin.androidtv.R.drawable.library_entry_series,org.jellyfin.androidtv.R.drawable.library_entry_recent)
        val entries=LibraryPageLayout.Entry.entries.mapIndexed {index,entry->
            val card=homeCard(entry.title,"浏览全部内容",illustrations[index]){renderGrid(entry.title,null,entry.query())}
            card.tag="library-entry-${entry.name.lowercase()}";place(host,card,LibraryPageLayout.homeEntry(index))
        }
        entries.forEachIndexed {index,card->card.nextFocusLeftId=entries.getOrNull(index-1)?.id ?: card.id;card.nextFocusRightId=entries.getOrNull(index+1)?.id ?: card.id;card.nextFocusUpId=search.id;card.nextFocusDownId=card.id}
        search.nextFocusDownId=entries.first().id;focus(entries.first(),initialInteraction)
        lifecycleScope.launch {
            val catalog=try {withTimeout(25000){repo.catalog(refresh=refresh)}}catch(error: CancellationException){if(error !is TimeoutCancellationException)throw error;return@launch}catch(error: Exception){if(page===host)libraryError(error){home(false,true)};return@launch}
            if(page!==host||session!=repo.sessionKey)return@launch
            val counts=withContext(Dispatchers.Default){LibraryPageLayout.Entry.entries.map {LibraryBrowse.select(catalog,it.query()).size}}
            if(page!==host||session!=repo.sessionKey)return@launch
            if(counts.sum()==0){if(page===host)empty(retry={home(false,true)});return@launch}
            LibraryPageLayout.Entry.entries.forEachIndexed {index,entry->
                val count=counts[index]
                entries[index].findViewWithTag<TextView>("entry-subtitle")?.text="${count} ${if(entry==LibraryPageLayout.Entry.MOVIES)"部电影" else if(entry==LibraryPageLayout.Entry.SERIES)"部剧集" else "项内容"}"
            }
        }
        host.tag=Runnable {refreshHomeResume(host,resumeArea,search,entries,null)}
        refreshHomeResume(host,resumeArea,search,entries,initialInteraction)
    }
    private fun resumeStatus(area: FrameLayout,message: String){
        area.removeAllViews()
        val bounds=LibraryPageLayout.HOME_RESUME_ROW
        ui.text(area,message,14,TvUi.MUTED,0f,0f,bounds.width,bounds.height).apply {gravity=Gravity.CENTER;tag="home-resume-status"}
    }
    private fun refreshHomeResume(host: FrameLayout,area: FrameLayout,search: View,entries: List<View>,initialInteraction: Int?){
        val session=repo.sessionKey;val prefix=currentPositionPrefix()
        homeResumeJob?.cancel()
        homeResumeJob=lifecycleScope.launch {
            val ids=LibraryResume.localIds(local.all,prefix)
            val (server,localItems)=coroutineScope {
                val remote=async {
                    try {withTimeout(8000){repo.resume()}}
                    catch(error: CancellationException){if(error !is TimeoutCancellationException)throw error;null}
                    catch(_: Exception){null}
                }
                // Resolve saved IDs through this session so removed/inaccessible items and
                // remotely completed titles cannot reappear from an old catalog snapshot.
                val saved=async {ids.map {id->async {
                    try {withTimeout(6000){repo.detail(id)}}
                    catch(error: CancellationException){if(error !is TimeoutCancellationException)throw error;null}
                    catch(_: Exception){null}
                }}.awaitAll().filterNotNull()}
                remote.await() to saved.await()
            }
            if(page!==host||session!=repo.sessionKey)return@launch
            val resumes=LibraryResume.select(server.orEmpty(),localItems,::resumeMs,{item->if(local.getLong(positionKey(item),0)>0)local.getLong("seen_${positionKey(item)}",0)else 0})
            val previous=currentFocus?.takeIf {isWithin(it,area)}?.tag
            area.removeAllViews()
            entries.forEach {it.nextFocusUpId=search.id};search.nextFocusDownId=entries.first().id
            if(resumes.isEmpty()){
                resumeStatus(area,if(server==null||ids.isNotEmpty()&&localItems.isEmpty())"暂时无法读取观看历史，请检查媒体库连接。" else "暂无观看历史")
                if(previous!=null)entries.first().requestFocus()
            }else{
                val scroller=HorizontalScrollView(this@JellyLibraryActivity);scroller.isHorizontalScrollBarEnabled=false;val row=LinearLayout(this@JellyLibraryActivity);scroller.addView(row)
                val cards=resumes.map {item->homeCard(cardTitle(item),if(item.type==BaseItemKind.EPISODE)"第 ${item.indexNumber ?: "?"} 集 · ${time(resumeMs(item))}" else "继续播放 · ${time(resumeMs(item))}",item=item){play(item,resumeMs(item))}.also {it.tag="resume-item-${item.id}";row.addView(it,LinearLayout.LayoutParams(ui.px(276f),ui.px(130f)).apply {marginEnd=ui.px(16f)})}}
                ui.at(area,scroller,0f,0f,LibraryPageLayout.HOME_RESUME_ROW.width,LibraryPageLayout.HOME_RESUME_ROW.height)
                cards.forEachIndexed {i,card->card.nextFocusUpId=search.id;card.nextFocusDownId=entries[i.coerceAtMost(entries.lastIndex)].id}
                entries.forEachIndexed {i,card->card.nextFocusUpId=cards[i.coerceAtMost(cards.lastIndex)].id}
                search.nextFocusDownId=cards.first().id
                if(previous!=null)(cards.firstOrNull {it.tag==previous} ?: cards.first()).requestFocus()
                else if(initialInteraction!=null)focus(cards.first(),initialInteraction)
            }
        }
    }
    private fun isWithin(view: View,parent: View): Boolean {
        var current: View?=view
        while(current!=null){if(current===parent)return true;current=current.parent as? View}
        return false
    }
    private fun homeCard(name: String,subtitle: String,illustration: Int?=null,item: BaseItemDto?=null,click: ()->Unit): FrameLayout {
        val card=focusCard(FrameLayout(this),click)
        if(item!=null)art(card,item,0f,0f,276f,LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT,true)
        else ui.at(card,ImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(requireNotNull(illustration))},0f,0f,276f,LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT)
        ui.at(card,View(this).apply {setBackgroundColor(0xff10182a.toInt())},0f,if(ui.largeText())62f else LibraryPageLayout.HOME_CARD_INFO_TOP,276f,if(ui.largeText())68f else 50f)
        pageText(card,name,16,TvUi.INK,LibraryPageLayout.HOME_CARD_TITLE.let {if(ui.largeText())it.copy(y=65f,height=31f)else it}).apply {setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        pageText(card,subtitle,12,TvUi.MUTED,LibraryPageLayout.HOME_CARD_SUBTITLE.let {if(ui.largeText())it.copy(y=99f,height=28f)else it}).apply {tag="entry-subtitle";maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        if(item!=null&&(item.runTimeTicks ?: 0)>0){val b=LibraryPageLayout.HOME_CARD_PROGRESS;ui.at(card,View(this).apply {setBackgroundColor(0xff414760.toInt())},b.x,b.y,b.width,b.height);ui.at(card,View(this).apply {setBackgroundColor(0xffac9aff.toInt())},b.x,b.y,b.width*(resumeMs(item)*10000.0/item.runTimeTicks!!).coerceIn(0.0,1.0).toFloat(),b.height)}
        card.contentDescription="$name，$subtitle";return card
    }
    private fun filterCount(query: LibraryBrowse.Query)=listOf(query.state!=LibraryBrowse.State.ALL,query.genre!=null,query.year!=null,query.resolution!=null,query.region!=null).count {it}
    private fun browseDialog(create: ()->AlertDialog){
        val anchor=currentFocus;val popup=create();dialog=popup
        popup.setOnDismissListener {if(dialog===popup)dialog=null;anchor?.takeIf {it.isAttachedToWindow&&it.isShown}?.requestFocus()}
    }
    /** Gallery 16: one category, one toolbar, one row of posters and centered pagination. */
    private fun renderGrid(title: String,parent: UUID?,initialQuery: LibraryBrowse.Query,start: Int=0,push: Boolean=true,refresh: Boolean=false) {
        if(!repo.connected){disconnected(push);return}
        fresh("grid",push);header(title,"正在准备媒体目录…");val session=repo.sessionKey
        ui.heading(page,"正在读取媒体目录…",17,48f,102f,864f,28f)
        ui.text(page,"首次打开大型媒体库可能需要一点时间。",14,TvUi.MUTED,48f,132f,864f,28f)
        repeat(LibraryPageLayout.columns(ui.largeText())) {index->
            val box=LibraryPageLayout.poster(index,ui.largeText());ui.at(page,View(this).apply {background=ui.surface(0xff171d32.toInt(),false)},box.x,171f,box.width,195f)
            ui.at(page,View(this).apply {setBackgroundColor(0xff30364e.toInt())},box.x+11f,335f,75f,8f)
        }
        val cancel=ui.button(page,"返回媒体库",350f,419f,260f,42f){back()};focus(cancel)
        load({repo.catalog(parent,refresh)}) {catalog->
            if(session!=repo.sessionKey){home(false);return@load}
            fresh("grid",false);val host=page;val large=ui.largeText()
            ui.header(host,title,"",{back()});ui.footer(host,"方向键浏览 · 确认箭头翻页 · 返回媒体库")
            val subtitle=ui.text(host,"正在加载媒体…",12,TvUi.MUTED,48f,if(large)78f else 65f,690f,if(large)30f else 23f)
            var query=initialQuery;var offset=start;val updates=LibraryGridUpdates()
            val posterHost=FrameLayout(this);ui.at(host,posterHost,0f,0f,960f,489f);posterHost.isFocusable=false
            val search=place(host,ui.button("⌕ 搜索媒体"){search(parent=parent,kind=query.kind)},LibraryPageLayout.toolbar(LibraryPageLayout.GRID_SEARCH,large)).apply {tag="grid-search"}
            val filter=place(host,ui.button("类型：全部"){browseDialog {LibraryBrowseDialogs.filters(this,ui,catalog,query){changed->query=changed;offset=0;(host.tag as? Runnable)?.run()}}},LibraryPageLayout.toolbar(LibraryPageLayout.GRID_FILTER,large)).apply {tag="grid-filter"}
            val sort=place(host,ui.button("排序：名称正序"){browseDialog {LibraryBrowseDialogs.sort(this,ui,query){changed->query=changed;offset=0;(host.tag as? Runnable)?.run()}}},LibraryPageLayout.toolbar(LibraryPageLayout.GRID_SORT,large)).apply {tag="grid-sort"}
            search.nextFocusRightId=filter.id;filter.nextFocusLeftId=search.id;filter.nextFocusRightId=sort.id;sort.nextFocusLeftId=filter.id;sort.nextFocusRightId=sort.id;search.nextFocusLeftId=search.id
            listOf(search,filter,sort).forEach {it.setSingleLine();it.ellipsize=android.text.TextUtils.TruncateAt.END}
            var selectedColumn=0;var preferred: String?=null
            fun update(){
                if(page!==host||session!=repo.sessionKey)return
                gridRenderJob?.cancel();val request=updates.begin(query,offset) ?: return
                val requestedQuery=request.query;val requestedOffset=request.offset
                gridRenderJob=lifecycleScope.launch {
                    val result=withContext(Dispatchers.Default){LibraryBrowse.page(LibraryBrowse.select(catalog,requestedQuery),requestedOffset,LibraryPageLayout.columns(large))}
                    if(page!==host||session!=repo.sessionKey)return@launch
                    val firstRender=!updates.hasResult;if(!updates.accept(request,result.start))return@launch
                    val previousFocus=currentFocus;posterHost.removeAllViews();offset=result.start
                    val count=filterCount(requestedQuery)
                    subtitle.text="${if(requestedQuery.kind==BaseItemKind.MOVIE)"全部电影" else if(requestedQuery.kind==BaseItemKind.SERIES)"全部剧集" else "全部媒体"} · ${result.total} ${if(requestedQuery.kind==BaseItemKind.MOVIE)"部" else "项"}${if(count>0)" · 已筛选" else ""}"
                    TvIcons.text(filter,if(count==0)"类型：全部" else if(count==1&&requestedQuery.genre!=null)"类型：${requestedQuery.genre}" else "筛选：${count}项")
                    val order=when(requestedQuery.sort){LibraryBrowse.Sort.ADDED->if(requestedQuery.ascending)"最早添加" else "最近添加";LibraryBrowse.Sort.WATCHED->if(requestedQuery.ascending)"最早观看" else "最近观看";LibraryBrowse.Sort.YEAR->if(requestedQuery.ascending)"发布时间升序" else "发布时间降序";LibraryBrowse.Sort.RATING->if(requestedQuery.ascending)"评分升序" else "评分降序";LibraryBrowse.Sort.NAME->if(requestedQuery.ascending)"名称正序" else "名称倒序";LibraryBrowse.Sort.DURATION->if(requestedQuery.ascending)"时长升序" else "时长降序"}
                    TvIcons.text(sort,"排序：$order")
                    val cards=result.items.mapIndexed {index,item->
                        val card=posterCard(item,large){detail(item)};card.tag="grid-item-${item.id}";val existing=card.onFocusChangeListener
                        card.setOnFocusChangeListener {view,focused->existing?.onFocusChange(view,focused);if(focused)selectedColumn=index}
                        place(posterHost,card,LibraryPageLayout.poster(index,large))
                    }
                    var recovery: Button?=null
                    if(cards.isEmpty()){
                        ui.text(posterHost,"⌕",48,0xffb7a2ff.toInt(),456f,175f,48f,48f).gravity=Gravity.CENTER
                        ui.text(posterHost,if(count>0)"没有符合条件的内容" else "这个分类暂时没有媒体内容",22,TvUi.INK,48f,245f,864f,36f).gravity=Gravity.CENTER
                        ui.text(posterHost,if(count>0)"试试清除筛选，或更改类型与条件。" else "可刷新分类，或返回媒体库选择其他内容。",14,TvUi.MUTED,48f,282f,864f,28f).gravity=Gravity.CENTER
                        recovery=ui.button(posterHost,if(count>0)"清除筛选" else "刷新分类",350f,330f,260f,42f){if(count>0){query=query.copy(state=LibraryBrowse.State.ALL,genre=null,year=null,resolution=null,region=null);offset=0;update()}else renderGrid(title,parent,query,0,false,true)}
                    }
                    val previous=place(posterHost,ui.button("‹"){offset=result.start-result.size;preferred="grid-previous";update()},LibraryPageLayout.pagerPrevious(large)).apply {tag="grid-previous";contentDescription="上一页";isEnabled=result.previous;isFocusable=result.previous;alpha=if(result.previous)1f else .35f}
                    pageText(posterHost,result.range,13,TvUi.MUTED,LibraryPageLayout.pagerRange(large)).gravity=Gravity.CENTER
                    val next=place(posterHost,ui.button("›"){offset=result.start+result.size;preferred="grid-next";update()},LibraryPageLayout.pagerNext(large)).apply {tag="grid-next";contentDescription="下一页";isEnabled=result.next;isFocusable=result.next;alpha=if(result.next)1f else .35f}
                    cards.forEachIndexed {i,card->card.nextFocusLeftId=cards.getOrNull(i-1)?.id ?: card.id;card.nextFocusRightId=cards.getOrNull(i+1)?.id ?: card.id;card.nextFocusUpId=listOf(search,filter,sort)[(i*3/LibraryPageLayout.columns(large)).coerceAtMost(2)].id;card.nextFocusDownId=if(next.isEnabled)next.id else if(previous.isEnabled)previous.id else card.id}
                    val target=cards.getOrNull(selectedColumn.coerceAtMost(cards.lastIndex)) ?: recovery ?: filter
                    recovery?.apply {nextFocusUpId=filter.id;nextFocusLeftId=id;nextFocusRightId=id;nextFocusDownId=id}
                    previous.nextFocusUpId=target.id;next.nextFocusUpId=target.id;previous.nextFocusRightId=if(next.isEnabled)next.id else previous.id;next.nextFocusLeftId=if(previous.isEnabled)previous.id else next.id
                    listOf(search,filter,sort).forEach {it.nextFocusDownId=target.id}
                    if(dialog?.isShowing!=true){
                        val wanted=preferred?.let {host.findViewWithTag<View>(it)}?.takeIf {it.isEnabled&&it.isFocusable};preferred=null
                        when {firstRender->focus(target);wanted!=null->focus(wanted);previousFocus?.isAttachedToWindow==true->Unit;else->focus(target)}
                    }
                }
            }
            gridReloads[host]={renderGrid(title,parent,query,offset,false,true)}
            host.tag=Runnable {update()};update()
        }
    }
    private fun posterCard(item: BaseItemDto,large: Boolean,click: ()->Unit): FrameLayout {
        val card=focusCard(FrameLayout(this),click);val bounds=LibraryPageLayout.poster(0,large)
        card.background=ColorDrawable(Color.TRANSPARENT);card.clipToOutline=false
        val imageHost=FrameLayout(this).apply {background=ui.surface(TvUi.PANEL,false);clipToOutline=true};ui.at(card,imageHost,0f,0f,bounds.width,LibraryPageLayout.posterImageHeight(large))
        art(imageHost,item,0f,0f,bounds.width,LibraryPageLayout.posterImageHeight(large))
        val imageHeight=LibraryPageLayout.posterImageHeight(large)
        ui.at(card,View(this).apply {background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x0010182a,0xee10182a.toInt()))},0f,imageHeight-48f,bounds.width,48f)
        val caption=pageText(card,cardTitle(item),14,TvUi.INK,LibraryPageLayout.posterTitle(large)).apply {
            setTypeface(null,Typeface.BOLD);setSingleLine();gravity=Gravity.CENTER;ellipsize=android.text.TextUtils.TruncateAt.END;marqueeRepeatLimit=-1
        }
        val original=card.onFocusChangeListener
        card.setOnFocusChangeListener {v,focused->original?.onFocusChange(v,focused);if(focused)card.foreground=android.graphics.drawable.InsetDrawable(ui.surface(Color.TRANSPARENT,false).apply {setColor(Color.TRANSPARENT);setStroke(ui.px(2f),Color.WHITE)},0,0,0,ui.px(bounds.height-imageHeight));caption.ellipsize=if(focused&&!ui.reduceMotion())android.text.TextUtils.TruncateAt.MARQUEE else android.text.TextUtils.TruncateAt.END;caption.isSelected=focused&&!ui.reduceMotion()}
        pageText(card,cardMeta(item),12,TvUi.MUTED,LibraryPageLayout.posterMetadata(large)).apply {gravity=Gravity.TOP;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        LibraryMetadata.rating(item)?.let {rating->ui.text(card,rating,13,0xfff7d68d.toInt(),7f,7f,41f,21f).apply {gravity=Gravity.CENTER;background=ui.glass(TvUi.PANEL)}}
        if(item.userData?.played==true)ui.text(card,"已看",10,TvUi.INK,bounds.width-43f,7f,36f,18f).apply {gravity=Gravity.CENTER;background=ui.surface(0xcc171c32.toInt(),false)}
        else if(resumeMs(item)>0&&(item.runTimeTicks ?: 0)>0){val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.max=1000;progress.progress=(resumeMs(item)*10000.0*1000/item.runTimeTicks!!).toInt().coerceIn(0,1000);ui.at(card,progress,4f,LibraryPageLayout.posterImageHeight(large)-4f,bounds.width-8f,3f)}
        card.contentDescription=listOfNotNull(cardTitle(item),LibraryMetadata.rating(item)?.let {"评分 $it"},cardMeta(item).takeIf {it.isNotBlank()},if(item.userData?.played==true)"已看" else null).joinToString("，")
        return card
    }
    private fun cardTitle(item: BaseItemDto)=if(item.type==BaseItemKind.EPISODE)item.seriesName ?: item.name ?: "未命名剧集" else item.name ?: "未命名媒体"
    private fun cardMeta(item: BaseItemDto)=listOfNotNull(LibraryMetadata.year(item)?.toString(),item.genres?.firstOrNull()).joinToString(" · ")
    private fun section(body: LinearLayout, title: String) { body.addView(ui.text(title,17,TvUi.INK),LinearLayout.LayoutParams(-1,ui.px(35f)).apply {topMargin=ui.px(7f);bottomMargin=ui.px(6f)}) }
    private fun horizontal(body: LinearLayout, items: List<BaseItemDto>, progress: Boolean=false, click: (BaseItemDto)->Unit) {
        val sc=HorizontalScrollView(this);sc.isHorizontalScrollBarEnabled=false
        val row=LinearLayout(this)
        items.forEach {item -> row.addView(tile(cardTitle(item), if(progress) if(item.type==BaseItemKind.EPISODE) "第 ${item.indexNumber ?: 1} 集 · ${time(resumeMs(item))}" else "继续播放 · ${time(resumeMs(item))}" else meta(item),287,209,item,progress) {click(item)},LinearLayout.LayoutParams(ui.px(276f),ui.px(209f)).apply {marginEnd=ui.px(14f)})}
        sc.addView(row);body.addView(sc,LinearLayout.LayoutParams(-1,ui.px(217f)))
    }
    private fun tile(name: String, sub: String, w: Int, h: Int, item: BaseItemDto?, progress: Boolean=false, poster: BaseItemDto?=null, click: ()->Unit): FrameLayout {
        val box=FrameLayout(this);box.id=View.generateViewId();box.isFocusable=true;box.isFocusableInTouchMode=true;box.isClickable=true;box.background=ui.focusBackground(TvUi.PANEL);box.setPadding(ui.px(2f),ui.px(2f),ui.px(2f),ui.px(2f));box.clipToOutline=true
        val compact=w>h&&!progress
        val landscape=progress||(item?.type==BaseItemKind.EPISODE&&poster==null)
        val artH=if(compact)h-12 else if(landscape)(w*9/16).coerceAtMost(h-48)else w*3/2
        val artW=if(compact)if(landscape)artH*16/9 else artH*2/3 else w
        val textX=if(compact)artW+16f else 8f
        val textWidth=w-textX-8f
        if(item!=null) art(box,poster ?: item,0f,if(compact)6f else 0f,artW.toFloat(),artH.toFloat(),landscape)
        else {val v=ui.text("▱",36,0xff8275bd.toInt());v.gravity=Gravity.CENTER;ui.at(box,v,0f,0f,w.toFloat(),artH.toFloat())}
        ui.text(box,name,14,TvUi.INK,textX,if(compact)12f else artH+4f,textWidth,if(landscape&&!compact)22f else 34f).apply {maxLines=if(landscape&&!compact)1 else 2;ellipsize=android.text.TextUtils.TruncateAt.END;setTypeface(null,Typeface.BOLD)}
        ui.text(box,sub,11,TvUi.MUTED,textX,if(compact)h-32f else artH+if(landscape)27f else 40f,textWidth,18f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        if(item?.userData?.played==true)ui.text(box,"已看",10,TvUi.INK,w-43f,7f,36f,18f).apply{gravity=Gravity.CENTER;background=ui.surface(0xcc171c32.toInt(),false)}
        else if(item!=null&&resumeMs(item)>0&&(item.runTimeTicks ?: 0)>0){
            val p=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.max=1000;p.progress=(resumeMs(item)*10000*1000/item.runTimeTicks!!).toInt().coerceIn(0,1000)
            ui.at(box,p,4f,artH-4f,artW-8f,3f)
        }
        box.setOnFocusChangeListener { _, focused ->
            box.foreground=if(focused) android.graphics.drawable.GradientDrawable().apply {setColor(Color.TRANSPARENT);cornerRadius=ui.px(8f).toFloat();setStroke(ui.px(2f),Color.WHITE)} else null
        }
        box.setOnClickListener {click()};return box
    }
    private fun art(parent: FrameLayout,item: BaseItemDto,x: Float,y: Float,w: Float,h: Float,backdrop: Boolean=false) {
        val image=AsyncImageView(this);image.allowHardwareBitmaps=false;image.scaleType=ImageView.ScaleType.CENTER_CROP;image.setBackgroundColor(0xff20283d.toInt());ui.at(parent,image,x,y,w,h)
        image.load(url=repo.image(item,backdrop),placeholder=ColorDrawable(0xff20283d.toInt()),aspectRatio=if(backdrop) 16.0/9 else 2.0/3)
        if(repo.image(item,backdrop)==null) ui.text(parent,"暂无封面",12,TvUi.MUTED,x+10,y+h/2-10,w-20,20f).gravity=Gravity.CENTER
    }
    private fun grid(title: String,parent: UUID?=null,kind: BaseItemKind?=null,start: Int=0,alphabetical: Boolean=true,genre: String?=null,push: Boolean=true) =
        renderGrid(title,parent,LibraryBrowse.Query(kind=kind,genre=genre,sort=if(alphabetical)LibraryBrowse.Sort.NAME else LibraryBrowse.Sort.ADDED,ascending=alphabetical),start,push)
    private fun launchDestination() {
        val requested=intent.getStringExtra("item_id")?.trim().orEmpty()
        if(requested.isNotEmpty()) {
            val id=try {UUID.fromString(requested)}catch(_:IllegalArgumentException){null}
            if(id!=null){detailById(id,false);return}
            Toast.makeText(this,"媒体标识无效，已返回媒体库",Toast.LENGTH_SHORT).show()
        }
        if(intent.hasExtra("search_query"))search(intent.getStringExtra("search_query").orEmpty(),false)else home(false)
    }
    private fun detail(item: BaseItemDto) = detailById(item.id)
    private fun detailById(id: UUID,push: Boolean=true) {
        fresh("detail",push);header("媒体详情","正在加载…")
        load({repo.detail(id)}) {full ->
            currentItem=full
            if(full.type==BaseItemKind.SERIES)series(full,false)else movie(full,false)
        }
    }
    private fun movie(item: BaseItemDto,push: Boolean=false) {
        fresh("movie",push);val large=ui.largeText()
        art(page,item,0f,0f,960f,540f,true)
        ui.at(page,View(this).apply {background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xfa101326.toInt(),0xeb101326.toInt(),0x7a101326))},0f,0f,960f,540f)
        header(if(item.type==BaseItemKind.EPISODE)"剧集详情" else "电影详情")
        art(page,item,48f,107f,140f,210f)
        label(item.name ?: "未命名媒体",212f,109f,700f,if(large)65f else 43f,32).apply {setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        item.taglines?.firstOrNull()?.let {label(it,212f,if(large)151f else 153f,700f,if(large)36f else 24f,14,true).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}}
        label(listOfNotNull(LibraryMetadata.rating(item)?.let {"★ $it"},LibraryMetadata.year(item)?.toString(),item.productionLocations?.firstOrNull(),item.runTimeTicks?.takeIf {it>0}?.let {time(it/10000)}).joinToString(" · "),212f,if(large)190f else 180f,700f,if(large)38f else 25f,14,true)
        label(listOfNotNull(LibraryBrowse.resolution(item)?.let {if(it==2160)"4K" else "${it}p"},item.genres?.take(3)?.joinToString(" / ")?.takeIf {it.isNotEmpty()}).joinToString(" · "),212f,if(large)221f else 209f,700f,if(large)38f else 25f,13,true)
        label(item.overview?.replace(Regex("<[^>]+>"),"") ?: "暂无简介",212f,if(large)252f else 246f,700f,if(large)72f else 50f,15).apply {maxLines=2;gravity=Gravity.TOP;setLineSpacing(ui.px(if(large)0f else 7f).toFloat(),1f);ellipsize=android.text.TextUtils.TruncateAt.END}
        val position=resumeMs(item)
        val first=ui.button(page,if(position>0)"继续播放 ${time(position)}" else "播放",212f,if(large)330f else 311f,if(large)342f else 224f,if(large)50f else 38f){play(item,position)}
        ui.button(page,"从头播放",if(large)566f else 448f,if(large)330f else 311f,if(large)342f else 224f,if(large)50f else 38f){play(item,0)}
        val watched=ui.button(page,if(item.userData?.played==true)"标为未看" else "标为已看",if(large)212f else 684f,if(large)393f else 311f,if(large)342f else 224f,if(large)50f else 38f){}
        var isPlayed=item.userData?.played==true
        watched.setOnClickListener {
            watched.isEnabled=false;val session=repo.sessionKey;val host=page
            lifecycleScope.launch {
                try {repo.setPlayed(item.id,!isPlayed);if(session==repo.sessionKey&&page===host){isPlayed=!isPlayed;TvIcons.text(watched,if(isPlayed)"标为未看" else "标为已看");local.edit().remove(positionKey(item)).remove("seen_${positionKey(item)}").apply()}}
                catch(_:Exception){if(page===host)Toast.makeText(this@JellyLibraryActivity,"观看状态未保存，请重试",Toast.LENGTH_SHORT).show()}
                finally {if(page===host)watched.isEnabled=true}
            }
        }
        val directors=item.people.orEmpty().filter {it.type==PersonKind.DIRECTOR}.mapNotNull {it.name}
        val actors=item.people.orEmpty().filter {it.type==PersonKind.ACTOR}.mapNotNull {it.name}
        if(!large)label(listOfNotNull(directors.takeIf {it.isNotEmpty()}?.take(2)?.joinToString(" / ")?.let {"导演  $it"},actors.takeIf {it.isNotEmpty()}?.take(3)?.joinToString(" / ")?.let {"主演  $it"}).joinToString("    "),212f,if(large)455f else 362f,700f,if(large)42f else 28f,14,true).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        label(listOfNotNull(if(item.type==BaseItemKind.EPISODE)"剧集" else "电影",LibraryBrowse.resolution(item)?.let {if(it==2160)"4K" else "${it}p"}).joinToString(" · "),48f,345f,140f,if(large)36f else 24f,13,true)
        label(item.genres.orEmpty().take(2).joinToString(" / "),48f,if(large)385f else 370f,140f,if(large)36f else 24f,13,true)
        val people=item.people.orEmpty().take(8)
        if(people.isNotEmpty()){
            label("演职人员",48f,if(large)454f else 397f,600f,if(large)45f else 30f,16)
            val scroll=HorizontalScrollView(this);scroll.isHorizontalScrollBarEnabled=false;scroll.isFocusable=true;val row=LinearLayout(this);scroll.addView(row);ui.at(page,scroll,48f,if(large)500f else 432f,864f,if(large)104f else 76f)
            people.forEach {person->
                val card=FrameLayout(this);card.background=ui.surface(TvUi.PANEL,false);row.addView(card,LinearLayout.LayoutParams(ui.px(165f),ui.px(if(large)104f else 76f)).apply {marginEnd=ui.px(12f)})
                val image=AsyncImageView(this);image.allowHardwareBitmaps=false;image.scaleType=ImageView.ScaleType.CENTER_CROP
                ui.at(card,image,8f,8f,48f,60f);image.load(url=repo.personImage(person),placeholder=androidx.core.content.ContextCompat.getDrawable(this,org.jellyfin.androidtv.R.drawable.ic_user))
                ui.text(card,person.name.orEmpty(),15,TvUi.INK,61f,13f,99f,if(large)38f else 25f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
                ui.text(card,person.role ?: if(person.type==PersonKind.DIRECTOR)"导演" else "演员",12,TvUi.MUTED,61f,if(large)58f else 44f,99f,if(large)30f else 20f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
            }
        }
        if(large)scrollContent(690f)
        focus(first)
    }
    /** A scrollable logical canvas keeps large text and system IME content reachable. */
    private fun scrollContent(height: Float): ScrollView {
        val content=FrameLayout(this);val children=(0 until page.childCount).map {page.getChildAt(it)}
        children.forEach {view->val params=view.layoutParams;page.removeView(view);content.addView(view,params)}
        val scroll=ScrollView(this).apply {isFillViewport=false;isVerticalScrollBarEnabled=false;clipToPadding=true;setPadding(0,0,0,ui.px(27f));addView(content,ViewGroup.LayoutParams(ui.px(960f),ui.px(height)))}
        page.addView(scroll,FrameLayout.LayoutParams(-1,-1));return scroll
    }
    private fun series(item: BaseItemDto,push: Boolean=false,selected: UUID?=null) {
        fresh("series",push);header("剧集详情","正在加载季和剧集…")
        load({
            val seasons=repo.seasons(item.id)
            val season=selected ?: seasons.firstOrNull()?.id
            Triple(seasons,repo.episodes(item.id,season),try {repo.nextEpisode(item.id)} catch (_: Exception) {null})
        }) { (seasons,episodes,next) ->
            seriesEpisodes=episodes;fresh("series",false);header("剧集详情")
            label(item.name ?: "未命名剧集",48f,102f,560f,44f,29).setTypeface(null,Typeface.BOLD)
            label("${meta(item)} · ${episodes.size} 集",48f,148f,560f,26f,14,true)
            label(item.overview?.replace(Regex("<[^>]+>"),"") ?: "暂无简介",48f,179f,540f,48f,14).maxLines=2
            art(page,item,609f,91f,303f,160f,true)
            val toPlay=next ?: episodes.firstOrNull {it.userData?.played!=true} ?: episodes.firstOrNull()
            val first=ui.button(page,if(toPlay==null) "暂无可播放剧集" else "▷ 继续播放 第 ${toPlay.indexNumber ?: 1} 集",48f,225f,165f,38f) {toPlay?.let {play(it,resumeMs(it))}}
            first.isEnabled=toPlay!=null
            val chosen=seasons.firstOrNull {it.id==selected} ?: seasons.firstOrNull()
            ui.button(page,"${chosen?.name ?: "全部剧集"} ⌄",48f,294f,130f,38f) {
                if(seasons.isNotEmpty())choose("选择季",seasons.map {it.name ?: "未命名季"},seasons.indexOf(chosen)) {series(item,false,seasons[it].id)}
            }
            val scroll=HorizontalScrollView(this);scroll.isHorizontalScrollBarEnabled=false;val row=LinearLayout(this);scroll.addView(row);ui.at(page,scroll,50f,350f,862f,113f)
            episodes.forEach { episode ->
                val status=if(episode.userData?.played==true) "已观看" else if(resumeMs(episode)>0) "继续 ${time(resumeMs(episode))}" else episode.name.orEmpty()
                val card=focusCard(FrameLayout(this)){play(episode,resumeMs(episode))};art(card,episode,0f,0f,200f,113f,true)
                ui.at(card,View(this).apply {setBackgroundColor(0xff10182b.toInt())},0f,63f,200f,50f)
                ui.text(card,"第 ${episode.indexNumber ?: "?"} 集",15,TvUi.INK,10f,70f,180f,24f).setTypeface(null,Typeface.BOLD)
                ui.text(card,status,12,TvUi.MUTED,10f,94f,180f,18f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
                row.addView(card,LinearLayout.LayoutParams(ui.px(200f),ui.px(113f)).apply {marginEnd=ui.px(14f)})
            }
            if(episodes.isEmpty())label("本季暂无可播放剧集",270f,297f,500f,30f,14,true)
            if(first.isEnabled)focus(first)
        }
    }
    private fun search(initial: String="",push: Boolean=true,parent: UUID?=null,kind: BaseItemKind?=null) {
        mediaSearchScope=MediaSearchScope(repo.sessionKey,parent,kind)
        fresh("search",push);header("搜索媒体","${repo.serverName} · ${when(kind){BaseItemKind.MOVIE->"电影";BaseItemKind.SERIES->"剧集";else->"电影和剧集"}}")
        val input=ui.input(page,"输入片名、剧集名",initial,50f,100f,350f,false);searchInput=input
        val body=LinearLayout(this);body.orientation=LinearLayout.VERTICAL;searchResults=body
        val sc=ScrollView(this);sc.addView(body);ui.at(page,sc,415f,105f,497f,354f)
        val chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        chars.forEachIndexed {index,c ->ui.button(page,c.toString(),50f+(index%6)*58f,159f+(index/6)*39f,51f,32f) {input.append(c.toString());runSearch(input.text.toString())}}
        ui.button(page,"中文／拼音",50f,405f,107f,39f) {input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}
        ui.button(page,"退格",171f,405f,107f,39f) {if(input.length()>0)input.text.delete(input.length()-1,input.length());runSearch(input.text.toString())}
        ui.button(page,"清除",292f,405f,107f,39f) {input.setText("");runSearch("")}
        input.setOnEditorActionListener {_,_,_ ->(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);runSearch(input.text.toString());true}
        input.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {runSearch(s.toString())}
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        runSearch(initial);focus(input)
    }
    private fun runSearch(query: String) {
        searchJob?.cancel();val serial=++mediaSearchSerial;val stamp=generation;val scope=mediaSearchScope ?: return;val resultBody=searchResults ?: return
        fun isCurrent()=stamp==generation&&serial==mediaSearchSerial&&scope==mediaSearchScope&&scope.session==repo.sessionKey
        resultBody.removeAllViews()
        resultBody.addView(ui.text(if(query.isBlank()) "输入关键词搜索你的媒体" else "正在搜索…",16,TvUi.MUTED))
        if(query.isBlank())return
        searchJob=lifecycleScope.launch {
            delay(300)
            try {
                if(scope.session!=repo.sessionKey){resultBody.removeAllViews();resultBody.addView(ui.text("媒体库已切换，请重新打开搜索。",14,TvUi.MUTED));return@launch}
                val result=withTimeout(20000) {withContext(Dispatchers.IO){LibraryBrowse.search(repo.catalog(scope.parent),query,scope.kind)}}
                if(!isCurrent())return@launch
                resultBody.removeAllViews();section(resultBody,"媒体 · ${result.size}")
                if(result.isEmpty())resultBody.addView(ui.text("没有找到相关媒体，试试其他关键词。",14,TvUi.MUTED))
                if(result.size>60)resultBody.addView(ui.text("结果较多，先显示前 60 项；可补充关键词缩小范围。",12,TvUi.MUTED))
                result.take(60).forEach {item -> resultBody.addView(tile(item.name ?: "未命名媒体",meta(item),490,112,item) {detail(item)},LinearLayout.LayoutParams(-1,ui.px(112f)).apply {bottomMargin=ui.px(12f)})}
            } catch(e: CancellationException) {if(e is TimeoutCancellationException && isCurrent()){resultBody.removeAllViews();resultBody.addView(ui.text("搜索超时，关键词已保留。",14,TvUi.MUTED))}}
            catch(_: Exception) {if(isCurrent()){resultBody.removeAllViews();resultBody.addView(ui.text("暂时无法搜索，请重试或检查连接。",14,TvUi.MUTED))}}
        }
    }

    private fun play(item: BaseItemDto,position: Long,keepOptions: Boolean=false) {
        if(!keepOptions){mediaSpeed=1f;val display=getSharedPreferences("bestv_live",MODE_PRIVATE);audioLanguage=display.getString("preferred_audio","auto")?.takeUnless {it=="auto"};subtitleLanguage=display.getString("preferred_subtitle","off")?.takeUnless {it=="off"};subtitleOff=subtitleLanguage==null}
        nextCancelled=false;manualEnd=false;handler.removeCallbacks(nextTick)
        fresh("player");playbackFailure=false;retryPosition=position;overlay=MediaOverlay();consumedPlayerConfirm=false;playingItem=item;playingSessionKey=repo.sessionKey;playingPositionPrefix=currentPositionPrefix();stream=null;reportStarted=false;lastPersist=0
        val video=PlayerView(this);video.useController=false;video.isFocusable=false;videoSurface=video;ui.at(page,video,0f,0f,960f,540f)
        val loading=label("正在获取播放地址…",32f,80f,850f,48f,18)
        makeControls(item)
        buffering=FrameLayout(this).also {panel->
            val titleHeight=maxOf(28f,28f*ui.fontScale);val hintHeight=maxOf(22f,19f*ui.fontScale)
            val panelHeight=20f+titleHeight+hintHeight
            panel.visibility=View.GONE;panel.background=ui.surface(0xf0101629.toInt(),false);ui.at(page,panel,332f,242f-panelHeight/2,296f,panelHeight)
            ui.text(panel,"正在缓冲…",18,TvUi.INK,16f,8f,264f,titleHeight).gravity=Gravity.CENTER
            ui.text(panel,"播放恢复后会自动继续",12,TvUi.MUTED,16f,12f+titleHeight,264f,hintHeight).gravity=Gravity.CENTER
        }
        page.isFocusableInTouchMode=true;page.requestFocus()
        video.setOnClickListener {if(overlay.visible)hidePlayerControls()else showControls()}
        val stamp=generation
        if(item.type==BaseItemKind.EPISODE&&item.seriesId!=null)lifecycleScope.launch {
            try {val episodes=repo.episodes(item.seriesId!!);if(stamp==generation){seriesEpisodes=episodes;if(player?.playbackState==Player.STATE_ENDED)showAutoNext(item)}}catch(_:CancellationException){}catch(_:Exception){}
        }
        lifecycleScope.launch {
            try {
                val profile=withContext(Dispatchers.IO) {createDeviceProfile(this@JellyLibraryActivity,preferences,repo.servers.currentServer.value?.serverVersion ?: ServerVersion(10,10,0))}
                if(stamp!=generation)return@launch
                val opts=VideoOptions().apply {itemId=item.id;this.profile=profile;mediaSources=item.mediaSources;mediaSourceId=item.mediaSources?.firstOrNull()?.id}
                playback.getVideoStreamInfo(this@JellyLibraryActivity,opts,position*10000,object: Response<StreamInfo>(lifecycle) {
                    override fun onResponse(response: StreamInfo) {
                        if(stamp!=generation)return
                        val url=response.mediaUrl
                        if(url.isNullOrBlank()) {loading.text="服务器未提供可播放的媒体流，请重试。";overlay.blocked=true;loading.visibility=View.GONE;showPlaybackRetry(item);return}
                        stream=response
                        val render=DefaultRenderersFactory(this@JellyLibraryActivity).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        val exo=ExoPlayer.Builder(this@JellyLibraryActivity,render).setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(LibraryMediaHttp.client(repo.api,mediaHttpFactory)))).build()
                        player=exo;video.player=exo
                        val display=getSharedPreferences("bestv_live",MODE_PRIVATE)
                        val aspect=display.getString("video_aspect",if(display.getBoolean("fill_video",false))"zoom" else "fit");video.resizeMode=when(aspect){"zoom"->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;"stretch"->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;else->androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT}
                        exo.setPlaybackSpeed(mediaSpeed)
                        exo.trackSelectionParameters=exo.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(audioLanguage).setPreferredTextLanguage(subtitleLanguage).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitleOff).build()
                        exo.addListener(object: Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {cancelAutoNext();overlay.blocked=true;loading.visibility=View.VISIBLE;loading.text="播放失败，请重试或返回检查网络与转码设置。";loading.visibility=View.GONE;showPlaybackRetry(item)}
                            override fun onTracksChanged(tracks: Tracks){updateTrackLabels()}
                            override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int){overlay.paused=!playWhenReady;armPlayerIdle()}
                            override fun onTimelineChanged(timeline: Timeline,reason: Int){updateSeekAvailability()}
                            override fun onPlaybackStateChanged(state: Int) {
                                if(state!=Player.STATE_BUFFERING){handler.removeCallbacks(showBuffering);buffering?.visibility=View.GONE}
                                if(state==Player.STATE_BUFFERING&&!playbackFailure){handler.removeCallbacks(showBuffering);handler.postDelayed(showBuffering,800)}
                                if(state==Player.STATE_READY){loading.visibility=View.GONE;if(!reportStarted&&playingSessionKey==repo.sessionKey){reportStarted=true;reporting.reportStart(this@JellyLibraryActivity,null,item,response,exo.currentPosition*10000,!exo.playWhenReady);lastReport=System.currentTimeMillis()}}
                                if(state==Player.STATE_ENDED){overlay.blocked=true;showControls();if(!manualEnd){lifecycleScope.launch {try {repo.setPlayed(item.id,true)}catch(_:Exception){}};showAutoNext(item)}}
                            }
                        })
                        val subtitles=response.mediaSource?.mediaStreams.orEmpty().filter { it.type==MediaStreamType.SUBTITLE && it.isExternal==true && !it.deliveryUrl.isNullOrBlank() }.mapNotNull { media ->
                            val mime=when(media.codec?.lowercase()) {"srt","subrip"->MimeTypes.APPLICATION_SUBRIP;"vtt","webvtt"->MimeTypes.TEXT_VTT;"ass","ssa"->MimeTypes.TEXT_SSA;"ttml"->MimeTypes.APPLICATION_TTML;else->null} ?: return@mapNotNull null
                            val subtitleUrl=repo.api.createUrl(media.deliveryUrl!!,ignorePathParameters=true)
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl)).setMimeType(mime).setLanguage(media.language).setLabel(media.displayTitle ?: media.language ?: "字幕").build()
                        }
                        exo.setMediaItem(MediaItem.Builder().setUri(url).setSubtitleConfigurations(subtitles).build());exo.seekTo(position);exo.prepare();exo.playWhenReady=true
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        handler.post(updatePlayer)
                    }
                    override fun onError(exception: Exception) {if(stamp==generation){loading.text="无法获取播放地址，请重试或返回。";overlay.blocked=true;loading.visibility=View.GONE;showPlaybackRetry(item)}}
                })
            } catch(e: CancellationException){throw e} catch(_: Exception){if(stamp==generation){loading.text="播放器初始化失败，请重试或返回。";overlay.blocked=true;loading.visibility=View.GONE;showPlaybackRetry(item)}}
        }
    }
    private fun showPlaybackRetry(item: BaseItemDto){
        if(playbackFailure)return
        retryPosition=player?.currentPosition ?: retryPosition
        playbackFailure=true;player?.pause();handler.removeCallbacks(hideControls);handler.removeCallbacks(showBuffering);handler.removeCallbacks(updatePlayer)
        buffering?.visibility=View.GONE;controls?.visibility=View.GONE;playerHeading?.visibility=View.GONE;seekPreview?.visibility=View.GONE
        val panel=FrameLayout(this);panel.setBackgroundColor(0xf0101629.toInt());ui.at(page,panel,0f,0f,960f,540f)
        ui.text(panel,"△",48,0xffb7a2ff.toInt(),456f,175f,48f,48f).gravity=Gravity.CENTER
        ui.heading(panel,"暂时无法播放",23,48f,246f,864f,40f).gravity=Gravity.CENTER
        ui.text(panel,"请检查网络或服务器转码设置，然后重试。",14,TvUi.MUTED,48f,285f,864f,28f).gravity=Gravity.CENTER
        val retry=ui.button(panel,"重试播放",313f,319f,160f,42f){replacePlayback(item,retryPosition)}
        val origin=history.lastOrNull()?.name
        val leave=ui.button(panel,if(origin in listOf("movie","series","detail"))"返回详情" else "返回媒体库",487f,319f,160f,42f){stopPlayer();backToSaved()}
        listOf(retry,leave).forEach {it.nextFocusUpId=it.id;it.nextFocusDownId=it.id}
        retry.nextFocusLeftId=retry.id;retry.nextFocusRightId=leave.id;leave.nextFocusLeftId=retry.id;leave.nextFocusRightId=leave.id
        retry.requestFocus()
    }
    private fun makeControls(item: BaseItemDto) {
        val panel=FrameLayout(this);panel.visibility=View.GONE;panel.background=ui.glass(TvUi.PANEL);controls=panel
        ui.at(page,panel,PlaybackLayout.BAR_X.toFloat(),PlaybackLayout.BAR_Y.toFloat(),PlaybackLayout.BAR_WIDTH.toFloat(),PlaybackLayout.BAR_HEIGHT.toFloat())
        ui.protectControls(panel)
        playerHeading=FrameLayout(this).also {top->
            top.visibility=View.GONE;top.background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0xf0101326.toInt(),0xeb101326.toInt(),0x00101326));ui.at(page,top,0f,0f,960f,135f)
            ui.title(top,item.seriesName ?: item.name ?: "媒体播放",48f,28f,700f,if(ui.largeText())48f else 33f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
            playerWallClock=ui.text(top,android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis()).toString(),14,TvUi.MUTED,if(ui.largeText())792f else 848f,28f,if(ui.largeText())120f else 64f,33f).apply {gravity=Gravity.CENTER_VERTICAL or Gravity.END}
            if(item.type==BaseItemKind.EPISODE)ui.text(top,"第 ${item.parentIndexNumber ?: "?"} 季 · 第 ${item.indexNumber ?: "?"} 集  ${item.name.orEmpty()}",13,TvUi.MUTED,48f,if(ui.largeText())80f else 63f,800f,if(ui.largeText())30f else 25f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        }
        ui.at(panel,View(this).apply {setBackgroundColor(0xff4b5268.toInt())},16f,15f,864f,3f)
        val progress=SeekBar(this);seek=progress;progress.id=View.generateViewId();progress.max=10000;progress.visibility=View.GONE;progress.setPadding(0,0,0,0)
        ui.at(panel,progress,16f,4.5f,864f,24f)
        progress.progressTintList=android.content.res.ColorStateList.valueOf(0xffb9a6ff.toInt());progress.progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0xff4b5268.toInt());progress.thumbTintList=android.content.res.ColorStateList.valueOf(TvUi.INK)
        progress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean){if(fromUser&&canSeek()){beginSeek();player?.let {overlay.preview(it.duration*value/10000,it.duration)};updateSeekClock()}}
            override fun onStartTrackingTouch(bar: SeekBar?){beginSeek()}
            override fun onStopTrackingTouch(bar: SeekBar?){commitSeek()}
        })
        progress.setOnTouchListener {_,event->if(event.action==MotionEvent.ACTION_CANCEL)cancelSeek();false}
        pause=ui.quietButton("Ⅱ","暂停或继续播放"){togglePlayback()};ui.at(panel,pause!!,16f,32f,32f,30f)
        val rewind=ui.quietButton("≪","快退"){lastPlayerAction=currentFocus;stepSeek(false,false)};rewindAction=rewind;ui.at(panel,rewind,48f,32f,32f,30f)
        val forward=ui.quietButton("≫","快进"){lastPlayerAction=currentFocus;stepSeek(true,false)};forwardAction=forward;ui.at(panel,forward,80f,32f,32f,30f)
        clock=ui.text(panel,"00:00:00 / --:--:--",13,TvUi.MUTED,124f,32f,310f,30f)
        speedAction=ui.quietButton(speedLabel(),"倍速，${speedLabel()}"){speeds()};ui.at(panel,speedAction!!,PlaybackLayout.actionX(0).toFloat(),32f,96f,30f)
        audioAction=ui.quietButton("音轨","音轨"){tracks(C.TRACK_TYPE_AUDIO)};ui.at(panel,audioAction!!,PlaybackLayout.actionX(1).toFloat(),32f,96f,30f)
        subtitleAction=ui.quietButton("字幕","字幕"){tracks(C.TRACK_TYPE_TEXT)};ui.at(panel,subtitleAction!!,PlaybackLayout.actionX(2).toFloat(),32f,96f,30f)
        listOf(pause!!,rewind,forward).forEach {it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16f*ui.scale*ui.fontScale)}
        val actions=listOf(pause!!,rewind,forward,speedAction!!,audioAction!!,subtitleAction!!)
        actions.forEachIndexed {index,button->button.nextFocusLeftId=actions.getOrNull(index-1)?.id ?: button.id;button.nextFocusRightId=actions.getOrNull(index+1)?.id ?: button.id;button.nextFocusUpId=progress.id;button.nextFocusDownId=button.id;button.setOnFocusChangeListener {_,focused->if(focused){lastPlayerAction=button;armPlayerIdle()}}}
        updateSeekAvailability()
        lastPlayerAction=pause;progress.nextFocusDownId=pause!!.id;progress.nextFocusUpId=progress.id
        seekPreview=ui.text(page,"",13,TvUi.INK,425f,411f,110f,22f).apply {gravity=Gravity.CENTER;background=ui.glass(TvUi.PANEL);visibility=View.GONE}
    }
    private fun nextEpisodeItem(item: BaseItemDto)=EpisodeSequence.next(item,seriesEpisodes)
    private fun showAutoNext(item: BaseItemDto){
        if(nextCancelled||nextCard!=null||manualEnd||!getSharedPreferences("bestv_live",MODE_PRIVATE).getBoolean("auto_next_episode",true))return
        val next=nextEpisodeItem(item) ?: return
        val card=FrameLayout(this);card.background=ui.glass(TvUi.PANEL);nextCard=card;ui.at(page,card,592f,312f,320f,114f)
        ui.at(card,View(this).apply {setBackgroundColor(OverlayContrast.OPTION_GUARD)},8f,33f,304f,77f)
        ui.heading(card,"下一集 · 第 ${next.parentIndexNumber ?: "?"} 季 第 ${next.indexNumber ?: "?"} 集",15,16f,8f,288f,25f)
        ui.text(card,next.name.orEmpty(),13,TvUi.MUTED,16f,34f,160f,26f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        nextClock=ui.text(card,"00:00:05 后播放",12,TvUi.MUTED,185f,34f,119f,26f)
        val now=ui.quietButton("立即播放","立即播放下一集"){replacePlayback(next,0)};ui.at(card,now,16f,65f,136f,30f)
        ui.at(card,ui.quietButton("取消","取消自动播放下一集"){cancelAutoNext()},168f,65f,136f,30f)
        TvGlass.apply(this,ui,card,592f,312f,320f,114f);nextSeconds=5;handler.post(nextTick)
        if(dialog?.isShowing!=true)now.requestFocus()
    }
    private fun cancelAutoNext(){nextCancelled=true;handler.removeCallbacks(nextTick);nextCard?.let {val focused=it.hasFocus();TvUi.detach(it);if(focused)pause?.requestFocus()};nextCard=null;nextClock=null}
    private fun replacePlayback(item: BaseItemDto,position: Long){stopPlayer();backToSaved();play(item,position,true)}
    private fun updateSeekAvailability(){
        val available=canSeek();seek?.apply {visibility=if(available)View.VISIBLE else View.GONE;isEnabled=available;isFocusable=available}
        val actions=listOfNotNull(pause,rewindAction,forwardAction,speedAction,audioAction,subtitleAction)
        listOfNotNull(rewindAction,forwardAction).forEach {button->if(!available&&button.hasFocus())pause?.requestFocus();button.isEnabled=available;button.isFocusable=available;button.alpha=if(available)1f else .35f}
        val active=actions.filter {it.isEnabled}
        active.forEachIndexed {index,button->button.nextFocusLeftId=active.getOrNull(index-1)?.id ?: button.id;button.nextFocusRightId=active.getOrNull(index+1)?.id ?: button.id;button.nextFocusUpId=if(available)seek!!.id else button.id}
        if(!available&&overlay.seeking){cancelSeek();pause?.requestFocus()}
        if(!available&&lastPlayerAction?.isEnabled==false)lastPlayerAction=pause
    }
    private fun canSeek()=player?.let {it.duration>0&&it.isCurrentMediaItemSeekable}==true
    private fun beginSeek(){
        val p=player ?: return;if(!canSeek())return
        overlay.begin(p.currentPosition,p.playWhenReady);p.pause();applyControlsVisibility();armPlayerIdle()
    }
    private fun stepSeek(forward: Boolean,longPress: Boolean){
        val p=player ?: return;if(!canSeek()){showControls();return}
        beginSeek();overlay.preview((overlay.target ?: p.currentPosition)+(if(forward)1 else -1)*(if(longPress)30_000L else 10_000L),p.duration)
        seek?.requestFocus();updateSeekClock()
    }
    private fun updateSeekClock(){val p=player ?: return;val target=overlay.target ?: return;seek?.progress=if(p.duration>0)(target*10000/p.duration).toInt()else 0;clock?.text="${time(target)} / ${time(p.duration)}";seekPreview?.apply {text=time(target);visibility=View.VISIBLE;layoutParams=(layoutParams as FrameLayout.LayoutParams).apply {leftMargin=ui.px((48f+864f*target/p.duration-55f).coerceIn(32f,818f))}}}
    private fun commitSeek(){val result=overlay.commit() ?: return;player?.let {manualEnd=result.position>=it.duration-500;it.seekTo(result.position);it.playWhenReady=result.resume};overlay.paused=!result.resume;seekPreview?.visibility=View.GONE;showControls(false);seek?.requestFocus()}
    private fun cancelSeek(){val resume=overlay.cancel() ?: return;player?.playWhenReady=resume;overlay.paused=!resume;seekPreview?.visibility=View.GONE;showControls(false);seek?.requestFocus()}
    private fun playRequested(){player?.let {if(it.playbackState==Player.STATE_ENDED){cancelAutoNext();manualEnd=false;it.seekTo(0);overlay.blocked=false};it.play()}}
    private fun togglePlayback(){player?.let {p->
        if(p.playbackState==Player.STATE_ENDED)playRequested()else if(p.playWhenReady)p.pause()else p.play()
        overlay.paused=!p.playWhenReady;showControls()
    }}
    private fun armPlayerIdle(){
        handler.removeCallbacks(hideControls);overlay.idleMillis=ui.timeout(5000).toLong();overlay.interact(android.os.SystemClock.uptimeMillis())
        overlay.remaining(android.os.SystemClock.uptimeMillis())?.let {handler.postDelayed(hideControls,it)}
    }
    private fun applyControlsVisibility(){
        val appearing=overlay.visible&&controls?.visibility!=View.VISIBLE
        controls?.visibility=if(overlay.visible)View.VISIBLE else View.GONE
        if(appearing)controls?.let {panel->panel.post {if(panel.isShown)TvGlass.apply(this,ui,panel,32f,436f,896f,72f)}}
        playerHeading?.visibility=if(overlay.visible)View.VISIBLE else View.GONE
        pause?.let {TvIcons.text(it,if(player?.playWhenReady==true&&player?.playbackState!=Player.STATE_ENDED)"Ⅱ" else "▷")}
        videoSurface?.subtitleView?.setBottomPaddingFraction(if(overlay.visible)0.18f else 0.08f)
        if(!overlay.visible)page.requestFocus()
    }
    private fun hidePlayerControls(){overlay.hide();handler.removeCallbacks(hideControls);applyControlsVisibility()}
    private fun optionPopup(title: String,names: List<String>,details: List<String>,selected: Int,chosen: (Int)->Unit){
        val anchor=currentFocus;handler.removeCallbacks(hideControls);overlay.dialog=true;applyControlsVisibility()
        val popup=TvOptions.show(this,ui,anchor,title,names.toTypedArray(),details.toTypedArray(),selected,false,if(title=="倍速")168f else 140f,260f,if(title=="倍速")30f else 29f){chosen(it)}
        dialog=popup;popup.setOnDismissListener {if(dialog===popup){dialog=null;overlay.dialog=false;applyControlsVisibility();armPlayerIdle();anchor?.takeIf {it.isAttachedToWindow}?.requestFocus()}}
    }
    private fun speedLabel()=java.math.BigDecimal(mediaSpeed.toString()).stripTrailingZeros().toPlainString()+"x"
    private fun speeds(){val speeds=listOf(.5f,.75f,1f,1.25f,1.5f,1.75f,2f);optionPopup("倍速",speeds.map {java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString()+"x"},emptyList(),speeds.indexOf(mediaSpeed)){index->player?.let {p->p.setPlaybackSpeed(speeds[index]);mediaSpeed=p.playbackParameters.speed;ui.value(speedAction!!,"倍速",speedLabel());speedAction?.announceForAccessibility(speedAction?.contentDescription)}}}
    private fun updateTrackLabels(){player?.let {p->audioAction?.let {ui.value(it,"音轨",MediaTracks.value(p,C.TRACK_TYPE_AUDIO))};subtitleAction?.let {ui.value(it,"字幕",MediaTracks.value(p,C.TRACK_TYPE_TEXT))}}}
    private fun tracks(type: Int) {
        val p=player ?: return;val choices=MediaTracks.choices(p,type);val text=type==C.TRACK_TYPE_TEXT
        if(choices.isEmpty()){Toast.makeText(this,"没有可切换的${if(text)"字幕" else "音轨"}",Toast.LENGTH_SHORT).show();return}
        val offset=if(text)1 else 0
        val names=(if(text)listOf("关闭") else emptyList())+choices.map {it.name}
        val details=(if(text)listOf("不显示字幕") else emptyList())+choices.map {it.detail}
        val disabled=p.trackSelectionParameters.disabledTrackTypes.contains(type)
        val selected=if(text&&disabled)0 else choices.indexOfFirst {it.selected()}+offset
        optionPopup(if(text)"字幕" else "音轨",names,details,selected){index->
            val choice=choices.getOrNull(index-offset);if(!MediaTracks.select(p,type,choice)){Toast.makeText(this,"轨道已变化，请重新选择",Toast.LENGTH_SHORT).show();return@optionPopup}
            if(text){subtitleOff=choice==null;subtitleLanguage=choice?.language}else audioLanguage=choice?.language
            updateTrackLabels();(if(text)subtitleAction else audioAction)?.let {it.announceForAccessibility(it.contentDescription)}
        }
    }
    private fun showControls(focusButton: Boolean=true) {if(playbackFailure)return;overlay.show(android.os.SystemClock.uptimeMillis());applyControlsVisibility();if(focusButton&&!overlay.seeking)pause?.requestFocus();armPlayerIdle()}
    private fun stopPlayer() {
        handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls);handler.removeCallbacks(showBuffering);buffering=null;handler.removeCallbacks(nextTick);nextCard?.let {TvUi.detach(it)};nextCard=null;nextClock=null
        val p=player
        if(p!=null){playingItem?.let {item ->rememberPosition(item,p.currentPosition);if(reportStarted&&playingSessionKey==repo.sessionKey)stream?.let {reporting.reportProgress(this,null,item,it,p.currentPosition*10000,true);reporting.reportStopped(this,item,it,p.currentPosition*10000)}};videoSurface?.player=null;p.release()}
        player=null;stream=null;playingItem=null;controls=null;rewindAction=null;forwardAction=null;playbackFailure=false;seek=null;clock=null;pause=null;playerHeading=null;playerWallClock=null;speedAction=null;audioAction=null;subtitleAction=null;seekPreview=null;lastPlayerAction=null;overlay=MediaOverlay();reportStarted=false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun choose(title: String,labels: List<String>,selected: Int,chosen: (Int)->Unit) {
        val previous=currentFocus
        handler.removeCallbacks(hideControls);if(pageName=="player"){overlay.dialog=true;applyControlsVisibility()}
        val d=AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected){dlg,index ->dlg.dismiss();chosen(index)}.setNegativeButton("取消",null).create()
        dialog=d;d.setOnDismissListener {if(dialog===d){dialog=null;if(pageName=="player"){overlay.dialog=false;applyControlsVisibility();armPlayerIdle()};previous?.takeIf {it.isAttachedToWindow&&it.visibility==View.VISIBLE}?.requestFocus()}};d.show()
        d.listView?.setSelector(ui.focusBackground(TvUi.TILE));d.window?.setBackgroundDrawable(ui.surface(TvUi.PANEL,false))
    }
    private fun backToSaved() {
        task?.cancel();searchJob?.cancel();gridRenderJob?.cancel();generation++
        val saved=history.removeLastOrNull()
        if(saved==null){finish();return}
        if(saved.session!=repo.sessionKey){history.clear();home(false);return}
        pageSessionKey=saved.session;page=saved.view;pageName=saved.name;setContentView(page);page.findViewById<View>(saved.focus)?.requestFocus();val restored=page;restored.post {if(page===restored)(restored.tag as? Runnable)?.run()}
    }
    private fun back() {
        if(dialog!=null){dialog?.dismiss();return}
        if(pageName=="player") {if(playbackFailure){stopPlayer();backToSaved();return};if(nextCard!=null){cancelAutoNext();return};if(overlay.seeking){cancelSeek();return};if(overlay.visible){hidePlayerControls();return};stopPlayer();backToSaved();return}
        if(history.isEmpty())finish()else backToSaved()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {if(event.actionMasked==MotionEvent.ACTION_DOWN)interaction++;return super.dispatchTouchEvent(event)}
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.action==KeyEvent.ACTION_DOWN)interaction++
        if(pageName=="player"&&dialog==null&&!playbackFailure){
            val key=event.keyCode
            val confirm=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER
            if(confirm&&event.action==KeyEvent.ACTION_UP&&consumedPlayerConfirm){consumedPlayerConfirm=false;return true}
            if(event.action==KeyEvent.ACTION_DOWN){
                armPlayerIdle()
                if(confirm&&(overlay.seeking||!overlay.visible||currentFocus===pause)){
                    consumedPlayerConfirm=true;if(event.repeatCount==0){if(overlay.seeking)commitSeek()else togglePlayback()};return true
                }
                when(key){
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY->{if(event.repeatCount==0){when(key){KeyEvent.KEYCODE_MEDIA_PLAY->playRequested();KeyEvent.KEYCODE_MEDIA_PAUSE->player?.pause();else->togglePlayback()};overlay.paused=player?.playWhenReady!=true;showControls()};return true}
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_REWIND->{stepSeek(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,event.repeatCount>0);return true}
                    KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT->if(!overlay.visible){showControls();return true}else if(currentFocus===seek){stepSeek(key==KeyEvent.KEYCODE_DPAD_RIGHT,event.repeatCount>0);return true}
                    KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN->{
                        if(!overlay.visible){showControls();return true}
                        if(currentFocus===seek){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(overlay.seeking)cancelSeek();lastPlayerAction?.requestFocus()};return true}
                        if(currentFocus===lastPlayerAction&&key==KeyEvent.KEYCODE_DPAD_UP&&!canSeek()){pause?.requestFocus();return true}
                        if(key==KeyEvent.KEYCODE_DPAD_UP&&controls?.hasFocus()==true){if(canSeek())seek?.requestFocus();return true}
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart(){
        super.onStart()
        if(player!=null&&playingSessionKey!=repo.sessionKey)stopPlayer()
        if(pageName !in listOf("loading","connection")&&pageSessionKey!=repo.sessionKey){history.clear();home(false)}
        if(pageName=="home")(page.tag as? Runnable)?.run()
        if(player!=null){handler.removeCallbacks(updatePlayer);handler.post(updatePlayer)}
    }
    override fun onStop(){
        overlay.cancel();overlay.paused=true;player?.pause();handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls);cancelAutoNext()
        val p=player;val item=playingItem;val info=stream
        if(p!=null && item!=null){rememberPosition(item,p.currentPosition);if(info!=null && reportStarted && playingSessionKey==repo.sessionKey)reporting.reportProgress(this,null,item,info,p.currentPosition*10000,true)}
        super.onStop()
    }
    override fun onDestroy(){repo.close();stopPlayer();task?.cancel();searchJob?.cancel();homeResumeJob?.cancel();gridRenderJob?.cancel();dialog?.dismiss();super.onDestroy()}
    private fun rememberPosition(item: BaseItemDto,position: Long){if(!reportStarted||position<=0)return;local.edit().putLong(positionKey(item),position).putLong("seen_${positionKey(item)}",System.currentTimeMillis()).apply()}
    private fun currentPositionPrefix()="pos_${repo.sessions.currentSession.value?.serverId}_${repo.sessions.currentSession.value?.userId}_"
    private fun positionKey(item: BaseItemDto)=(if(playingItem?.id==item.id&&playingPositionPrefix.isNotEmpty())playingPositionPrefix else currentPositionPrefix())+item.id
    private fun resumeMs(item: BaseItemDto): Long {
        val server=(item.userData?.playbackPositionTicks ?: 0)/10000
        val seen=local.getLong("seen_${positionKey(item)}",0)
        return LibraryResume.position(server,item.userData?.lastPlayedDate?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli() ?: 0,local.getLong(positionKey(item),0),seen)
    }
    private fun meta(item: BaseItemDto): String = listOfNotNull(LibraryMetadata.year(item)?.toString(),LibraryMetadata.rating(item)?.let {"评分 $it"},LibraryBrowse.resolution(item)?.let {if(it==2160)"4K" else "${it}p"},item.genres?.take(2)?.joinToString(" / ")?.takeIf {it.isNotEmpty()},item.runTimeTicks?.let {time(it/10000)}).joinToString(" · ")
    private fun time(ms: Long): String = PlaybackLayout.time(ms)
}
