package app.notes.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UpdateNoteRequest(
  val id: ResourceId,
  val body: JsonElement,
  val mentions: List<MentionRequest> = emptyList(),
)
