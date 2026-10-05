package app.auth

import app.auth.dtos.TokenRequest
import app.auth.dtos.TokenResponse
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
  }
}
