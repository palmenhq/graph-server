package app.notes

import app.notes.dtos.CreateNoteRequest
import app.notes.dtos.UpdateNoteRequest
import app.notes.dtos.toDto
import app.utils.http.ClientError
import app.utils.http.ClientException
import app.utils.http.ListResponse
import app.utils.http.workspaceId
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.noteRoutes(notesService: NotesService) {
  route("notes") {
    post("create") {
      val req = call.receive<CreateNoteRequest>()
      val result =
        try {
          notesService.createNote(
            body = req.body,
            nodeId = req.nodeId,
            workspaceId = call.workspaceId,
            mentions = req.mentions,
          )
        } catch (e: ClientException) {
          return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }

      call.respond(result.toDto())
    }

    post("update") {
      val req = call.receive<UpdateNoteRequest>()
      val result =
        try {
          notesService.updateNote(
            noteId = req.id,
            body = req.body,
            workspaceId = call.workspaceId,
            mentions = req.mentions,
          )
        } catch (e: ClientException) {
          return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }

      call.respond(result.toDto())
    }

    get("getNotesForNode") {
      val nodeId =
        call.queryParameters["nodeId"]
          ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Missing nodeId", code = "missing_node_id"),
          )

      val notes =
        try {
          notesService.getNotesForNode(nodeId = nodeId, workspaceId = call.workspaceId)
        } catch (e: ClientException) {
          return@get call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
        }

      call.respond(ListResponse(notes.map { it.toDto() }))
    }
  }
}
