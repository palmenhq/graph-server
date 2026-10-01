package app.workspaces

import app.utils.http.ClientError
import app.utils.http.ListResponse
import app.workspaces.dtos.CreateWorkspaceRequest
import app.workspaces.dtos.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.workspaceRoutes(workspaceService: WorkspaceService) {
  route("workspaces") {
    post("create") {
      val request = call.receive<CreateWorkspaceRequest>()
      val created = workspaceService.createWorkspace(request.name)
      call.respond(created.toDto())
    }

    get("/getAll") {
      call.respond(
        ListResponse(workspaceService.getAll().map { it.toDto() }),
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
          .getById(id) ?: return@get call.respond(
          status = HttpStatusCode.UnprocessableEntity,
          message = ClientError(description = "Workspace not found", code = "workspace_not_found"),
        )

      call.respond(workspace.toDto())
    }
  }
}
