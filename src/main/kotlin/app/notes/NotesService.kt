package app.notes

import app.graph.GraphService
import app.notes.db.NoteDbo
import app.notes.db.NoteMentionDbo
import app.notes.dtos.MentionRequest
import app.notes.validation.NoSuchMentionedNodeException
import app.utils.ResourceId
import app.utils.toResourceId
import app.workspaces.WorkspaceService
import app.workspaces.validation.NoSuchWorkspaceException
import db.tables.pojos.Note
import db.tables.pojos.NoteMention
import kotlinx.serialization.json.JsonElement
import org.jooq.JSONB
import java.time.OffsetDateTime
import java.util.UUID

class NotesService(
  private val notesRepository: NotesRepository,
  private val workspaceService: WorkspaceService,
  private val graphService: GraphService,
) {
  fun createNote(
    body: JsonElement,
    workspaceId: ResourceId,
    mentions: List<MentionRequest>,
  ): NoteDbo {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()

    val mentionedNodes =
      mentions
        .map { it.nodeId }
        .distinct()
        .map { nodeId ->
          graphService.getNodeById(nodeId = nodeId, workspaceId = workspace.id!!)
            ?: throw NoSuchMentionedNodeException(nodeId)
        }

    val note =
      Note(
        id = newNoteId(),
        body = JSONB.valueOf(body.toString()),
        createdAt = OffsetDateTime.now(),
        workspaceId = workspace.id!!,
      )
    val noteMentions =
      mentionedNodes.map { node ->
        NoteMentionDbo(
          mention =
            NoteMention(
              id = newNoteMentionId(),
              note = note.id,
              node = node.id,
            ),
          node = node,
        )
      }

    notesRepository.createNote(note, noteMentions.map { it.mention })
    return NoteDbo(note = note, mentions = noteMentions)
  }

  fun getNoteById(
    noteId: ResourceId,
    workspaceId: ResourceId,
  ): NoteDbo? = notesRepository.findNoteById(noteId = noteId, workspaceId = workspaceId)

  fun getAllNotes(workspaceId: ResourceId): List<NoteDbo> = notesRepository.findNotes(workspaceId)

  fun getNotesMentioningNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): List<NoteDbo> {
    graphService.getNodeById(nodeId = nodeId, workspaceId = workspaceId) ?: throw NoSuchMentionedNodeException(nodeId)
    return notesRepository.findNotes(workspaceId = workspaceId, mentionedNodeId = nodeId)
  }

  companion object {
    fun newNoteId() =
      UUID
        .randomUUID()
        .toResourceId("nte")

    fun newNoteMentionId() =
      UUID
        .randomUUID()
        .toResourceId("nmn")
  }
}
