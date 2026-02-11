package workspaces

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import workspaces.dtos.CreateWorkspaceRequest
import workspaces.dtos.toDto

fun Route.workspaceRoutes(workspaceService: WorkspaceService) {
  route("Workspaces") {
    post("createWorkspace") {
      val request = call.receive<CreateWorkspaceRequest>()
      val created = workspaceService.createWorkspace(request.name)
      call.respond(created.toDto())
    }

    get("getWorkspaceById") {
      val id = call.queryParameters["id"]!!
      workspaceService
        .getById(id)
        ?.let { workspace ->
          call.respond(workspace.toDto())
        }
        ?: call.respond(HttpStatusCode.NotFound)
    }
  }
}
