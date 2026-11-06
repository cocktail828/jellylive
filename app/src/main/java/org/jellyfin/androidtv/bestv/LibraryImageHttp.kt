package org.jellyfin.androidtv.bestv

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory

/** Bind each image call to its starting session; never send credentials across origins. */
object LibraryImageHttp {
    private class Binding(val server: HttpUrl?, val authorization: String?)
    private fun HttpUrl.sameOrigin(other: HttpUrl?) =
        other != null && scheme == other.scheme && host == other.host && port == other.port

    fun client(api: ApiClient, factory: OkHttpFactory, options: HttpClientOptions): OkHttpClient {
        val builder = factory.createClient(options).newBuilder()
        builder.interceptors().add(0) { chain ->
            val request = chain.request()
            val server = api.baseUrl?.toHttpUrlOrNull()
            val authorization = if (request.url.sameOrigin(server) && !api.accessToken.isNullOrBlank())
                AuthorizationHeaderBuilder.buildHeader(api.clientInfo.name, api.clientInfo.version,
                    api.deviceInfo.id, api.deviceInfo.name, api.accessToken)
            else null
            chain.proceed(request.newBuilder().tag(Binding::class.java, Binding(server, authorization)).build())
        }
        builder.networkInterceptors().add(0) { chain ->
            val request = chain.request()
            val binding = request.tag(Binding::class.java)
            val next = request.newBuilder().removeHeader("Authorization")
                .removeHeader("X-Emby-Authorization").removeHeader("X-Emby-Token")
            if (binding?.authorization != null && request.url.sameOrigin(binding.server))
                next.header("Authorization", binding.authorization)
            chain.proceed(next.build())
        }
        return builder.build()
    }
}
