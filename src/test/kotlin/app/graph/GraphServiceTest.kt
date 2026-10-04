package app.graph

import TestHelp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
      graphService.getNodes(workspace.id!!).map { it.id }.toSet(),
    )
    assertEquals(
      setOf(alice.id, bob.id),
      graphService.getNodes(workspace.id!!, nodeTypeId = contact.id!!).map { it.id }.toSet(),
    )
    assertEquals(listOf(acme.id), graphService.getNodes(workspace.id!!, nodeTypeId = company.id!!).map { it.id })
    assertEquals(0, graphService.getNodes(otherWorkspace.id!!, nodeTypeId = contact.id!!).size)
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
      graphService.getNodes(workspace.id!!, searchQuery = "ali").map { it.id }.toSet(),
    )
    assertEquals(
      setOf(alice.id, aliceCorp.id),
      graphService.getNodes(workspace.id!!, searchQuery = "ALICE").map { it.id }.toSet(),
    )
    assertEquals(
      setOf(alice.id),
      graphService.getNodes(workspace.id!!, searchQuery = "anders").map { it.id }.toSet(),
    )
    assertEquals(
      setOf(alice.id, alicia.id),
      graphService.getNodes(workspace.id!!, nodeTypeId = contact.id!!, searchQuery = "ali").map { it.id }.toSet(),
    )
    assertEquals(
      setOf(alice.id, alicia.id, bob.id, aliceCorp.id),
      graphService.getNodes(workspace.id!!, searchQuery = null).map { it.id }.toSet(),
    )
    assertEquals(0, graphService.getNodes(workspace.id!!, searchQuery = "mallory").size)
    assertEquals(0, graphService.getNodes(otherWorkspace.id!!, searchQuery = "bob").size)
  }
}
