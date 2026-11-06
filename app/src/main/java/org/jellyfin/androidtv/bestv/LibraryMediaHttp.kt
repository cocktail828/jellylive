/* Session-bound authenticated media transport. GPL-2.0. */
package org.jellyfin.androidtv.bestv

import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import kotlin.time.Duration

/** Capture credentials once for this player; check every network hop, including HLS and redirects. */
object LibraryMediaHttp {
    @JvmStatic fun client(api: ApiClient,factory: OkHttpFactory): OkHttpClient {
        val server=requireNotNull(api.baseUrl).toHttpUrl()
        val authorization=AuthorizationHeaderBuilder.buildHeader(api.clientInfo.name,api.clientInfo.version,api.deviceInfo.id,api.deviceInfo.name,api.accessToken)
        val client=factory.createClient(api.httpClientOptions.copy(requestTimeout=Duration.ZERO)).newBuilder()
        // Prepend before the shared legacy-header network interceptor, which copies the resulting Authorization.
        client.networkInterceptors().add(0,okhttp3.Interceptor {chain ->
            val request=chain.request();val url=request.url
            val sameOrigin=url.scheme==server.scheme && url.host==server.host && url.port==server.port
            val next=request.newBuilder().removeHeader("Authorization").removeHeader("X-Emby-Authorization")
            if(sameOrigin)next.header("Authorization",authorization)
            chain.proceed(next.build())
        })
        return client.build()
    }
}
