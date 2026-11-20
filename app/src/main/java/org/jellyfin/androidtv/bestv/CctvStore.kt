package org.jellyfin.androidtv.bestv
import android.content.Context
class CctvStore(context:Context) {
    private val prefs=context.getSharedPreferences("jellylive_cctv",Context.MODE_PRIVATE)
    fun read():CctvState=synchronized(lock){prefs.getString("state",null)?.let(CctvState::decode)?:CctvState()}
    private fun update(transform:(CctvState)->CctvState):CctvState = synchronized(lock) {val old=prefs.getString("state",null);val state=transform(read());if(!prefs.edit().putString("state",state.encode()).commit()){prefs.edit().putString("state",old).commit();error("本机记录保存失败，请重试")};state}
    companion object {private val lock=Any()}
    fun favorite(album:CctvAlbum,value:Boolean)=update {it.favorite(album,value)}
    fun watch(album:CctvAlbum,episode:CctvEpisode,position:Long,duration:Long,completed:Boolean)=update {it.watch(album,episode,position,duration,completed)}
}
