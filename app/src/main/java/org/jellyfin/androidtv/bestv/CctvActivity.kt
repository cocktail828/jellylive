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
    private var detailPlay:Button?=null
    private var detailError:String?=null
    private var interaction=0
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
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);ui=TvUi(this,true);store=CctvStore(this);section=preferences.getInt("section",0).coerceIn(0,5);window.decorView.systemUiVisibility=5894
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){if(dialog?.isShowing==true){dialog?.dismiss();return};if(detail!=null&&currentFocus?.tag?.toString()?.startsWith("cctv-episode-")==true){rangeButton?.requestFocus();return};if(detail!=null&&currentFocus?.tag?.toString()?.startsWith("cctv-range-")==true){detailPlay?.requestFocus();return};if(detail!=null){task?.cancel();generation++;loading=false;detail=null;showBrowser();return};finish()}})
        sections.indices.forEach {i->oldest[i]=preferences.getBoolean("oldest_$i",false);val prefix="browse_$i";if(preferences.contains(prefix+"_index"))positions[i]=TvMediaBrowser.Position(preferences.getString(prefix+"_id",null),preferences.getInt(prefix+"_index",0),preferences.getInt(prefix+"_offset",0));val raw=preferences.getString(prefix+"_filters",null);if(raw!=null)runCatching {val j=org.json.JSONObject(raw);val f=mutableMapOf<String,String>();j.keys().forEach {key->f[key]=j.getString(key)};filters[i]=f}}
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
    private fun showBrowser(){host=ui.canvas();setContentView(host);browser=TvMediaBrowser(ui,host,"央视频",sections,section,::select);controls();browser.onNearEnd={if(section<4&&more&&!loading)load(false)}
        if(section>=4){render();positions[section]?.let(browser::restore);return};if(albums.isEmpty())load(true)else {render();positions[section]?.let(browser::restore)}
    }
    private fun values()=filters.getOrPut(section){mutableMapOf()}
    private fun choice(title:String,key:String,options:List<String>){val all=listOf("")+options;val selected=all.indexOf(values()[key].orEmpty()).coerceAtLeast(0);dialog=TvMediaBrowser.choices(ui,title,all.map {it.ifBlank {"全部"}.substringBefore(',')},selected){i->remember();if(i==0)values().remove(key)else values()[key]=all[i];leaveDetail();if(section<4)load(true)else render();controls()}}
    private fun chooseLetter(){
        val anchor=currentFocus;val letters=listOf("")+('A'..'Z').map(Char::toString)
        val cell=if(ui.largeText())56f else 42f;val size=cell-6f
        val panel=FrameLayout(this).apply {background=TvBrowserStyle.surface(ui,0xff252633.toInt())}
        ui.heading(panel,"首字母",14,12f,6f,cell*6,26f)
        val picker=AlertDialog.Builder(this).setView(panel).create()
        val buttons=letters.mapIndexed {i,letter->
            ui.button(panel,letter.ifBlank {"全部"},12f+i%6*cell,36f+i/6*cell,size,size){
                remember();if(letter.isBlank())values().remove("letter")else values()["letter"]=letter
                picker.dismiss();leaveDetail();if(section<4)load(true)else render();controls()
            }.apply {TvBrowserStyle.button(ui,this,selected=values()["letter"].orEmpty()==letter,center=true);setPadding(ui.px(3f),0,ui.px(3f),0)}
        }
        buttons.forEachIndexed {i,b->b.nextFocusLeftId=if(i%6==0)b.id else buttons[i-1].id;b.nextFocusRightId=if(i%6==5)b.id else buttons.getOrNull(i+1)?.id?:b.id;b.nextFocusUpId=buttons.getOrNull(i-6)?.id?:b.id;b.nextFocusDownId=buttons.getOrNull(i+6)?.id?:b.id}
        dialog=picker;picker.setOnDismissListener {anchor?.takeIf {it.isAttachedToWindow}?.requestFocus()};picker.show()
        picker.window?.apply {setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));setDimAmount(0f);setGravity(android.view.Gravity.TOP or android.view.Gravity.START);setLayout(ui.px(cell*6+28f),ui.px(cell*5+54f));attributes=attributes.apply {x=ui.px(200f);y=ui.px(76f)}}
        buttons[letters.indexOf(values()["letter"].orEmpty()).coerceAtLeast(0)].requestFocus()
    }
    private fun controls(target:TvMediaBrowser=browser){val f=if(section<4)facets.getValue(sections[section])else listOf(Facet("分类","category",sections.take(4)),Facet("进度","progress",listOf("未看","在看","已看")))
        target.configureControls("首字母：${values()["letter"]?:"全部"}",f.map {"${it.label}：${values()[it.key]?.substringBefore(',')?:"全部"}"});TvBrowserStyle.button(ui,target.search,"search",down=true);target.controlsSelected(f.map {values().containsKey(it.key)});target.clear.visibility=if(values().isNotEmpty())View.VISIBLE else View.GONE;target.clear.setOnClickListener {values().clear();leaveDetail();if(section<4)load(true)else render();controls();browser.search.requestFocus()};if(section<4)target.refreshControl();target.search.setOnClickListener {chooseLetter()};listOf(target.genre,target.year,target.rating,target.progress).forEachIndexed {i,b->f.getOrNull(i)?.let {v->b.setOnClickListener {choice(v.label,v.key,v.values)}}};target.sort.text=if(section<4)"刷新" else (if(oldest[section]==true)"最早"else "最近")+(if(section==4)"观看"else "收藏");target.sort.setOnClickListener {if(section<4){leaveDetail();load(true)}else {val noun=if(section==4)"观看"else "收藏";dialog=TvMediaBrowser.choices(ui,"排序",listOf("最近$noun","最早$noun"),if(oldest[section]==true)1 else 0){index->oldest[section]=index==1;preferences.edit().putBoolean("oldest_$section",index==1).apply();render();controls()}}}
    }
    private fun leaveDetail(){if(detail!=null){detail=null;showBrowser()}}
    private fun load(refreshRequested:Boolean){val refresh=refreshRequested||CctvBrowseCursor(loadedQueries[section],nextPage).page(values())==1;if(loading&&!refresh)return;if(refresh){task?.cancel();loading=false};val stamp=++generation;val category=sections[section];val page=if(refresh)1 else nextPage;val query=values().toMap();val keepRefreshFocus=refresh&&currentFocus===browser.sort;val input=interaction;loading=true;if(albums.isEmpty())browser.loading()else browser.sort.isEnabled=false
        task=lifecycleScope.launch {try {val response=repo.albums(category,page,query);if(stamp!=generation)return@launch;val old=albums;val reset=loadedQueries[section]!=query;albums=if(refresh)response.items else (albums+response.items).distinctBy {it.id};if(!refresh&&response.items.isNotEmpty()&&albums.size==old.size)error("节目分页未前进，请重试");more=response.items.size>=30;nextPage=page+1;loadedQueries[section]=query;loading=false;render(reset);if(old.isEmpty()){positions[section]?.takeIf {!reset}?.let(browser::restore)?:browser.focusFirst()};if(keepRefreshFocus&&input==interaction)browser.sort.requestFocus()}catch(e:CancellationException){throw e}catch(e:Exception){if(stamp!=generation)return@launch;loading=false;browser.busy(false);if(albums.isEmpty())browser.state("节目加载失败",e.message?:"请稍后重试",TvMediaBrowser.Action("重试"){load(refresh)})else browser.retry("加载失败",TvMediaBrowser.Action("重试"){load(refresh)})}}
    }
    private fun render(reset:Boolean=false){if(section>=4){if(stateError!=null){browser.state("本机记录读取失败",stateError!!,TvMediaBrowser.Action("重试"){readState();render()});return};val recent=if(section==4)state?.history().orEmpty()else state?.favorites().orEmpty();val entries=if(oldest[section]==true)recent.reversed()else recent;val f=values();albums=entries.filter {e->(f["category"]==null||e.album.category==f["category"])&&(f["progress"]==null||f["progress"]==when{e.watchedAt==0L->"未看";e.completed->"已看";else->"在看"})&&(f["letter"]==null||initial(e.album.title)==f["letter"])}.map {it.album}}
        if(albums.isEmpty()){val filtered=section>=4&&values().isNotEmpty();browser.state(if(filtered)"暂无符合条件的节目"else if(section==4)"暂无观看记录" else if(section==5)"暂无收藏" else "暂无符合条件的节目",if(filtered)"可清除筛选条件后重试"else if(section>=4)"浏览节目后可在这里查看本机记录"else "可更换筛选条件后重试",TvMediaBrowser.Action(if(section>=4&&!filtered)"浏览电视剧"else "清除筛选"){if(section>=4&&!filtered)select(0)else {values().clear();controls();if(section>=4)render()else load(true)}});return}
        browser.show(albums.map {a->val e=state?.entries?.get(a.id);TvMediaBrowser.Card(a.id,displayTitle(a),cardMetadata(a),a.imageUrl.takeIf(String::isNotBlank),a.count?.let {"${it}${if(a.category=="特别节目")"期"else "集"}"},e?.takeIf {it.watchedAt>0&&it.duration>0}?.let {it.position.toDouble()/it.duration})},!reset){card->remember();albums.firstOrNull {it.id==card.id}?.let {showDetail(it,true)}}
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
        val shell=TvMediaBrowser(ui,host,"央视频",sections,section,::select);controls(shell)
        val large=ui.largeText();val body=FrameLayout(this)
        val scroll=ScrollView(this).apply {isVerticalScrollBarEnabled=false;isFocusable=false;addView(body,android.view.ViewGroup.LayoutParams(ui.px(728f),ui.px(if(large)520f else 350f)))}
        shell.content(scroll)
        val record=state?.entries?.get(album.id)
        val resume=record?.episode?.takeIf {!record.completed}
        val artwork=FrameLayout(this).apply {background=TvBrowserStyle.surface(ui,TvUi.PANEL);clipToOutline=true}
        val artH=if(large)258f else 204f;val artW=if(large)172f else 136f
        ui.at(body,artwork,0f,0f,artW,artH)
        if(album.imageUrl.isNotBlank()){
            val image=org.jellyfin.androidtv.ui.AsyncImageView(this).apply {scaleType=ImageView.ScaleType.CENTER_CROP}
            ui.at(artwork,image,0f,0f,artW,artH);image.load(url=album.imageUrl,placeholder=android.graphics.drawable.ColorDrawable(TvUi.PANEL),aspectRatio=2.0/3)
        }
        countText(album)?.let {ui.text(artwork,it,11,TvBrowserStyle.INK,7f,7f,60f,19f).apply {gravity=android.view.Gravity.CENTER;background=TvBrowserStyle.surface(ui,0xff11151e.toInt())}}
        record?.takeIf {it.duration>0&&it.watchedAt>0}?.let {e->
            ui.at(artwork,View(this).apply {setBackgroundColor(0xff5b5968.toInt())},7f,artH-9f,artW-14f,3f)
            ui.at(artwork,View(this).apply {setBackgroundColor(0xffc3b0e7.toInt())},7f,artH-9f,(artW-14f)*(e.position.toDouble()/e.duration).coerceIn(0.0,1.0).toFloat(),3f)
        }
        ui.text(body,displayTitle(album),15,TvBrowserStyle.INK,0f,artH+8f,artW,30f).apply {gravity=android.view.Gravity.CENTER;setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END}
        ui.text(body,cardMetadata(album),12,TvBrowserStyle.MUTED,0f,artH+36f,artW,24f).gravity=android.view.Gravity.CENTER
        val x=if(large)200f else 172f;val width=728f-x
        ui.text(body,displayTitle(album),24,TvBrowserStyle.INK,x,0f,width,if(large)64f else 40f).apply {setTypeface(null,Typeface.BOLD);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END}
        ui.text(body,metadata(album),12,TvBrowserStyle.MUTED,x,if(large)67f else 39f,width,28f).apply {setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END}
        ui.text(body,countText(album)?.let {"收录$it"}.orEmpty(),12,TvBrowserStyle.MUTED,x,if(large)101f else 65f,width,24f)
        if(detailError!=null){
            ui.text(body,"暂时无法加载分集",20,TvBrowserStyle.INK,x,130f,width,45f).gravity=android.view.Gravity.CENTER
            ui.text(body,"节目资料已保留，请重试加载分集。",12,TvBrowserStyle.MUTED,x,180f,width,36f).gravity=android.view.Gravity.CENTER
            ui.button(body,"重试分集",x+20f,230f,220f,40f){loadEpisodes(album)}.apply {TvBrowserStyle.button(ui,this,center=true);requestFocus()}
            ui.button(body,"返回节目",x+256f,230f,220f,40f){detailError=null;showDetail(album,false)}.apply {TvBrowserStyle.button(ui,this,center=true)}
            return
        }
        ui.heading(body,"节目简介",14,x,if(large)139f else 96f,width,26f)
        val description=ui.text(body,album.brief.ifBlank {"暂无节目介绍"},13,TvBrowserStyle.MUTED,x,if(large)168f else 121f,width,if(large)44f else 36f).apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END;isFocusable=true;tag="cctv-description"}
        description.setOnClickListener {val text=ui.text(album.brief.ifBlank {"暂无节目介绍"},14,TvBrowserStyle.INK);val content=ScrollView(this).apply {setPadding(ui.px(16f),ui.px(12f),ui.px(16f),ui.px(12f));addView(text)};dialog=AlertDialog.Builder(this).setTitle("节目简介").setView(content).create().also {it.show();it.window?.setLayout(ui.px(700f),ui.px(380f))}}
        val actionY=if(large)222f else 157f
        fun action(label:String,tagName:String,left:Float,w:Float,click:()->Unit)=ui.button(body,label,left,actionY,w,36f,click).apply {tag=tagName;TvBrowserStyle.button(ui,this,center=true);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END}
        val play=action(if(resume!=null)"继续播放 · ${if(album.category=="特别节目")resume.title else "第${resume.part}集"}"else if(album.category=="特别节目")"播放节目"else "从第1集播放","cctv-play",x,if(large)176f else 184f){
            val episode=resume?.let {saved->detailEpisodes.firstOrNull {it.guid==saved.guid}} ?: detailEpisodes.firstOrNull()
            if(episode!=null)launch(album,episode,if(episode.guid==resume?.guid)record!!.resumePosition else 0)
        }
        detailPlay=play;shell.contentFocus(play);play.isEnabled=episodesLoaded&&detailEpisodes.isNotEmpty()
        val viewEpisodes=action("查看分集","cctv-view-episodes",x+(if(large)188f else 196f),if(large)158f else 152f){rangeButton?.requestFocus()}
        val favorite=action(if((record?.favoriteAt?:0)>0)"取消收藏"else "收藏","cctv-favorite",x+(if(large)358f else 360f),if(large)158f else 152f){
            try{state=store.favorite(album,(state?.entries?.get(album.id)?.favoriteAt?:0)==0L);showDetail(album,false);host.findViewWithTag<View>("cctv-favorite")?.requestFocus()}catch(e:Exception){Toast.makeText(this,e.message,Toast.LENGTH_LONG).show()}
        }
        favorite.isEnabled=stateError==null;description.nextFocusDownId=play.id
        listOf(play,viewEpisodes,favorite).forEachIndexed {i,b->b.nextFocusLeftId=listOf(play,viewEpisodes,favorite).getOrNull(i-1)?.id ?: host.findViewWithTag<View>("browser-nav-$section")!!.id;b.nextFocusRightId=listOf(play,viewEpisodes,favorite).getOrNull(i+1)?.id ?: b.id;b.nextFocusUpId=description.id}
        if(episodesLoaded)drawEpisodes(album,body,x,if(large)278f else 203f,listOf(play,viewEpisodes,favorite))
        else ui.text(body,"正在加载选集…",14,TvBrowserStyle.MUTED,x,if(large)290f else 220f,width,50f).gravity=android.view.Gravity.CENTER
        (previous?.let {host.findViewWithTag<View>(it)}?.takeIf {it.isEnabled} ?: if(play.isEnabled)play else favorite).requestFocus()
        if(fetch)loadEpisodes(album)
    }
    private fun loadEpisodes(album:CctvAlbum){
        task?.cancel();val stamp=++generation;val input=interaction;detailError=null
        task=lifecycleScope.launch {
            try{val result=repo.episodes(album.id);if(stamp!=generation||detail?.id!=album.id)return@launch;detailEpisodes=result;episodesLoaded=true;showDetail(album,false);if(input==interaction)detailPlay?.takeIf {it.isEnabled}?.requestFocus()}
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(stamp==generation&&detail?.id==album.id){detailError=e.message;showDetail(album,false)}}
        }
    }
    private fun drawEpisodes(album:CctvAlbum,body:FrameLayout,x:Float,y:Float,actions:List<Button>){
        val columns=if(ui.largeText())4 else 5;val size=columns*2;val chunks=detailEpisodes.chunked(size)
        group=group.coerceIn(0,chunks.lastIndex.coerceAtLeast(0))
        ui.heading(body,"选集",16,x,y+3f,100f,36f)
        val ranges=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false;isFocusable=false}
        val row=LinearLayout(this);ranges.addView(row);ui.at(body,ranges,x+103f,y,728f-x-103f,36f)
        val rangeButtons=chunks.indices.map {index->
            val label="${index*size+1}–${minOf((index+1)*size,detailEpisodes.size)}${if(album.category=="特别节目")"期"else "集"}"
            ui.button(label){group=index;focusEpisodes=true;showDetail(album,false)}.apply {tag="cctv-range-$index";TvBrowserStyle.button(ui,this,selected=index==group,center=true);row.addView(this,LinearLayout.LayoutParams(ui.px(172f),ui.px(36f)).apply {marginEnd=ui.px(12f)})}
        }
        rangeButton=rangeButtons.getOrNull(group)
        val step=(728f-x)/columns
        val buttons=chunks.getOrNull(group).orEmpty().mapIndexed {i,ep->
            ui.button(body,if(album.category=="特别节目")ep.title else "第${ep.part}集",x+i%columns*step,y+43f+i/columns*46f,step-9f,36f){launch(album,ep,0)}.apply {tag="cctv-episode-${ep.guid}";contentDescription=ep.title;TvBrowserStyle.button(ui,this,center=true);maxLines=2}
        }
        rangeButtons.forEachIndexed {i,b->b.nextFocusLeftId=rangeButtons.getOrNull(i-1)?.id ?: b.id;b.nextFocusRightId=rangeButtons.getOrNull(i+1)?.id ?: b.id;b.nextFocusUpId=actions[minOf(i,actions.lastIndex)].id;b.nextFocusDownId=buttons.firstOrNull()?.id ?: b.id}
        actions.forEach {it.nextFocusDownId=rangeButton?.id ?: it.id}
        buttons.forEachIndexed {i,b->b.nextFocusLeftId=if(i%columns==0)host.findViewWithTag<View>("browser-nav-$section")!!.id else buttons[i-1].id;b.nextFocusRightId=if(i%columns==columns-1)b.id else buttons.getOrNull(i+1)?.id ?: b.id;b.nextFocusUpId=buttons.getOrNull(i-columns)?.id ?: rangeButton?.id ?: actions[0].id;b.nextFocusDownId=buttons.getOrNull(i+columns)?.id ?: b.id}
        if(focusEpisodes){focusEpisodes=false;buttons.firstOrNull()?.post {buttons.firstOrNull()?.requestFocus()}}
    }
    private fun launch(album:CctvAlbum,episode:CctvEpisode,position:Long){startActivity(Intent(this,JellyLibraryActivity::class.java).putExtra("cctv_album",album.encode()).putExtra("cctv_guid",episode.guid).putExtra("cctv_episode_title",episode.title).putExtra("cctv_part",episode.part).putExtra("cctv_position",position))}
}
