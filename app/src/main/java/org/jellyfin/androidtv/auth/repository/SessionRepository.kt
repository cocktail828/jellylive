package org.jellyfin.androidtv.auth.repository

import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import org.jellyfin.sdk.Jellyfin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.preference.PreferencesRepository
import org.jellyfin.androidtv.preference.TelemetryPreferences
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.DISABLED
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.LAST_USER
import org.jellyfin.androidtv.preference.constant.UserSelectBehavior.SPECIFIC_USER
import org.jellyfin.androidtv.util.sdk.forUser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

data class Session(
	val userId: UUID,
	val serverId: UUID,
	val accessToken: String,
)

enum class SessionRepositoryState {
	READY,
	RESTORING_SESSION,
	SWITCHING_SESSION,
}

interface SessionRepository {
	val currentSession: StateFlow<Session?>
	val state: StateFlow<SessionRepositoryState>

	suspend fun restoreSession(destroyOnly: Boolean)
	suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean
	fun destroyCurrentSession()
}

class SessionRepositoryImpl(
	private val authenticationPreferences: AuthenticationPreferences,
	private val authenticationStore: AuthenticationStore,
	private val userApiClient: ApiClient,
	private val preferencesRepository: PreferencesRepository,
	private val defaultDeviceInfo: DeviceInfo,
	private val userRepository: UserRepository,
	private val serverRepository: ServerRepository,
	private val telemetryPreferences: TelemetryPreferences,
	private val jellyfin: Jellyfin,
) : SessionRepository {
	private val currentSessionMutex = Mutex()
	private val preferenceScope = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
	private var preferenceRefresh: Job? = null
	private val _currentSession = MutableStateFlow<Session?>(null)
	override val currentSession = _currentSession.asStateFlow()
	private val _state = MutableStateFlow(SessionRepositoryState.READY)
	override val state = _state.asStateFlow()

	override suspend fun restoreSession(destroyOnly: Boolean): Unit = withContext(NonCancellable) {
		currentSessionMutex.withLock {
			Timber.i("Restoring session")

			_state.value = SessionRepositoryState.RESTORING_SESSION

			val alwaysAuthenticate = authenticationPreferences[AuthenticationPreferences.alwaysAuthenticate]
			val autoLoginBehavior = authenticationPreferences[AuthenticationPreferences.autoLoginUserBehavior]

			when {
				alwaysAuthenticate -> destroyCurrentSession()
				autoLoginBehavior == DISABLED -> destroyCurrentSession()
				autoLoginBehavior == LAST_USER && !destroyOnly -> setCurrentSession(createLastUserSession())
				autoLoginBehavior == SPECIFIC_USER && !destroyOnly -> {
					val serverId = authenticationPreferences[AuthenticationPreferences.autoLoginServerId].toUUIDOrNull()
					val userId = authenticationPreferences[AuthenticationPreferences.autoLoginUserId].toUUIDOrNull()
					if (serverId != null && userId != null) setCurrentSession(createUserSession(serverId, userId))
				}
			}

			_state.value = SessionRepositoryState.READY
		}
	}

	override suspend fun switchCurrentSession(serverId: UUID, userId: UUID): Boolean = currentSessionMutex.withLock {
        _state.value = SessionRepositoryState.SWITCHING_SESSION
        try {
            val session = createUserSession(serverId, userId) ?: return@withLock false
            // A refreshed token for the same server/user must still be applied.
            setCurrentSession(session)
        } finally { _state.value = SessionRepositoryState.READY }
    }

	override fun destroyCurrentSession() {
		Timber.i("Destroying current session")

		userRepository.setCurrentUser(null)
		serverRepository.setCurrentServer(null)
		_currentSession.value = null
		_state.value = SessionRepositoryState.READY
	}

    private suspend fun setCurrentSession(session: Session?): Boolean {
        if(session==null){destroyCurrentSession();userApiClient.applySession(null);return true}
        val previous=currentSession.value
        val server=serverRepository.getServer(session.serverId,true) ?: return false
        if(!server.versionSupported)return false
        val deviceInfo=defaultDeviceInfo.forUser(session.userId)
        // Authenticate with an isolated client. Failure/cancellation cannot modify the active origin/token.
        val candidate=jellyfin.createApi(server.address,accessToken=session.accessToken,deviceInfo=deviceInfo)
        val user=try {withContext(Dispatchers.IO){candidate.userApi.getCurrentUser().content}}
        catch(error:ApiClientException){Timber.w("Unable to verify candidate session");return false}
        coroutineContext.ensureActive()
        return withContext(Dispatchers.Main.immediate) {
            ensureActive()
            if(currentSession.value!=previous)return@withContext false
            // No suspensions between applying the new API binding and publishing the verified session.
            if(!userApiClient.applySession(session,deviceInfo))return@withContext false
            preferenceRefresh?.cancel();preferencesRepository.invalidateSession()
            userRepository.setCurrentUser(user);serverRepository.setCurrentServer(server)
            authenticationPreferences[AuthenticationPreferences.lastServerId]=session.serverId.toString()
            authenticationPreferences[AuthenticationPreferences.lastUserId]=session.userId.toString()
            telemetryPreferences[TelemetryPreferences.crashReportUrl]=userApiClient.clientLogApi.logFileUrl()
            telemetryPreferences[TelemetryPreferences.crashReportToken]=session.accessToken
            _currentSession.value=session
            // Display preference reads are optional and never delay or undo a successful connection.
            preferenceRefresh=preferenceScope.launch {
                try {preferencesRepository.onSessionChanged()}
                catch(error:CancellationException){throw error}
                catch(_:Exception){Timber.w("Unable to refresh optional display preferences")}
            }
            true
        }
    }

	private fun createLastUserSession(): Session? {
		val lastUserId = authenticationPreferences[AuthenticationPreferences.lastUserId].toUUIDOrNull()
		val lastServerId = authenticationPreferences[AuthenticationPreferences.lastServerId].toUUIDOrNull()

		return if (lastUserId != null && lastServerId != null) createUserSession(lastServerId, lastUserId)
		else null
	}

	private fun createUserSession(serverId: UUID, userId: UUID): Session? {
		val account = authenticationStore.getUser(serverId, userId)
		if (account?.accessToken == null) return null

		return Session(
			userId = userId,
			serverId = serverId,
			accessToken = account.accessToken
		)
	}

	private fun ApiClient.applySession(session: Session?, newDeviceInfo: DeviceInfo = defaultDeviceInfo): Boolean {
		if (session == null) {
			update(
				baseUrl = null,
				accessToken = null,
				deviceInfo = newDeviceInfo,
			)
		} else {
			val server = authenticationStore.getServer(session.serverId)
				?: return false

			update(
				baseUrl = server.address,
				accessToken = session.accessToken,
				deviceInfo = newDeviceInfo,
			)
		}

		return true
	}
}
