package app.notes.db

import db.tables.pojos.Node
import db.tables.pojos.Note
import db.tables.pojos.NoteMention

data class NoteDbo(
  val note: Note,
  val mentions: List<NoteMentionDbo>,
)

data class NoteMentionDbo(
  val mention: NoteMention,
  val node: Node,
)
