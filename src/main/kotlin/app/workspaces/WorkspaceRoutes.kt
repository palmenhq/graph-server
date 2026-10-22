package app.workspaces

import app.auth.userSession
import app.utils.http.ClientError
import app.utils.http.ListResponse
import app.utils.http.userId
import app.workspaces.dtos.CreateWorkspaceRequest
import app.workspaces.dtos.toDto
import app.workspaces.validation.NoSuchWorkspaceException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class WorkspaceAccessConfig {
  lateinit var workspaceService: WorkspaceService
}

/**
 * Rejects calls to `{workspaceId}` routes unless the session user is a member of that workspace.
 * Responds exactly like a missing workspace, so the existence of other users' workspaces is not revealed.
 * Install it behind RequireSession.
 */
val RequireWorkspaceAccess =
  createRouteScopedPlugin("RequireWorkspaceAccess", ::WorkspaceAccessConfig) {
    val workspaceService = pluginConfig.workspaceService

    onCall { call ->
      if (call.isHandled) return@onCall

      val userId = call.userSession?.userId
      val workspaceId = call.parameters["workspaceId"]
      if (userId == null || workspaceId == null || !workspaceService.hasAccess(workspaceId, userId)) {
        call.respond(HttpStatusCode.UnprocessableEntity, NoSuchWorkspaceException().toError())
      }
    }
  }

fun Route.workspaceRoutes(workspaceService: WorkspaceService) {
  route("workspaces") {
    post("create") {
      val request = call.receive<CreateWorkspaceRequest>()
      val created = workspaceService.createWorkspace(request.name, ownerId = call.userId)
      call.respond(created.toDto())
    }

    get("/getAll") {
      call.respond(
        ListResponse(workspaceService.getAllForUser(call.userId).map { it.toDto() }),
      )
    }

    get("getById") {
      val id =
        call.queryParameters["id"]
          ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Missing id", code = "missing_id"),
          )

      val workspace =
        workspaceService
          .getByIdForUser(id = id, userId = call.userId) ?: return@get call.respond(
          status = HttpStatusCode.UnprocessableEntity,
          message = ClientError(description = "Workspace not found", code = "workspace_not_found"),
        )

      call.respond(workspace.toDto())
    }
  }
}
