package app.graph

import TestHelp
import app.graph.db.PreferredEdgeTypeDbo
import app.graph.dtos.toFullDto
import app.graph.dtos.toNeighborDto
import app.graph.validation.NoSuchEdgeException
import app.graph.validation.NoSuchEdgeTypeException
import app.graph.validation.NoSuchNodeException
import app.graph.validation.NoSuchNodeTypeException
import app.utils.http.Cursor
import app.utils.http.InvalidCursorException
import db.tables.pojos.Node
import db.tables.references.EDGE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.time.toKotlinInstant

class GraphServiceTest {
  val graphService = GraphService(GraphRepository(TestHelp.dslContext), TestHelp.workspaceService)

  @Test
  fun `creates node type and node and gets node by id`() {
    val workspace = TestHelp.createWorkspace("test")

    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val node = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)

    val retrievedNode = graphService.getNodeById(node.id!!, workspaceId = workspace.id!!)

    assertEquals(node.id, retrievedNode?.id)
    assertEquals(node.type, retrievedNode?.type)
    assertEquals(node.title, retrievedNode?.title)

    assertNull(graphService.getNodeById(node.id!!, workspaceId = "invalid"))
  }

  @Test
  fun `adds preferred edge types for node and returns it`() {
    val workspace = TestHelp.createWorkspace("test")

    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType1 =
      graphService.createEdgeType(
        relationshipTitle = "knows",
        reverseRelationshipTitle = "is known by",
        workspaceId = workspace.id!!,
      )
    val edgeType2 =
      graphService.createEdgeType(
        relationshipTitle = "is of brand",
        reverseRelationshipTitle = "is brand of",
        workspaceId = workspace.id!!,
      )

    val preferredEdgeTypes =
      listOf(
        PreferredEdgeTypeDbo(
          edgeTypeId = edgeType1.id!!,
          reverse = false,
        ),
      )
    graphService.setPreferredEdgeTypesForNodeType(
      nodeTypeId = nodeType.id!!,
      preferredEdgeTypes = preferredEdgeTypes,
      workspaceId = workspace.id!!,
    )

    val nodeTypeWithPreferredEdgeTypes =
      graphService.getNodeTypeById(nodeTypeId = nodeType.id!!, workspaceId = workspace.id!!)
    assertNotNull(nodeTypeWithPreferredEdgeTypes)
    assertEquals(nodeTypeWithPreferredEdgeTypes!!.preferredEdgeTypes, preferredEdgeTypes)
  }

  @Test
  fun `replaces preferred edge types preserving order and direction`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val knows = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val employedBy = graphService.createEdgeType(relationshipTitle = "is employed by", workspaceId = workspace.id!!)
    val preferences =
      listOf(
        PreferredEdgeTypeDbo(edgeTypeId = knows.id!!, reverse = false),
        PreferredEdgeTypeDbo(edgeTypeId = employedBy.id!!, reverse = true),
        PreferredEdgeTypeDbo(edgeTypeId = knows.id!!, reverse = true),
      )

    assertEquals(
      preferences,
      graphService
        .setPreferredEdgeTypesForNodeType(nodeType.id!!, preferences, workspace.id!!)
        .preferredEdgeTypes,
    )
    assertEquals(
      preferences,
      graphService.getNodeTypes(workspace.id!!).single { it.id == nodeType.id }.preferredEdgeTypes,
    )

    val replacement =
      listOf(
        PreferredEdgeTypeDbo(edgeTypeId = employedBy.id!!, reverse = false),
        PreferredEdgeTypeDbo(edgeTypeId = knows.id!!, reverse = true),
        PreferredEdgeTypeDbo(edgeTypeId = employedBy.id!!, reverse = false),
      )
    assertEquals(
      replacement.distinct(),
      graphService
        .setPreferredEdgeTypesForNodeType(nodeType.id!!, replacement, workspace.id!!)
        .preferredEdgeTypes,
    )
    assertEquals(
      emptyList<PreferredEdgeTypeDbo>(),
      graphService.setPreferredEdgeTypesForNodeType(nodeType.id!!, emptyList(), workspace.id!!).preferredEdgeTypes,
    )
  }

  @Test
  fun `rejects invalid preferred edge types without changing saved preferences`() {
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val validEdgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val otherWorkspaceEdgeType =
      graphService.createEdgeType(relationshipTitle = "works for", workspaceId = otherWorkspace.id!!)
    val existingPreferences = listOf(PreferredEdgeTypeDbo(edgeTypeId = validEdgeType.id!!, reverse = false))
    graphService.setPreferredEdgeTypesForNodeType(nodeType.id!!, existingPreferences, workspace.id!!)

    assertThrows(NoSuchNodeTypeException::class.java) {
      graphService.setPreferredEdgeTypesForNodeType("missing-node-type", emptyList(), workspace.id!!)
    }
    assertThrows(NoSuchEdgeTypeException::class.java) {
      graphService.setPreferredEdgeTypesForNodeType(
        nodeType.id!!,
        listOf(PreferredEdgeTypeDbo(edgeTypeId = "missing-edge-type", reverse = false)),
        workspace.id!!,
      )
    }
    assertThrows(NoSuchEdgeTypeException::class.java) {
      graphService.setPreferredEdgeTypesForNodeType(
        nodeType.id!!,
        listOf(PreferredEdgeTypeDbo(edgeTypeId = otherWorkspaceEdgeType.id!!, reverse = false)),
        workspace.id!!,
      )
    }

    assertEquals(existingPreferences, graphService.getNodeTypeById(nodeType.id!!, workspace.id!!)!!.preferredEdgeTypes)
  }

  @Test
  fun `does not return soft-deleted edge types as preferences`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    graphService.setPreferredEdgeTypesForNodeType(
      nodeType.id!!,
      listOf(PreferredEdgeTypeDbo(edgeTypeId = edgeType.id!!, reverse = false)),
      workspace.id!!,
    )

    graphService.deleteEdgeType(edgeType.id!!, workspace.id!!)

    assertEquals(emptyList<PreferredEdgeTypeDbo>(), graphService.getNodeTypes(workspace.id!!).single().preferredEdgeTypes)
  }

  @Test
  fun `creates edges on nodes and gets other node via edge`() {
    val workspace = TestHelp.createWorkspace("test")

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
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")

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
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")

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
    val workspace = TestHelp.createWorkspace("test")
    val otherWorkspace = TestHelp.createWorkspace("other")

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
    val workspace = TestHelp.createWorkspace("test")
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
    val workspace = TestHelp.createWorkspace("test")
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
  fun `soft-deletes node without deleting its edges and excludes them from queries`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    assertThrows(NoSuchNodeException::class.java) { graphService.deleteNode(bob.id!!, "invalid") }

    graphService.deleteNode(bob.id!!, workspace.id!!)

    val deletedNode = graphService.getNodeById(bob.id!!, workspace.id!!)
    assertNotNull(deletedNode)
    assertNotNull(deletedNode?.deletedAt)
    assertEquals(deletedNode?.deletedAt?.toInstant()?.toKotlinInstant(), deletedNode?.toNeighborDto()?.deletedAt)
    assertEquals(
      deletedNode?.deletedAt?.toInstant()?.toKotlinInstant(),
      deletedNode?.toFullDto(emptyList(), emptyList(), emptyList())?.deletedAt,
    )
    assertEquals(listOf(alice.id), graphService.getNodes(workspace.id!!).items.map { it.id })
    assertEquals(0, graphService.getNodes(workspace.id!!, searchQuery = "bob").items.size)
    assertEquals(
      setOf(alice.id, bob.id),
      graphService
        .getNodes(workspace.id!!, includeDeleted = true)
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(
      listOf(bob.id),
      graphService.getNodes(workspace.id!!, searchQuery = "bob", includeDeleted = true).items.map { it.id },
    )
    assertNull(
      TestHelp.dslContext
        .select(EDGE.DELETED_AT)
        .from(EDGE)
        .where(EDGE.ID.eq(edge.id))
        .fetchOne(EDGE.DELETED_AT),
    )
    assertEquals(0, graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).size)
    assertNull(graphService.getEdgeById(edge.id!!, workspace.id!!))
    assertThrows(NoSuchNodeException::class.java) { graphService.deleteNode(bob.id!!, workspace.id!!) }
  }

  @Test
  fun `restores node and reveals its active edges`() {
    val workspace = TestHelp.createWorkspace("test")
    val nodeType = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val edgeType = graphService.createEdgeType(relationshipTitle = "knows", workspaceId = workspace.id!!)
    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)
    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    graphService.deleteNode(bob.id!!, workspace.id!!)

    val restoredNode = graphService.restoreNode(bob.id!!, workspace.id!!)

    assertNull(restoredNode.deletedAt)
    assertEquals(
      setOf(alice.id, bob.id),
      graphService
        .getNodes(workspace.id!!)
        .items
        .map { it.id }
        .toSet(),
    )
    assertEquals(listOf(edge.id), graphService.getEdgeNodesFromNode(alice.id!!, workspace.id!!).map { it.edge.id })
    assertNotNull(graphService.getEdgeById(edge.id!!, workspace.id!!))
    assertThrows(NoSuchNodeException::class.java) { graphService.restoreNode(bob.id!!, workspace.id!!) }
    assertThrows(NoSuchNodeException::class.java) { graphService.restoreNode("invalid", workspace.id!!) }
  }

  @Test
  fun `soft-deletes node type and keeps its nodes but prevents new ones`() {
    val workspace = TestHelp.createWorkspace("test")
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
      graphService.createNode(company.id!!, "Acme", workspaceId = TestHelp.createWorkspace("other").id!!)
    }
    assertEquals(listOf(alice.id), graphService.getNodes(workspace.id!!).items.map { it.id })
  }

  @Test
  fun `soft-deletes edge type and keeps its edges but prevents new ones`() {
    val workspace = TestHelp.createWorkspace("test")
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
    val workspace = TestHelp.createWorkspace("test")
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
  fun `paginates nodes with identical last viewed times by id`() {
    val workspace = TestHelp.createWorkspace("test")
    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val timestamp = OffsetDateTime.parse("2026-01-01T00:00:00Z")
    val repository = GraphRepository(TestHelp.dslContext)
    val ids =
      (1..5)
        .map { GraphService.newNodeId() }
        .onEach {
          repository.createNode(
            Node(
              id = it,
              type = contact.id,
              title = it,
              workspaceId = workspace.id,
              createdAt = timestamp,
              lastViewedAt = timestamp,
            ),
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
  fun `marks node as viewed and lists most recently viewed nodes first`() {
    val workspace = TestHelp.createWorkspace("test")
    val contact = graphService.createNodeType(title = "Contact", workspaceId = workspace.id!!)
    val nodes = (1..5).map { graphService.createNode(contact.id!!, "Node $it", workspaceId = workspace.id!!) }
    assertEquals(nodes[0].createdAt, nodes[0].lastViewedAt)

    val viewed = graphService.markNodeViewed(nodes[1].id!!, workspace.id!!)
    assertEquals(nodes[1].createdAt, viewed.createdAt)
    assertTrue(viewed.lastViewedAt!!.isAfter(nodes[4].lastViewedAt))
    graphService.markNodeViewed(nodes[3].id!!, workspace.id!!)

    val first = graphService.getNodes(workspace.id!!, pageSize = 2)
    val second = graphService.getNodes(workspace.id!!, cursor = first.nextCursor, pageSize = 2)
    val third = graphService.getNodes(workspace.id!!, cursor = second.nextCursor, pageSize = 2)

    assertEquals(listOf(nodes[3].id, nodes[1].id), first.items.map { it.id })
    assertEquals(listOf(nodes[4].id, nodes[2].id), second.items.map { it.id })
    assertEquals(listOf(nodes[0].id), third.items.map { it.id })
    val storedLastViewedAt = graphService.getNodeById(nodes[3].id!!, workspace.id!!)?.lastViewedAt
    val dto = first.items.first().toNeighborDto()
    assertEquals(storedLastViewedAt?.toInstant()?.toKotlinInstant(), dto.lastViewedAt)

    assertThrows(NoSuchNodeException::class.java) { graphService.markNodeViewed("invalid", workspace.id!!) }
    assertThrows(NoSuchNodeException::class.java) {
      graphService.markNodeViewed(nodes[0].id!!, TestHelp.createWorkspace("other").id!!)
    }
  }

  @Test
  fun `rejects invalid cursors`() {
    assertThrows(InvalidCursorException::class.java) { Cursor.decode("not a cursor") }
    assertThrows(InvalidCursorException::class.java) { Cursor.decode("bm9waXBl") }
    assertThrows(InvalidCursorException::class.java) { Cursor.decode(Cursor(OffsetDateTime.now(), "").encode()) }
  }
}
