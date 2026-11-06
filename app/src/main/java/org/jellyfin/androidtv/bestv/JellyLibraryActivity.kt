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
    private var playerBack: Button? = null
    private var episodeAction: Button? = null
    private var nextAction: Button? = null
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
            seek?.visibility=if(canSeek())View.VISIBLE else View.GONE
            seek?.progress=if(duration>0)(shownPosition*10000/duration).toInt() else 0
            clock?.text="${time(shownPosition)} / ${if(duration>0)time(duration) else "--:--"}"
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
        val expected = generation
        task = lifecycleScope.launch {
            try { val result = withTimeout(25000) { block() }; if (expected == generation) success(result) }
            catch (error: CancellationException) { if (error is TimeoutCancellationException && expected == generation) offline(false) }
            catch (_: Exception) { if (expected == generation) offline(false) }
        }
    }
    private fun focus(v: View, stamp: Int=interaction) { v.post { if (stamp == interaction && v.isAttachedToWindow) v.requestFocus() } }
    private fun label(text: String, x: Float, y: Float, w: Float, h: Float, size: Int=14, muted: Boolean=false) =
        ui.text(page, text, size, if(muted) TvUi.MUTED else TvUi.INK, x, y, w, h)
    private fun centerState(title: String, subtitle: String, buttons: List<Pair<String, () -> Unit>>) {
        val panel=FrameLayout(this); panel.background=ui.surface(TvUi.PANEL,false); ui.at(page,panel,32f,103f,896f,347f)
        ui.text(panel,"▱",50,0xffb7a2ff.toInt(),410f,20f,100f,75f)
        ui.title(panel,title,104f,120f,760f,50f)
        ui.text(panel,subtitle,14,TvUi.MUTED,150f,174f,720f,45f)
        val width=if(buttons.size==3) 160f else 200f
        val start=(896-(buttons.size*width+(buttons.size-1)*14))/2
        buttons.forEachIndexed { i, (name, click) ->
            val b=ui.button(panel,name,start+i*(width+14),257f,width,40f,click)
            if(i==0) focus(b)
        }
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
        label(if(repo.connected) "已连接 · ${repo.serverName}" else "尚未连接",64f,101f,540f,30f,13)
        label("服务器地址",64f,151f,540f,24f,14,true)
        val preset=bootstrap
        val initialAddress=repo.address.ifEmpty { local.getString("last_address","").orEmpty() }.ifEmpty { preset?.address.orEmpty() }
        val initialUsername=local.getString("last_username","").orEmpty().ifEmpty { if(preset?.matchesAddress(initialAddress)==true) preset.username else "" }
        val address=ui.input(page,"http://服务器地址:8096",initialAddress,64f,181f,540f,false)
        label("用户名",64f,237f,540f,24f,14,true)
        val username=ui.input(page,"输入用户名",initialUsername,64f,267f,540f,false)
        label("密码",64f,322f,540f,24f,14,true)
        val password=ui.input(page,"输入密码",if(preset?.matches(initialAddress,initialUsername)==true) preset.password else "",64f,352f,540f,true)
        val clearForeignPassword=object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(preset!=null && !preset.matches(address.text.toString(),username.text.toString())) password.setText("")
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        address.addTextChangedListener(clearForeignPassword);username.addTextChangedListener(clearForeignPassword)
        label("连接后可以",655f,164f,260f,32f,18)
        label("✓  浏览电影和剧集\n\n✓  同步继续观看进度\n\n✓  使用服务器提供的直播",655f,201f,265f,150f,14,true)
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
    private fun home(push: Boolean=true) {
        if(!repo.connected){disconnected(push);return}
        fresh("home",push);header("我的媒体库","${repo.serverName} · 已连接")
        val host=page;val session=repo.sessionKey;val initialInteraction=interaction
        val search=place(host,ui.button("⌕ 搜索媒体"){search()},LibraryPageLayout.HOME_SEARCH)
        val resumeTitle=pageText(host,"继续观看",17,TvUi.INK,LibraryPageLayout.HOME_RESUME_LABEL).apply {setTypeface(null,Typeface.BOLD)}
        val status=pageText(host,"正在加载继续观看…",14,TvUi.MUTED,LibraryPageLayout.HOME_RESUME_ROW)
        val explore=pageText(host,"探索媒体库",17,TvUi.INK,LibraryPageLayout.HOME_EXPLORE_LABEL).apply {setTypeface(null,Typeface.BOLD)}
        val illustrations=listOf(org.jellyfin.androidtv.R.drawable.library_entry_movies,org.jellyfin.androidtv.R.drawable.library_entry_series,org.jellyfin.androidtv.R.drawable.library_entry_recent)
        val entries=LibraryPageLayout.Entry.entries.mapIndexed {index,entry->
            val card=homeCard(entry.title,"浏览全部内容",illustrations[index]){renderGrid(entry.title,null,entry.query())}
            card.tag="library-entry-${entry.name.lowercase()}";place(host,card,LibraryPageLayout.homeEntry(index))
        }
        entries.forEachIndexed {index,card->card.nextFocusLeftId=entries.getOrNull(index-1)?.id ?: card.id;card.nextFocusRightId=entries.getOrNull(index+1)?.id ?: card.id;card.nextFocusUpId=search.id;card.nextFocusDownId=card.id}
        search.nextFocusDownId=entries.first().id;focus(entries.first(),initialInteraction)
        homeResumeJob?.cancel()
        homeResumeJob=lifecycleScope.launch {
            val resumes=try {withTimeout(8000){repo.resume()}.filter {it.userData?.played!=true&&resumeMs(it)>0}.distinctBy {it.seriesId ?: it.id}}
                catch(error: CancellationException){if(error !is TimeoutCancellationException)throw error;null}
                catch(_:Exception){null}
            if(session!=repo.sessionKey)return@launch
            if(resumes==null){status.text="继续观看暂不可用，仍可浏览下方分类。";return@launch}
            host.removeView(status)
            if(resumes.isEmpty()){
                host.removeView(resumeTitle)
                fun move(view: View,box: LibraryPageLayout.Box){view.layoutParams=(view.layoutParams as FrameLayout.LayoutParams).apply {leftMargin=ui.px(box.x);topMargin=ui.px(box.y)}}
                move(explore,LibraryPageLayout.homeExploreLabel(false));entries.forEachIndexed {i,view->move(view,LibraryPageLayout.homeEntry(i,false))}
            }else{
                val scroller=HorizontalScrollView(this@JellyLibraryActivity);scroller.isHorizontalScrollBarEnabled=false;val row=LinearLayout(this@JellyLibraryActivity);scroller.addView(row)
                val cards=resumes.map {item->homeCard(cardTitle(item),if(item.type==BaseItemKind.EPISODE)"第 ${item.indexNumber ?: "?"} 集 · ${time(resumeMs(item))}" else "继续播放 · ${time(resumeMs(item))}",item=item){play(item,resumeMs(item))}.also {row.addView(it,LinearLayout.LayoutParams(ui.px(287f),ui.px(130f)).apply {marginEnd=ui.px(14f)})}}
                place(host,scroller,LibraryPageLayout.HOME_RESUME_ROW)
                cards.forEachIndexed {i,card->card.nextFocusUpId=search.id;card.nextFocusDownId=entries[i.coerceAtMost(entries.lastIndex)].id}
                entries.forEachIndexed {i,card->card.nextFocusUpId=cards[i.coerceAtMost(cards.lastIndex)].id}
                search.nextFocusDownId=cards.first().id
                if(page===host)focus(cards.first(),initialInteraction)
            }
        }
    }
    private fun homeCard(name: String,subtitle: String,illustration: Int?=null,item: BaseItemDto?=null,click: ()->Unit): FrameLayout {
        val card=focusCard(FrameLayout(this),click)
        if(item!=null)art(card,item,0f,0f,287f,LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT,true)
        else ui.at(card,ImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_CROP;setImageResource(requireNotNull(illustration))},0f,0f,287f,LibraryPageLayout.HOME_CARD_ARTWORK_HEIGHT)
        ui.at(card,View(this).apply {setBackgroundColor(0xff10182a.toInt())},0f,LibraryPageLayout.HOME_CARD_INFO_TOP,287f,50f)
        pageText(card,name,16,TvUi.INK,LibraryPageLayout.HOME_CARD_TITLE).apply {setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        pageText(card,subtitle,12,TvUi.MUTED,LibraryPageLayout.HOME_CARD_SUBTITLE).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        if(item!=null&&(item.runTimeTicks ?: 0)>0){val b=LibraryPageLayout.HOME_CARD_PROGRESS;ui.at(card,View(this).apply {setBackgroundColor(0xff414760.toInt())},b.x,b.y,b.width,b.height);ui.at(card,View(this).apply {setBackgroundColor(0xffac9aff.toInt())},b.x,b.y,b.width*(resumeMs(item)*10000.0/item.runTimeTicks!!).coerceIn(0.0,1.0).toFloat(),b.height)}
        card.contentDescription="$name，$subtitle";return card
    }
    private fun filterCount(query: LibraryBrowse.Query)=listOf(query.state!=LibraryBrowse.State.ALL,query.genre!=null,query.year!=null,query.resolution!=null,query.region!=null).count {it}
    private fun browseDialog(create: ()->AlertDialog){
        val anchor=currentFocus;val popup=create();dialog=popup
        popup.setOnDismissListener {if(dialog===popup)dialog=null;anchor?.takeIf {it.isAttachedToWindow&&it.isShown}?.requestFocus()}
    }
    /** Gallery 16: one category, one toolbar, one row of posters and centered pagination. */
    private fun renderGrid(title: String,parent: UUID?,initialQuery: LibraryBrowse.Query,start: Int=0,push: Boolean=true) {
        if(!repo.connected){disconnected(push);return}
        fresh("grid",push);header(title,"正在加载媒体…");val session=repo.sessionKey
        load({repo.catalog(parent)}) {catalog->
            if(session!=repo.sessionKey){home(false);return@load}
            fresh("grid",false);val host=page;val large=ui.fontScale>1
            val back=ui.header(host,title,"",{back()});ui.footer(host,"方向键浏览 · 确认箭头翻页 · 返回媒体库")
            val subtitle=ui.text(host,"正在加载媒体…",12,TvUi.MUTED,84f,65f,690f,23f)
            var query=initialQuery;var offset=start;val updates=LibraryGridUpdates()
            val posterHost=FrameLayout(this);ui.at(host,posterHost,0f,0f,960f,489f);posterHost.isFocusable=false
            val search=place(host,ui.button("⌕ 搜索媒体"){search(parent=parent,kind=query.kind)},LibraryPageLayout.GRID_SEARCH).apply {tag="grid-search"}
            val filter=place(host,ui.button("类型：全部"){browseDialog {LibraryBrowseDialogs.filters(this,ui,catalog,query){changed->query=changed;offset=0;(host.tag as? Runnable)?.run()}}},LibraryPageLayout.GRID_FILTER).apply {tag="grid-filter"}
            val sort=place(host,ui.button("排序：最近添加"){browseDialog {LibraryBrowseDialogs.sort(this,ui,query){changed->query=changed;offset=0;(host.tag as? Runnable)?.run()}}},LibraryPageLayout.GRID_SORT).apply {tag="grid-sort"}
            search.nextFocusRightId=filter.id;filter.nextFocusLeftId=search.id;filter.nextFocusRightId=sort.id;sort.nextFocusLeftId=filter.id;sort.nextFocusRightId=sort.id;search.nextFocusLeftId=back.id
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
                    val order=when(requestedQuery.sort){LibraryBrowse.Sort.ADDED->if(requestedQuery.ascending)"最早添加" else "最近添加";LibraryBrowse.Sort.WATCHED->if(requestedQuery.ascending)"最早观看" else "最近观看";LibraryBrowse.Sort.YEAR->if(requestedQuery.ascending)"年份升序" else "年份降序";LibraryBrowse.Sort.RATING->if(requestedQuery.ascending)"评分升序" else "评分降序";LibraryBrowse.Sort.NAME->if(requestedQuery.ascending)"名称正序" else "名称倒序"}
                    TvIcons.text(sort,"排序：$order")
                    val cards=result.items.mapIndexed {index,item->
                        val card=posterCard(item,large){detail(item)};card.tag="grid-item-${item.id}";val existing=card.onFocusChangeListener
                        card.setOnFocusChangeListener {view,focused->existing?.onFocusChange(view,focused);if(focused)selectedColumn=index}
                        place(posterHost,card,LibraryPageLayout.poster(index,large))
                    }
                    if(cards.isEmpty()){
                        ui.text(posterHost,if(count>0)"没有符合条件的内容，可调整筛选。" else "这个分类暂时没有媒体内容。",17,TvUi.MUTED,64f,205f,830f,50f).gravity=Gravity.CENTER
                        if(count>0)ui.button(posterHost,"清除筛选",396f,275f,168f,40f){query=query.copy(state=LibraryBrowse.State.ALL,genre=null,year=null,resolution=null,region=null);offset=0;update()}
                    }
                    val previous=place(posterHost,ui.button("‹"){offset=result.start-result.size;preferred="grid-previous";update()},LibraryPageLayout.pagerPrevious(large)).apply {tag="grid-previous";contentDescription="上一页";isEnabled=result.previous;isFocusable=result.previous;alpha=if(result.previous)1f else .35f}
                    pageText(posterHost,result.range,13,TvUi.MUTED,LibraryPageLayout.pagerRange(large)).gravity=Gravity.CENTER
                    val next=place(posterHost,ui.button("›"){offset=result.start+result.size;preferred="grid-next";update()},LibraryPageLayout.pagerNext(large)).apply {tag="grid-next";contentDescription="下一页";isEnabled=result.next;isFocusable=result.next;alpha=if(result.next)1f else .35f}
                    cards.forEachIndexed {i,card->card.nextFocusLeftId=cards.getOrNull(i-1)?.id ?: card.id;card.nextFocusRightId=cards.getOrNull(i+1)?.id ?: card.id;card.nextFocusUpId=listOf(search,filter,sort)[(i*3/LibraryPageLayout.columns(large)).coerceAtMost(2)].id;card.nextFocusDownId=if(next.isEnabled)next.id else if(previous.isEnabled)previous.id else card.id}
                    val target=cards.getOrNull(selectedColumn.coerceAtMost(cards.lastIndex)) ?: filter
                    previous.nextFocusUpId=target.id;next.nextFocusUpId=target.id;previous.nextFocusRightId=if(next.isEnabled)next.id else previous.id;next.nextFocusLeftId=if(previous.isEnabled)previous.id else next.id
                    listOf(search,filter,sort).forEach {it.nextFocusDownId=target.id}
                    if(dialog?.isShowing!=true){
                        val wanted=preferred?.let {host.findViewWithTag<View>(it)}?.takeIf {it.isEnabled&&it.isFocusable};preferred=null
                        when {firstRender->focus(target);wanted!=null->focus(wanted);previousFocus?.isAttachedToWindow==true->Unit;else->focus(target)}
                    }
                }
            }
            host.tag=Runnable {update()};update()
        }
    }
    private fun posterCard(item: BaseItemDto,large: Boolean,click: ()->Unit): FrameLayout {
        val card=focusCard(FrameLayout(this),click);val bounds=LibraryPageLayout.poster(0,large)
        art(card,item,0f,0f,bounds.width,LibraryPageLayout.posterImageHeight(large))
        pageText(card,cardTitle(item),16,TvUi.INK,LibraryPageLayout.posterTitle(large)).apply {setTypeface(null,Typeface.BOLD);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
        pageText(card,cardMeta(item),12,TvUi.MUTED,LibraryPageLayout.posterMetadata(large)).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        if(item.userData?.played==true)ui.text(card,"已看",10,TvUi.INK,bounds.width-43f,7f,36f,18f).apply {gravity=Gravity.CENTER;background=ui.surface(0xcc171c32.toInt(),false)}
        else if(resumeMs(item)>0&&(item.runTimeTicks ?: 0)>0){val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.max=1000;progress.progress=(resumeMs(item)*10000.0*1000/item.runTimeTicks!!).toInt().coerceIn(0,1000);ui.at(card,progress,4f,LibraryPageLayout.posterImageHeight(large)-4f,bounds.width-8f,3f)}
        return card
    }
    private fun cardTitle(item: BaseItemDto)=if(item.type==BaseItemKind.EPISODE)item.seriesName ?: item.name ?: "未命名剧集" else item.name ?: "未命名媒体"
    private fun cardMeta(item: BaseItemDto)=listOfNotNull(LibraryMetadata.year(item)?.toString(),LibraryMetadata.rating(item)?.let {"评分 $it"}).joinToString(" · ")
    private fun section(body: LinearLayout, title: String) { body.addView(ui.text(title,17,TvUi.INK),LinearLayout.LayoutParams(-1,ui.px(35f)).apply {topMargin=ui.px(7f);bottomMargin=ui.px(6f)}) }
    private fun horizontal(body: LinearLayout, items: List<BaseItemDto>, progress: Boolean=false, click: (BaseItemDto)->Unit) {
        val sc=HorizontalScrollView(this);sc.isHorizontalScrollBarEnabled=false
        val row=LinearLayout(this)
        items.forEach {item -> row.addView(tile(cardTitle(item), if(progress) if(item.type==BaseItemKind.EPISODE) "第 ${item.indexNumber ?: 1} 集 · ${time(resumeMs(item))}" else "继续播放 · ${time(resumeMs(item))}" else meta(item),287,209,item,progress) {click(item)},LinearLayout.LayoutParams(ui.px(287f),ui.px(209f)).apply {marginEnd=ui.px(14f)})}
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
        val image=AsyncImageView(this);image.scaleType=ImageView.ScaleType.CENTER_CROP;image.setBackgroundColor(0xff20283d.toInt());ui.at(parent,image,x,y,w,h)
        image.load(url=repo.image(item,backdrop),placeholder=ColorDrawable(0xff20283d.toInt()),aspectRatio=if(backdrop) 16.0/9 else 2.0/3)
        if(repo.image(item,backdrop)==null) ui.text(parent,"暂无封面",12,TvUi.MUTED,x+10,y+h/2-10,w-20,20f).gravity=Gravity.CENTER
    }
    private fun grid(title: String,parent: UUID?=null,kind: BaseItemKind?=null,start: Int=0,alphabetical: Boolean=false,genre: String?=null,push: Boolean=true) =
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
        fresh("movie",push);header(if(item.type==BaseItemKind.EPISODE) "剧集详情" else "电影详情")
        art(page,item,460f,0f,500f,450f,true)
        val shade=View(this);shade.setBackgroundColor(0x33101326);ui.at(page,shade,460f,0f,500f,450f)
        label("JellyLive",814f,29f,114f,34f,16,true)
        // Text remains on the opaque left half, matching the supplied movie page.
        label(if(item.type==BaseItemKind.EPISODE) "剧集 · 第 ${item.indexNumber ?: "?"} 集" else "电影",50f,112f,380f,24f,13,true)
        label(item.name ?: "未命名媒体",50f,155f,390f,63f,31).apply {maxLines=2;setTypeface(null,Typeface.BOLD)}
        label(meta(item),50f,222f,380f,32f,14,true)
        label(item.overview?.replace(Regex("<[^>]+>"),"") ?: "暂无简介",50f,269f,380f,95f,14).apply {maxLines=4;ellipsize=android.text.TextUtils.TruncateAt.END}
        val position=resumeMs(item)
        val first=ui.button(page,if(position>0) "▷ 继续播放 ${time(position)}" else "▷ 播放",50f,379f,208f,40f) {play(item,position)}
        if(position>0)ui.button(page,"从头播放",274f,379f,128f,40f) {play(item,0)}
        label(item.people.orEmpty().take(4).joinToString(" / ") {it.name.orEmpty()},50f,431f,380f,28f,12,true)
        focus(first)
    }
    private fun series(item: BaseItemDto,push: Boolean=false,selected: UUID?=null) {
        fresh("series",push);header("剧集详情","正在加载季和剧集…")
        load({
            val seasons=repo.seasons(item.id)
            val season=selected ?: seasons.firstOrNull()?.id
            Triple(seasons,repo.episodes(item.id,season),try {repo.nextEpisode(item.id)} catch (_: Exception) {null})
        }) { (seasons,episodes,next) ->
            seriesEpisodes=episodes;fresh("series",false);header("剧集详情")
            label(item.name ?: "未命名剧集",32f,102f,580f,44f,29).setTypeface(null,Typeface.BOLD)
            label("${meta(item)} · ${episodes.size} 集",32f,148f,580f,26f,14,true)
            label(item.overview?.replace(Regex("<[^>]+>"),"") ?: "暂无简介",32f,179f,555f,48f,14).maxLines=2
            art(page,item,625f,91f,303f,160f,true)
            val toPlay=next ?: episodes.firstOrNull {it.userData?.played!=true} ?: episodes.firstOrNull()
            val first=ui.button(page,if(toPlay==null) "暂无可播放剧集" else "▷ 继续播放 第 ${toPlay.indexNumber ?: 1} 集",32f,230f,230f,38f) {toPlay?.let {play(it,resumeMs(it))}}
            first.isEnabled=toPlay!=null
            val chosen=seasons.firstOrNull {it.id==selected} ?: seasons.firstOrNull()
            ui.button(page,"${chosen?.name ?: "全部剧集"} ⌄",32f,294f,174f,38f) {
                if(seasons.isNotEmpty())choose("选择季",seasons.map {it.name ?: "未命名季"},seasons.indexOf(chosen)) {series(item,false,seasons[it].id)}
            }
            val scroll=HorizontalScrollView(this);scroll.isHorizontalScrollBarEnabled=false;val row=LinearLayout(this);scroll.addView(row);ui.at(page,scroll,32f,337f,896f,149f)
            episodes.forEach { episode ->
                val status=if(episode.userData?.played==true) "已观看" else if(resumeMs(episode)>0) "继续 ${time(resumeMs(episode))}" else episode.name.orEmpty()
                row.addView(tile("第 ${episode.indexNumber ?: "?"} 集",status,180,149,episode,true) {play(episode,resumeMs(episode))},LinearLayout.LayoutParams(ui.px(180f),ui.px(149f)).apply {marginEnd=ui.px(14f)})
            }
            if(episodes.isEmpty())label("本季暂无可播放剧集",270f,297f,500f,30f,14,true)
            if(first.isEnabled)focus(first)
        }
    }
    private fun search(initial: String="",push: Boolean=true,parent: UUID?=null,kind: BaseItemKind?=null) {
        mediaSearchScope=MediaSearchScope(repo.sessionKey,parent,kind)
        fresh("search",push);header("搜索媒体","${repo.serverName} · ${when(kind){BaseItemKind.MOVIE->"电影";BaseItemKind.SERIES->"剧集";else->"电影和剧集"}}")
        val input=ui.input(page,"输入片名、剧集名",initial,32f,100f,350f,false);searchInput=input
        val body=LinearLayout(this);body.orientation=LinearLayout.VERTICAL;searchResults=body
        val sc=ScrollView(this);sc.addView(body);ui.at(page,sc,415f,105f,513f,354f)
        val chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        chars.forEachIndexed {index,c ->ui.button(page,c.toString(),32f+(index%6)*58f,159f+(index/6)*39f,51f,32f) {input.append(c.toString());runSearch(input.text.toString())}}
        ui.button(page,"中文／拼音",32f,405f,107f,39f) {input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}
        ui.button(page,"退格",153f,405f,107f,39f) {if(input.length()>0)input.text.delete(input.length()-1,input.length());runSearch(input.text.toString())}
        ui.button(page,"清除",274f,405f,107f,39f) {input.setText("");runSearch("")}
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

    private fun play(item: BaseItemDto,position: Long) {
        fresh("player");overlay=MediaOverlay();consumedPlayerConfirm=false;playingItem=item;playingSessionKey=repo.sessionKey;playingPositionPrefix=currentPositionPrefix();stream=null;reportStarted=false;lastPersist=0
        val video=PlayerView(this);video.useController=false;video.isFocusable=false;videoSurface=video;ui.at(page,video,0f,0f,960f,540f)
        val loading=label("正在获取播放地址…",32f,80f,850f,48f,18)
        makeControls(item)
        page.isFocusableInTouchMode=true;page.requestFocus()
        video.setOnClickListener {if(overlay.visible)hidePlayerControls()else showControls()}
        val stamp=generation
        if(item.type==BaseItemKind.EPISODE&&item.seriesId!=null)lifecycleScope.launch {
            try {val episodes=repo.episodes(item.seriesId!!);if(stamp==generation){seriesEpisodes=episodes;updateEpisodeActions(item)}}catch(_:CancellationException){}catch(_:Exception){}
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
                        if(url.isNullOrBlank()) {loading.text="服务器未提供可播放的媒体流，可在更多中重试。";overlay.blocked=true;showControls();return}
                        stream=response
                        val render=DefaultRenderersFactory(this@JellyLibraryActivity).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        val exo=ExoPlayer.Builder(this@JellyLibraryActivity,render).setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(LibraryMediaHttp.client(repo.api,mediaHttpFactory)))).build()
                        player=exo;video.player=exo
                        val display=getSharedPreferences("bestv_live",MODE_PRIVATE)
                        video.resizeMode=if(display.getBoolean("fill_video",false))androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val audio=display.getString("preferred_audio","auto");val subtitle=display.getString("preferred_subtitle","off")
                        exo.trackSelectionParameters=exo.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(audio?.takeUnless {it=="auto"}).setPreferredTextLanguage(subtitle?.takeUnless {it=="off"}).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle=="off").build()
                        exo.addListener(object: Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {overlay.blocked=true;loading.visibility=View.VISIBLE;loading.text="播放失败，可在更多中重试，或返回检查格式、网络与转码设置。";showControls()}
                            override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int){overlay.paused=!playWhenReady;armPlayerIdle()}
                            override fun onTimelineChanged(timeline: Timeline,reason: Int){seek?.visibility=if(canSeek())View.VISIBLE else View.GONE}
                            override fun onPlaybackStateChanged(state: Int) {
                                if(state==Player.STATE_READY){loading.visibility=View.GONE;if(!reportStarted&&playingSessionKey==repo.sessionKey){reportStarted=true;reporting.reportStart(this@JellyLibraryActivity,null,item,response,exo.currentPosition*10000,!exo.playWhenReady);lastReport=System.currentTimeMillis()}}
                                if(state==Player.STATE_ENDED){overlay.blocked=true;showControls()}
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
                    override fun onError(exception: Exception) {if(stamp==generation){loading.text="无法获取播放地址，请返回重试。";showControls()}}
                })
            } catch(e: CancellationException){throw e} catch(_: Exception){if(stamp==generation){loading.text="播放器初始化失败，可在更多中重试。";overlay.blocked=true;showControls()}}
        }
    }
    private fun makeControls(item: BaseItemDto) {
        // Hide before attaching, including the central action and back button.
        val panel=FrameLayout(this);panel.visibility=View.GONE;panel.background=ui.surface(0xef10172b.toInt(),false);controls=panel;ui.at(page,panel,32f,374f,896f,134f)
        ui.title(panel,item.name ?: "媒体播放",20f,10f,645f,32f)
        clock=ui.text(panel,"00:00 / --:--",14,TvUi.MUTED,684f,10f,195f,32f)
        val progress=SeekBar(this);seek=progress;progress.id=View.generateViewId();progress.max=10000;progress.visibility=View.GONE
        ui.at(panel,progress,14f,47f,866f,23f)
        progress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean){if(fromUser&&canSeek()){beginSeek();player?.let {overlay.preview(it.duration*value/10000,it.duration)};updateSeekClock()}}
            override fun onStartTrackingTouch(bar: SeekBar?){beginSeek()}
            override fun onStopTrackingTouch(bar: SeekBar?){commitSeek()}
        })
        progress.setOnTouchListener {_,event->if(event.action==MotionEvent.ACTION_CANCEL)cancelSeek();false}
        pause=ui.button("Ⅱ"){togglePlayback()}.apply {
            visibility=View.GONE;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,26f*ui.scale);contentDescription="暂停或继续播放"
            background=android.graphics.drawable.StateListDrawable().apply {
                fun circle(color: Int)=android.graphics.drawable.GradientDrawable().apply {shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(color);setStroke(ui.px(2f),Color.WHITE)}
                addState(intArrayOf(android.R.attr.state_focused),circle(TvUi.ACCENT));addState(intArrayOf(),circle(0x9910172b.toInt()))
            }
        };ui.at(page,pause!!,444f,234f,72f,72f)
        playerBack=ui.button("‹ 返回"){back()}.apply{visibility=View.GONE};ui.at(page,playerBack!!,32f,28f,110f,38f)
        val audio=ui.button(panel,"音轨",20f,77f,100f,39f){tracks(C.TRACK_TYPE_AUDIO)}
        val subtitle=ui.button(panel,"字幕",135f,77f,100f,39f){tracks(C.TRACK_TYPE_TEXT)}
        episodeAction=ui.button(panel,"选集",250f,77f,100f,39f){selectEpisode(item)}.apply{visibility=View.GONE}
        nextAction=ui.button(panel,"下一集",365f,77f,100f,39f){nextEpisodeItem(item)?.let {replacePlayback(it,resumeMs(it))}}.apply{visibility=View.GONE}
        val more=ui.button(panel,"更多",480f,77f,100f,39f){
            choose("播放操作",listOf(if(player?.playerError!=null)"重试播放" else "从头播放","画面比例"),-1){
                if(it==0)replacePlayback(item,0) else choose("画面比例",listOf("保持比例","填充画面"),if(videoSurface?.resizeMode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)1 else 0){index->videoSurface?.resizeMode=if(index==1)androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT}
            }
        }
        listOf(audio,subtitle,episodeAction!!,nextAction!!,more).forEach {button->button.nextFocusUpId=progress.id;button.setOnFocusChangeListener {_,focused->if(focused){lastPlayerAction=button;armPlayerIdle()}}}
        lastPlayerAction=audio;progress.nextFocusDownId=audio.id;progress.nextFocusUpId=pause!!.id;pause!!.nextFocusDownId=progress.id;pause!!.nextFocusUpId=playerBack!!.id;playerBack!!.nextFocusDownId=pause!!.id
        ui.text(panel,"拖动或左右预览 · 确定跳转 · 返回取消",12,TvUi.MUTED,602f,83f,278f,28f)
        updateEpisodeActions(item)
    }
    private fun nextEpisodeItem(item: BaseItemDto): BaseItemDto? {
        if(item.type!=BaseItemKind.EPISODE)return null
        val episodes=seriesEpisodes.filter {it.seriesId==item.seriesId}
        val index=episodes.indexOfFirst {it.id==item.id}
        return if(index>=0)episodes.getOrNull(index+1)else null
    }
    private fun updateEpisodeActions(item: BaseItemDto){
        episodeAction?.visibility=if(item.type==BaseItemKind.EPISODE&&seriesEpisodes.any {it.id==item.id})View.VISIBLE else View.GONE
        nextAction?.visibility=if(nextEpisodeItem(item)!=null)View.VISIBLE else View.GONE
    }
    private fun selectEpisode(item: BaseItemDto){
        val episodes=seriesEpisodes.filter {it.seriesId==item.seriesId};if(episodes.isEmpty())return
        choose("选集",episodes.map {"第 ${it.indexNumber ?: "?"} 集 · ${it.name.orEmpty()}"},episodes.indexOfFirst {it.id==item.id}){replacePlayback(episodes[it],resumeMs(episodes[it]))}
    }
    private fun replacePlayback(item: BaseItemDto,position: Long){stopPlayer();backToSaved();play(item,position)}
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
    private fun updateSeekClock(){val p=player ?: return;val target=overlay.target ?: return;seek?.progress=if(p.duration>0)(target*10000/p.duration).toInt()else 0;clock?.text="${time(target)} / ${time(p.duration)}"}
    private fun commitSeek(){val result=overlay.commit() ?: return;player?.let {it.seekTo(result.position);it.playWhenReady=result.resume};overlay.paused=!result.resume;showControls(false);seek?.requestFocus()}
    private fun cancelSeek(){val resume=overlay.cancel() ?: return;player?.playWhenReady=resume;overlay.paused=!resume;showControls(false);seek?.requestFocus()}
    private fun playRequested(){player?.let {if(it.playbackState==Player.STATE_ENDED){it.seekTo(0);overlay.blocked=false};it.play()}}
    private fun togglePlayback(){player?.let {p->
        if(p.playbackState==Player.STATE_ENDED)playRequested()else if(p.playWhenReady)p.pause()else p.play()
        overlay.paused=!p.playWhenReady;showControls()
    }}
    private fun armPlayerIdle(){
        handler.removeCallbacks(hideControls);overlay.interact(android.os.SystemClock.uptimeMillis())
        overlay.remaining(android.os.SystemClock.uptimeMillis())?.let {handler.postDelayed(hideControls,it)}
    }
    private fun applyControlsVisibility(){
        controls?.visibility=if(overlay.visible)View.VISIBLE else View.GONE
        pause?.visibility=if(overlay.visible&&!overlay.seeking&&!overlay.dialog)View.VISIBLE else View.GONE
        playerBack?.visibility=if(overlay.visible)View.VISIBLE else View.GONE
        pause?.let {TvIcons.text(it,if(player?.playWhenReady==true&&player?.playbackState!=Player.STATE_ENDED)"Ⅱ" else "▷")}
        videoSurface?.subtitleView?.setBottomPaddingFraction(if(overlay.visible)0.32f else 0.08f)
        if(!overlay.visible)page.requestFocus()
    }
    private fun hidePlayerControls(){overlay.hide();handler.removeCallbacks(hideControls);applyControlsVisibility()}
    private fun tracks(type: Int) {
        val p=player ?: return
        data class Choice(val group: Tracks.Group,val index: Int,val label: String)
        val choices=p.currentTracks.groups.filter {it.type==type}.flatMap {g -> (0 until g.length).filter {g.isTrackSupported(it)}.map {i ->val f=g.getTrackFormat(i);Choice(g,i,f.label ?: f.language ?: "${if(type==C.TRACK_TYPE_TEXT) "字幕" else "音轨"} ${i+1}")}}
        val labels=(if(type==C.TRACK_TYPE_TEXT) listOf("关闭字幕") else emptyList())+choices.map {it.label}
        if(labels.isEmpty()){Toast.makeText(this,"没有可切换的${if(type==C.TRACK_TYPE_TEXT) "字幕" else "音轨"}",Toast.LENGTH_SHORT).show();return}
        val selected=choices.indexOfFirst {it.group.isTrackSelected(it.index)}.let {if(type==C.TRACK_TYPE_TEXT)it+1 else it}
        choose(if(type==C.TRACK_TYPE_TEXT) "字幕" else "音轨",labels,selected){index ->
            val builder=p.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
            if(type==C.TRACK_TYPE_TEXT && index==0)builder.setTrackTypeDisabled(type,true)
            else {val c=choices[index-if(type==C.TRACK_TYPE_TEXT)1 else 0];builder.setTrackTypeDisabled(type,false).setOverrideForType(TrackSelectionOverride(c.group.mediaTrackGroup,c.index))}
            p.trackSelectionParameters=builder.build();showControls(false)
        }
    }
    private fun showControls(focusButton: Boolean=true) {overlay.show(android.os.SystemClock.uptimeMillis());applyControlsVisibility();if(focusButton&&!overlay.seeking)pause?.requestFocus();armPlayerIdle()}
    private fun stopPlayer() {
        handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls)
        val p=player
        if(p!=null){playingItem?.let {item ->rememberPosition(item,p.currentPosition);if(reportStarted&&playingSessionKey==repo.sessionKey)stream?.let {reporting.reportStopped(this,item,it,p.currentPosition*10000)}};videoSurface?.player=null;p.release()}
        player=null;stream=null;playingItem=null;controls=null;seek=null;clock=null;pause=null;playerBack=null;episodeAction=null;nextAction=null;lastPlayerAction=null;overlay=MediaOverlay();reportStarted=false
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
        if(pageName=="player") {if(overlay.seeking){cancelSeek();return};if(overlay.visible){hidePlayerControls();return};stopPlayer();backToSaved();return}
        if(history.isEmpty())finish()else backToSaved()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {if(event.actionMasked==MotionEvent.ACTION_DOWN)interaction++;return super.dispatchTouchEvent(event)}
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.action==KeyEvent.ACTION_DOWN)interaction++
        if(pageName=="player"&&dialog==null){
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
                    KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT->if(!overlay.visible||currentFocus===pause||currentFocus===seek){stepSeek(key==KeyEvent.KEYCODE_DPAD_RIGHT,event.repeatCount>0);return true}
                    KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN->{
                        if(!overlay.visible){showControls();return true}
                        if(currentFocus===seek){if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(!overlay.seeking)lastPlayerAction?.requestFocus()}else if(!overlay.seeking)pause?.requestFocus();return true}
                        if(currentFocus===lastPlayerAction&&key==KeyEvent.KEYCODE_DPAD_UP&&!canSeek()){pause?.requestFocus();return true}
                        if(currentFocus===pause&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(canSeek())seek?.requestFocus()else lastPlayerAction?.requestFocus();return true}
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
        if(player!=null){handler.removeCallbacks(updatePlayer);handler.post(updatePlayer)}
    }
    override fun onStop(){
        overlay.cancel();overlay.paused=true;player?.pause();handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls)
        val p=player;val item=playingItem;val info=stream
        if(p!=null && item!=null){rememberPosition(item,p.currentPosition);if(info!=null && reportStarted && playingSessionKey==repo.sessionKey)reporting.reportProgress(this,null,item,info,p.currentPosition*10000,true)}
        super.onStop()
    }
    override fun onDestroy(){stopPlayer();task?.cancel();searchJob?.cancel();homeResumeJob?.cancel();gridRenderJob?.cancel();dialog?.dismiss();super.onDestroy()}
    private fun rememberPosition(item: BaseItemDto,position: Long){local.edit().putLong(positionKey(item),position).putLong("seen_${positionKey(item)}",System.currentTimeMillis()).apply()}
    private fun currentPositionPrefix()="pos_${repo.sessions.currentSession.value?.serverId}_${repo.sessions.currentSession.value?.userId}_"
    private fun positionKey(item: BaseItemDto)=(if(playingItem?.id==item.id&&playingPositionPrefix.isNotEmpty())playingPositionPrefix else currentPositionPrefix())+item.id
    private fun resumeMs(item: BaseItemDto): Long {
        val server=(item.userData?.playbackPositionTicks ?: 0)/10000
        val seen=local.getLong("seen_${positionKey(item)}",0)
        return if(seen>0 && seen>=(item.userData?.lastPlayedDate?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli() ?: 0))local.getLong(positionKey(item),server)else server
    }
    private fun meta(item: BaseItemDto): String = listOfNotNull(LibraryMetadata.year(item)?.toString(),LibraryMetadata.rating(item)?.let {"评分 $it"},LibraryBrowse.resolution(item)?.let {if(it==2160)"4K" else "${it}p"},item.genres?.take(2)?.joinToString(" / ")?.takeIf {it.isNotEmpty()},item.runTimeTicks?.let {"${it/10000/60000} 分钟"}).joinToString(" · ")
    private fun time(ms: Long): String {val secs=(ms.coerceAtLeast(0)/1000);return if(secs>=3600) "%d:%02d:%02d".format(secs/3600,secs/60%60,secs%60) else "%02d:%02d".format(secs/60,secs%60)}
}
