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
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.OffsetDateTime

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

  fun updateNote(
    noteId: ResourceId,
    workspaceId: ResourceId,
    body: JSONB,
    updatedAt: OffsetDateTime,
    mentions: List<NoteMention>,
  ): Boolean =
    dsl.transactionResult { config ->
      val tx = DSL.using(config)
      val updated =
        tx
          .update(NOTE)
          .set(NOTE.BODY, body)
          .set(NOTE.UPDATED_AT, updatedAt)
          .where(NOTE.ID.eq(noteId))
          .and(NOTE.WORKSPACE_ID.eq(workspaceId))
          .execute() > 0

      if (updated) {
        tx.deleteFrom(NOTE_MENTION).where(NOTE_MENTION.NOTE.eq(noteId)).execute()
        if (mentions.isNotEmpty()) {
          NoteMentionDao(config).insert(mentions)
        }
      }

      updated
    }

  fun findNoteById(
    noteId: ResourceId,
    workspaceId: ResourceId,
  ): NoteDbo? {
    val note =
      dsl
        .select(*NOTE.fields(), *NODE.fields())
        .from(NOTE)
        .leftJoin(NODE)
        .on(NOTE.NODE_ID.eq(NODE.ID))
        .where(NOTE.ID.eq(noteId))
        .and(NOTE.WORKSPACE_ID.eq(workspaceId))
        .fetchOne(::noteAndNode) ?: return null

    return withMentions(listOf(note)).single()
  }

  fun findNotes(
    workspaceId: ResourceId,
    nodeId: ResourceId,
    cursor: Cursor? = null,
    limit: Int? = null,
  ): List<NoteDbo> {
    val notes =
      dsl
        .select(*NOTE.fields(), *NODE.fields())
        .from(NOTE)
        .leftJoin(NODE)
        .on(NOTE.NODE_ID.eq(NODE.ID))
        .where(NOTE.WORKSPACE_ID.eq(workspaceId))
        .and(
          NOTE.ID
            .`in`(
              DSL
                .select(NOTE_MENTION.NOTE)
                .from(NOTE_MENTION)
                .where(NOTE_MENTION.NODE.eq(nodeId)),
            ).or(
              NOTE.NODE_ID.eq(nodeId),
            ),
        ).and(cursor?.after(NOTE.CREATED_AT, NOTE.ID) ?: DSL.noCondition())
        .orderBy(NOTE.CREATED_AT.desc(), NOTE.ID)
        .let { if (limit != null) it.limit(limit) else it }
        .fetch(::noteAndNode)

    return withMentions(notes)
  }

  private fun noteAndNode(row: Record): Pair<Note, Node?> =
    row.into(NOTE).into(Note::class.java) to
      row.into(NODE).into(Node::class.java).takeIf { it.id != null }

  private fun withMentions(notes: List<Pair<Note, Node?>>): List<NoteDbo> {
    if (notes.isEmpty()) return emptyList()

    val mentionsByNote =
      dsl
        .select(*NOTE_MENTION.fields(), *NODE.fields())
        .from(NOTE_MENTION)
        .innerJoin(NODE)
        .on(NOTE_MENTION.NODE.eq(NODE.ID))
        .where(NOTE_MENTION.NOTE.`in`(notes.map { it.first.id }))
        .and(NODE.DELETED_AT.isNull)
        .orderBy(NOTE_MENTION.ID)
        .fetch { row ->
          NoteMentionDbo(
            mention = row.into(NOTE_MENTION).into(NoteMention::class.java),
            node = row.into(NODE).into(Node::class.java),
          )
        }.groupBy { it.mention.note }

    return notes.map { (note, node) ->
      NoteDbo(note = note, node = node, mentions = mentionsByNote[note.id].orEmpty())
    }
  }
}
