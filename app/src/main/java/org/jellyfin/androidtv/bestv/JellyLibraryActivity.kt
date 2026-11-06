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
    private data class SavedPage(val view: FrameLayout, val name: String, val focus: Int)
    private val history = ArrayDeque<SavedPage>()
    private var task: Job? = null
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
    private var videoSurface: PlayerView? = null
    private var controls: FrameLayout? = null
    private var seek: SeekBar? = null
    private var clock: TextView? = null
    private var pause: Button? = null
    private var reportStarted = false
    private var lastReport = 0L
    private var lastPersist = 0L
    private var searchInput: EditText? = null
    private var searchResults: LinearLayout? = null
    private var searchJob: Job? = null
    private var bootstrap: LibraryBootstrap? = null
    private var bootstrapFailure = false
    private val hideControls = Runnable { if (player?.isPlaying == true) controls?.visibility = View.GONE }
    private val updatePlayer = object : Runnable {
        override fun run() {
            val p = player ?: return
            val duration = p.duration.takeIf { it > 0 } ?: ((stream?.runTimeTicks ?: 0) / 10000)
            if (seek?.isPressed != true) seek?.progress = if (duration > 0) (p.currentPosition * 10000 / duration).toInt() else 0
            clock?.text = "${time(p.currentPosition)} / ${time(duration)}"
            pause?.let { TvIcons.text(it, if (p.isPlaying) "Ⅱ  暂停" else "▷  播放") }
            playingItem?.let { item ->
                if(System.currentTimeMillis()-lastPersist>=5000){rememberPosition(item,p.currentPosition);lastPersist=System.currentTimeMillis()}
                if (reportStarted && System.currentTimeMillis() - lastReport > 15000) {
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
        task?.cancel(); searchJob?.cancel(); generation++
        if (push && ::page.isInitialized) history.addLast(SavedPage(page, pageName, currentFocus?.id ?: View.NO_ID))
        pageName = name; page = ui.canvas(); setContentView(page)
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

    private fun home(push: Boolean=true) {
        if(!repo.connected) {disconnected(push);return}
        fresh("home",push);header("我的媒体库","${repo.serverName} · 正在加载…")
        load({
            val views=repo.views()
            val resumes=try { repo.resume() } catch (_: Exception) { emptyList() }
            views to resumes
        }) { (views,resumes) ->
            if(views.isEmpty()) { empty();return@load }
            fresh("home",false);header("我的媒体库","${repo.serverName} · 已连接")
            ui.button(page,"⌕ 搜索媒体",770f,69f,158f,38f) { search() }
            val scroll=ScrollView(this);scroll.isFillViewport=false;scroll.clipToPadding=false
            val body=LinearLayout(this);body.orientation=LinearLayout.VERTICAL;scroll.addView(body)
            ui.at(page,scroll,32f,100f,896f,369f)
            if(resumes.isNotEmpty()) {
                section(body,"继续观看")
                horizontal(body,resumes.take(12),true) { play(it,resumeMs(it)) }
            }
            section(body,"探索媒体库")
            val row=LinearLayout(this);row.orientation=LinearLayout.HORIZONTAL
            val options=listOf(Triple("电影",BaseItemKind.MOVIE,null),Triple("剧集",BaseItemKind.SERIES,null),Triple("最近添加",null,null))
            options.forEach { (name,kind,_) ->
                val button=tile(name,"浏览全部内容",300,130,null) { grid(name,kind=kind) }
                row.addView(button,LinearLayout.LayoutParams(ui.px(287f),ui.px(130f)).apply { marginEnd=ui.px(14f) })
            };body.addView(row)
            section(body,"我的分类")
            views.chunked(3).forEach { group ->
                val r=LinearLayout(this)
                group.forEach { item -> r.addView(tile(item.name ?: "媒体库","浏览分类内容",287,120,item) {grid(item.name ?: "媒体库",item.id)},LinearLayout.LayoutParams(ui.px(287f),ui.px(120f)).apply {marginEnd=ui.px(14f);bottomMargin=ui.px(12f)}) }
                body.addView(r)
            }
            focus(body.getChildAt(1)?.let { (it as? ViewGroup)?.getChildAt(0) } ?: scroll)
        }
    }
    private fun section(body: LinearLayout, title: String) { body.addView(ui.text(title,17,TvUi.INK),LinearLayout.LayoutParams(-1,ui.px(35f)).apply {topMargin=ui.px(7f);bottomMargin=ui.px(6f)}) }
    private fun horizontal(body: LinearLayout, items: List<BaseItemDto>, progress: Boolean=false, click: (BaseItemDto)->Unit) {
        val sc=HorizontalScrollView(this);sc.isHorizontalScrollBarEnabled=false
        val row=LinearLayout(this)
        items.forEach {item -> row.addView(tile(item.name ?: "未命名媒体", if(progress) "继续播放 · ${time(resumeMs(item))}" else meta(item),287,132,item,progress) {click(item)},LinearLayout.LayoutParams(ui.px(287f),ui.px(132f)).apply {marginEnd=ui.px(14f)})}
        sc.addView(row);body.addView(sc,LinearLayout.LayoutParams(-1,ui.px(140f)))
    }
    private fun tile(name: String, sub: String, w: Int, h: Int, item: BaseItemDto?, progress: Boolean=false, click: ()->Unit): FrameLayout {
        val box=FrameLayout(this);box.id=View.generateViewId();box.isFocusable=true;box.isFocusableInTouchMode=true;box.isClickable=true;box.background=ui.focusBackground(TvUi.PANEL);box.setPadding(ui.px(2f),ui.px(2f),ui.px(2f),ui.px(2f));box.clipToOutline=true
        val artH=h-48
        if(item!=null) art(box,item,0f,0f,w.toFloat(),artH.toFloat(),true)
        else { val v=ui.text("▱",36,0xff8275bd.toInt());v.gravity=Gravity.CENTER;ui.at(box,v,0f,0f,w.toFloat(),artH.toFloat()) }
        ui.text(box,name,16,TvUi.INK,10f,artH+5f,w-20f,22f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;setTypeface(null,Typeface.BOLD)}
        ui.text(box,sub,12,TvUi.MUTED,10f,artH+26f,w-20f,18f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        if(progress && item!=null) {
            val p=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);p.max=1000;p.progress=if((item.runTimeTicks ?: 0)>0) (resumeMs(item)*10000*1000/(item.runTimeTicks!!)).toInt().coerceIn(0,1000) else 0
            ui.at(box,p,8f,h-7f,w-16f,3f)
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
    private fun grid(title: String,parent: UUID?=null,kind: BaseItemKind?=null,start: Int=0,alphabetical: Boolean=false,genre: String?=null,push: Boolean=true) {
        fresh("grid",push);header(title,"正在加载媒体…")
        load({repo.items(parent,kind,start,alphabetical=alphabetical,genre=genre)}) { result ->
            fresh("grid",false);header(title,"${result.totalRecordCount} 项内容${if(genre!=null) " · $genre" else ""}")
            ui.button(page,"类型：${genre ?: "全部"}",590f,69f,150f,38f) {
                val genres=result.items.flatMap {it.genres.orEmpty()}.distinct().sorted()
                choose("类型",listOf("全部")+genres,if(genre==null) 0 else genres.indexOf(genre)+1) {grid(title,parent,kind,0,alphabetical,if(it==0)null else genres[it-1],false)}
            }
            ui.button(page,"排序：${if(alphabetical) "名称" else "最近添加"}",755f,69f,173f,38f) {
                choose("排序",listOf("最近添加","名称"),if(alphabetical)1 else 0) {grid(title,parent,kind,0,it==1,genre,false)}
            }
            if(result.items.isEmpty()) {
                label("这里暂时没有媒体内容",40f,200f,750f,45f,21)
                ui.button(page,"刷新",40f,270f,160f,40f) {grid(title,parent,kind,start,alphabetical,genre,false)}
                return@load
            }
            val scroll=ScrollView(this);val rows=LinearLayout(this);rows.orientation=LinearLayout.VERTICAL;scroll.addView(rows);ui.at(page,scroll,32f,134f,896f,290f)
            result.items.chunked(6).forEach { group ->
                val row=LinearLayout(this)
                group.forEach { item ->
                    row.addView(tile(item.name ?: "未命名媒体",meta(item),137,270,item) {detail(item)},LinearLayout.LayoutParams(ui.px(137f),ui.px(270f)).apply {marginEnd=ui.px(14f);bottomMargin=ui.px(16f)})
                };rows.addView(row)
            }
            label("${start+1}–${start+result.items.size} / ${result.totalRecordCount}",32f,435f,480f,24f,13,true)
            if(start>0)ui.button(page,"上一页",650f,432f,120f,34f) {grid(title,parent,kind,(start-60).coerceAtLeast(0),alphabetical,genre,false)}
            if(start+result.items.size<result.totalRecordCount)ui.button(page,"下一页",784f,432f,144f,34f) {grid(title,parent,kind,start+60,alphabetical,genre,false)}
            focus((rows.getChildAt(0) as ViewGroup).getChildAt(0))
        }
    }
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
            val scroll=HorizontalScrollView(this);scroll.isHorizontalScrollBarEnabled=false;val row=LinearLayout(this);scroll.addView(row);ui.at(page,scroll,32f,350f,896f,116f)
            episodes.forEach { episode ->
                val status=if(episode.userData?.played==true) "已观看" else if(resumeMs(episode)>0) "继续 ${time(resumeMs(episode))}" else episode.name.orEmpty()
                row.addView(tile("第 ${episode.indexNumber ?: "?"} 集",status,213,112,episode,true) {play(episode,resumeMs(episode))},LinearLayout.LayoutParams(ui.px(213f),ui.px(112f)).apply {marginEnd=ui.px(14f)})
            }
            if(episodes.isEmpty())label("本季暂无可播放剧集",270f,297f,500f,30f,14,true)
            if(first.isEnabled)focus(first)
        }
    }
    private fun search(initial: String="",push: Boolean=true) {
        fresh("search",push);header("搜索","媒体 · 仅查询当前连接的 Jellyfin")
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
        searchJob?.cancel();val stamp=generation;val resultBody=searchResults ?: return
        resultBody.removeAllViews()
        resultBody.addView(ui.text(if(query.isBlank()) "输入关键词搜索你的媒体" else "正在搜索…",16,TvUi.MUTED))
        if(query.isBlank())return
        searchJob=lifecycleScope.launch {
            delay(300)
            try {
                val result=withTimeout(20000) {repo.items(search=query)}
                if(stamp!=generation)return@launch
                resultBody.removeAllViews();section(resultBody,"媒体 · ${result.totalRecordCount}")
                if(result.items.isEmpty())resultBody.addView(ui.text("没有找到相关媒体，试试其他关键词。",14,TvUi.MUTED))
                result.items.forEach {item -> resultBody.addView(tile(item.name ?: "未命名媒体",meta(item),490,112,item) {detail(item)},LinearLayout.LayoutParams(-1,ui.px(112f)).apply {bottomMargin=ui.px(12f)})}
            } catch(e: CancellationException) {if(e is TimeoutCancellationException && stamp==generation){resultBody.removeAllViews();resultBody.addView(ui.text("搜索超时，关键词已保留。",14,TvUi.MUTED))}}
            catch(_: Exception) {if(stamp==generation){resultBody.removeAllViews();resultBody.addView(ui.text("暂时无法搜索，请重试或检查连接。",14,TvUi.MUTED))}}
        }
    }

    private fun play(item: BaseItemDto,position: Long) {
        fresh("player");playingItem=item;stream=null;reportStarted=false;lastPersist=0
        val video=PlayerView(this);video.useController=false;video.isFocusable=false;videoSurface=video;ui.at(page,video,0f,0f,960f,540f)
        val loading=label("正在获取播放地址…",32f,80f,850f,48f,18)
        makeControls(item)
        val stamp=generation
        lifecycleScope.launch {
            try {
                val profile=withContext(Dispatchers.IO) {createDeviceProfile(this@JellyLibraryActivity,preferences,repo.servers.currentServer.value?.serverVersion ?: ServerVersion(10,10,0))}
                if(stamp!=generation)return@launch
                val opts=VideoOptions().apply {itemId=item.id;this.profile=profile;mediaSources=item.mediaSources;mediaSourceId=item.mediaSources?.firstOrNull()?.id}
                playback.getVideoStreamInfo(this@JellyLibraryActivity,opts,position*10000,object: Response<StreamInfo>(lifecycle) {
                    override fun onResponse(response: StreamInfo) {
                        if(stamp!=generation)return
                        val url=response.mediaUrl
                        if(url.isNullOrBlank()) {loading.text="服务器未提供可播放的媒体流，请返回后重试。";return}
                        stream=response
                        val render=DefaultRenderersFactory(this@JellyLibraryActivity).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        val exo=ExoPlayer.Builder(this@JellyLibraryActivity,render).setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(LibraryMediaHttp.client(repo.api,mediaHttpFactory)))).build()
                        player=exo;video.player=exo
                        val display=getSharedPreferences("bestv_live",MODE_PRIVATE)
                        video.resizeMode=if(display.getBoolean("fill_video",false))androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        val audio=display.getString("preferred_audio","auto");val subtitle=display.getString("preferred_subtitle","off")
                        exo.trackSelectionParameters=exo.trackSelectionParameters.buildUpon().setPreferredAudioLanguage(audio?.takeUnless {it=="auto"}).setPreferredTextLanguage(subtitle?.takeUnless {it=="off"}).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,subtitle=="off").build()
                        exo.addListener(object: Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {loading.visibility=View.VISIBLE;loading.text="播放失败，请检查格式、网络或服务器转码设置。";showControls()}
                            override fun onPlaybackStateChanged(state: Int) {
                                if(state==Player.STATE_READY){loading.visibility=View.GONE;if(!reportStarted){reportStarted=true;reporting.reportStart(this@JellyLibraryActivity,null,item,response,exo.currentPosition*10000,!exo.playWhenReady);lastReport=System.currentTimeMillis()}}
                                if(state==Player.STATE_ENDED)showControls()
                            }
                        })
                        val subtitles=response.mediaSource?.mediaStreams.orEmpty().filter { it.type==MediaStreamType.SUBTITLE && it.isExternal==true && !it.deliveryUrl.isNullOrBlank() }.mapNotNull { media ->
                            val mime=when(media.codec?.lowercase()) {"srt","subrip"->MimeTypes.APPLICATION_SUBRIP;"vtt","webvtt"->MimeTypes.TEXT_VTT;"ass","ssa"->MimeTypes.TEXT_SSA;"ttml"->MimeTypes.APPLICATION_TTML;else->null} ?: return@mapNotNull null
                            val subtitleUrl=repo.api.createUrl(media.deliveryUrl!!,ignorePathParameters=true)
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl)).setMimeType(mime).setLanguage(media.language).setLabel(media.displayTitle ?: media.language ?: "字幕").build()
                        }
                        exo.setMediaItem(MediaItem.Builder().setUri(url).setSubtitleConfigurations(subtitles).build());exo.seekTo(position);exo.prepare();exo.playWhenReady=true
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        handler.post(updatePlayer);showControls()
                    }
                    override fun onError(exception: Exception) {if(stamp==generation){loading.text="无法获取播放地址，请返回重试。";showControls()}}
                })
            } catch(e: CancellationException){throw e} catch(_: Exception){if(stamp==generation)loading.text="播放器初始化失败，请返回重试。"}
        }
    }
    private fun makeControls(item: BaseItemDto) {
        val panel=FrameLayout(this);panel.background=ui.surface(0xef10172b.toInt(),false);controls=panel;ui.at(page,panel,32f,374f,896f,134f)
        ui.title(panel,item.name ?: "媒体播放",20f,10f,645f,32f)
        clock=ui.text(panel,"00:00 / --:--",14,TvUi.MUTED,684f,10f,195f,32f)
        val progress=SeekBar(this);seek=progress;progress.max=10000;progress.keyProgressIncrement=100
        ui.at(panel,progress,14f,47f,866f,23f)
        progress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,p: Int,fromUser: Boolean){if(fromUser){player?.let {if(it.duration>0)it.seekTo(it.duration*p/10000)};showControls(false)}}
            override fun onStartTrackingTouch(bar: SeekBar?){handler.removeCallbacks(hideControls)}
            override fun onStopTrackingTouch(bar: SeekBar?){showControls(false)}
        })
        progress.setOnKeyListener {_,key,event ->if(event.action==KeyEvent.ACTION_DOWN && (key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){player?.let {it.seekTo((it.currentPosition+if(key==KeyEvent.KEYCODE_DPAD_RIGHT)10000 else -10000).coerceAtLeast(0))};showControls(false);true}else false}
        pause=ui.button(panel,"▷ 播放",20f,77f,110f,39f) {player?.let {if(it.isPlaying)it.pause()else it.play()};showControls(false)}
        ui.button(panel,"音轨",145f,77f,100f,39f) {tracks(C.TRACK_TYPE_AUDIO)}
        ui.button(panel,"字幕",260f,77f,100f,39f) {tracks(C.TRACK_TYPE_TEXT)}
        if(item.type==BaseItemKind.EPISODE)ui.button(panel,"选集",375f,77f,100f,39f) {
            val list=seriesEpisodes
            if(list.isNotEmpty())choose("选集",list.map {"第 ${it.indexNumber ?: "?"} 集 · ${it.name.orEmpty()}"},list.indexOfFirst {it.id==item.id}) {i ->stopPlayer();backToSaved();play(list[i],resumeMs(list[i]))}
            else item.seriesId?.let {id ->lifecycleScope.launch {try {val episodes=repo.episodes(id);seriesEpisodes=episodes;choose("选集",episodes.map {"第 ${it.indexNumber ?: "?"} 集"},-1){i->stopPlayer();backToSaved();play(episodes[i],resumeMs(episodes[i]))}}catch(_:Exception){Toast.makeText(this@JellyLibraryActivity,"暂时无法加载剧集",Toast.LENGTH_SHORT).show()}}}
        }
        ui.text(panel,"左右调整进度 · 返回隐藏",12,TvUi.MUTED,616f,83f,260f,28f)
        focus(pause!!)
    }
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
    private fun showControls(focusButton: Boolean=true) {controls?.visibility=View.VISIBLE;if(focusButton)pause?.requestFocus();handler.removeCallbacks(hideControls);if(player?.isPlaying==true)handler.postDelayed(hideControls,6000)}
    private fun stopPlayer() {
        handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls)
        val p=player
        if(p!=null){playingItem?.let {item ->rememberPosition(item,p.currentPosition);if(reportStarted)stream?.let {reporting.reportStopped(this,item,it,p.currentPosition*10000)}};videoSurface?.player=null;p.release()}
        player=null;stream=null;playingItem=null;controls=null;seek=null;clock=null;pause=null;reportStarted=false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun choose(title: String,labels: List<String>,selected: Int,chosen: (Int)->Unit) {
        val previous=currentFocus
        handler.removeCallbacks(hideControls)
        val d=AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected){dlg,index ->dlg.dismiss();chosen(index)}.setNegativeButton("取消",null).create()
        dialog=d;d.setOnDismissListener {dialog=null;previous?.takeIf {it.isAttachedToWindow}?.requestFocus()};d.show()
        d.listView?.setSelector(ui.focusBackground(TvUi.TILE));d.window?.setBackgroundDrawable(ui.surface(TvUi.PANEL,false))
    }
    private fun backToSaved() {
        task?.cancel();searchJob?.cancel();generation++
        val saved=history.removeLastOrNull()
        if(saved==null){finish();return}
        page=saved.view;pageName=saved.name;setContentView(page);page.findViewById<View>(saved.focus)?.requestFocus()
    }
    private fun back() {
        if(dialog!=null){dialog?.dismiss();return}
        if(pageName=="player") {if(controls?.visibility==View.VISIBLE){controls?.visibility=View.GONE;return};stopPlayer();backToSaved();return}
        if(history.isEmpty())finish()else backToSaved()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.action==KeyEvent.ACTION_DOWN)interaction++
        if(pageName=="player" && event.action==KeyEvent.ACTION_DOWN && dialog==null) {
            when(event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,KeyEvent.KEYCODE_MEDIA_PAUSE,KeyEvent.KEYCODE_MEDIA_PLAY ->{player?.let {if(it.isPlaying)it.pause()else it.play()};showControls();return true}
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,KeyEvent.KEYCODE_MEDIA_REWIND ->{player?.let {it.seekTo((it.currentPosition+if(event.keyCode==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)30000 else -10000).coerceAtLeast(0))};showControls();return true}
                KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER ->if(controls?.visibility!=View.VISIBLE){showControls();return true}
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onStart(){super.onStart();if(player!=null){handler.removeCallbacks(updatePlayer);handler.post(updatePlayer)}}
    override fun onStop(){
        player?.pause();handler.removeCallbacks(updatePlayer);handler.removeCallbacks(hideControls)
        val p=player;val item=playingItem;val info=stream
        if(p!=null && item!=null){rememberPosition(item,p.currentPosition);if(info!=null && reportStarted)reporting.reportProgress(this,null,item,info,p.currentPosition*10000,true)}
        super.onStop()
    }
    override fun onDestroy(){stopPlayer();task?.cancel();searchJob?.cancel();dialog?.dismiss();super.onDestroy()}
    private fun rememberPosition(item: BaseItemDto,position: Long){local.edit().putLong(positionKey(item),position).putLong("seen_${positionKey(item)}",System.currentTimeMillis()).apply()}
    private fun positionKey(item: BaseItemDto)="pos_${repo.sessions.currentSession.value?.serverId}_${repo.sessions.currentSession.value?.userId}_${item.id}"
    private fun resumeMs(item: BaseItemDto): Long {
        val server=(item.userData?.playbackPositionTicks ?: 0)/10000
        val seen=local.getLong("seen_${positionKey(item)}",0)
        return if(seen>0 && seen>=(item.userData?.lastPlayedDate?.toInstant(java.time.ZoneOffset.UTC)?.toEpochMilli() ?: 0))local.getLong(positionKey(item),server)else server
    }
    private fun meta(item: BaseItemDto): String = listOfNotNull(LibraryMetadata.year(item)?.toString(),LibraryMetadata.rating(item)?.let {"评分 $it"},item.genres?.take(2)?.joinToString(" / ")?.takeIf {it.isNotEmpty()},item.runTimeTicks?.let {"${it/10000/60000} 分钟"}).joinToString(" · ")
    private fun time(ms: Long): String {val secs=(ms.coerceAtLeast(0)/1000);return if(secs>=3600) "%d:%02d:%02d".format(secs/3600,secs/60%60,secs%60) else "%02d:%02d".format(secs/60,secs%60)}
}
