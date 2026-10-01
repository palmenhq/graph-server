package app.graph

import TestHelp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphServiceTest {
  val graphService = GraphService(GraphRepository(TestHelp.dslContext), TestHelp.workspaceService)

  @Test
  fun `creates node type and node and gets node by id`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")

    val nodeType = graphService.createNodeType(name = "Contact", workspaceId = workspace.id!!)
    val node = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)

    val retrievedNode = graphService.getNodeById(node.id!!)

    assertEquals(node.id, retrievedNode?.id)
    assertEquals(node.type, retrievedNode?.type)
    assertEquals(node.title, retrievedNode?.title)
  }

  @Test
  fun `creates edges on nodes and gets other node via edge`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")

    val nodeType = graphService.createNodeType(name = "Contact", workspaceId = workspace.id!!)
    val edgeType =
      graphService.createEdgeType(
        sourceNodeType = nodeType.id!!,
        targetNodeType = nodeType.id!!,
        relationshipName = "Contact",
        workspaceId = workspace.id!!,
      )

    val alice = graphService.createNode(nodeType.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(nodeType.id!!, "Bob", workspaceId = workspace.id!!)

    val edge = graphService.createEdge(edgeType.id!!, alice.id!!, bob.id!!, workspaceId = workspace.id!!)

    val aliceContacts = graphService.getEdgeNodesFromNode(alice.id!!)

    assertEquals(1, aliceContacts.size)
    assertEquals(bob.id, aliceContacts.first().toNode.id)
    assertEquals(alice.id, aliceContacts.first().fromNode.id)
  }
}
