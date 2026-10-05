package app.superadmin

import app.auth.AuthService
import app.superadmin.dtos.CreateUserRequest
import app.superadmin.dtos.UserDto
import app.utils.http.ClientError
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jooq.exception.IntegrityConstraintViolationException
import java.security.MessageDigest

const val SUPERADMIN_TOKEN_HEADER = "X-Superadmin-Token"

private val logger = KotlinLogging.logger { }

class SuperadminTokenConfig {
  /**
   * The expected token. When null or blank, every request is rejected (fail closed).
   */
  var secret: String? = null
}

/**
 * Rejects calls whose [SUPERADMIN_TOKEN_HEADER] does not match the configured secret with 401.
 */
val RequireSuperadminToken =
  createRouteScopedPlugin("RequireSuperadminToken", ::SuperadminTokenConfig) {
    val expected = pluginConfig.secret?.takeIf { it.isNotBlank() }?.let(::sha256)

    if (expected == null) {
      logger.warn { "Superadmin token is not configured, rejecting all requests" }
    }

    onCall { call ->
      val provided = call.request.headers[SUPERADMIN_TOKEN_HEADER]
      // Comparing fixed-length digests in constant time avoids leaking the secret or its length via timing
      val valid = expected != null && provided != null && MessageDigest.isEqual(expected, sha256(provided))
      if (!valid) {
        call.respond(
          HttpStatusCode.Unauthorized,
          ClientError(description = "Missing or invalid superadmin token", code = "unauthorized"),
        )
      }
    }
  }

private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

fun Route.superadminRoutes(authService: AuthService) {
  route("users") {
    post("createUser") {
      val req = call.receive<CreateUserRequest>()
      if (req.username.isBlank() || req.password.isBlank()) {
        return@post call.respond(
          HttpStatusCode.UnprocessableEntity,
          ClientError(description = "username and password must not be blank", code = "invalid_request"),
        )
      }

      val user =
        try {
          authService.createUser(username = req.username, password = req.password)
        } catch (_: IntegrityConstraintViolationException) {
          return@post call.respond(
            HttpStatusCode.Conflict,
            ClientError(description = "Username is already taken", code = "username_taken"),
          )
        }

      call.respond(HttpStatusCode.Created, UserDto(id = user.id!!, username = user.username!!))
    }
  }
}
