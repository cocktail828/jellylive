package org.jellyfin.androidtv.bestv
fun main(){
 var count=0
 fun verify(name: String,condition: Boolean){check(condition){name};count++;println("PASS $name")}
 val state=MediaOverlay()
 verify("controls hidden initially",!state.visible)
 state.show(0);verify("playing visible for five seconds",state.remaining(0)==5000L&&!state.expire(4999))
 verify("idle collapses",state.expire(5000)&&!state.visible)
 state.show(10000);state.interact(14000);verify("input extends deadline",!state.expire(15000)&&state.expire(19000))
 state.paused=true;state.show(20000);verify("pause holds controls",state.remaining(20000)==null&&!state.expire(100000))
 state.hide();verify("manual hide preserves pause",!state.visible&&state.paused)
 state.paused=false;state.show(110000);verify("resume rearms",state.remaining(110000)==5000L)
 state.dialog=true;state.interact(111000);verify("dialog suspends idle",!state.expire(200000))
 state.dialog=false;state.interact(210000);verify("dialog close grants full idle period",state.remaining(210000)==5000L)
 state.begin(20000,true);state.paused=true
 state.preview(60000,100000);verify("drag previews target",state.target==60000L&&state.seeking&&!state.expire(999999))
 state.preview(40000,100000);verify("drag backward previews target",state.target==40000L)
 val commit=state.commit();verify("release commits one target and restores playing",commit==MediaOverlay.Seek(40000,true)&&!state.seeking)
 verify("duplicate release does nothing",state.commit()==null)
 state.begin(50000,false);state.preview(80000,100000);verify("cancel preserves paused state",state.cancel()==false&&!state.seeking)
 state.begin(50000,true);state.preview(-10000,100000);verify("rewind clamps to zero",state.target==0L)
 state.preview(200000,100000);verify("forward clamps to duration",state.target==100000L)
 verify("cancel returns original playing state",state.cancel()==true)
 state.blocked=true;state.show(0);verify("errors and ended hold actions",!state.expire(100000))
 state.blocked=false;state.paused=false;state.idleMillis=30000;state.show(0)
 verify("accessibility timeout extends actual policy",!state.expire(5000)&&!state.expire(29999)&&state.expire(30000))
 state.paused=true;state.show(100);verify("extended timeout still respects pause",!state.expire(100000))
 println("$count media overlay checks passed")
}
