package org.jellyfin.androidtv.bestv
fun main() {
 val cols=TvMediaBrowserLayout.columns()
 check(cols==5)
 for(i in 0 until cols){val b=TvMediaBrowserLayout.poster(i);check(b.x>=0&&b.x+b.width<=TvMediaBrowserLayout.CONTENT_WIDTH);check(TvMediaBrowserLayout.imageHeight()==b.width*1.5f)}
 check(TvMediaBrowserLayout.down(cols*3-1,cols*3,true)==cols*3)
 check(TvMediaBrowserLayout.down(cols*3-1,cols*3,false)==cols*3-1)
 check(TvMediaBrowserLayout.down(0,cols*3,true)==cols)
 val last=TvMediaBrowserLayout.poster(cols*3-1)
 check(TvMediaBrowserLayout.contentHeight(cols*3,true)>=last.y+last.height+12f+54f)
 check(TvMediaBrowserLayout.contentHeight(cols*3,true)>TvMediaBrowserLayout.contentHeight(cols*3))
 check(TvMediaBrowserLayout.appendLimit(0,100,0)==cols*3)
 check(TvMediaBrowserLayout.appendLimit(cols*3,100,cols)==cols*5)
 println("PASS fixed 4K browser geometry, poster ratio and focus append")
}
