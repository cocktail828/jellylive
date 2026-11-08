package org.jellyfin.androidtv.bestv

import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/** Presentation categories use status/type, never raw server messages or credentials. */
enum class LibraryFailure {
    SESSION, PERMISSION, MISSING, TIMEOUT, NETWORK, SERVER, INCOMPLETE, CONTENT;
    companion object {
        fun from(error: Throwable): LibraryFailure {
            var cause: Throwable?=error
            repeat(12) {
                when(val current=cause) {
                    is InvalidStatusException -> return when(current.status){401->SESSION;403->PERMISSION;404->MISSING;in 500..599->SERVER;else->CONTENT}
                    is TimeoutCancellationException, is TimeoutException, is SocketTimeoutException -> return TIMEOUT
                    is InvalidContentException -> return CONTENT
                    is CatalogIncompleteException -> return INCOMPLETE
                }
                cause=cause?.cause
            }
            return if(error is IOException || generateSequence(error.cause){it.cause}.take(12).any {it is IOException}) NETWORK else CONTENT
        }
    }
}
class CatalogIncompleteException(message: String): IllegalStateException(message)
