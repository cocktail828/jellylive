package org.jellyfin.androidtv.bestv

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

/** Public CCTV catalogue; local state is independent of media-server and live favourites. */
class CctvActivity:FragmentActivity() {
    private val repo=CctvRepository()
    private lateinit var ui:TvUi
    private lateinit var store:CctvStore
    private lateinit var host:FrameLayout
    private lateinit var browser:TvMediaGrid
    private val sections=listOf("电视剧","动画片","纪录片","特别节目","观看记录","收藏")
    private val preferences by lazy {getSharedPreferences("jellylive_cctv",MODE_PRIVATE)}
    private var section=0
    private var state:CctvState?=null
    private var stateError:String?=null
    private var task:Job?=null
    private var generation=0
    private var loading=false
    private var nextPage=1
    private var more=true
    private var albums=emptyList<CctvAlbum>()
    private var detail:CctvAlbum?=null
    private var detailEpisodes=emptyList<CctvEpisode>()
    private var episodesLoaded=false
    private var rangeButton:Button?=null
    private var detailPlay:Button?=null
    private var detailContent:TvMediaDetail?=null
    private var detailError:String?=null
    private var interaction=0
    private var focusEpisodes=false
    private var group=0
    private var dialog:AlertDialog?=null
    private val filters=mutableMapOf<Int,MutableMap<String,String>>()
    private val positions=mutableMapOf<Int,TvMediaGrid.Position>()
    private val cached=mutableMapOf<Int,List<CctvAlbum>>()
    private val pageNumbers=mutableMapOf<Int,Int>()
    private val hasMore=mutableMapOf<Int,Boolean>()
    private val oldest=mutableMapOf<Int,Boolean>()
    private val loadedQueries=mutableMapOf<Int,Map<String,String>>()
    private data class Facet(val label:String,val key:String,val values:List<String>)
    private val facets=mapOf("电视剧" to listOf(Facet("类型","sc",listOf("谍战","悬疑","刑侦","历史","古装","武侠","军旅","战争","喜剧","青春","言情","偶像","家庭","年代","革命","农村","都市","其他")),Facet("地区","area",listOf("内地（大陆）","港澳台","欧美","其他")),Facet("年份","year",(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 1997).map {it.toString()})),"动画片" to listOf(Facet("类型","sc",listOf("亲子","搞笑","冒险","动作","宠物","体育","益智","历史","教育","校园","言情","武侠","经典","未来","古代","神话","真人","励志","热血","奇幻","童话","剧情","夺宝","其他")),Facet("地区","area",listOf("内地（大陆）","港澳台","欧美"))),"纪录片" to listOf(Facet("类型","sc",listOf("人文历史","人物","军事","探索","社会","自然","时政","经济","科技")),Facet("频道","channel",listOf("CCTV-1综合,CCTV-1高清,CCTV-1综合高清","CCTV-2财经,CCTV-2高清,CCTV-2财经高清","CCTV-3综艺,CCTV-3综艺高清","CCTV-4中文国际,CCTV-4高清,CCTV-4中文国际(亚)高清","CCTV-5体育,CCTV-5体育高清","CCTV-6电影,CCTV-6电影高清","CCTV-7军事农业,CCTV-7军事农业高清，CCTV-7国防军事高清","CCTV-9纪录,CCTV-9高清,CCTV-9纪录高清","CCTV-10科教,CCTV-10高清,CCTV-10科教高清","CCTV-11戏曲","CCTV-12社会与法,CCTV-12社会与法高清","CCTV-13新闻","CCTV-14少儿,CCTV-14少儿高清","CCTV-15音乐,CCTV-15音乐高清","CCTV-17农业农村高清")),Facet("年份","year",(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 2008).map {it.toString()})),"特别节目" to listOf(Facet("类型","sc",listOf("新闻","经济","综艺","体育","军事","影视","科教","戏曲","青少","音乐","社会","文化","公益","其他")),Facet("频道","channel",listOf("CCTV-1综合,CCTV-1高清,CCTV-1综合高清","CCTV-2财经,CCTV-2高清,CCTV-2财经高清","CCTV-3综艺,CCTV-3高清,CCTV-3综艺高清","CCTV-4中文国际,CCTV-4高清,CCTV-4中文国际(亚)高清,CCTV-4中文国际(亚)","CCTV-5体育,CCTV-5高清,CCTV-5体育高清","CCTV-6电影,CCTV-6高清,CCTV-6电影高清","CCTV-7军事农业,CCTV-7高清,CCTV-7军事农业高清,CCTV-7国防军事高清","CCTV-8电视剧,CCTV-8高清,CCTV-8电视剧高清","CCTV-9纪录,CCTV-9高清,CCTV-9纪录高清","CCTV-10科教,CCTV-10高清,CCTV-10科教高清","CCTV-11戏曲,CCTV-11高清,CCTV-11戏曲高清","CCTV-12社会与法,CCTV-12高清,CCTV-12社会与法高清","CCTV-13新闻,CCTV-13高清,CCTV-13新闻高清","CCTV-14少儿,CCTV-14高清,CCTV-14少儿高清","CCTV-15音乐,CCTV-15高清,CCTV-15音乐高清","CCTV-17农业农村高清")),Facet("节日","bigday",listOf("春节","元宵","清明","劳动节","青年节","六一","端午","七夕","教师节","中秋","国庆","重阳","开学第一课","谜语大会","成语大会","诗词大会","汉字听写大会","民歌大会","寻找最美医生","寻找最美教师"))))
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);ui=TvUi(this);store=CctvStore(this);section=preferences.getInt("section",0).coerceIn(0,5);window.decorView.systemUiVisibility=5894
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){
            if(dialog?.isShowing==true){dialog?.dismiss();return}
            if(detail!=null&&currentFocus?.tag?.toString()?.startsWith("cctv-episode-")==true){rangeButton?.requestFocus();return}
            if(detail!=null&&currentFocus===rangeButton){detailPlay?.takeIf {it.isEnabled}?.requestFocus();return}
            if(detail!=null){task?.cancel();generation++;loading=false;detail=null;detailContent=null;showBrowser();return}
            finish()
        }})
        sections.indices.forEach {i->oldest[i]=preferences.getBoolean("oldest_$i",false);val prefix="browse_$i";if(preferences.contains(prefix+"_index"))positions[i]=TvMediaGrid.Position(preferences.getString(prefix+"_id",null),preferences.getInt(prefix+"_index",0),preferences.getInt(prefix+"_offset",0));val raw=preferences.getString(prefix+"_filters",null);if(raw!=null)runCatching {val j=org.json.JSONObject(raw);val f=mutableMapOf<String,String>();j.keys().forEach {key->f[key]=j.getString(key)};filters[i]=f}}
        readState();showBrowser()
    }
    override fun dispatchKeyEvent(event:android.view.KeyEvent):Boolean {if(event.action==android.view.KeyEvent.ACTION_DOWN)interaction++;return super.dispatchKeyEvent(event)}
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)interaction++;return super.dispatchTouchEvent(event)}
    override fun onResume(){super.onResume();if(::store.isInitialized){readState();detail?.let {showDetail(it,false)}?:run {if(::browser.isInitialized&&section>=4)render()}}}
    override fun onPause(){remember();super.onPause()}
    override fun onDestroy(){task?.cancel();dialog?.dismiss();super.onDestroy()}
    private fun readState(){try{state=store.read();stateError=null}catch(e:Exception){stateError=e.message?:"本机记录读取失败"}}
    private fun remember(){if(::browser.isInitialized&&detail==null){positions[section]=browser.position();cached[section]=albums;pageNumbers[section]=nextPage;hasMore[section]=more;val pos=positions.getValue(section);val prefix="browse_$section";preferences.edit().putString(prefix+"_id",pos.id).putInt(prefix+"_index",pos.index).putInt(prefix+"_offset",pos.offset).putString(prefix+"_filters",org.json.JSONObject(values().toMap()).toString()).apply()}}
    private fun select(index:Int){remember();detail=null;task?.cancel();generation++;loading=false;section=index;preferences.edit().putInt("section",index).apply();albums=cached[index].orEmpty();nextPage=pageNumbers[index]?:1;more=hasMore[index]?:true;showBrowser()}
    private fun showBrowser(){detailContent=null;host=ui.canvas();setContentView(host);browser=TvMediaGrid(ui,host,"央视频",sections,section,::select);controls();browser.onNearEnd={if(section<4&&more&&!loading)load(false)}
        if(section>=4){render();positions[section]?.let(browser::restore);return};if(albums.isEmpty())load(true)else {render();positions[section]?.let(browser::restore)}
    }
    private fun values()=filters.getOrPut(section){mutableMapOf()}
    private fun choice(title:String,key:String,options:List<String>){val all=listOf("")+options;val selected=all.indexOf(values()[key].orEmpty()).coerceAtLeast(0);dialog=TvMediaGrid.choices(ui,title,all.map {it.ifBlank {"全部"}.substringBefore(',')},selected){i->remember();if(i==0)values().remove(key)else values()[key]=all[i];leaveDetail();if(section<4)load(true)else render();controls()}}
    private fun chooseLetter()=choice("首字母","letter",('A'..'Z').map(Char::toString))
    private fun controls(){
        val facets=if(section<4)facets.getValue(sections[section])else listOf(Facet("分类","category",sections.take(4)),Facet("进度","progress",listOf("未看","在看","已看")))
        browser.search.text="首字母：${values()["letter"]?:"全部"}";browser.search.setOnClickListener {chooseLetter()}
        browser.filter.text=if(values().isEmpty())"筛选：全部" else "筛选：${values().size}项"
        browser.filter.setOnClickListener {
            dialog=TvMediaGrid.choices(ui,"筛选",facets.map {"${it.label}：${values()[it.key]?.substringBefore(',')?:"全部"}"}+"清除筛选",-1){index->
                if(index==facets.size){remember();values().clear();if(section<4)load(true)else render();controls()}
                else facets[index].let {choice(it.label,it.key,it.values)}
            }
        }
        browser.sort.text=if(section<4)"刷新" else (if(oldest[section]==true)"最早"else "最近")+(if(section==4)"观看"else "收藏")
        browser.sort.setOnClickListener {if(section<4)load(true)else {val noun=if(section==4)"观看"else "收藏";dialog=TvMediaGrid.choices(ui,"排序",listOf("最近$noun","最早$noun"),if(oldest[section]==true)1 else 0){i->oldest[section]=i==1;preferences.edit().putBoolean("oldest_$section",i==1).apply();render();controls()}}}
    }
    private fun leaveDetail(){if(detail!=null){detail=null;showBrowser()}}
    private fun load(refreshRequested:Boolean){val refresh=refreshRequested||CctvBrowseCursor(loadedQueries[section],nextPage).page(values())==1;if(loading&&!refresh)return;if(refresh){task?.cancel();loading=false};val stamp=++generation;val category=sections[section];val page=if(refresh)1 else nextPage;val query=values().toMap();val keepRefreshFocus=refresh&&currentFocus===browser.sort;val input=interaction;loading=true;if(albums.isEmpty())browser.loading()else browser.busy(true)
        task=lifecycleScope.launch {try {val response=repo.albums(category,page,query);if(stamp!=generation)return@launch;val old=albums;val reset=loadedQueries[section]!=query;albums=if(refresh)response.items else (albums+response.items).distinctBy {it.id};if(!refresh&&response.items.isNotEmpty()&&albums.size==old.size)error("节目分页未前进，请重试");more=response.items.size>=30&&(response.total<=0||albums.size<response.total);nextPage=page+1;loadedQueries[section]=query;loading=false;render(reset);if(old.isEmpty()){positions[section]?.takeIf {!reset}?.let(browser::restore)?:browser.focusFirst()};if(keepRefreshFocus&&input==interaction)browser.sort.requestFocus()}catch(e:CancellationException){throw e}catch(e:Exception){if(stamp!=generation)return@launch;loading=false;browser.busy(false);if(albums.isEmpty())browser.state("节目加载失败",e.message?:"请稍后重试",TvMediaGrid.Action("重试"){load(refresh)})else browser.retry("加载失败",TvMediaGrid.Action("重试"){load(refresh)})}}
    }
    private fun render(reset:Boolean=false){if(section>=4){if(stateError!=null){browser.state("本机记录读取失败",stateError!!,TvMediaGrid.Action("重试"){readState();render()});return};val recent=if(section==4)state?.history().orEmpty()else state?.favorites().orEmpty();val entries=if(oldest[section]==true)recent.reversed()else recent;val f=values();albums=entries.filter {e->(f["category"]==null||e.album.category==f["category"])&&(f["progress"]==null||f["progress"]==when{e.watchedAt==0L->"未看";e.completed->"已看";else->"在看"})&&(f["letter"]==null||initial(e.album.title)==f["letter"])}.map {it.album}}
        if(albums.isEmpty()){val filtered=section>=4&&values().isNotEmpty();browser.state(if(filtered)"暂无符合条件的节目"else if(section==4)"暂无观看记录" else if(section==5)"暂无收藏" else "暂无符合条件的节目",if(filtered)"可清除筛选条件后重试"else if(section>=4)"浏览节目后可在这里查看本机记录"else "可更换筛选条件后重试",TvMediaGrid.Action(if(section>=4&&!filtered)"浏览电视剧"else "清除筛选"){if(section>=4&&!filtered)select(0)else {values().clear();controls();if(section>=4)render()else load(true)}});return}
        browser.subtitle.text="${sections[section]} · 已加载 ${albums.size} 项"
        browser.show(albums.map {a->val e=state?.entries?.get(a.id);TvMediaCard.Data(a.id,displayTitle(a),cardMetadata(a),a.imageUrl.takeIf(String::isNotBlank),played=e?.completed==true,progress=e?.takeIf {it.watchedAt>0&&it.duration>0}?.let {it.position.toDouble()/it.duration})},!reset){card->remember();albums.firstOrNull {it.id==card.id}?.let {showDetail(it,true)}}
    }
    private fun initial(title:String):String {val first=title.dropWhile {it.isWhitespace()||it in "《》〈〉【】[]“”\"‘’·"}.firstOrNull()?.toString().orEmpty();if(first.matches(Regex("[a-zA-Z]")))return first.uppercase();val bytes=first.toByteArray(charset("GB2312"));if(bytes.size<2)return "";val value=(bytes[0].toInt()and 255)*256+(bytes[1].toInt()and 255)-65536;val bounds=intArrayOf(-20319,-20284,-19776,-19219,-18711,-18527,-18240,-17923,-17418,-16475,-16213,-15641,-15166,-14923,-14915,-14631,-14150,-14091,-13319,-12839,-12557,-11848,-11056);val letters="ABCDEFGHJKLMNOPQRSTWXYZ";return bounds.indices.lastOrNull {value>=bounds[it]}?.let {letters[it].toString()}.orEmpty()}
    private fun displayTitle(a:CctvAlbum)=a.title.removeSurrounding("《","》")
    private fun countText(a:CctvAlbum)=a.count?.let {"${it}${if(a.category=="特别节目")"期"else "集"}"}
    private fun cardMetadata(a:CctvAlbum)=if(a.year!=null)listOfNotNull(a.year.toString(),countText(a)).joinToString(" · ")else listOfNotNull(countText(a),a.genre.takeIf(String::isNotBlank)).joinToString(" · ")
    private fun metadata(a:CctvAlbum)=listOfNotNull(a.year?.toString(),a.genre.takeIf(String::isNotBlank),a.channel.takeIf(String::isNotBlank)).joinToString(" · ")
    private fun showDetail(album:CctvAlbum,fetch:Boolean){
        val previous=currentFocus?.tag as? String
        if(detail==null){task?.cancel();generation++;loading=false}
        detail=album
        if(fetch){episodesLoaded=false;detailEpisodes=emptyList();detailError=null;rangeButton=null;group=0}
        host=ui.canvas();setContentView(host)
        val content=TvMediaDetail(ui,host,"央视频详情",displayTitle(album),null,metadata(album),countText(album)?.let {"收录$it"}.orEmpty(),album.brief,album.imageUrl.takeIf(String::isNotBlank))
        detailContent=content
        val record=state?.entries?.get(album.id);val resume=record?.episode?.takeIf {!record.completed}
        val play=content.action(if(resume!=null)"继续播放"else "播放节目"){
            val ep=resume?.let {saved->detailEpisodes.firstOrNull {it.guid==saved.guid}}?:detailEpisodes.firstOrNull()
            ep?.let {launch(album,it,if(it.guid==resume?.guid)record!!.resumePosition else 0)}
        }.apply {tag="cctv-play";isEnabled=episodesLoaded&&detailEpisodes.isNotEmpty()};detailPlay=play
        val restart=content.action("从头播放"){detailEpisodes.firstOrNull()?.let {launch(album,it,0)}}.apply {tag="cctv-restart";isEnabled=play.isEnabled}
        val favorite=content.action(if((record?.favoriteAt?:0)>0)"取消收藏"else "收藏","取消收藏"){
            try{state=store.favorite(album,(state?.entries?.get(album.id)?.favoriteAt?:0)==0L);showDetail(album,false);host.findViewWithTag<View>("cctv-favorite")?.requestFocus()}catch(_:Exception){Toast.makeText(this,"收藏未保存，请检查本机存储后重试",Toast.LENGTH_LONG).show()}
        }.apply {tag="cctv-favorite";isEnabled=stateError==null}
        val actions=listOf(play,restart,favorite)
        actions.forEachIndexed {i,b->b.nextFocusLeftId=actions[maxOf(0,i-1)].id;b.nextFocusRightId=actions[minOf(actions.lastIndex,i+1)].id;b.nextFocusUpId=b.id;b.nextFocusDownId=b.id}
        when {
            detailError!=null->detailState("暂时无法加载分集，请检查网络后重试。",album,actions)
            !episodesLoaded->ui.text(host,"正在加载选集…",14,TvUi.MUTED,48f,390f,864f,42f).apply {gravity=android.view.Gravity.CENTER}
            detailEpisodes.isEmpty()->detailState("来源暂未提供可播放分集，请稍后重试。",album,actions)
            else->drawEpisodes(album,actions)
        }
        val target=previous?.let {host.findViewWithTag<View>(it)}?.takeIf {it.isEnabled}?:if(play.isEnabled)play else favorite
        target.requestFocus()
        if(fetch)loadEpisodes(album)
    }
    private fun detailState(message:String,album:CctvAlbum,actions:List<Button>){
        ui.text(host,message,14,TvUi.MUTED,48f,370f,864f,42f).apply {gravity=android.view.Gravity.CENTER;maxLines=2}
        val retry=ui.button(host,"重试分集",390f,426f,180f,34f){loadEpisodes(album)}.apply {tag="cctv-retry"}
        actions.forEach {it.nextFocusDownId=retry.id};retry.nextFocusUpId=actions.firstOrNull {it.isEnabled}?.id?:retry.id;retry.nextFocusDownId=retry.id;retry.nextFocusLeftId=retry.id;retry.nextFocusRightId=retry.id
    }
    private fun loadEpisodes(album:CctvAlbum){
        task?.cancel();val stamp=++generation;val input=interaction;detailError=null
        task=lifecycleScope.launch {
            try{val result=repo.episodes(album.id);if(stamp!=generation||detail?.id!=album.id)return@launch;detailEpisodes=result;episodesLoaded=true;showDetail(album,false);if(input==interaction)detailPlay?.takeIf {it.isEnabled}?.requestFocus()}
            catch(e:CancellationException){throw e}
            catch(_:Exception){if(stamp==generation&&detail?.id==album.id){detailError="分集加载失败";showDetail(album,false)}}
        }
    }
    private fun drawEpisodes(album:CctvAlbum,actions:List<Button>){
        val chunks=detailEpisodes.chunked(20);group=group.coerceIn(0,chunks.lastIndex)
        val units=if(album.category=="特别节目")"期"else "集"
        val range=ui.button(host,"选$units：${group*20+1}–${minOf((group+1)*20,detailEpisodes.size)}",48f,354f,180f,28f){
            val names=chunks.indices.map {"${it*20+1}–${minOf((it+1)*20,detailEpisodes.size)}$units"}
            dialog=TvMediaGrid.choices(ui,"选择分${units}范围",names,group){i->group=i;focusEpisodes=true;showDetail(album,false)}
        }.apply {tag="cctv-range";isFocusableInTouchMode=true};rangeButton=range
        val episodes=chunks[group];val pages=LibraryDetails.pageCount(episodes.size,4)
        val indicator=ui.text(host,if(pages>1)"1 / $pages"else "",12,TvUi.MUTED,816f,354f,96f,28f).apply {gravity=android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL}
        val scroll=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false;isFocusable=false}
        val row=FrameLayout(this);scroll.addView(row,android.view.ViewGroup.LayoutParams(ui.px(pages*LibraryDetails.RAIL_WIDTH),ui.px(122f)));ui.at(host,scroll,48f,390f,864f,122f)
        val cards=episodes.mapIndexed {i,ep->
            val card=TvMediaCard.focus(ui,FrameLayout(this)){launch(album,ep,0)}.apply {tag="cctv-episode-${ep.guid}";contentDescription=ep.title}
            val box=LibraryDetails.episode(i);ui.at(row,card,box.x,box.y,box.width,box.height)
            TvMediaCard.image(ui,card,album.imageUrl.takeIf(String::isNotBlank),0f,0f,box.width,76f,true)
            ui.text(card,ep.title,13,TvUi.INK,8f,80f,box.width-16f,31f).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
            card
        }
        TvMediaRail.bind(ui,host,scroll,cards,4,listOf(range)){index->indicator.text=if(pages>1)"${index+1} / $pages"else ""}
        range.nextFocusUpId=actions.first().id;actions.forEach {it.nextFocusDownId=range.id}
        if(focusEpisodes){focusEpisodes=false;cards.firstOrNull()?.post {cards.first().requestFocus()}}
    }
    private fun launch(album:CctvAlbum,episode:CctvEpisode,position:Long){startActivity(Intent(this,JellyLibraryActivity::class.java).putExtra("cctv_album",album.encode()).putExtra("cctv_guid",episode.guid).putExtra("cctv_position",position))}
}
