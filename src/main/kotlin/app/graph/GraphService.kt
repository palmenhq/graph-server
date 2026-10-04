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
    title: String,
    workspaceId: ResourceId,
  ): NodeType {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val nodeType =
      NodeType(
        id = newNodeTypeId(),
        title = title,
        createdAt = OffsetDateTime.now(),
        workspaceId = workspace.id!!,
      )
    graphRepository.createNodeType(nodeType)
    return nodeType
  }

  fun createEdgeType(
    relationshipTitle: String,
    workspaceId: ResourceId,
    reverseRelationshipTitle: String? = null,
  ): EdgeType {
    val workspace = workspaceService.getById(workspaceId) ?: throw NoSuchWorkspaceException()
    val edgeType =
      EdgeType(
        id = newEdgeTypeId(),
        relationshipTitle = relationshipTitle,
        reverseRelationshipTitle = reverseRelationshipTitle,
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

  fun getNodeById(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): Node? =
    graphRepository.findNodeById(
      nodeId = nodeId,
      workspaceId = workspaceId,
    )

  fun getNodes(
    workspaceId: ResourceId,
    nodeTypeId: ResourceId? = null,
    searchQuery: String? = null,
  ): List<Node> =
    graphRepository.findNodes(
      workspaceId = workspaceId,
      nodeTypeId = nodeTypeId,
      searchQuery = searchQuery,
    )

  fun getNodeTypeById(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): NodeType? =
    graphRepository.findNodeTypeById(
      nodeTypeId = nodeTypeId,
      workspaceId = workspaceId,
    )

  fun getNodeTypes(workspaceId: ResourceId): List<NodeType> = graphRepository.findNodeTypes(workspaceId)

  fun getEdgeTypeById(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): EdgeType? =
    graphRepository.findEdgeTypeById(
      edgeTypeId = edgeTypeId,
      workspaceId = workspaceId,
    )

  fun getEdgeTypes(workspaceId: ResourceId): List<EdgeType> = graphRepository.findEdgeTypes(workspaceId)

  fun getEdgeNodesFromNode(
    id: ResourceId,
    workspaceId: ResourceId,
  ): List<NodeEdgeDbo> =
    graphRepository.findEdgeNodesFromNode(
      nodeId = id,
      workspaceId = workspaceId,
    )

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
