package org.jellyfin.androidtv.bestv

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.jellyfin.androidtv.ui.AsyncImageView

/** Source-independent TV browser. Providers own identity, queries, persistence and mutations. */
class TvMediaBrowser(private val ui: TvUi,val host: FrameLayout,libraryName: String,
    sections: List<String>,selectedSection: Int,onSection: (Int)->Unit) {
    data class Card(val id: String,val title: String,val metadata: String,val imageUrl: String?=null,
        val score: String?=null,val progress: Double?=null)
    data class Position(val id: String?,val index: Int,val offset: Int)
    fun position()=Position(focusedId,lastIndex,scroll.scrollY)
    fun restore(position: Position){
        lastIndex=models.indexOfFirst {it.id==position.id}.takeIf {it>=0} ?: position.index.coerceIn(0,(models.size-1).coerceAtLeast(0))
        while(cards.size<=lastIndex&&cards.size<models.size)append(cards.size)
        wire();cards.getOrNull(lastIndex)?.requestFocus();scroll.post {scroll.scrollTo(0,position.offset)}
    }
    data class Action(val label: String,val click: ()->Unit)
    private val activity=ui.activity
    private val large=ui.largeText()
    private val columns=TvMediaBrowserLayout.columns(large)
    private val selectedNav=selectedSection
    private val body=FrameLayout(activity)
    private val scroll=ScrollView(activity)
    private val cards=mutableListOf<FrameLayout>()
    private var models=emptyList<Card>()
    private var choose: (Card)->Unit={}
    /** Provider owns paging, request deduplication and end-of-source detection. */
    var onNearEnd: (() -> Unit)?=null
    private val toolbar=mutableListOf<Button>()
    private val nav=mutableListOf<Button>()
    private val heading: TextView
    private var lastIndex=0
    private var focusedId: String?=null
    private var recovery: View?=null
    private var contentFocus:View?=null
    val search: Button
    val genre: Button
    val year: Button
    val rating: Button
    val progress: Button
    val sort: Button
    val clear: Button
    init {
        TvBrowserStyle.background(ui,host)
        ui.text(host,libraryName,if(libraryName=="央视频")18 else 16,TvBrowserStyle.INK,32f,38f,128f,if(large)45f else 35f).apply {
            maxLines=2;ellipsize=TextUtils.TruncateAt.END;setTypeface(null,Typeface.BOLD)
        }
        sections.forEachIndexed {index,label->
            val button=ui.button(host,label,32f,94f+index*46f,128f,36f){onSection(index)}
            val symbol=when(label){"全部媒体"->"grid";"观看记录"->"history";"收藏"->"star";"电视剧","动画片"->"series";else->"movie"}
            TvBrowserStyle.button(ui,button,symbol,selected=index==selectedSection)
            if(index!=selectedSection)button.background=android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused,android.R.attr.state_window_focused),TvBrowserStyle.surface(ui,0xff242631.toInt(),true))
                addState(intArrayOf(),android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            };button.setTextColor(if(index==selectedSection)TvBrowserStyle.INK else TvBrowserStyle.MUTED);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14f*ui.scale*ui.fontScale);button.setTypeface(null,if(index==selectedSection)Typeface.BOLD else Typeface.NORMAL);button.tag="browser-nav-$index";button.setSingleLine();button.ellipsize=TextUtils.TruncateAt.END;nav+=button
        }
        fun control(label: String,x: Float,y: Float,w: Float)=ui.button(host,label,x,y,w,if(large)40f else 36f){}.apply {
            TvBrowserStyle.button(ui,this,if(label=="搜索媒体")"search" else null,down=label!="搜索媒体")
            setSingleLine();ellipsize=TextUtils.TruncateAt.END;toolbar+=this
        }
        search=control("搜索媒体",200f,32f,if(large)728f else 226f).apply {setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13f*ui.scale*ui.fontScale)}
        val filterY=if(large)86f else 32f
        val widths=if(large)176f else 117.5f
        val firstX=if(large)200f else 434f
        genre=control("类型：全部",firstX,filterY,widths)
        year=control("年份：全部",firstX+widths+8f,filterY,widths)
        rating=control("评分：不限",firstX+2*(widths+8f),filterY,widths)
        progress=control("进度：全部",firstX+3*(widths+8f),filterY,widths)
        val headingY=if(large)149f else 90f
        heading=ui.text(host,sections[selectedSection],if(large)22 else 20,TvBrowserStyle.INK,200f,headingY,370f,if(large)32f else 36f).apply {setTypeface(null,Typeface.BOLD)}
        sort=ui.button(host,"名称升序",if(large)690f else 800f,headingY,if(large)238f else 128f,if(large)36f else 36f){}
        TvBrowserStyle.button(ui,sort,"sort",down=true)
        search.tag="browser-search";genre.tag="browser-genre";year.tag="browser-year";rating.tag="browser-rating";progress.tag="browser-progress";sort.tag="browser-sort"
        sort.setSingleLine();sort.ellipsize=TextUtils.TruncateAt.END
        clear=ui.button(host,"清除筛选",if(large)530f else 576f,headingY,148f,36f){}.apply {visibility=View.GONE}
        TvBrowserStyle.button(ui,clear)
        scroll.apply {isFocusable=false;isVerticalScrollBarEnabled=false;isSmoothScrollingEnabled=false;clipToPadding=true;setPadding(ui.px(6.5f),ui.px(6.5f),ui.px(6.5f),0);addView(body,ViewGroup.LayoutParams(ui.px(TvMediaBrowserLayout.CONTENT_WIDTH),1))}
        ui.at(host,scroll,193.5f,TvMediaBrowserLayout.top(large)-6.5f,741f,508f-TvMediaBrowserLayout.top(large)+6.5f)
        toolbar.forEachIndexed {index,b->
            b.nextFocusLeftId=toolbar.getOrNull(index-1)?.id ?: b.id;b.nextFocusRightId=toolbar.getOrNull(index+1)?.id ?: b.id;b.nextFocusUpId=b.id
            b.setOnFocusChangeListener {_,has->if(has)wire()}
        }
        sort.nextFocusUpId=progress.id;sort.nextFocusRightId=sort.id
        progress.nextFocusDownId=sort.id
        nav.forEachIndexed {index,b->b.nextFocusUpId=nav.getOrNull(index-1)?.id ?: b.id;b.nextFocusDownId=nav.getOrNull(index+1)?.id ?: b.id;b.nextFocusLeftId=b.id
            b.setOnKeyListener {_,key,event->if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT&&event.action==android.view.KeyEvent.ACTION_DOWN){(cards.getOrNull(lastIndex) ?: recovery ?: contentFocus ?: search).requestFocus();true}else false}}
    }
    /** Providers may expose up to four source-specific facets without duplicating the shell. */
    fun configureControls(searchLabel: String,filterLabels: List<String>){
        require(filterLabels.size<=4)
        TvIcons.text(search,searchLabel)
        val filters=listOf(genre,year,rating,progress)
        val width=if(filterLabels.isEmpty())0f else ((if(large)728f else 494f)-8f*(filterLabels.size-1))/filterLabels.size
        filters.forEachIndexed {index,button->
            button.visibility=if(index<filterLabels.size)View.VISIBLE else View.GONE
            if(index<filterLabels.size){TvIcons.text(button,filterLabels[index]);button.layoutParams=FrameLayout.LayoutParams(ui.px(width),ui.px(if(large)40f else 36f)).apply {
                leftMargin=ui.px((if(large)200f else 434f)+index*(width+8f));topMargin=ui.px(if(large)86f else 32f)
            }}
        }
        wire()
    }
    fun retry(message: String,action: Action){
        recovery?.let {body.removeView(it)}
        val bottom=cards.lastOrNull()?.let {val b=TvMediaBrowserLayout.poster(cards.lastIndex,large);b.y+b.height+12f} ?: 40f
        recovery=ui.button(body,"$message · ${action.label}",12f,bottom,720f,if(large)54f else 40f){action.click()}.apply {
            nextFocusUpId=cards.lastOrNull()?.id ?: search.id;nextFocusDownId=id;nextFocusLeftId=nav.first().id;nextFocusRightId=id
        }
        body.layoutParams.height=ui.px(TvMediaBrowserLayout.contentHeight(cards.size,large,true))
        cards.takeLast(columns).forEach {it.nextFocusDownId=recovery!!.id}
    }
    fun content(panel: android.view.View){
        scroll.visibility=View.GONE;clear.visibility=View.GONE
        ui.at(host,panel,200f,TvMediaBrowserLayout.top(large),728f,508f-TvMediaBrowserLayout.top(large))
    }
    fun contentFocus(view:View){contentFocus=view}
    fun refreshControl(){TvBrowserStyle.button(ui,sort,"refresh");sort.layoutParams=(sort.layoutParams as FrameLayout.LayoutParams).apply {width=ui.px(76f);leftMargin=ui.px(852f)}}
    fun controlsSelected(values:List<Boolean>){listOf(genre,year,rating,progress).forEachIndexed {i,b->TvBrowserStyle.button(ui,b,down=true,selected=values.getOrElse(i){false})}}
    fun title(value: String){heading.text=value}
    fun busy(value: Boolean){(toolbar+sort+clear).forEach {it.isEnabled=!value;it.alpha=if(value).45f else 1f}}
    fun loading(){busy(true);body.removeAllViews();cards.clear();recovery=null
        repeat(columns){i->val b=TvMediaBrowserLayout.poster(i,large);ui.at(body,View(activity).apply {background=ui.surface(TvUi.PANEL,false)},b.x,b.y,b.width,TvMediaBrowserLayout.imageHeight(large))}
        body.layoutParams.height=ui.px(TvMediaBrowserLayout.imageHeight(large)+12f);nav.getOrNull(selectedNav)?.requestFocus()
    }
    fun state(title: String,message: String,action: Action,requestFocus: Boolean=true){
        busy(false);body.removeAllViews();cards.clear();models=emptyList();body.layoutParams.height=ui.px(310f)
        ui.text(body,title,22,TvUi.INK,16f,70f,708f,65f).apply {gravity=Gravity.CENTER;maxLines=2}
        ui.text(body,message,14,TvUi.MUTED,24f,140f,692f,60f).apply {gravity=Gravity.CENTER;maxLines=2}
        recovery=ui.button(body,action.label,242f,220f,260f,if(large)50f else 40f){action.click()}.apply {nextFocusUpId=search.id;nextFocusLeftId=nav.first().id;nextFocusRightId=id;nextFocusDownId=id}
        wire();if(requestFocus)recovery?.post {recovery?.requestFocus()}
    }
    fun show(items: List<Card>,preserve: Boolean=false,onClick: (Card)->Unit){
        if(preserve&&items.size>models.size&&items.take(models.size)==models){
            busy(false);models=items;choose=onClick;recovery?.let(body::removeView);recovery=null
            append(lastIndex);wire();return
        }
        val previous=focusedId;val previousIndex=lastIndex;val offset=scroll.scrollY
        val hadCardFocus=cards.any {it.hasFocus()}
        busy(false);body.removeAllViews();cards.clear();models=items;choose=onClick;recovery=null
        lastIndex=if(preserve)items.indexOfFirst {it.id==previous}.takeIf {it>=0} ?: previousIndex.coerceAtMost(items.lastIndex).coerceAtLeast(0) else 0
        append(if(preserve)maxOf(lastIndex,columns*3) else 0)
        while(cards.size<=lastIndex&&cards.size<items.size)append(cards.size)
        wire()
        if(preserve){scroll.scrollTo(0,offset);if(hadCardFocus)cards.getOrNull(lastIndex)?.requestFocus()}
        else {scroll.scrollTo(0,0);focusedId=null}
    }
    fun focusFirst(){(cards.firstOrNull() ?: recovery ?: search).post {(cards.firstOrNull() ?: recovery ?: search).requestFocus()}}
    private fun append(index: Int){
        val limit=TvMediaBrowserLayout.appendLimit(cards.size,models.size,index,large)
        for(i in cards.size until limit){val model=models[i];val card=poster(model)
            cards+=card;val b=TvMediaBrowserLayout.poster(i,large);ui.at(body,card,b.x,b.y,b.width,b.height)
            val original=card.onFocusChangeListener
            card.setOnFocusChangeListener {v,has->original?.onFocusChange(v,has);if(has){lastIndex=i;focusedId=model.id;append(i);wire();if(i>=models.size-columns*2&&recovery==null)card.post {if(card.hasFocus())onNearEnd?.invoke()};card.post {card.requestRectangleOnScreen(Rect(0,0,card.width,card.height),false)}}}
        }
        body.layoutParams.height=ui.px(TvMediaBrowserLayout.contentHeight(cards.size,large,recovery!=null))
    }
    private fun wire(){
        val first=cards.firstOrNull() ?: recovery ?: search
        val visible=toolbar.filter {it.visibility==View.VISIBLE}
        val facets=visible.filter {it!==search}
        visible.forEachIndexed {index,b->
            b.nextFocusLeftId=visible.getOrNull(index-1)?.id ?: nav[selectedNav].id
            b.nextFocusRightId=visible.getOrNull(index+1)?.id ?: b.id
            b.nextFocusUpId=if(large&&b!==search)search.id else b.id
            b.nextFocusDownId=if(large&&b===search&&facets.isNotEmpty())facets.first().id else cards.getOrNull(if(index==0)lastIndex else (index-1).coerceAtMost(columns-1))?.id ?: first.id
        }
        facets.lastOrNull()?.nextFocusDownId=sort.id
        sort.nextFocusUpId=facets.lastOrNull()?.id ?: search.id
        sort.nextFocusDownId=cards.getOrNull(if(lastIndex<cards.size)lastIndex else columns-1)?.id ?: first.id
        sort.nextFocusLeftId=if(clear.visibility==View.VISIBLE)clear.id else sort.id
        clear.nextFocusRightId=sort.id;clear.nextFocusUpId=facets.lastOrNull()?.id ?: search.id;clear.nextFocusDownId=first.id
        cards.forEachIndexed {index,card->val col=index%columns
            card.nextFocusLeftId=if(col==0)nav[selectedNav].id else cards[index-1].id
            card.nextFocusRightId=if(col==columns-1)card.id else cards.getOrNull(index+1)?.id ?: card.id
            card.nextFocusDownId=cards.getOrNull(TvMediaBrowserLayout.down(index,cards.size,large,recovery!=null))?.id ?: recovery?.id ?: card.id
            card.nextFocusUpId=if(index>=columns)cards[index-columns].id else if(col==columns-1)sort.id else (if(large)facets else visible).getOrNull(col)?.id ?: search.id
        }
    }
    private fun poster(model: Card): FrameLayout {
        val b=TvMediaBrowserLayout.poster(0,large);val h=TvMediaBrowserLayout.imageHeight(large)
        val card=FrameLayout(activity).apply {id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true;clipChildren=false;tag=model.id;setOnClickListener {choose(model)}}
        val imageHost=FrameLayout(activity).apply {background=ui.surface(TvUi.PANEL,false);clipToOutline=true}
        ui.at(card,imageHost,0f,0f,b.width,h)
        if(model.imageUrl!=null){val image=AsyncImageView(activity).apply {allowHardwareBitmaps=false;scaleType=ImageView.ScaleType.CENTER_CROP};ui.at(imageHost,image,0f,0f,b.width,h);image.load(url=model.imageUrl,placeholder=ColorDrawable(TvUi.PANEL),aspectRatio=2.0/3)}
        else ui.at(imageHost,PosterPlaceholder(activity,model.id.hashCode()),0f,0f,b.width,h)
        model.score?.let {value->
            val badge=ui.text(value,11,0xffe9d4a9.toInt()).apply {gravity=Gravity.CENTER;setSingleLine();ellipsize=TextUtils.TruncateAt.END;background=ui.surface(0xff11151e.toInt(),false)}
            ui.at(imageHost,badge,7f,7f,(badge.paint.measureText(value)/ui.scale+12f).coerceIn(36f,b.width-14f),19f)
        }
        model.progress?.takeIf {it.isFinite()}?.let {fraction->
            ui.at(imageHost,View(activity).apply {background=ui.surface(0xff5b5968.toInt(),false)},7f,h-9f,b.width-14f,3f)
            if(fraction>0)ui.at(imageHost,View(activity).apply {setBackgroundColor(0xffc3b0e7.toInt())},7f,h-9f,(b.width-14f)*fraction.coerceIn(0.0,1.0).toFloat(),3f)
        }
        val caption=ui.text(card,model.title,15,TvBrowserStyle.INK,0f,h+8f,b.width,if(large)32f else 23f).apply {setTypeface(null,Typeface.BOLD);setSingleLine();gravity=Gravity.CENTER;ellipsize=TextUtils.TruncateAt.END;marqueeRepeatLimit=-1}
        ui.text(card,model.metadata,12,TvBrowserStyle.MUTED,0f,h+if(large)43f else 33f,b.width,if(large)28f else 19f).apply {setSingleLine();gravity=Gravity.CENTER;ellipsize=TextUtils.TruncateAt.END}
        imageHost.foreground=android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected,android.R.attr.state_window_focused),TvBrowserStyle.surface(ui,Color.TRANSPARENT,true))
            addState(intArrayOf(),ColorDrawable(Color.TRANSPARENT))
        }
        card.setOnFocusChangeListener {_,has->imageHost.isSelected=has}
        scrollTitleOnFocus(card,caption,ui)
        card.contentDescription=listOfNotNull(model.title,model.metadata,model.score).joinToString("，")
        return card
    }
    private class PosterPlaceholder(context: android.content.Context,private val seed: Int): View(context){
        private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas){super.onDraw(canvas)
            val colors=listOf(0xff2e3e43.toInt(),0xff39354c.toInt(),0xff454034.toInt(),0xff35414d.toInt(),0xff4b3539.toInt());canvas.drawColor(colors[(seed and Int.MAX_VALUE)%colors.size]);paint.color=0x559eb5b7;paint.style=android.graphics.Paint.Style.STROKE;paint.strokeWidth=width*.025f
            canvas.drawCircle(width/2f,height*.43f,width*.31f,paint);paint.strokeWidth=width*.07f;canvas.drawLine(width*.25f,height*.58f,width*.75f,height*.28f,paint)
        }
    }
    companion object {
        /** Shared by grid and search cards; reduced motion keeps the centered ellipsis. */
        fun scrollTitleOnFocus(card: View,caption: TextView,ui: TvUi){
        val handler=Handler(Looper.getMainLooper())
        var animation: android.animation.ValueAnimator?=null
        lateinit var marquee: Runnable
        marquee=Runnable {
            if(card.hasFocus()&&!ui.reduceMotion()){
                val overflow=(caption.paint.measureText(caption.text.toString())-caption.width).toInt()
                if(overflow>0){
                    caption.ellipsize=null;caption.gravity=Gravity.START or Gravity.CENTER_VERTICAL
                    animation=android.animation.ValueAnimator.ofInt(0,overflow).apply {
                        duration=(overflow/ui.scale/15f*1000).toLong().coerceAtLeast(1)
                        interpolator=android.view.animation.LinearInterpolator()
                        addUpdateListener {caption.scrollTo(it.animatedValue as Int,0)}
                        addListener(object:android.animation.AnimatorListenerAdapter(){override fun onAnimationEnd(a:android.animation.Animator){
                            if(card.hasFocus())handler.postDelayed({if(card.hasFocus()){caption.scrollTo(0,0);handler.postDelayed(marquee,800)}},1200)
                        }})
                        start()
                    }
                }
            }
        }
        fun stop(){animation?.removeAllListeners();animation?.cancel();animation=null;handler.removeCallbacks(marquee);caption.scrollTo(0,0);caption.ellipsize=TextUtils.TruncateAt.END;caption.gravity=Gravity.CENTER}
        val original=card.onFocusChangeListener
        card.setOnFocusChangeListener {v,has->original?.onFocusChange(v,has);stop();if(has)handler.postDelayed(marquee,800)}
        card.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){};override fun onViewDetachedFromWindow(v:View){stop()}})
        }

        /** Confirm commits and closes; Back cancels without changing the provider's selection. */
        fun choices(ui: TvUi,title: String,values: List<String>,selected: Int,onSelect: (Int)->Unit): AlertDialog {
            val anchor=ui.activity.currentFocus
            val body=LinearLayout(ui.activity).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.px(6f),ui.px(8f),ui.px(6f),ui.px(8f));background=TvBrowserStyle.surface(ui,0xff252633.toInt())}
            body.addView(ui.text(title,14,TvBrowserStyle.INK),LinearLayout.LayoutParams(-1,ui.px(36f)).apply {leftMargin=ui.px(6f)})
            val scroll=ScrollView(ui.activity).apply {addView(body);isVerticalScrollBarEnabled=false}
            val dialog=AlertDialog.Builder(ui.activity).setView(scroll).create()
            val buttons=values.mapIndexed {index,value->ui.button(value){onSelect(index);dialog.dismiss()}.apply {
                TvBrowserStyle.button(ui,this,selected=index==selected);setSingleLine();ellipsize=TextUtils.TruncateAt.END
                if(index==selected)setCompoundDrawablesRelative(null,null,TvBrowserStyle.icon(ui,"check"),null)
                body.addView(this,LinearLayout.LayoutParams(-1,ui.px(if(ui.largeText())54f else 36f)))
                setOnFocusChangeListener {v,has->if(has)v.requestRectangleOnScreen(Rect(0,0,v.width,v.height),false)}
            }}
            buttons.forEachIndexed {i,b->b.nextFocusUpId=buttons.getOrNull(i-1)?.id ?: b.id;b.nextFocusDownId=buttons.getOrNull(i+1)?.id ?: b.id;b.nextFocusLeftId=b.id;b.nextFocusRightId=b.id}
            dialog.setOnDismissListener {anchor?.takeIf {it.isAttachedToWindow}?.requestFocus()}
            dialog.show()
            val point=IntArray(2);anchor?.getLocationOnScreen(point)
            val w=ui.px(if(ui.largeText())320f else if(title=="排序")262f else 225f)
            val h=minOf(ui.px(430f),ui.px(52f+values.size*(if(ui.largeText())54f else 36f)))
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));setDimAmount(0f)
                setGravity(Gravity.TOP or Gravity.START);setLayout(w,h)
                attributes=attributes.apply {x=point[0].coerceIn(ui.px(32f),ui.px(928f)-w);y=(point[1]+(anchor?.height ?: 0)+ui.px(8f)).coerceIn(ui.px(32f),ui.px(508f)-h)}
            }
            buttons.getOrNull(selected)?.requestFocus()
            return dialog
        }
    }
}
