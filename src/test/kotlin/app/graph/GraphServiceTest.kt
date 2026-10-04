package app.graph

import TestHelp
import app.graph.validation.NoSuchEdgeException
import app.graph.validation.NoSuchEdgeTypeException
import app.graph.validation.NoSuchNodeException
import app.graph.validation.NoSuchNodeTypeException
import app.utils.http.Cursor
import app.utils.http.InvalidCursorException
import db.tables.pojos.Node
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class GraphServiceTest {
  val graphService = GraphService(GraphRepository(TestHelp.dslContext), TestHelp.workspaceService)

  @Test
  fun `creates node type and node and gets node by id`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")

    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val node = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)

    val retrievedNode = graphService.getNodeById(node.id!!, workspaceId = workspace.id!!)

    assertEquals(node.id, retrievedNode?.id)
    assertEquals(node.type, retrievedNode?.type)
    assertEquals(node.title, retrievedNode?.title)

    assertNull(graphService.getNodeById(node.id!!, workspaceId = "invalid"))
  }

  @Test
  fun `creates edges on nodes and gets other node via edge`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")

    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType =
      graphService.createEdgeType(
        relationshipTitle = "Contact",
        workspaceId = workspace.id!!,
      )

    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    val aliceContacts = graphService.getEdgeNodesFromNode(alice.id!!, workspaceId = workspace.id!!)

    assertEquals(1, aliceContacts.size)
    assertEquals(bob.id, aliceContacts.first().toNode.id)
    assertEquals(alice.id, aliceContacts.first().fromNode.id)
    assertEquals(edge.id, aliceContacts.first().edge.id)
    assertEquals(edgeType.id, aliceContacts.first().edgeType.id)
    assertEquals(edgeType.relationshipTitle, aliceContacts.first().edgeType.relationshipTitle)

    assertEquals(0, graphService.getEdgeNodesFromNode(alice.id!!, workspaceId = "invalid").size)

    val edgeWithNodes = graphService.getEdgeWithNodesByEdgeId(edge.id!!, workspaceId = workspace.id!!)

    assertEquals(edge.id, edgeWithNodes?.edge?.id)
    assertEquals(alice.id, edgeWithNodes?.fromNode?.id)
    assertEquals(bob.id, edgeWithNodes?.toNode?.id)
    assertEquals(edgeType.id, edgeWithNodes?.edgeType?.id)

    assertNull(graphService.getEdgeWithNodesByEdgeId(edge.id!!, workspaceId = "invalid"))
  }

  @Test
  fun `lists and gets node types and edge types in workspace`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")

    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    graphService.createNodeType(title = "Other", workspaceId = otherWorkspace.id!!)

    val worksAt =
      graphService.createEdgeType(
        relationshipTitle = "works at",
        workspaceId = workspace.id!!,
        reverseRelationshipTitle = "employs",
      )

    assertEquals(setOf(contact.id, company.id), graphService.getNodeTypes(workspace.id!!).map { it.id }.toSet())
    assertEquals(listOf(worksAt.id), graphService.getEdgeTypes(workspace.id!!).map { it.id })
    assertEquals(0, graphService.getEdgeTypes(otherWorkspace.id!!).size)

    assertEquals("Company", graphService.getNodeTypeById(company.id!!, workspace.id!!)?.title)
    assertNull(graphService.getNodeTypeById(company.id!!, otherWorkspace.id!!))

    assertEquals("employs", graphService.getEdgeTypeById(worksAt.id!!, workspace.id!!)?.reverseRelationshipTitle)
    assertNull(graphService.getEdgeTypeById(worksAt.id!!, otherWorkspace.id!!))
  }

  @Test
  fun `lists nodes in workspace with optional node type filter`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")

    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val otherType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)

    val alice = graphService.createNode(contact.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(contact.id!!, "Bob", workspaceId = workspace.id!!)
    val acme = graphService.createNode(company.id!!, "Acme", workspaceId = workspace.id!!)
    graphService.createNode(otherType.id!!, "Mallory", workspaceId = otherWorkspace.id!!)

    assertEquals(
      setOf(alice.id, bob.id, acme.id),
      graphService
        .getNodes(workspace.id!!)
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      setOf(alice.id, bob.id),
      graphService
        .getNodes(workspace.id!!, nodeTypeId = contact.id!!)
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(listOf(acme.id), graphService.getNodes(workspace.id!!, nodeTypeId = company.id!!).items.map { it.id })
    assertEquals(0, graphService.getNodes(otherWorkspace.id!!, nodeTypeId = contact.id!!).items.size)
  }

  @Test
  fun `searches nodes in workspace by title`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")

    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val otherType = graphService.createNodeType(title = "Contact", workspaceId = otherWorkspace.id!!)

    val alice = graphService.createNode(contact.id!!, "Alice Anderson", workspaceId = workspace.id!!)
    val alicia = graphService.createNode(contact.id!!, "Alicia", workspaceId = workspace.id!!)
    val bob = graphService.createNode(contact.id!!, "Bob", workspaceId = workspace.id!!)
    val aliceCorp = graphService.createNode(company.id!!, "Alice Corp", workspaceId = workspace.id!!)
    graphService.createNode(otherType.id!!, "Alice Other", workspaceId = otherWorkspace.id!!)

    assertEquals(
      setOf(alice.id, alicia.id, aliceCorp.id),
      graphService
        .getNodes(workspace.id!!, searchQuery = "ali")
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      setOf(alice.id, aliceCorp.id),
      graphService
        .getNodes(workspace.id!!, searchQuery = "ALICE")
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      setOf(alice.id),
      graphService
        .getNodes(workspace.id!!, searchQuery = "anders")
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      setOf(alice.id, alicia.id),
      graphService
        .getNodes(workspace.id!!, nodeTypeId = contact.id!!, searchQuery = "ali")
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      setOf(alice.id, alicia.id, bob.id, aliceCorp.id),
      graphService
        .getNodes(workspace.id!!, searchQuery = null)
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(0, graphService.getNodes(workspace.id!!, searchQuery = "mallory").items.size)
    assertEquals(0, graphService.getNodes(otherWorkspace.id!!, searchQuery = "bob").items.size)
  }

  @Test
  fun `updates node type, edge type, node and edge`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    assertEquals("Person", graphService.updateNodeType(nodeType.id!!, workspace.id!!, "Person").title)
    assertEquals("Person", graphService.getNodeTypeById(nodeType.id!!, workspace.id!!)?.title)

    val updatedEdgeType = graphService.updateEdgeType(edgeType.id!!, workspace.id!!, "manages", "is managed by")
    assertEquals("manages", updatedEdgeType.relationshipTitle)
    assertEquals("is managed by", updatedEdgeType.reverseRelationshipTitle)
    assertEquals("manages", graphService.getEdgeTypeById(edgeType.id!!, workspace.id!!)?.relationshipTitle)

    assertEquals("Alice Anderson", graphService.updateNode(alice.id!!, workspace.id!!, "Alice Anderson").title)
    assertEquals("Alice Anderson", graphService.getNodeById(alice.id!!, workspace.id!!)?.title)

    assertEquals("colleagues", graphService.updateEdge(edge.id!!, workspace.id!!, "colleagues").title)
    assertEquals("colleagues", graphService.getEdgeById(edge.id!!, workspace.id!!)?.title)
    assertNull(graphService.updateEdge(edge.id!!, workspace.id!!, null).title)

    assertThrows(NoSuchNodeTypeException::class.java) { graphService.updateNodeType(nodeType.id!!, "invalid", "x") }
    assertThrows(NoSuchEdgeTypeException::class.java) { graphService.updateEdgeType(edgeType.id!!, "invalid", "x") }
    assertThrows(NoSuchNodeException::class.java) { graphService.updateNode(alice.id!!, "invalid", "x") }
    assertThrows(NoSuchEdgeException::class.java) { graphService.updateEdge(edge.id!!, "invalid", "x") }

    graphService.deleteNode(bob.id!!, workspace.id!!)
    assertThrows(NoSuchNodeException::class.java) { graphService.updateNode(bob.id!!, workspace.id!!, "x") }
    assertThrows(NoSuchEdgeException::class.java) { graphService.updateEdge(edge.id!!, workspace.id!!, "x") }
  }

  @Test
  fun `soft-deletes edge and excludes it from queries`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    assertThrows(NoSuchEdgeException::class.java) { graphService.deleteEdge(edge.id!!, "invalid") }
    assertNotNull(graphService.getEdgeById(edge.id!!, workspace.id!!))

    graphService.deleteEdge(edge.id!!, workspace.id!!)

    assertNull(graphService.getEdgeById(edge.id!!, workspace.id!!))
    assertEquals(0, graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).size)
    assertThrows(NoSuchEdgeException::class.java) { graphService.deleteEdge(edge.id!!, workspace.id!!) }

    // The same edge can be created again after deletion
    val recreated = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)
    assertEquals(listOf(recreated.id), graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).map { it.edge.id })
  }

  @Test
  fun `soft-deletes node with its edges and excludes them from queries`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    assertThrows(NoSuchNodeException::class.java) { graphService.deleteNode(bob.id!!, "invalid") }

    graphService.deleteNode(bob.id!!, workspace.id!!)

    assertNull(graphService.getNodeById(bob.id!!, workspace.id!!))
    assertEquals(listOf(alice.id), graphService.getNodes(workspace.id!!).items.map { it.id })
    assertEquals(0, graphService.getNodes(workspace.id!!, searchQuery = "bob").items.size)
    assertEquals(0, graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).size)
    assertNull(graphService.getEdgeById(edge.id!!, workspace.id!!))
    assertThrows(NoSuchNodeException::class.java) { graphService.deleteNode(bob.id!!, workspace.id!!) }
  }

  @Test
  fun `soft-deletes node type and keeps its nodes but prevents new ones`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val alice = graphService.createNode(contact.id!!, "Alice", workspaceId = workspace.id!!)

    assertThrows(NoSuchNodeTypeException::class.java) { graphService.deleteNodeType(contact.id!!, "invalid") }

    graphService.deleteNodeType(contact.id!!, workspace.id!!)

    assertNull(graphService.getNodeTypeById(contact.id!!, workspace.id!!))
    assertEquals(listOf(company.id), graphService.getNodeTypes(workspace.id!!).map { it.id })
    assertThrows(NoSuchNodeTypeException::class.java) { graphService.deleteNodeType(contact.id!!, workspace.id!!) }

    // Existing nodes of the deleted type are kept
    assertNotNull(graphService.getNodeById(alice.id!!, workspace.id!!))
    assertEquals(listOf(alice.id), graphService.getNodes(workspace.id!!).items.map { it.id })

    // New nodes cannot be created with the deleted type
    assertThrows(NoSuchNodeTypeException::class.java) {
      graphService.createNode(contact.id!!, "Bob", workspaceId = workspace.id!!)
    }
    assertThrows(NoSuchNodeTypeException::class.java) {
      graphService.createNode(company.id!!, "Acme", workspaceId = TestHelp.workspaceService.createWorkspace("other").id!!)
    }
    assertEquals(listOf(alice.id), graphService.getNodes(workspace.id!!).items.map { it.id })
  }

  @Test
  fun `soft-deletes edge type and keeps its edges but prevents new ones`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val knows = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val likes = graphService.createEdgeType(relationshipTitle = "likes", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(knows.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    assertThrows(NoSuchEdgeTypeException::class.java) { graphService.deleteEdgeType(knows.id!!, "invalid") }

    graphService.deleteEdgeType(knows.id!!, workspace.id!!)

    assertNull(graphService.getEdgeTypeById(knows.id!!, workspace.id!!))
    assertEquals(listOf(likes.id), graphService.getEdgeTypes(workspace.id!!).map { it.id })
    assertThrows(NoSuchEdgeTypeException::class.java) { graphService.deleteEdgeType(knows.id!!, workspace.id!!) }

    // Existing edges of the deleted type are kept
    assertNotNull(graphService.getEdgeById(edge.id!!, workspace.id!!))
    assertEquals(listOf(edge.id), graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).map { it.edge.id })

    // New edges cannot be created with the deleted type
    assertThrows(NoSuchEdgeTypeException::class.java) {
      graphService.createEdge(knows.id!!, bob.id!!, alice.id!!, workspaceId = workspace.id!!)
    }
    assertEquals(1, graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).size)
  }

  @Test
  fun `paginates nodes newest first with a cursor`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val nodes = (1..5).map { graphService.createNode(contact.id!!, "Node $it", workspaceId = workspace.id!!) }

    val first = graphService.getNodes(workspace.id!!, pageSize = 2)
    val second = graphService.getNodes(workspace.id!!, cursor = first.nextCursor, pageSize = 2)
    val third = graphService.getNodes(workspace.id!!, cursor = second.nextCursor, pageSize = 2)

    assertEquals(listOf(nodes[4].id, nodes[3].id), first.items.map { it.id })
    assertEquals(listOf(nodes[2].id, nodes[1].id), second.items.map { it.id })
    assertEquals(listOf(nodes[0].id), third.items.map { it.id })
    assertNull(third.nextCursor)

    // An exactly full last page has no next cursor
    assertNull(graphService.getNodes(workspace.id!!, pageSize = 5).nextCursor)

    // Filters are applied together with the cursor
    val filtered = graphService.getNodes(workspace.id!!, searchQuery = "Node", cursor = first.nextCursor, pageSize = 10)
    assertEquals(listOf(nodes[2].id, nodes[1].id, nodes[0].id), filtered.items.map { it.id })
  }

  @Test
  fun `paginates nodes with identical creation times by id`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z")
    val repository = GraphRepository(TestHelp.dslContext)
    val ids =
      (1..5)
        .map { GraphService.newNodeId() }
        .onEach {
          repository.createNode(
            Node(id = it, type = contact.id, title = it, workspaceId = workspace.id, createdAt = createdAt),
          )
        }.sorted()

    val collected = mutableListOf<String?>()
    var cursor: Cursor? = null
    do {
      val page = graphService.getNodes(workspace.id!!, cursor = cursor, pageSize = 2)
      collected += page.items.map { it.id }
      cursor = page.nextCursor?.encode()?.let { Cursor.decode(it) }
    } while (cursor != null)

    assertEquals(ids, collected)
  }

  @Test
  fun `rejects invalid cursors`() {
    assertThrows(InvalidCursorException::class.java) { Cursor.decode("not a cursor") }
    assertThrows(InvalidCursorException::class.java) { Cursor.decode("bm9waXBl") }
    assertThrows(InvalidCursorException::class.java) { Cursor.decode(Cursor(OffsetDateTime.now(), "").encode()) }
  }
}
