package app.auth

import app.utils.ResourceId
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.get
import io.ktor.server.sessions.serialization.KotlinxSessionSerializer
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SESSION_HEADER = "X-Session-Token"

@Serializable
data class UserSession(
  val userId: ResourceId,
)

val userSessionSerializer: SessionSerializer<UserSession> = KotlinxSessionSerializer(Json)

val ApplicationCall.userSession: UserSession?
  get() = sessions.get<UserSession>()
