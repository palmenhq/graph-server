package app.notes.dtos

import app.notes.db.NoteDbo
import app.notes.db.NoteMentionDbo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class NoteDto(
  val id: String,
  val body: JsonElement,
  val mentions: List<NoteMentionDto>,
)

@Serializable
data class NoteMentionDto(
  val id: String,
  val nodeId: String,
  val nodeTitle: String,
)

fun NoteDbo.toDto(): NoteDto =
  NoteDto(
    id = note.id!!,
    body = Json.parseToJsonElement(note.body!!.data()),
    mentions = mentions.map { it.toDto() },
  )

fun NoteMentionDbo.toDto(): NoteMentionDto =
  NoteMentionDto(
    id = mention.id!!,
    nodeId = node.id!!,
    nodeTitle = node.title!!,
  )
