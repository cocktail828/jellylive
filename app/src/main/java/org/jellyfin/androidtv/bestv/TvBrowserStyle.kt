package org.jellyfin.androidtv.bestv

import android.graphics.*
import android.graphics.drawable.*
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout

/** Colors and vector controls from the approved native-4K media/CCTV drawings. */
internal object TvBrowserStyle {
    const val INK=0xfff3f1f8.toInt()
    const val MUTED=0xffb5b3c5.toInt()
    private val paths=mapOf(
        "search" to "M27 15a12 12 0 1 1-24 0a12 12 0 1 1 24 0M24 24l10 10",
        "down" to "M8 14l10 10l10-10",
        "grid" to "M4 4h11v11H4zM22 4h11v11H22zM4 22h11v11H4zM22 22h11v11H22z",
        "movie" to "M4 7h28v25H4zM4 15h28M13 7l5 8M24 7l5 8",
        "series" to "M4 7h28v21H4zM12 33h12M18 28v5",
        "history" to "M6 11a14 14 0 1 1-1 14M5 3v10h10M18 9v10l7 4",
        "sort" to "M4 7h19M4 18h13M4 29h7M28 5v26M23 26l5 5l5-5",
        "star" to "M18 3l4.6 9.6L33 14l-7.5 7.3L27.3 32L18 27l-9.3 5l1.8-10.7L3 14l10.4-1.4z",
        "check" to "M5 18l9 9L31 8",
        "refresh" to "M30 13A13 13 0 1 0 30 25M30 4v10H20"
    )
    fun icon(ui:TvUi,name:String,size:Float=12f)=object:Drawable(){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=MUTED;style=Paint.Style.STROKE;strokeWidth=2.2f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        val path=androidx.core.graphics.PathParser.createPathFromPathData(paths.getValue(name))!!
        init {setBounds(0,0,ui.px(size),ui.px(size))}
        override fun draw(canvas:Canvas){canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/36f,bounds.height()/36f);canvas.drawPath(path,paint);canvas.restore()}
        override fun setAlpha(alpha:Int){paint.alpha=alpha}
        override fun setColorFilter(filter:ColorFilter?){paint.colorFilter=filter}
        @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
    }
    fun surface(ui:TvUi,color:Int,focus:Boolean=false)=GradientDrawable().apply {
        setColor(color);cornerRadius=ui.px(7f).toFloat()
        if(focus)setStroke(ui.px(2f),0xfff6f4ff.toInt())
    }
    fun button(ui:TvUi,button:Button,leading:String?=null,down:Boolean=false,selected:Boolean=false,center:Boolean=false){
        button.setTextColor(INK);button.setTypeface(null,Typeface.NORMAL)
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12f*ui.scale*ui.fontScale)
        button.gravity=(if(center)Gravity.CENTER_HORIZONTAL else Gravity.START) or Gravity.CENTER_VERTICAL
        button.setPadding(ui.px(8f),0,ui.px(8f),0);button.compoundDrawablePadding=ui.px(6f)
        button.setCompoundDrawablesRelative(leading?.let {icon(ui,it)},null,if(down)icon(ui,"down",10f)else null,null)
        button.background=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused,android.R.attr.state_window_focused),surface(ui,if(selected)0xff373047.toInt() else 0xff242631.toInt(),true))
            addState(intArrayOf(),surface(ui,if(selected)0xff373047.toInt() else 0xff242631.toInt()))
        }
    }
    fun background(ui:TvUi,host:FrameLayout){
        host.background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xff171b28.toInt(),0xff10141e.toInt(),0xff0c1017.toInt()))
        ui.at(host,android.view.View(ui.activity).apply {setBackgroundColor(0xff232738.toInt())},0f,0f,176f,540f)
        ui.at(host,android.view.View(ui.activity).apply {setBackgroundColor(0xff191d2b.toInt())},176f,0f,784f,80f)
    }
}
