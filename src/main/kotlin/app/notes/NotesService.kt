package app.notes

import app.graph.GraphService
import app.graph.validation.NoSuchNodeException
import app.notes.db.NoteDbo
import app.notes.db.NoteMentionDbo
import app.notes.dtos.MentionRequest
import app.notes.validation.NoSuchMentionedNodeException
import app.notes.validation.NoSuchNoteException
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
    nodeId: ResourceId,
    workspaceId: ResourceId,
    mentions: List<MentionRequest>,
  ): NoteDbo {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val node = graphService.getNodeById(nodeId = nodeId, workspaceId = workspaceId) ?: throw NoSuchNodeException(nodeId)

    val noteId = newNoteId()
    val noteMentions = buildMentions(noteId = noteId, workspaceId = workspace.id!!, mentions = mentions)
    val note =
      Note(
        id = noteId,
        nodeId = nodeId,
        body = JSONB.valueOf(body.toString()),
        createdAt = OffsetDateTime.now(),
        workspaceId = workspace.id!!,
      )

    notesRepository.createNote(note, noteMentions.map { it.mention })
    return NoteDbo(note = note, node = node, mentions = noteMentions)
  }

  fun updateNote(
    noteId: ResourceId,
    body: JsonElement,
    workspaceId: ResourceId,
    mentions: List<MentionRequest>,
  ): NoteDbo {
    notesRepository.findNoteById(noteId = noteId, workspaceId = workspaceId) ?: throw NoSuchNoteException(noteId)

    val noteMentions = buildMentions(noteId = noteId, workspaceId = workspaceId, mentions = mentions)

    if (
      !notesRepository.updateNote(
        noteId = noteId,
        workspaceId = workspaceId,
        body = JSONB.valueOf(body.toString()),
        updatedAt = OffsetDateTime.now(),
        mentions = noteMentions.map { it.mention },
      )
    ) {
      throw NoSuchNoteException(noteId)
    }

    return notesRepository.findNoteById(noteId = noteId, workspaceId = workspaceId) ?: throw NoSuchNoteException(noteId)
  }

  fun getNoteById(
    noteId: ResourceId,
    workspaceId: ResourceId,
  ): NoteDbo? = notesRepository.findNoteById(noteId = noteId, workspaceId = workspaceId)

  fun getNotesForNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): List<NoteDbo> {
    graphService.getNodeById(nodeId = nodeId, workspaceId = workspaceId) ?: throw NoSuchMentionedNodeException(nodeId)
    return notesRepository.findNotes(workspaceId = workspaceId, nodeId = nodeId)
  }

  private fun buildMentions(
    noteId: ResourceId,
    workspaceId: ResourceId,
    mentions: List<MentionRequest>,
  ): List<NoteMentionDbo> =
    mentions
      .map { it.nodeId }
      .distinct()
      .map { mentionedNodeId ->
        val node =
          graphService.getNodeById(nodeId = mentionedNodeId, workspaceId = workspaceId)
            ?: throw NoSuchMentionedNodeException(mentionedNodeId)
        NoteMentionDbo(
          mention =
            NoteMention(
              id = newNoteMentionId(),
              note = noteId,
              node = node.id,
            ),
          node = node,
        )
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
