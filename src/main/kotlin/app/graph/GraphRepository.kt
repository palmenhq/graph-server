package app.graph

import app.graph.db.NodeEdgeDbo
import app.utils.ResourceId
import app.utils.http.Cursor
import db.tables.daos.EdgeDao
import db.tables.daos.EdgeTypeDao
import db.tables.daos.NodeDao
import db.tables.daos.NodeTypeDao
import db.tables.pojos.Edge
import db.tables.pojos.EdgeType
import db.tables.pojos.Node
import db.tables.pojos.NodeType
import db.tables.references.EDGE
import db.tables.references.EDGE_TYPE
import db.tables.references.NODE
import db.tables.references.NODE_TYPE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.lower
import java.time.OffsetDateTime

class GraphRepository(
  private val dsl: DSLContext,
  private val nodeDao: NodeDao = NodeDao(dsl.configuration()),
  private val nodeTypeDao: NodeTypeDao = NodeTypeDao(dsl.configuration()),
  private val edgeDao: EdgeDao = EdgeDao(dsl.configuration()),
  private val edgeTypeDao: EdgeTypeDao = EdgeTypeDao(dsl.configuration()),
) {
  fun createNodeType(nodeType: NodeType) {
    nodeTypeDao.insert(nodeType)
  }

  fun createEdgeType(edgeType: EdgeType) {
    edgeTypeDao.insert(edgeType)
  }

  fun createNode(node: Node) {
    nodeDao.insert(node)
  }

  fun createEdge(edge: Edge) {
    edgeDao.insert(edge)
  }

  fun findNodeById(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): Node? =
    dsl
      .selectFrom(NODE)
      .where(NODE.ID.eq(nodeId))
      .and(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE.DELETED_AT.isNull)
      .fetchOneInto(Node::class.java)

  fun findNodes(
    workspaceId: ResourceId,
    nodeTypeId: ResourceId? = null,
    searchQuery: String? = null,
    cursor: Cursor? = null,
    limit: Int,
  ): List<Node> =
    dsl
      .selectFrom(NODE)
      .where(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE.DELETED_AT.isNull)
      .and(if (nodeTypeId != null) NODE.TYPE.eq(nodeTypeId) else DSL.noCondition())
      .and(if (searchQuery != null) lower(NODE.TITLE).likeIgnoreCase(lower("%$searchQuery%")) else DSL.noCondition())
      .and(cursor?.after(NODE.CREATED_AT, NODE.ID) ?: DSL.noCondition())
      .orderBy(NODE.CREATED_AT.desc(), NODE.ID)
      .limit(limit)
      .fetchInto(Node::class.java)

  fun findNodeTypeById(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): NodeType? =
    dsl
      .selectFrom(NODE_TYPE)
      .where(NODE_TYPE.ID.eq(nodeTypeId))
      .and(NODE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_TYPE.DELETED_AT.isNull)
      .fetchOneInto(NodeType::class.java)

  fun findNodeTypes(workspaceId: ResourceId): List<NodeType> =
    dsl
      .selectFrom(NODE_TYPE)
      .where(NODE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_TYPE.DELETED_AT.isNull)
      .orderBy(NODE_TYPE.CREATED_AT, NODE_TYPE.ID)
      .fetchInto(NodeType::class.java)

  fun findEdgeTypeById(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): EdgeType? =
    dsl
      .selectFrom(EDGE_TYPE)
      .where(EDGE_TYPE.ID.eq(edgeTypeId))
      .and(EDGE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE_TYPE.DELETED_AT.isNull)
      .fetchOneInto(EdgeType::class.java)

  fun findEdgeTypes(workspaceId: ResourceId): List<EdgeType> =
    dsl
      .selectFrom(EDGE_TYPE)
      .where(EDGE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE_TYPE.DELETED_AT.isNull)
      .orderBy(EDGE_TYPE.CREATED_AT, EDGE_TYPE.ID)
      .fetchInto(EdgeType::class.java)

  fun findEdgeNodesFromNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): List<NodeEdgeDbo> {
    val edge = EDGE.`as`("edge")
    val fromNode = NODE.`as`("from_node")
    val toNode = NODE.`as`("to_node")
    val edgeType = EDGE_TYPE.`as`("edge_type")
    return dsl
      .select(*edge.fields(), *edgeType.fields(), *fromNode.fields(), *toNode.fields())
      .from(edge)
      .innerJoin(fromNode)
      .on(edge.FROM_NODE.eq(fromNode.ID))
      .innerJoin(toNode)
      .on(edge.TO_NODE.eq(toNode.ID))
      .innerJoin(edgeType)
      .on(edge.TYPE.eq(edgeType.ID))
      .where(
        edge.FROM_NODE
          .eq(nodeId)
          .or(edge.TO_NODE.eq(nodeId)),
      ).and(fromNode.WORKSPACE_ID.eq(workspaceId))
      .and(toNode.WORKSPACE_ID.eq(workspaceId))
      .and(edge.DELETED_AT.isNull)
      .and(fromNode.DELETED_AT.isNull)
      .and(toNode.DELETED_AT.isNull)
      .fetch { row ->
        NodeEdgeDbo(
          edge = row.into(edge).into(Edge::class.java),
          edgeType = row.into(edgeType).into(EdgeType::class.java),
          fromNode = row.into(fromNode).into(Node::class.java),
          toNode = row.into(toNode).into(Node::class.java),
        )
      }
  }

  fun findEdgeById(
    edgeId: ResourceId,
    workspaceId: ResourceId,
  ): Edge? =
    dsl
      .selectFrom(EDGE)
      .where(EDGE.ID.eq(edgeId))
      .and(EDGE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE.DELETED_AT.isNull)
      .fetchOneInto(Edge::class.java)

  fun updateNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
    title: String,
  ): Boolean =
    dsl
      .update(NODE_TYPE)
      .set(NODE_TYPE.TITLE, title)
      .where(NODE_TYPE.ID.eq(nodeTypeId))
      .and(NODE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_TYPE.DELETED_AT.isNull)
      .execute() > 0

  fun updateEdgeType(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
    relationshipTitle: String,
    reverseRelationshipTitle: String?,
  ): Boolean =
    dsl
      .update(EDGE_TYPE)
      .set(EDGE_TYPE.RELATIONSHIP_TITLE, relationshipTitle)
      .set(EDGE_TYPE.REVERSE_RELATIONSHIP_TITLE, reverseRelationshipTitle)
      .where(EDGE_TYPE.ID.eq(edgeTypeId))
      .and(EDGE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE_TYPE.DELETED_AT.isNull)
      .execute() > 0

  fun updateNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
    title: String,
  ): Boolean =
    dsl
      .update(NODE)
      .set(NODE.TITLE, title)
      .where(NODE.ID.eq(nodeId))
      .and(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE.DELETED_AT.isNull)
      .execute() > 0

  fun updateEdge(
    edgeId: ResourceId,
    workspaceId: ResourceId,
    title: String?,
  ): Boolean =
    dsl
      .update(EDGE)
      .set(EDGE.TITLE, title)
      .where(EDGE.ID.eq(edgeId))
      .and(EDGE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE.DELETED_AT.isNull)
      .execute() > 0

  fun softDeleteNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
    deletedAt: OffsetDateTime,
  ): Boolean =
    dsl
      .update(NODE_TYPE)
      .set(NODE_TYPE.DELETED_AT, deletedAt)
      .where(NODE_TYPE.ID.eq(nodeTypeId))
      .and(NODE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_TYPE.DELETED_AT.isNull)
      .execute() > 0

  fun softDeleteEdgeType(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
    deletedAt: OffsetDateTime,
  ): Boolean =
    dsl
      .update(EDGE_TYPE)
      .set(EDGE_TYPE.DELETED_AT, deletedAt)
      .where(EDGE_TYPE.ID.eq(edgeTypeId))
      .and(EDGE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE_TYPE.DELETED_AT.isNull)
      .execute() > 0

  /**
   * Soft-deletes the node together with all of its (not yet deleted) edges.
   */
  fun softDeleteNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
    deletedAt: OffsetDateTime,
  ): Boolean =
    dsl.transactionResult { config ->
      val tx = DSL.using(config)
      val deleted =
        tx
          .update(NODE)
          .set(NODE.DELETED_AT, deletedAt)
          .where(NODE.ID.eq(nodeId))
          .and(NODE.WORKSPACE_ID.eq(workspaceId))
          .and(NODE.DELETED_AT.isNull)
          .execute() > 0

      if (deleted) {
        tx
          .update(EDGE)
          .set(EDGE.DELETED_AT, deletedAt)
          .where(EDGE.FROM_NODE.eq(nodeId).or(EDGE.TO_NODE.eq(nodeId)))
          .and(EDGE.WORKSPACE_ID.eq(workspaceId))
          .and(EDGE.DELETED_AT.isNull)
          .execute()
      }

      deleted
    }

  fun softDeleteEdge(
    edgeId: ResourceId,
    workspaceId: ResourceId,
    deletedAt: OffsetDateTime,
  ): Boolean =
    dsl
      .update(EDGE)
      .set(EDGE.DELETED_AT, deletedAt)
      .where(EDGE.ID.eq(edgeId))
      .and(EDGE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE.DELETED_AT.isNull)
      .execute() > 0
}
