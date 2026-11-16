package org.jellyfin.androidtv.bestv
fun main() {
 for(large in listOf(false,true)) {
  val cols=TvMediaBrowserLayout.columns(large)
  check(cols==if(large)4 else 5)
  for(i in 0 until cols){val b=TvMediaBrowserLayout.poster(i,large);check(b.x>=0&&b.x+b.width<=TvMediaBrowserLayout.CONTENT_WIDTH);check(TvMediaBrowserLayout.imageHeight(large)==b.width*1.5f)}
  check(TvMediaBrowserLayout.down(cols*3-1,cols*3,large,true)==cols*3)
  check(TvMediaBrowserLayout.down(cols*3-1,cols*3,large,false)==cols*3-1)
  check(TvMediaBrowserLayout.down(0,cols*3,large,true)==cols)
  val last=TvMediaBrowserLayout.poster(cols*3-1,large)
  check(TvMediaBrowserLayout.contentHeight(cols*3,large,true)>=last.y+last.height+12f+54f)
  check(TvMediaBrowserLayout.contentHeight(cols*3,large,true)>TvMediaBrowserLayout.contentHeight(cols*3,large))
  check(TvMediaBrowserLayout.appendLimit(0,100,0,large)==cols*3)
  check(TvMediaBrowserLayout.appendLimit(cols*3,100,cols,large)==cols*5)
 }
 println("PASS browser safe geometry, poster ratio, and focus append")
}
