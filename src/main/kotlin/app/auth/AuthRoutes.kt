package app.auth

import app.auth.dtos.CompletePasswordResetRequest
import app.auth.dtos.TokenRequest
import app.auth.dtos.TokenResponse
import app.auth.dtos.ValidatePasswordResetRequest
import app.utils.http.ClientError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Rejects calls without a valid session with 401. Install it on routes that require authentication.
 */
val RequireSession =
  createRouteScopedPlugin("RequireSession") {
    onCall { call ->
      if (call.userSession == null) {
        call.respond(
          HttpStatusCode.Unauthorized,
          ClientError(description = "Missing or invalid session token", code = "unauthorized"),
        )
      }
    }
  }

fun Route.authRoutes(authService: AuthService) {
  route("auth") {
    post("token") {
      val req = call.receive<TokenRequest>()
      val token =
        authService.createSession(username = req.username, password = req.password)
          ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            ClientError(description = "Invalid username or password", code = "invalid_credentials"),
          )

      call.respond(TokenResponse(token = token))
    }

    post("password-reset/validate") {
      val req = call.receive<ValidatePasswordResetRequest>()
      if (!authService.validatePasswordReset(requestId = req.requestId, token = req.token)) {
        return@post call.respond(
          HttpStatusCode.UnprocessableEntity,
          ClientError(description = "Invalid or expired password reset token", code = "invalid_reset_token"),
        )
      }

      call.respond(HttpStatusCode.NoContent)
    }

    post("password-reset") {
      val req = call.receive<CompletePasswordResetRequest>()
      if (req.password.isBlank()) {
        return@post call.respond(
          HttpStatusCode.UnprocessableEntity,
          ClientError(description = "password must not be blank", code = "invalid_request"),
        )
      }

      if (!authService.resetPassword(requestId = req.requestId, token = req.token, newPassword = req.password)) {
        return@post call.respond(
          HttpStatusCode.UnprocessableEntity,
          ClientError(description = "Invalid or expired password reset token", code = "invalid_reset_token"),
        )
      }

      call.respond(HttpStatusCode.NoContent)
    }
  }
}
