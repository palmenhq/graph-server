package app.notes

import app.notes.db.NoteDbo
import app.notes.db.NoteMentionDbo
import app.utils.ResourceId
import app.utils.http.Cursor
import db.tables.daos.NoteDao
import db.tables.daos.NoteMentionDao
import db.tables.pojos.Node
import db.tables.pojos.Note
import db.tables.pojos.NoteMention
import db.tables.references.NODE
import db.tables.references.NOTE
import db.tables.references.NOTE_MENTION
import org.jooq.DSLContext
import org.jooq.impl.DSL

class NotesRepository(
  private val dsl: DSLContext,
) {
  fun createNote(
    note: Note,
    mentions: List<NoteMention>,
  ) {
    dsl.transaction { config ->
      NoteDao(config).insert(note)
      if (mentions.isNotEmpty()) {
        NoteMentionDao(config).insert(mentions)
      }
    }
  }

  fun findNoteById(
    noteId: ResourceId,
    workspaceId: ResourceId,
  ): NoteDbo? {
    val note =
      dsl
        .selectFrom(NOTE)
        .where(NOTE.ID.eq(noteId))
        .and(NOTE.WORKSPACE_ID.eq(workspaceId))
        .fetchOneInto(Note::class.java) ?: return null

    return withMentions(listOf(note)).single()
  }

  fun findNotes(
    workspaceId: ResourceId,
    mentionedNodeId: ResourceId? = null,
    cursor: Cursor? = null,
    limit: Int? = null,
  ): List<NoteDbo> {
    val notes =
      dsl
        .selectFrom(NOTE)
        .where(NOTE.WORKSPACE_ID.eq(workspaceId))
        .and(
          if (mentionedNodeId != null) {
            NOTE.ID.`in`(
              DSL
                .select(NOTE_MENTION.NOTE)
                .from(NOTE_MENTION)
                .where(NOTE_MENTION.NODE.eq(mentionedNodeId)),
            )
          } else {
            DSL.noCondition()
          },
        ).and(cursor?.after(NOTE.CREATED_AT, NOTE.ID) ?: DSL.noCondition())
        .orderBy(NOTE.CREATED_AT.desc(), NOTE.ID)
        .let { if (limit != null) it.limit(limit) else it }
        .fetchInto(Note::class.java)

    return withMentions(notes)
  }

  private fun withMentions(notes: List<Note>): List<NoteDbo> {
    if (notes.isEmpty()) return emptyList()

    val mentionsByNote =
      dsl
        .select(*NOTE_MENTION.fields(), *NODE.fields())
        .from(NOTE_MENTION)
        .innerJoin(NODE)
        .on(NOTE_MENTION.NODE.eq(NODE.ID))
        .where(NOTE_MENTION.NOTE.`in`(notes.map { it.id }))
        .and(NODE.DELETED_AT.isNull)
        .orderBy(NOTE_MENTION.ID)
        .fetch { row ->
          NoteMentionDbo(
            mention = row.into(NOTE_MENTION).into(NoteMention::class.java),
            node = row.into(NODE).into(Node::class.java),
          )
        }.groupBy { it.mention.note }

    return notes.map { NoteDbo(note = it, mentions = mentionsByNote[it.id].orEmpty()) }
  }
}
