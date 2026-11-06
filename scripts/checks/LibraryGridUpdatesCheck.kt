package org.jellyfin.androidtv.bestv
fun main(){
 var count=0
 fun verify(name: String,value: Boolean){check(value){name};count++;println("PASS $name")}
 val a=LibraryBrowse.Query();val b=a.copy(resolution=2160);val c=a.copy(year=2024)
 val updates=LibraryGridUpdates()
 val first=updates.begin(a,0)!!;verify("first response accepted",updates.accept(first,0)&&updates.hasResult)
 verify("cached response avoids redraw",updates.begin(a,0)==null)
 val pending=updates.begin(b,0)!!;verify("back to cached A avoids redraw",updates.begin(a,0)==null)
 verify("A B A cannot display stale B",!updates.accept(pending,0))
 val older=updates.begin(b,0)!!;val latest=updates.begin(c,0)!!
 verify("old query rejected",!updates.accept(older,0));verify("latest query accepted",updates.accept(latest,0))
 val page1=updates.begin(c,6)!!;val page2=updates.begin(c,12)!!
 verify("old page rejected",!updates.accept(page1,6));verify("new page accepted",updates.accept(page2,12))
 val clamp=updates.begin(c,60)!!;verify("clamped page accepted",updates.accept(clamp,6));verify("clamped page is cached",updates.begin(c,6)==null)
 println("$count async grid update checks passed")
}
