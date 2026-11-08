package org.jellyfin.androidtv.bestv

import kotlinx.coroutines.*

/** One complete snapshot per scope; concurrent home/grid/search readers share actual work.
 * The final reader leaving cancels the request. Partial pages and failures are never cached.
 */
class CatalogCache<T>(private val ttlMillis: Long=120_000, private val now: ()->Long={System.nanoTime()/1_000_000}) {
    private class Flight<T>(val value: Deferred<T>, var readers: Int=0)
    private data class Snapshot<T>(val value: T,val at: Long)
    private val lock=Any()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val flights=mutableMapOf<String,Flight<T>>()
    private val snapshots=mutableMapOf<String,Snapshot<T>>()
    suspend fun get(key: String, refresh: Boolean=false, loader: suspend ()->T): T {
        val flight=synchronized(lock) {
            val existing=flights[key]
            if(existing!=null){existing.readers++;existing}
            else {
                val cached=snapshots[key]
                if(!refresh&&cached!=null&&now()-cached.at<ttlMillis)return cached.value
                Flight(scope.async(start=CoroutineStart.LAZY){loader()}).also {it.readers=1;flights[key]=it}
            }
        }
        try {
            val result=flight.value.await()
            synchronized(lock){if(flights[key]===flight){snapshots[key]=Snapshot(result,now());while(snapshots.size>4)snapshots.remove(snapshots.keys.first())}}
            return result
        } finally {
            synchronized(lock){
                flight.readers--
                if(flight.readers==0&&flights[key]===flight){flights.remove(key);if(!flight.value.isCompleted)flight.value.cancel()}
            }
        }
    }
    fun invalidate(){synchronized(lock){snapshots.clear()}}
    fun close(){scope.cancel();synchronized(lock){flights.clear();snapshots.clear()}}
}
