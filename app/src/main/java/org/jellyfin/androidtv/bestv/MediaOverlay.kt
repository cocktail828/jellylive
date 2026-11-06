package org.jellyfin.androidtv.bestv

/** Playback overlay policy, independent of Android rendering and the player clock. */
class MediaOverlay {
    var visible=false; private set
    var paused=false
    var dialog=false
    var blocked=false
    var target: Long?=null; private set
    private var resumeAfterSeek=false
    private var deadline: Long?=null
    val seeking get()=target!=null
    fun show(now: Long){visible=true;interact(now)}
    fun hide(){visible=false;deadline=null}
    fun interact(now: Long){deadline=if(visible&&!paused&&!dialog&&!seeking&&!blocked)now+5000 else null}
    fun remaining(now: Long): Long?=deadline?.let {(it-now).coerceAtLeast(0)}
    fun expire(now: Long): Boolean {
        if(visible&&!paused&&!dialog&&!seeking&&!blocked&&deadline?.let {now>=it}==true){hide();return true}
        return false
    }
    fun begin(position: Long,playing: Boolean){if(!seeking){target=position;resumeAfterSeek=playing};visible=true;deadline=null}
    fun preview(position: Long,duration: Long){if(seeking&&duration>0)target=position.coerceIn(0,duration)}
    data class Seek(val position: Long,val resume: Boolean)
    fun commit(): Seek? {val position=target ?: return null;target=null;return Seek(position,resumeAfterSeek)}
    fun cancel(): Boolean? {if(!seeking)return null;target=null;return resumeAfterSeek}
}
