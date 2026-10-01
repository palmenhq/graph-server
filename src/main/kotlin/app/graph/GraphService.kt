package app.graph

import app.graph.db.NodeEdgeDbo
import app.utils.ResourceId
import app.utils.toResourceId
import app.workspaces.WorkspaceService
import app.workspaces.validation.NoSuchWorkspaceException
import db.tables.pojos.Edge
import db.tables.pojos.EdgeType
import db.tables.pojos.Node
import db.tables.pojos.NodeType
import java.time.OffsetDateTime
import java.util.UUID

class GraphService(
  private val graphRepository: GraphRepository,
  private val workspaceService: WorkspaceService,
) {
  fun createNodeType(
    name: String,
    workspaceId: ResourceId,
  ): NodeType {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val nodeType =
      NodeType(
        id = newNodeTypeId(),
        name = name,
        createdAt = OffsetDateTime.now(),
        workspaceId = workspace.id!!,
      )
    graphRepository.createNodeType(nodeType)
    return nodeType
  }

  fun createEdgeType(
    sourceNodeType: ResourceId,
    targetNodeType: ResourceId,
    relationshipName: String,
    workspaceId: ResourceId,
    reverseRelationshipName: String? = null,
  ): EdgeType {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val edgeType =
      EdgeType(
        id = newEdgeTypeId(),
        relationshipName = relationshipName,
        reverseRelationshipName = reverseRelationshipName,
        sourceNodeType = sourceNodeType,
        targetNodeType = targetNodeType,
        workspaceId = workspace.id!!,
        createdAt = OffsetDateTime.now(),
      )
    graphRepository.createEdgeType(edgeType)
    return edgeType
  }

  fun createNode(
    nodeTypeId: ResourceId,
    title: String,
    workspaceId: ResourceId,
  ): Node {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val node =
      Node(
        id = newNodeId(),
        type = nodeTypeId,
        title = title,
        workspaceId = workspace.id!!,
        createdAt = OffsetDateTime.now(),
      )
    graphRepository.createNode(node)
    return node
  }

  fun createEdge(
    edgeTypeId: ResourceId,
    fromNode: ResourceId,
    toNode: ResourceId,
    workspaceId: ResourceId,
    title: String? = null,
  ): Edge {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()

    val edge =
      Edge(
        id = newEdgeId(),
        type = edgeTypeId,
        fromNode = fromNode,
        toNode = toNode,
        title = title,
        workspaceId = workspace.id!!,
        createdAt = OffsetDateTime.now(),
      )
    graphRepository.createEdge(edge)
    return edge
  }

  fun getNodeById(nodeId: ResourceId): Node? = graphRepository.findNodeById(nodeId)

  fun getEdgeNodesFromNode(id: ResourceId): List<NodeEdgeDbo> = graphRepository.findEdgeNodesFromNode(id)

  companion object {
    fun newNodeTypeId() =
      UUID
        .randomUUID()
        .toResourceId("nty")

    fun newEdgeTypeId() =
      UUID
        .randomUUID()
        .toResourceId("ety")

    fun newNodeId() =
      UUID
        .randomUUID()
        .toResourceId("nde")

    fun newEdgeId() =
      UUID
        .randomUUID()
        .toResourceId("edg")
  }
}
