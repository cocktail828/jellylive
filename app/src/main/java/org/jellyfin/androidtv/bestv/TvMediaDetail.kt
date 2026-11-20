package org.jellyfin.androidtv.bestv

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.StaticLayout
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*

/** Shared current-library detail canvas; each source supplies facts and actions only. */
class TvMediaDetail(val ui:TvUi,val parent:FrameLayout,kind:String,title:String,tagline:String?,metadata:String,secondary:String,brief:String,image:String?,backdrop:String?=image) {
    var nextActionX=212f
        private set
    init {
        TvMediaCard.image(ui,parent,backdrop,0f,0f,960f,540f,true)
        ui.at(parent,View(ui.activity).apply {background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(0xfa101326.toInt(),0xeb101326.toInt(),0x7a101326))},0f,0f,960f,540f)
        ui.header(parent,kind,""){}
        TvMediaCard.image(ui,parent,image,48f,107f,140f,210f)
        ui.text(parent,title,32,TvUi.INK,212f,109f,700f,43f).apply {setTypeface(null,Typeface.BOLD);maxLines=1;ellipsize=TextUtils.TruncateAt.END}
        tagline?.let {ui.text(parent,it,14,TvUi.MUTED,212f,153f,700f,24f).apply {maxLines=1;ellipsize=TextUtils.TruncateAt.END}}
        ui.text(parent,metadata,14,TvUi.MUTED,212f,180f,700f,25f).apply {maxLines=1;ellipsize=TextUtils.TruncateAt.END}
        ui.text(parent,secondary,13,TvUi.MUTED,212f,209f,700f,25f).apply {maxLines=1;ellipsize=TextUtils.TruncateAt.END}
        overview(ui,parent,brief,212f,246f,700f,50f,15)
    }
    fun action(label:String,reserve:String=label,click:()->Unit):Button {
        val b=ui.button(label,click).apply {isFocusableInTouchMode=true};val width=maxOf(80f,b.paint.measureText(reserve)/ui.scale+32f)
        ui.at(parent,b,nextActionX,311f,width,34f);nextActionX+=width+12f;return b
    }
    companion object {
        fun overview(ui:TvUi,parent:FrameLayout,value:String?,x:Float,y:Float,w:Float,h:Float,size:Int):TextView {
            val full=value?.replace(Regex("<[^>]+>"),"")?.trim()?.takeIf(String::isNotBlank)?:"暂无简介"
            return ui.text(parent,full,size,TvUi.INK,x,y,w,h).apply {
                maxLines=2;gravity=Gravity.TOP;setLineSpacing(ui.px(7f).toFloat(),1f);contentDescription=full
                post {val available=width-paddingLeft-paddingRight
                    if(available>0&&height>0)text=LibraryDetails.ellipsize(full){candidate->StaticLayout.Builder.obtain(candidate,0,candidate.length,paint,available).setIncludePad(false).setLineSpacing(lineSpacingExtra,lineSpacingMultiplier).build().let {it.lineCount<=2&&it.height<=height-paddingTop-paddingBottom}}
                }
            }
        }
    }
}
