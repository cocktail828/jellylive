package org.jellyfin.androidtv.bestv

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import org.jellyfin.androidtv.ui.AsyncImageView

/** Source-neutral rendering of the existing library poster. Geometry stays in LibraryPageLayout. */
object TvMediaCard {
    data class Data(val id:String,val title:String,val metadata:String,val image:String?=null,val rating:String?=null,val played:Boolean=false,val progress:Double?=null)
    fun focus(ui:TvUi,box:FrameLayout,action:()->Unit):FrameLayout {
        box.id=View.generateViewId();box.isFocusable=true;box.isFocusableInTouchMode=true;box.isClickable=true
        box.background=ui.surface(TvUi.PANEL,false);box.clipToOutline=true
        box.setOnFocusChangeListener {_,focused->box.foreground=if(focused)GradientDrawable().apply {setColor(Color.TRANSPARENT);cornerRadius=ui.px(8f).toFloat();setStroke(ui.px(2f),Color.WHITE)}else null}
        box.setOnClickListener {action()};return box
    }
    fun image(ui:TvUi,parent:FrameLayout,url:String?,x:Float,y:Float,w:Float,h:Float,landscape:Boolean=false) {
        val image=AsyncImageView(ui.activity).apply {allowHardwareBitmaps=false;scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(0xff20283d.toInt())}
        ui.at(parent,image,x,y,w,h);image.load(url=url,placeholder=ColorDrawable(0xff20283d.toInt()),aspectRatio=if(landscape)16.0/9 else 2.0/3)
        if(url.isNullOrBlank())ui.text(parent,"暂无封面",12,TvUi.MUTED,x+10,y+h/2-10,w-20,20f).gravity=Gravity.CENTER
    }
    fun poster(ui:TvUi,data:Data,click:()->Unit):FrameLayout {
        val bounds=LibraryPageLayout.poster(0);val imageHeight=LibraryPageLayout.posterImageHeight()
        val card=FrameLayout(ui.activity).apply {id=View.generateViewId();isFocusable=true;isFocusableInTouchMode=true;isClickable=true;background=ColorDrawable(Color.TRANSPARENT);clipToOutline=false;setOnClickListener {click()}}
        val art=FrameLayout(ui.activity).apply {background=ui.surface(TvUi.PANEL,false);clipToOutline=true};ui.at(card,art,0f,0f,bounds.width,imageHeight)
        image(ui,art,data.image,0f,0f,bounds.width,imageHeight)
        ui.at(card,View(ui.activity).apply {background=GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(0x0010182a,0xee10182a.toInt()))},0f,imageHeight-48f,bounds.width,48f)
        val titleBox=LibraryPageLayout.posterTitle()
        val caption=ui.text(card,data.title,14,TvUi.INK,titleBox.x,titleBox.y,titleBox.width,titleBox.height).apply {setTypeface(null,Typeface.BOLD);setSingleLine();gravity=Gravity.CENTER;ellipsize=TextUtils.TruncateAt.END;marqueeRepeatLimit=-1}
        card.setOnFocusChangeListener {_,focused->
            card.foreground=if(focused)android.graphics.drawable.InsetDrawable(ui.surface(Color.TRANSPARENT,false).apply {setColor(Color.TRANSPARENT);setStroke(ui.px(2f),Color.WHITE)},0,0,0,ui.px(bounds.height-imageHeight))else null
            caption.ellipsize=if(focused&&!ui.reduceMotion())TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END;caption.isSelected=focused&&!ui.reduceMotion()
        }
        val meta=LibraryPageLayout.posterMetadata();ui.text(card,data.metadata,12,TvUi.MUTED,meta.x,meta.y,meta.width,meta.height).apply {gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;maxLines=1;ellipsize=TextUtils.TruncateAt.END}
        data.rating?.let {ui.text(card,it,13,0xfff7d68d.toInt(),7f,7f,41f,21f).apply {gravity=Gravity.CENTER;background=ui.glass(TvUi.PANEL)}}
        if(data.played)ui.text(card,"已看",10,TvUi.INK,bounds.width-43f,7f,36f,18f).apply {gravity=Gravity.CENTER;background=ui.surface(0xcc171c32.toInt(),false)}
        else data.progress?.takeIf {it>0}?.let {value->val p=ProgressBar(ui.activity,null,android.R.attr.progressBarStyleHorizontal);p.max=1000;p.progress=(value.coerceIn(0.0,1.0)*1000).toInt();ui.at(card,p,4f,imageHeight-4f,bounds.width-8f,3f)}
        card.contentDescription=listOfNotNull(data.title,data.rating?.let {"评分 $it"},data.metadata.takeIf(String::isNotBlank),if(data.played)"已看"else null).joinToString("，")
        return card
    }
}
