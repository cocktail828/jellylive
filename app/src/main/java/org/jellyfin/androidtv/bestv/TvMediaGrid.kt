package org.jellyfin.androidtv.bestv

import android.app.AlertDialog
import android.graphics.Typeface
import android.text.TextUtils
import android.view.*
import android.widget.*
import kotlin.math.min

/** Current media-library toolbar, six-column poster grid, focus and append behavior for both sources. */
class TvMediaGrid(private val ui:TvUi,private val host:FrameLayout,title:String,sections:List<String> = emptyList(),selected:Int=0,select:(Int)->Unit={}) {
    data class Position(val id:String?,val index:Int=0,val offset:Int=0)
    data class Action(val label:String,val click:()->Unit)
    val search:Button
    val filter:Button
    val sort:Button
    val subtitle:TextView
    var onNearEnd:(()->Unit)?=null
    private val body=FrameLayout(ui.activity)
    private val scroll=ScrollView(ui.activity)
    private val cards=mutableListOf<FrameLayout>()
    private var values=emptyList<TvMediaCard.Data>()
    private var opened:(TvMediaCard.Data)->Unit={}
    private var footer:Button?=null
    private var focusedIndex=0
    private var categoryButton:Button?=null
    init {
        ui.header(host,title,""){}
        subtitle=ui.text(host,"",12,TvUi.MUTED,48f,if(sections.isEmpty())65f else 109f,380f,23f)
        if(sections.isNotEmpty())categoryButton=ui.button(host,"分类：${sections[selected]}",48f,68f,226f,38f){choices(ui,"央视频分类",sections,selected,select)}.apply {tag="source-section";isFocusableInTouchMode=true;setSingleLine()}
        fun button(label:String,b:LibraryPageLayout.Box,tagName:String)=ui.button(host,label,b.x,b.y,b.width,b.height){}.apply {tag=tagName;isFocusableInTouchMode=true;setSingleLine();ellipsize=TextUtils.TruncateAt.END}
        search=button("⌕ 搜索媒体",LibraryPageLayout.GRID_SEARCH,"grid-search")
        filter=button("类型：全部",LibraryPageLayout.GRID_FILTER,"grid-filter")
        sort=button("排序：名称正序",LibraryPageLayout.GRID_SORT,"grid-sort")
        listOf(search,filter,sort).forEachIndexed {i,b->val all=listOf(search,filter,sort);b.nextFocusLeftId=if(i==0)categoryButton?.id?:b.id else all[i-1].id;b.nextFocusRightId=all[min(i+1,2)].id;b.nextFocusUpId=b.id}
        categoryButton?.apply {nextFocusLeftId=id;nextFocusRightId=search.id;nextFocusUpId=id}
        scroll.apply {isVerticalScrollBarEnabled=false;isFocusable=false;isSmoothScrollingEnabled=false;descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS;addView(body,android.view.ViewGroup.LayoutParams(ui.px(864f),ui.px(1f)))}
        val b=LibraryPageLayout.scrollViewport();ui.at(host,scroll,b.x,b.y,b.width,b.height)
    }
    fun position():Position {val i=cards.indexOfFirst {it.hasFocus()}.takeIf {it>=0}?:focusedIndex;return Position(values.getOrNull(i)?.id,i,scroll.scrollY)}
    fun restore(pos:Position) {val i=values.indexOfFirst {it.id==pos.id}.takeIf {it>=0}?:pos.index.coerceIn(0,values.lastIndex.coerceAtLeast(0));append(maxOf(cards.size,i+LibraryPageLayout.columns()+1));cards.getOrNull(i)?.requestFocus();scroll.scrollTo(0,pos.offset)}
    fun focusFirst(){cards.firstOrNull()?.requestFocus()?:footer?.requestFocus()?:filter.requestFocus()}
    private fun height(){body.layoutParams=body.layoutParams.apply {height=ui.px(if(cards.isEmpty())350f else LibraryPageLayout.scrollPoster(cards.lastIndex).bottom+4f)}}
    private fun wire(){val n=LibraryPageLayout.columns();val controls=listOfNotNull(categoryButton)+listOf(search,filter,sort)
        cards.forEachIndexed {i,c->c.nextFocusLeftId=if(i%n>0)cards[i-1].id else c.id;c.nextFocusRightId=if(i%n<n-1)cards.getOrNull(i+1)?.id?:c.id else c.id;c.nextFocusUpId=cards.getOrNull(i-n)?.id?:controls[i%n*controls.size/n].id;c.nextFocusDownId=cards.getOrNull(min(i+n,cards.lastIndex))?.takeIf {i/n<(cards.size-1)/n}?.id?:footer?.id?:c.id}
        controls.forEach {it.nextFocusDownId=cards.firstOrNull()?.id?:footer?.id?:it.id}
    }
    private fun append(limit:Int){
        for(i in cards.size until min(limit,values.size)) {
            val data=values[i];val c=TvMediaCard.poster(ui,data){values.firstOrNull {it.id==data.id}?.let(opened)};c.tag="grid-item-${data.id}"
            val previous=c.onFocusChangeListener;c.setOnFocusChangeListener {v,focused->previous?.onFocusChange(v,focused);if(focused){focusedIndex=i};if(focused)host.post {if(v.isAttachedToWindow&&v.hasFocus()){append(LibraryPageLayout.appendLimit(cards.size,values.size,i));scroll.smoothScrollTo(0,ui.px(LibraryPageLayout.scrollPoster(i).y-4f));if(i>=values.size-LibraryPageLayout.columns()*2)onNearEnd?.invoke()}}}
            c.setOnKeyListener {_,key,event->if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN&&i>=values.size-LibraryPageLayout.columns()) {onNearEnd?.invoke();false}else false}
            cards+=c;val b=LibraryPageLayout.scrollPoster(i);ui.at(body,c,b.x,b.y,b.width,b.height)
        }
        height();wire()
    }
    fun show(items:List<TvMediaCard.Data>,preserve:Boolean=true,click:(TvMediaCard.Data)->Unit){
        val old=position();val hadFocus=body.hasFocus();opened=click;footer?.let {body.removeView(it)};footer=null
        val samePrefix=preserve&&cards.isNotEmpty()&&values.take(cards.size)==items.take(cards.size)&&items.size>=cards.size
        values=items
        if(!samePrefix){body.removeAllViews();cards.clear();scroll.scrollTo(0,0)}
        append(if(cards.isEmpty())LibraryPageLayout.columns()*3 else cards.size)
        if(preserve&&hadFocus&&(!samePrefix||!body.hasFocus()))restore(old)
        busy(false)
    }
    fun busy(value:Boolean){sort.isEnabled=!value}
    fun loading(){state("正在加载节目…","正在读取来源目录，请稍候。");busy(true)}
    fun state(title:String,message:String,action:Action?=null){
        values=emptyList();cards.clear();body.removeAllViews();footer=null;scroll.scrollTo(0,0);height()
        ui.at(body,ImageView(ui.activity).apply {setImageDrawable(TvIcons('▱',0xffb7a2ff.toInt(),ui.px(48f)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},408f,42f,48f,48f)
        ui.text(body,title,22,TvUi.INK,24f,116f,816f,34f).apply {gravity=Gravity.CENTER;maxLines=1;ellipsize=TextUtils.TruncateAt.END;setTypeface(null,Typeface.BOLD)}
        ui.text(body,message,14,TvUi.MUTED,24f,164f,816f,42f).apply {gravity=Gravity.CENTER;maxLines=2;ellipsize=TextUtils.TruncateAt.END}
        action?.let {footer=ui.button(body,it.label,302f,228f,260f,38f,it.click).apply {isFocusableInTouchMode=true;nextFocusUpId=filter.id;nextFocusLeftId=id;nextFocusRightId=id;nextFocusDownId=id}}
        wire();footer?.requestFocus();busy(false)
    }
    fun retry(message:String,action:Action){
        footer?.let {body.removeView(it)};val y=if(cards.isEmpty())228f else LibraryPageLayout.scrollPoster(cards.lastIndex).bottom+12f
        footer=ui.button(body,"$message · ${action.label}",302f,y,260f,38f,action.click).apply {isFocusableInTouchMode=true;nextFocusUpId=cards.lastOrNull()?.id?:filter.id;nextFocusLeftId=id;nextFocusRightId=id;nextFocusDownId=id;setOnFocusChangeListener {_,f->if(f)scroll.smoothScrollTo(0,ui.px(y-180f))}}
        body.layoutParams=body.layoutParams.apply {height=ui.px(y+46f)};wire();busy(false)
    }
    companion object {
        fun choices(ui:TvUi,title:String,labels:List<String>,selected:Int,chosen:(Int)->Unit):AlertDialog {
            val previous=ui.activity.currentFocus
            val d=AlertDialog.Builder(ui.activity).setTitle(title).setSingleChoiceItems(labels.toTypedArray(),selected){dlg,i->dlg.dismiss();chosen(i)}.setNegativeButton("取消",null).create()
            d.setOnDismissListener {previous?.takeIf {it.isAttachedToWindow&&it.isShown}?.requestFocus()};d.show();d.listView?.setSelector(ui.focusBackground(TvUi.TILE));d.window?.setBackgroundDrawable(ui.surface(TvUi.PANEL,false));return d
        }
    }
}
