package org.jellyfin.androidtv.bestv

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.InvalidContentException

fun main()=runBlocking {
    var checks=0
    fun verify(name: String,value: Boolean){check(value){name};checks++;println("PASS $name")}
    var clock=0L
    val cache=CatalogCache<List<Int>>(100){clock}
    val calls=AtomicInteger()
    val started=CompletableDeferred<Unit>();val gate=CompletableDeferred<Unit>()
    val first=async {cache.get("server-A|user|root"){calls.incrementAndGet();started.complete(Unit);gate.await();listOf(1,2)}}
    started.await()
    val second=async(start=CoroutineStart.UNDISPATCHED){cache.get("server-A|user|root"){error("must share in-flight loader")}}
    first.cancelAndJoin();gate.complete(Unit)
    verify("one reader cancellation preserves other reader",second.await()==listOf(1,2)&&calls.get()==1)
    verify("completed snapshot reused",cache.get("server-A|user|root"){error("must use cache")}==listOf(1,2))
    verify("explicit refresh bypasses cached success",cache.get("server-A|user|root",true){listOf(3)}==listOf(3))
    verify("server change never reuses another scope",cache.get("server-B|user|root"){listOf(4)}==listOf(4))
    clock=150
    verify("expired snapshot reloads",cache.get("server-A|user|root"){listOf(5)}==listOf(5))
    cache.invalidate()
    verify("invalidating watch state clears snapshots",cache.get("server-A|user|root"){listOf(6)}==listOf(6))
    val cancelStarted=CompletableDeferred<Unit>();val cancelled=CompletableDeferred<Unit>()
    val abandoned=async {cache.get("abandoned"){try {cancelStarted.complete(Unit);awaitCancellation()}finally{cancelled.complete(Unit)}}}
    cancelStarted.await();abandoned.cancelAndJoin();withTimeout(1000){cancelled.await()}
    verify("last reader leaving cancels abandoned network work",cancelled.isCompleted)
    try {cache.get("failure"){throw CatalogIncompleteException("partial")}}catch(_:CatalogIncompleteException){}
    verify("partial catalog is never cached",cache.get("failure"){listOf(7)}==listOf(7))
    for((status,kind) in listOf(401 to LibraryFailure.SESSION,403 to LibraryFailure.PERMISSION,404 to LibraryFailure.MISSING,500 to LibraryFailure.SERVER,400 to LibraryFailure.CONTENT))
        verify("HTTP $status selects recovery category",LibraryFailure.from(InvalidStatusException(status))==kind)
    verify("nested authorization failure retains category",LibraryFailure.from(IllegalStateException("wrapper",InvalidStatusException(401)))==LibraryFailure.SESSION)
    verify("network failures differ from authorization",LibraryFailure.from(java.net.ConnectException())==LibraryFailure.NETWORK)
    verify("timeouts differ from network failure",LibraryFailure.from(java.net.SocketTimeoutException())==LibraryFailure.TIMEOUT)
    verify("catalog limits cannot pretend library is empty",LibraryFailure.from(CatalogIncompleteException("limit"))==LibraryFailure.INCOMPLETE)
    verify("unexpected data never claims connection failure",LibraryFailure.from(IllegalArgumentException())==LibraryFailure.CONTENT)
    cache.close()
    println("$checks catalog / recovery checks passed")
}
