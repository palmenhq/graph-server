package app.notes

import TestHelp
import app.graph.GraphRepository
import app.graph.GraphService
import app.notes.dtos.MentionRequest
import app.notes.dtos.toDto
import app.notes.validation.NoSuchMentionedNodeException
import app.workspaces.validation.NoSuchWorkspaceException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
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
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val created =
      notesService.createNote(
        body = body("Met Alice and Bob"),
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(alice.id!!), MentionRequest(bob.id!!)),
      )

    assertEquals(workspace.id, created.note.workspaceId)
    assertEquals(setOf(alice.id, bob.id), created.mentions.map { it.node.id }.toSet())
    assertTrue(created.mentions.all { it.mention.note == created.note.id })

    val retrieved = notesService.getNoteById(created.note.id!!, workspaceId = workspace.id!!)!!

    assertEquals(created.note.id, retrieved.note.id)
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
    val workspace = TestHelp.workspaceService.createWorkspace("test")

    val created = notesService.createNote(body = body("Plain"), workspaceId = workspace.id!!, mentions = emptyList())
    val notes = notesService.getAllNotes(workspace.id!!)

    assertEquals(listOf(created.note.id), notes.map { it.note.id })
    assertEquals(emptyList<Any>(), notes.single().mentions)
    assertEquals(body("Plain"), notes.single().toDto().body)
  }

  @Test
  fun `deduplicates mentions of the same node`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)

    val created =
      notesService.createNote(
        body = body("Alice, Alice"),
        workspaceId = workspace.id!!,
        mentions = listOf(MentionRequest(alice.id!!), MentionRequest(alice.id!!)),
      )

    assertEquals(1, created.mentions.size)
    assertEquals(1, notesService.getNoteById(created.note.id!!, workspace.id!!)!!.mentions.size)
  }

  @Test
  fun `gets all notes of a workspace newest first with their own mentions`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val first = notesService.createNote(body("first"), workspace.id!!, listOf(MentionRequest(alice.id!!)))
    val second = notesService.createNote(body("second"), workspace.id!!, listOf(MentionRequest(bob.id!!)))
    notesService.createNote(body("other"), otherWorkspace.id!!, emptyList())

    val notes = notesService.getAllNotes(workspace.id!!)

    assertEquals(listOf(second.note.id, first.note.id), notes.map { it.note.id })
    assertEquals(listOf(bob.id), notes[0].mentions.map { it.node.id })
    assertEquals(listOf(alice.id), notes[1].mentions.map { it.node.id })
  }

  @Test
  fun `does not get notes from another workspace`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val created = notesService.createNote(body("secret"), workspace.id!!, emptyList())

    assertNull(notesService.getNoteById(created.note.id!!, workspaceId = otherWorkspace.id!!))
    assertEquals(emptyList<Any>(), notesService.getAllNotes(otherWorkspace.id!!))
  }

  @Test
  fun `gets notes mentioning a node newest first with all their mentions`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val carol = graphService.createNode(nodeType.id!!, "Carol", workspaceId = workspace.id!!)

    val first = notesService.createNote(body("first"), workspace.id!!, listOf(MentionRequest(alice.id!!)))
    notesService.createNote(body("bob only"), workspace.id!!, listOf(MentionRequest(bob.id!!)))
    val third =
      notesService.createNote(
        body("third"),
        workspace.id!!,
        listOf(MentionRequest(alice.id!!), MentionRequest(bob.id!!)),
      )
    notesService.createNote(body("no mentions"), workspace.id!!, emptyList())

    val notes = notesService.getNotesMentioningNode(nodeId = alice.id!!, workspaceId = workspace.id!!)

    assertEquals(listOf(third.note.id, first.note.id), notes.map { it.note.id })
    assertEquals(setOf(alice.id, bob.id), notes[0].mentions.map { it.node.id }.toSet())
    assertEquals(listOf(alice.id), notes[1].mentions.map { it.node.id })
    assertEquals(emptyList<Any>(), notesService.getNotesMentioningNode(carol.id!!, workspace.id!!))
  }

  @Test
  fun `fails to get notes mentioning node from another workspace or unknown node`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val foreignNode = graphService.createNode(nodeType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)
    notesService.createNote(body("foreign"), otherWorkspace.id!!, listOf(MentionRequest(foreignNode.id!!)))

    assertThrows<NoSuchMentionedNodeException> {
      notesService.getNotesMentioningNode(nodeId = foreignNode.id!!, workspaceId = workspace.id!!)
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.getNotesMentioningNode(nodeId = "nde:does-not-exist", workspaceId = workspace.id!!)
    }
  }

  @Test
  fun `gets no notes for invalid workspace`() {
    assertEquals(emptyList<Any>(), notesService.getAllNotes("wsp:does-not-exist"))
  }

  @Test
  fun `fails to create note in invalid workspace`() {
    assertThrows<NoSuchWorkspaceException> {
      notesService.createNote(body("nope"), "wsp:does-not-exist", emptyList())
    }
  }

  @Test
  fun `fails to create note mentioning node from another workspace and saves nothing`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)
    val foreignNode = graphService.createNode(nodeType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)

    assertThrows<NoSuchMentionedNodeException> {
      notesService.createNote(body("nope"), workspace.id!!, listOf(MentionRequest(foreignNode.id!!)))
    }
    assertThrows<NoSuchMentionedNodeException> {
      notesService.createNote(body("nope"), workspace.id!!, listOf(MentionRequest("nde:does-not-exist")))
    }
    assertEquals(emptyList<Any>(), notesService.getAllNotes(workspace.id!!))
  }
}
