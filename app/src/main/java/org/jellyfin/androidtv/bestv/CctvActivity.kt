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
    private lateinit var browser:TvMediaBrowser
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
    private var focusEpisodes=false
    private var group=0
    private var dialog:AlertDialog?=null
    private val filters=mutableMapOf<Int,MutableMap<String,String>>()
    private val positions=mutableMapOf<Int,TvMediaBrowser.Position>()
    private val cached=mutableMapOf<Int,List<CctvAlbum>>()
    private val pageNumbers=mutableMapOf<Int,Int>()
    private val hasMore=mutableMapOf<Int,Boolean>()
    private val oldest=mutableMapOf<Int,Boolean>()
    private val loadedQueries=mutableMapOf<Int,Map<String,String>>()
    private data class Facet(val label:String,val key:String,val values:List<String>)
    private val facets=mapOf("电视剧" to listOf(Facet("类型","sc",listOf("谍战","悬疑","刑侦","历史","古装","武侠","军旅","战争","喜剧","青春","言情","偶像","家庭","年代","革命","农村","都市","其他")),Facet("地区","area",listOf("内地（大陆）","港澳台","欧美","其他")),Facet("年份","year",(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 1997).map {it.toString()})),"动画片" to listOf(Facet("类型","sc",listOf("亲子","搞笑","冒险","动作","宠物","体育","益智","历史","教育","校园","言情","武侠","经典","未来","古代","神话","真人","励志","热血","奇幻","童话","剧情","夺宝","其他")),Facet("地区","area",listOf("内地（大陆）","港澳台","欧美"))),"纪录片" to listOf(Facet("类型","sc",listOf("人文历史","人物","军事","探索","社会","自然","时政","经济","科技")),Facet("频道","channel",listOf("CCTV-1综合,CCTV-1高清,CCTV-1综合高清","CCTV-2财经,CCTV-2高清,CCTV-2财经高清","CCTV-3综艺,CCTV-3综艺高清","CCTV-4中文国际,CCTV-4高清,CCTV-4中文国际(亚)高清","CCTV-5体育,CCTV-5体育高清","CCTV-6电影,CCTV-6电影高清","CCTV-7军事农业,CCTV-7军事农业高清，CCTV-7国防军事高清","CCTV-9纪录,CCTV-9高清,CCTV-9纪录高清","CCTV-10科教,CCTV-10高清,CCTV-10科教高清","CCTV-11戏曲","CCTV-12社会与法,CCTV-12社会与法高清","CCTV-13新闻","CCTV-14少儿,CCTV-14少儿高清","CCTV-15音乐,CCTV-15音乐高清","CCTV-17农业农村高清")),Facet("年份","year",(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo 2008).map {it.toString()})),"特别节目" to listOf(Facet("类型","sc",listOf("新闻","经济","综艺","体育","军事","影视","科教","戏曲","青少","音乐","社会","文化","公益","其他")),Facet("频道","channel",listOf("CCTV-1综合,CCTV-1高清,CCTV-1综合高清","CCTV-2财经,CCTV-2高清,CCTV-2财经高清","CCTV-3综艺,CCTV-3高清,CCTV-3综艺高清","CCTV-4中文国际,CCTV-4高清,CCTV-4中文国际(亚)高清,CCTV-4中文国际(亚)","CCTV-5体育,CCTV-5高清,CCTV-5体育高清","CCTV-6电影,CCTV-6高清,CCTV-6电影高清","CCTV-7军事农业,CCTV-7高清,CCTV-7军事农业高清,CCTV-7国防军事高清","CCTV-8电视剧,CCTV-8高清,CCTV-8电视剧高清","CCTV-9纪录,CCTV-9高清,CCTV-9纪录高清","CCTV-10科教,CCTV-10高清,CCTV-10科教高清","CCTV-11戏曲,CCTV-11高清,CCTV-11戏曲高清","CCTV-12社会与法,CCTV-12高清,CCTV-12社会与法高清","CCTV-13新闻,CCTV-13高清,CCTV-13新闻高清","CCTV-14少儿,CCTV-14高清,CCTV-14少儿高清","CCTV-15音乐,CCTV-15高清,CCTV-15音乐高清","CCTV-17农业农村高清")),Facet("节日","bigday",listOf("春节","元宵","清明","劳动节","青年节","六一","端午","七夕","教师节","中秋","国庆","重阳","开学第一课","谜语大会","成语大会","诗词大会","汉字听写大会","民歌大会","寻找最美医生","寻找最美教师"))))
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);ui=TvUi(this);store=CctvStore(this);section=preferences.getInt("section",0).coerceIn(0,5);window.decorView.systemUiVisibility=5894
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(dialog?.isShowing==true){dialog?.dismiss();return};if(detail!=null&&currentFocus?.tag=="cctv_episode"){rangeButton?.requestFocus();return};if(detail!=null){task?.cancel();generation++;loading=false;detail=null;showBrowser();return};finish()}})
        sections.indices.forEach {i->oldest[i]=preferences.getBoolean("oldest_$i",false);val prefix="browse_$i";if(preferences.contains(prefix+"_index"))positions[i]=TvMediaBrowser.Position(preferences.getString(prefix+"_id",null),preferences.getInt(prefix+"_index",0),preferences.getInt(prefix+"_offset",0));val raw=preferences.getString(prefix+"_filters",null);if(raw!=null)runCatching {val j=org.json.JSONObject(raw);val f=mutableMapOf<String,String>();j.keys().forEach {key->f[key]=j.getString(key)};filters[i]=f}}
        readState();showBrowser()
    }
    override fun onResume(){super.onResume();if(::store.isInitialized){readState();detail?.let {showDetail(it,false)}?:run {if(::browser.isInitialized&&section>=4)render()}}}
    override fun onPause(){remember();super.onPause()}
    override fun onDestroy(){task?.cancel();dialog?.dismiss();super.onDestroy()}
    private fun readState(){try{state=store.read();stateError=null}catch(e:Exception){stateError=e.message?:"本机记录读取失败"}}
    private fun remember(){if(::browser.isInitialized&&detail==null){positions[section]=browser.position();cached[section]=albums;pageNumbers[section]=nextPage;hasMore[section]=more;val pos=positions.getValue(section);val prefix="browse_$section";preferences.edit().putString(prefix+"_id",pos.id).putInt(prefix+"_index",pos.index).putInt(prefix+"_offset",pos.offset).putString(prefix+"_filters",org.json.JSONObject(values().toMap()).toString()).apply()}}
    private fun select(index:Int){remember();task?.cancel();generation++;loading=false;section=index;preferences.edit().putInt("section",index).apply();albums=cached[index].orEmpty();nextPage=pageNumbers[index]?:1;more=hasMore[index]?:true;showBrowser()}
    private fun showBrowser(){host=ui.canvas();setContentView(host);browser=TvMediaBrowser(ui,host,"央视频",sections,section,::select);controls();browser.onNearEnd={if(section<4&&more&&!loading)load(false)}
        if(section>=4){render();positions[section]?.let(browser::restore);return};if(albums.isEmpty())load(true)else {render();positions[section]?.let(browser::restore)}
    }
    private fun values()=filters.getOrPut(section){mutableMapOf()}
    private fun choice(title:String,key:String,options:List<String>){val all=listOf("")+options;val selected=all.indexOf(values()[key].orEmpty()).coerceAtLeast(0);dialog=TvMediaBrowser.choices(ui,title,all.map {it.ifBlank {"全部"}.substringBefore(',')},selected){i->remember();if(i==0)values().remove(key)else values()[key]=all[i];if(section<4)load(true)else render();controls()}}
    private fun chooseLetter(){
        val letters=listOf("")+('A'..'Z').map(Char::toString)
        val cell=if(ui.largeText())72f else 54f
        val row=if(ui.largeText())58f else 48f
        val panel=FrameLayout(this).apply {background=ui.surface(TvUi.PANEL,false)}
        val picker=AlertDialog.Builder(this).setTitle("首字母").setView(panel).create()
        val buttons=letters.mapIndexed {i,letter->ui.button(panel,(if(values()["letter"].orEmpty()==letter)"✓ "else "")+letter.ifBlank {"全部"},12f+(i%6)*cell,12f+(i/6)*row,cell-6f,row-6f){
            remember();if(letter.isBlank())values().remove("letter")else values()["letter"]=letter
            picker.dismiss();if(section<4)load(true)else render();controls()
        }}
        buttons.forEachIndexed {i,b->b.nextFocusLeftId=if(i%6==0)b.id else buttons[i-1].id;b.nextFocusRightId=if(i%6==5)b.id else buttons.getOrNull(i+1)?.id?:b.id;b.nextFocusUpId=buttons.getOrNull(i-6)?.id?:b.id;b.nextFocusDownId=buttons.getOrNull(i+6)?.id?:b.id}
        dialog=picker;picker.show();picker.window?.setLayout(ui.px(cell*6+48f),ui.px(row*5+90f));buttons[letters.indexOf(values()["letter"].orEmpty()).coerceAtLeast(0)].requestFocus()
    }
    private fun controls(){val f=if(section<4)facets.getValue(sections[section])else listOf(Facet("分类","category",sections.take(4)),Facet("进度","progress",listOf("未看","在看","已看")))
        browser.configureControls("首字母：${values()["letter"]?:"全部"}",f.map {"${it.label}：${values()[it.key]?.substringBefore(',')?:"全部"}"});browser.search.setOnClickListener {chooseLetter()};listOf(browser.genre,browser.year,browser.rating,browser.progress).forEachIndexed {i,b->f.getOrNull(i)?.let {v->b.setOnClickListener {choice(v.label,v.key,v.values)}}};browser.sort.text=if(section<4)"刷新" else (if(oldest[section]==true)"最早"else "最近")+(if(section==4)"观看"else "收藏");browser.sort.setOnClickListener {if(section<4)load(true)else {val noun=if(section==4)"观看"else "收藏";dialog=TvMediaBrowser.choices(ui,"排序",listOf("最近$noun","最早$noun"),if(oldest[section]==true)1 else 0){index->oldest[section]=index==1;preferences.edit().putBoolean("oldest_$section",index==1).apply();render();controls()}}}
    }
    private fun load(refreshRequested:Boolean){val refresh=refreshRequested||CctvBrowseCursor(loadedQueries[section],nextPage).page(values())==1;if(loading&&!refresh)return;if(refresh){task?.cancel();loading=false};val stamp=++generation;val category=sections[section];val page=if(refresh)1 else nextPage;val query=values().toMap();loading=true;if(albums.isEmpty())browser.loading()else browser.sort.isEnabled=false
        task=lifecycleScope.launch {try {val response=repo.albums(category,page,query);if(stamp!=generation)return@launch;val old=albums;albums=if(refresh)response.items else (albums+response.items).distinctBy {it.id};if(!refresh&&response.items.isNotEmpty()&&albums.size==old.size)error("节目分页未前进，请重试");more=response.items.size>=30;nextPage=page+1;loadedQueries[section]=query;loading=false;render();if(refresh&&old.isEmpty())positions[section]?.let(browser::restore)}catch(e:CancellationException){throw e}catch(e:Exception){if(stamp!=generation)return@launch;loading=false;browser.busy(false);if(albums.isEmpty())browser.state("节目加载失败",e.message?:"请稍后重试",TvMediaBrowser.Action("重试"){load(refresh)})else browser.retry("加载失败",TvMediaBrowser.Action("重试"){load(refresh)})}}
    }
    private fun render(){if(section>=4){if(stateError!=null){browser.state("本机记录读取失败",stateError!!,TvMediaBrowser.Action("重试"){readState();render()});return};val recent=if(section==4)state?.history().orEmpty()else state?.favorites().orEmpty();val entries=if(oldest[section]==true)recent.reversed()else recent;val f=values();albums=entries.filter {e->(f["category"]==null||e.album.category==f["category"])&&(f["progress"]==null||f["progress"]==when{e.watchedAt==0L->"未看";e.completed->"已看";else->"在看"})&&(f["letter"]==null||initial(e.album.title)==f["letter"])}.map {it.album}}
        if(albums.isEmpty()){val filtered=section>=4&&values().isNotEmpty();browser.state(if(filtered)"暂无符合条件的节目"else if(section==4)"暂无观看记录" else if(section==5)"暂无收藏" else "暂无符合条件的节目",if(filtered)"可清除筛选条件后重试"else if(section>=4)"浏览节目后可在这里查看本机记录"else "可更换筛选条件后重试",TvMediaBrowser.Action(if(section>=4&&!filtered)"浏览电视剧"else "清除筛选"){if(section>=4&&!filtered)select(0)else {values().clear();controls();if(section>=4)render()else load(true)}});return}
        browser.show(albums.map {a->val e=state?.entries?.get(a.id);TvMediaBrowser.Card(a.id,a.title,metadata(a),a.imageUrl.takeIf(String::isNotBlank),a.count?.let {"收录${it}${if(a.category=="特别节目")"期"else "集"}"},e?.takeIf {it.watchedAt>0&&it.duration>0}?.let {it.position.toDouble()/it.duration})},true){card->remember();albums.firstOrNull {it.id==card.id}?.let {showDetail(it,true)}}
    }
    private fun initial(title:String):String {val first=title.dropWhile {it.isWhitespace()||it in "《》〈〉【】[]“”\"‘’·"}.firstOrNull()?.toString().orEmpty();if(first.matches(Regex("[a-zA-Z]")))return first.uppercase();val bytes=first.toByteArray(charset("GB2312"));if(bytes.size<2)return "";val value=(bytes[0].toInt()and 255)*256+(bytes[1].toInt()and 255)-65536;val bounds=intArrayOf(-20319,-20284,-19776,-19219,-18711,-18527,-18240,-17923,-17418,-16475,-16213,-15641,-15166,-14923,-14915,-14631,-14150,-14091,-13319,-12839,-12557,-11848,-11056);val letters="ABCDEFGHJKLMNOPQRSTWXYZ";return bounds.indices.lastOrNull {value>=bounds[it]}?.let {letters[it].toString()}.orEmpty()}
    private fun metadata(a:CctvAlbum)=listOfNotNull(a.year?.toString(),a.count?.let {"收录${it}${if(a.category=="特别节目")"期"else "集"}"},a.genre.takeIf(String::isNotBlank),a.channel.takeIf(String::isNotBlank)).joinToString(" · ")
    private fun showDetail(album:CctvAlbum,fetch:Boolean){if(detail==null){task?.cancel();generation++;loading=false};detail=album;host=ui.canvas();setContentView(host);ui.text(host,album.title,28,TvUi.INK,40f,35f,870f,64f).apply {setTypeface(null,Typeface.BOLD);maxLines=2};ui.text(host,metadata(album),15,TvUi.MUTED,40f,105f,870f,38f)
        val description=ScrollView(this);val body=ui.text(album.brief.ifBlank {"暂无节目介绍"},16,TvUi.MUTED);body.setPadding(ui.px(8f),0,ui.px(8f),0);description.addView(body);description.isFocusable=true;ui.at(host,description,200f,149f,710f,98f)
        if(album.imageUrl.isNotBlank()){val image=org.jellyfin.androidtv.ui.AsyncImageView(this);image.scaleType=ImageView.ScaleType.CENTER_CROP;ui.at(host,image,40f,149f,135f,98f);image.load(url=album.imageUrl,placeholder=android.graphics.drawable.ColorDrawable(TvUi.PANEL),aspectRatio=2.0/3)}
        val e=state?.entries?.get(album.id);val resume=e?.episode?.takeIf {!e.completed};val play=ui.button(host,if(resume!=null)"继续播放 · ${resume.title}"else if(album.category=="特别节目")"播放节目"else "从第1集播放",40f,260f,585f,42f){val episode=resume?:detailEpisodes.firstOrNull();if(episode!=null)launch(album,episode,if(resume!=null)e!!.resumePosition else 0)else loadEpisodes(album)}
        val favorite=ui.button(host,if((e?.favoriteAt?:0)>0)"取消收藏"else "收藏",645f,260f,265f,42f){}
        favorite.setOnClickListener {try{state=store.favorite(album,(state?.entries?.get(album.id)?.favoriteAt?:0)==0L);favorite.text=if((state?.entries?.get(album.id)?.favoriteAt?:0)>0)"取消收藏"else "收藏"}catch(ex:Exception){Toast.makeText(this,ex.message,Toast.LENGTH_LONG).show()}}
        if(stateError!=null){favorite.isEnabled=false;ui.button(host,"本机记录读取失败 · 重试",40f,310f,870f,38f){readState();showDetail(album,false)}}
        if(fetch){episodesLoaded=false;detailEpisodes=emptyList();group=0;loadEpisodes(album)}else if(episodesLoaded)drawEpisodes(album)else {ui.button(host,"加载选集 · 重试",40f,360f,870f,42f){loadEpisodes(album)}};if(episodesLoaded&&detailEpisodes.isEmpty())play.isEnabled=false;play.requestFocus()
    }
    private fun loadEpisodes(album:CctvAlbum){task?.cancel();val stamp=++generation;task=lifecycleScope.launch {try{val result=repo.episodes(album.id);if(stamp!=generation||detail?.id!=album.id)return@launch;detailEpisodes=result;episodesLoaded=true;showDetail(album,false)}catch(e:CancellationException){throw e}catch(e:Exception){if(stamp==generation&&detail?.id==album.id){ui.button(host,"选集加载失败 · 重试",40f,360f,870f,42f){loadEpisodes(album)};Toast.makeText(this@CctvActivity,e.message,Toast.LENGTH_LONG).show()}}}}
    private fun drawEpisodes(album:CctvAlbum){val columns=if(ui.largeText())4 else 5;val size=columns*2;val chunks=detailEpisodes.chunked(size);group=group.coerceIn(0,chunks.lastIndex.coerceAtLeast(0));val range=ui.button(host,if(chunks.isEmpty())"暂无可播放选集"else "选集 ${group*size+1}–${(group*size+size).coerceAtMost(detailEpisodes.size)} / ${detailEpisodes.size}",40f,320f,870f,38f){dialog=TvMediaBrowser.choices(ui,"选择集数区间",chunks.indices.map {"${it*size+1}–${(it*size+size).coerceAtMost(detailEpisodes.size)}"},group){group=it;focusEpisodes=true;showDetail(album,false)}}
        range.isEnabled=chunks.isNotEmpty();rangeButton=range
        val buttons=chunks.getOrNull(group).orEmpty().mapIndexed {i,ep->ui.button(host,ep.title,40f+(i%columns)*(880f/columns),374f+(i/columns)*64f,880f/columns-10f,54f){launch(album,ep,0)}.apply {tag="cctv_episode";maxLines=2;nextFocusUpId=range.id}}
        buttons.forEachIndexed {i,b->b.nextFocusLeftId=if(i%columns==0)b.id else buttons[i-1].id;b.nextFocusRightId=if(i%columns==columns-1)b.id else buttons.getOrNull(i+1)?.id?:b.id;b.nextFocusDownId=buttons.getOrNull(i+columns)?.id?:b.id;if(i>=columns)b.nextFocusUpId=buttons[i-columns].id};range.nextFocusDownId=buttons.firstOrNull()?.id?:range.id;if(focusEpisodes){focusEpisodes=false;buttons.firstOrNull()?.post {buttons.firstOrNull()?.requestFocus()}}
    }
    private fun launch(album:CctvAlbum,episode:CctvEpisode,position:Long){startActivity(Intent(this,JellyLibraryActivity::class.java).putExtra("cctv_album",album.encode()).putExtra("cctv_guid",episode.guid).putExtra("cctv_episode_title",episode.title).putExtra("cctv_part",episode.part).putExtra("cctv_position",position))}
}
