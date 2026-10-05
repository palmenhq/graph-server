package app.notes

import app.notes.dtos.CreateNoteRequest
import app.notes.dtos.toDto
import app.utils.http.ClientError
import app.utils.http.ClientException
import app.utils.http.Cursor
import app.utils.http.DEFAULT_PAGE_SIZE
import app.utils.http.ListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val ApplicationCall.workspaceId: String
  get() = parameters["workspaceId"]!!

fun Route.noteRoutes(notesService: NotesService) {
  route("workspaces/{workspaceId}/notes") {
    post("create") {
      val req = call.receive<CreateNoteRequest>()
      val result =
        try {
          notesService.createNote(
            body = req.body,
            workspaceId = call.workspaceId,
            mentions = req.mentions,
          )
        } catch (e: ClientException) {
          return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }

      call.respond(result.toDto())
    }

    get("getAll") {
      val pageSize = call.queryParameters["pageSize"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
      val page =
        try {
          notesService.getAllNotes(
            workspaceId = call.workspaceId,
            cursor = call.queryParameters["cursor"]?.let { Cursor.decode(it) },
            pageSize = pageSize,
          )
        } catch (e: ClientException) {
          return@get call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }
      call.respond(page.map { it.toDto() })
    }

    get("getByMentionedNode") {
      val nodeId =
        call.queryParameters["nodeId"]
          ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Missing nodeId", code = "missing_node_id"),
          )

      val notes =
        try {
          notesService.getNotesMentioningNode(nodeId = nodeId, workspaceId = call.workspaceId)
        } catch (e: ClientException) {
          return@get call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }

      call.respond(ListResponse(notes.map { it.toDto() }))
    }
  }
}
