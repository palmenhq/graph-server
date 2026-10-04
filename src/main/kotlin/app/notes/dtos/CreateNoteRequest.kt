package app.notes.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CreateNoteRequest(
  val body: JsonElement, // TODO type defs of tiptap editor's JSONContent?
  val mentions: List<MentionRequest> = emptyList(),
)

@Serializable
data class MentionRequest(
  val nodeId: ResourceId,
)
