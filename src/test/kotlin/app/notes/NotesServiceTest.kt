package app.notes

import TestHelp
import app.graph.GraphRepository
import app.graph.GraphService
import app.graph.validation.NoSuchNodeException
import app.notes.dtos.MentionRequest
import app.notes.dtos.toDto
import app.notes.validation.NoSuchMentionedNodeException
import app.notes.validation.NoSuchNoteException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NotesServiceTest {
  val graphService = GraphService(GraphRepository(TestHelp.dslContext), TestHelp.workspaceService)
  val notesService = NotesService(NotesRepository(TestHelp.dslContext), TestHelp.workspaceService, graphService)

  private fun body(text: String): JsonElement =
    Json.parseToJsonElement(
      """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"$text"}]}]}""",
    )

  @Test
  fun `creates note with mentions and gets it`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val created =
      notesService.createNote(
        body = body("Met Alice and Bob"),
        nodeId = alice.id!!,
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(alice.id!!), MentionRequest(bob.id!!)),
      )

    assertEquals(workspace.id, created.note.workspaceId)
    assertEquals(setOf(alice.id, bob.id), created.mentions.map { it.node.id }.toSet())
    assertTrue(created.mentions.all { it.mention.note == created.note.id })

    val retrieved = notesService.getNoteById(created.note.id!!, workspaceId = workspace.id!!)!!

    assertEquals(created.note.id, retrieved.note.id)
    assertEquals(alice.id, retrieved.node?.id)
    assertEquals(body("Met Alice and Bob"), retrieved.toDto().body)
    assertEquals(
      setOf(alice.id to "Alice", bob.id to "Bob"),
      retrieved
        .toDto()
        .mentions
        .map { it.nodeId to it.nodeTitle }
        .toSet(),
    )
    assertEquals(
      created.mentions.map { it.mention.id }.toSet(),
      retrieved.mentions.map { it.mention.id }.toSet(),
    )
  }

  @Test
  fun `creates note without mentions`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val created =
      notesService.createNote(
        body = body("Plain"),
        nodeId = alice.id!!,
        workspaceId = workspace.id!!,
        mentions = emptyList(),
      )
    val notes = notesService.getNotesForNode(nodeId = alice.id!!, workspaceId = workspace.id!!)

    assertEquals(listOf(created.note.id), notes.map { it.note.id })
    assertEquals(alice.id, notes.single().node?.id)
    assertEquals(emptyList<Any>(), notes.single().mentions)
    assertEquals(body("Plain"), notes.single().toDto().body)
  }

  @Test
  fun `deduplicates mentions of the same node`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)

    val created =
      notesService.createNote(
        body = body("Alice, Alice"),
        nodeId = alice.id!!,
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(alice.id!!), MentionRequest(alice.id!!)),
      )

    assertEquals(1, created.mentions.size)
    assertEquals(1, notesService.getNoteById(created.note.id!!, workspace.id!!)!!.mentions.size)
  }

  @Test
  fun `updates note body timestamp and replaces mentions`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val carol = graphService.createNode(nodeType.id!!, "Carol", workspaceId = workspace.id!!)
    val created =
      notesService.createNote(
        body = body("Before"),
        nodeId = alice.id!!,
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(bob.id!!)),
      )

    assertNull(created.toDto().updatedAt)

    val updated =
      notesService.updateNote(
        noteId = created.note.id!!,
        body = body("After"),
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(carol.id!!), MentionRequest(carol.id!!)),
      )

    assertEquals(body("After"), updated.toDto().body)
    assertEquals(alice.id, updated.node?.id)
    assertEquals(listOf(carol.id), updated.mentions.map { it.node.id })
    assertEquals(1, updated.mentions.size)
    assertNotNull(updated.toDto().updatedAt)
    assertEquals(
      setOf(carol.id),
      updated.mentions.map { it.node.id }.toSet(),
    )
    assertTrue(updated.mentions.none { it.mention.id in created.mentions.map { mention -> mention.mention.id } })
  }

  @Test
  fun `cannot update an unknown note or a note from another workspace`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = otherWorkspace.id!!)
    val created = notesService.createNote(body("Secret"), alice.id!!, otherWorkspace.id!!, emptyList())

    assertThrows<NoSuchNoteException> {
      notesService.updateNote(
        noteId = "nte:does-not-exist",
        body = body("Changed"),
        workspaceId = workspace.id!!,
        mentions = emptyList(),
      )
    }
    assertThrows<NoSuchNoteException> {
      notesService.updateNote(
        noteId = created.note.id!!,
        body = body("Changed"),
        workspaceId = workspace.id!!,
        mentions = emptyList(),
      )
    }
    assertEquals(body("Secret"), notesService.getNoteById(created.note.id!!, otherWorkspace.id!!)!!.toDto().body)
    assertNull(notesService.getNoteById(created.note.id!!, workspace.id!!))
  }

  @Test
  fun `invalid update mentions leave the note unchanged`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val foreignNodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val foreignNode = graphService.createNode(foreignNodeType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)
    val created =
      notesService.createNote(
        body = body("Before"),
        nodeId = alice.id!!,
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(bob.id!!)),
      )

    assertThrows<NoSuchMentionedNodeException> {
      notesService.updateNote(
        noteId = created.note.id!!,
        body = body("Unknown mention"),
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest("nde:does-not-exist")),
      )
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.updateNote(
        noteId = created.note.id!!,
        body = body("Foreign mention"),
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(foreignNode.id!!)),
      )
    }

    val unchanged = notesService.getNoteById(created.note.id!!, workspace.id!!)!!
    assertEquals(body("Before"), unchanged.toDto().body)
    assertNull(unchanged.toDto().updatedAt)
    assertEquals(listOf(bob.id), unchanged.mentions.map { it.node.id })
    assertEquals(created.mentions.map { it.mention.id }, unchanged.mentions.map { it.mention.id })
  }

  @Test
  fun `gets all notes of a workspace newest first with their own mentions`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val cesar = graphService.createNode(nodeType.id!!, "Cesar", workspaceId = workspace.id!!)

    val first =
      notesService.createNote(body("first"), nodeId = alice.id!!, workspace.id!!, listOf(MentionRequest(bob.id!!)))
    val second =
      notesService.createNote(body("second"), nodeId = bob.id!!, workspaceId = workspace.id!!, listOf(MentionRequest(alice.id!!)))
    notesService.createNote(body("other"), nodeId = cesar.id!!, workspaceId = workspace.id!!, emptyList())

    val notes = notesService.getNotesForNode(nodeId = bob.id!!, workspaceId = workspace.id!!)

    assertEquals(listOf(second.note.id, first.note.id), notes.map { it.note.id })
    assertEquals(listOf(bob.id, alice.id), notes.map { it.node?.id })
    assertEquals(listOf(alice.id), notes[0].mentions.map { it.node.id })
    assertEquals(listOf(bob.id), notes[1].mentions.map { it.node.id })
  }

  @Test
  fun `does not get notes from another workspace`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val nodeType1 = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val nodeType2 = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val alice = graphService.createNode(nodeType1.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType2.id!!, "Bob", workspaceId = otherWorkspace.id!!)

    val created = notesService.createNote(body("secret"), nodeId = alice.id!!, workspace.id!!, emptyList())

    assertNull(notesService.getNoteById(created.note.id!!, workspaceId = otherWorkspace.id!!))
    assertThrows<NoSuchMentionedNodeException> {
      notesService.getNotesForNode(nodeId = alice.id!!, workspaceId = otherWorkspace.id!!)
    }
  }

  @Test
  fun `gets notes mentioning a node newest first with all their mentions`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val carol = graphService.createNode(nodeType.id!!, "Carol", workspaceId = workspace.id!!)

    val first =
      notesService.createNote(body("first"), nodeId = alice.id!!, workspace.id!!, listOf(MentionRequest(alice.id!!)))
    notesService.createNote(body("bob only"), nodeId = bob.id!!, workspace.id!!, listOf(MentionRequest(bob.id!!)))
    val third =
      notesService.createNote(
        body("third"),
        nodeId = alice.id!!,
        workspace.id!!,
        listOf(MentionRequest(alice.id!!), MentionRequest(bob.id!!)),
      )
    notesService.createNote(body("no mentions"), nodeId = bob.id!!, workspace.id!!, emptyList())

    val notes = notesService.getNotesForNode(nodeId = alice.id!!, workspaceId = workspace.id!!)

    assertEquals(listOf(third.note.id, first.note.id), notes.map { it.note.id })
    assertEquals(setOf(alice.id, bob.id), notes[0].mentions.map { it.node.id }.toSet())
    assertEquals(listOf(alice.id), notes[1].mentions.map { it.node.id })
    assertEquals(emptyList<Any>(), notesService.getNotesForNode(carol.id!!, workspace.id!!))
  }

  @Test
  fun `fails to get notes mentioning node from another workspace or unknown node`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val foreignNode = graphService.createNode(nodeType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)
    notesService.createNote(body("foreign"), foreignNode.id!!, otherWorkspace.id!!, listOf(MentionRequest(foreignNode.id!!)))

    assertThrows<NoSuchMentionedNodeException> {
      notesService.getNotesForNode(nodeId = foreignNode.id!!, workspaceId = workspace.id!!)
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.getNotesForNode(nodeId = "nde:does-not-exist", workspaceId = workspace.id!!)
    }
  }

  @Test
  fun `fails to create note mentioning node from another workspace and saves nothing`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val thisNodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val foreignNodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val thisNode = graphService.createNode(thisNodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val foreignNode = graphService.createNode(foreignNodeType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)

    assertThrows<NoSuchNodeException> {
      notesService.createNote(
        body("nope"),
        nodeId = foreignNode.id!!,
        workspaceId = workspace.id!!,
        mentions = listOf(),
      )
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.createNote(
        body("nope"),
        nodeId = thisNode.id!!,
        workspaceId = workspace.id!!,
        listOf(MentionRequest(foreignNode.id!!)),
      )
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.createNote(
        body("nope"),
        nodeId = thisNode.id!!,
        workspaceId = workspace.id!!,
        listOf(MentionRequest("nde:does-not-exist")),
      )
    }
    assertEquals(emptyList<Any>(), notesService.getNotesForNode(thisNode.id!!, workspace.id!!))
    assertEquals(emptyList<Any>(), notesService.getNotesForNode(foreignNode.id!!, otherWorkspace.id!!))
  }
}
