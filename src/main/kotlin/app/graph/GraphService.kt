package app.graph

import app.graph.db.NodeEdgeDbo
import app.graph.validation.NoSuchEdgeException
import app.graph.validation.NoSuchEdgeTypeException
import app.graph.validation.NoSuchNodeException
import app.graph.validation.NoSuchNodeTypeException
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
    graphRepository.findNodeTypeById(nodeTypeId = nodeTypeId, workspaceId = workspace.id!!)
      ?: throw NoSuchNodeTypeException(nodeTypeId)
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
    graphRepository.findEdgeTypeById(edgeTypeId = edgeTypeId, workspaceId = workspace.id!!)
      ?: throw NoSuchEdgeTypeException(edgeTypeId)

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

  fun getEdgeById(
    edgeId: ResourceId,
    workspaceId: ResourceId,
  ): Edge? =
    graphRepository.findEdgeById(
      edgeId = edgeId,
      workspaceId = workspaceId,
    )

  fun updateNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
    title: String,
  ): NodeType {
    if (!graphRepository.updateNodeType(nodeTypeId = nodeTypeId, workspaceId = workspaceId, title = title)) {
      throw NoSuchNodeTypeException(nodeTypeId)
    }
    return graphRepository.findNodeTypeById(nodeTypeId, workspaceId) ?: throw NoSuchNodeTypeException(nodeTypeId)
  }

  fun updateEdgeType(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
    relationshipTitle: String,
    reverseRelationshipTitle: String? = null,
  ): EdgeType {
    if (!graphRepository.updateEdgeType(
        edgeTypeId = edgeTypeId,
        workspaceId = workspaceId,
        relationshipTitle = relationshipTitle,
        reverseRelationshipTitle = reverseRelationshipTitle,
      )
    ) {
      throw NoSuchEdgeTypeException(edgeTypeId)
    }
    return graphRepository.findEdgeTypeById(edgeTypeId, workspaceId) ?: throw NoSuchEdgeTypeException(edgeTypeId)
  }

  fun updateNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
    title: String,
  ): Node {
    if (!graphRepository.updateNode(nodeId = nodeId, workspaceId = workspaceId, title = title)) {
      throw NoSuchNodeException(nodeId)
    }
    return graphRepository.findNodeById(nodeId, workspaceId) ?: throw NoSuchNodeException(nodeId)
  }

  fun updateEdge(
    edgeId: ResourceId,
    workspaceId: ResourceId,
    title: String?,
  ): Edge {
    if (!graphRepository.updateEdge(edgeId = edgeId, workspaceId = workspaceId, title = title)) {
      throw NoSuchEdgeException(edgeId)
    }
    return graphRepository.findEdgeById(edgeId, workspaceId) ?: throw NoSuchEdgeException(edgeId)
  }

  /**
   * Soft-deletes a node type. Existing nodes of the type are kept, but no new nodes can be created with it.
   */
  fun deleteNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ) {
    if (!graphRepository.softDeleteNodeType(nodeTypeId, workspaceId, OffsetDateTime.now())) {
      throw NoSuchNodeTypeException(nodeTypeId)
    }
  }

  /**
   * Soft-deletes an edge type. Existing edges of the type are kept, but no new edges can be created with it.
   */
  fun deleteEdgeType(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
  ) {
    if (!graphRepository.softDeleteEdgeType(edgeTypeId, workspaceId, OffsetDateTime.now())) {
      throw NoSuchEdgeTypeException(edgeTypeId)
    }
  }

  /**
   * Soft-deletes a node and all edges connected to it.
   */
  fun deleteNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ) {
    if (!graphRepository.softDeleteNode(nodeId, workspaceId, OffsetDateTime.now())) {
      throw NoSuchNodeException(nodeId)
    }
  }

  fun deleteEdge(
    edgeId: ResourceId,
    workspaceId: ResourceId,
  ) {
    if (!graphRepository.softDeleteEdge(edgeId, workspaceId, OffsetDateTime.now())) {
      throw NoSuchEdgeException(edgeId)
    }
  }

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
