package org.jellyfin.androidtv.bestv

/** Fixed TV geometry: 3840 × 2160 artwork mapped to a 960 × 540 logical canvas. */
object TvMediaBrowserLayout {
    const val CONTENT_X=200f
    const val CONTENT_WIDTH=728f
    data class Box(val x: Float,val y: Float,val width: Float,val height: Float)
    fun columns()=5
    fun imageHeight()=204f
    fun top()=138f
    fun poster(index: Int)=Box(index%columns()*147f,index/columns()*272f,136f,256f)
    fun appendLimit(shown:Int,total:Int,focused:Int):Int = when {
        shown==0->minOf(total,columns()*3)
        focused>=shown-columns()*2->minOf(total,shown+columns()*2)
        else->shown
    }
    /** count is the recovery action sentinel; otherwise the result is a card index. */
    fun down(index:Int,count:Int,retry:Boolean):Int =
        if(index/columns()<(count-1)/columns())minOf(index+columns(),count-1) else if(retry)count else index
    fun contentHeight(count:Int,retry:Boolean=false):Float {
        val end=if(count>0)poster(count-1).let {it.y+it.height} else 28f
        return end+if(retry)76f else 6f
    }
}
