package app.graph

import app.graph.db.NodeEdgeDbo
import app.graph.db.PreferredEdgeTypeDbo
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
import db.tables.references.NODE_TYPE_PREFERRED_EDGE_TYPE
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import java.time.OffsetDateTime

class GraphRepository(
  private val dsl: DSLContext,
  private val nodeTypeDao: NodeTypeDao = NodeTypeDao(dsl.configuration()),
  private val edgeDao: EdgeDao = EdgeDao(dsl.configuration()),
  private val edgeTypeDao: EdgeTypeDao = EdgeTypeDao(dsl.configuration()),
) {
  private val edgeAlias = EDGE.`as`("edge")
  private val fromNodeAlias = NODE.`as`("from_node")
  private val toNodeAlias = NODE.`as`("to_node")
  private val edgeTypeAlias = EDGE_TYPE.`as`("edge_type")
  private val preferredEdgeTypeAlias = NODE_TYPE_PREFERRED_EDGE_TYPE.`as`("preferred_edge_type")

  fun createNodeType(nodeType: NodeType) {
    nodeTypeDao.insert(nodeType)
  }

  fun createEdgeType(edgeType: EdgeType) {
    edgeTypeDao.insert(edgeType)
  }

  fun createNode(node: Node) {
    dsl.transaction { configuration ->
      NodeDao(configuration).insert(node)
      NodeSearch.refreshSearchVector(DSL.using(configuration), node.id!!)
    }
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
      .fetchOneInto(Node::class.java)

  fun findNodes(
    workspaceId: ResourceId,
    nodeTypeId: ResourceId? = null,
    searchQuery: String? = null,
    cursor: Cursor? = null,
    limit: Int,
    includeDeleted: Boolean = false,
  ): List<Node> =
    dsl
      .selectFrom(NODE)
      .where(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(if (includeDeleted) DSL.noCondition() else NODE.DELETED_AT.isNull)
      .and(if (nodeTypeId != null) NODE.TYPE.eq(nodeTypeId) else DSL.noCondition())
      .and(if (searchQuery != null) NodeSearch.matches(searchQuery) else DSL.noCondition())
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

  fun findPreferredEdgeTypes(
    nodeTypeIds: Collection<ResourceId>,
    workspaceId: ResourceId,
  ): Map<ResourceId, List<PreferredEdgeTypeDbo>> {
    if (nodeTypeIds.isEmpty()) return emptyMap()

    return dsl
      .select(
        preferredEdgeTypeAlias.NODE_TYPE_ID,
        preferredEdgeTypeAlias.EDGE_TYPE_ID,
        preferredEdgeTypeAlias.REVERSE,
      ).from(preferredEdgeTypeAlias)
      .innerJoin(edgeTypeAlias)
      .on(preferredEdgeTypeAlias.EDGE_TYPE_ID.eq(edgeTypeAlias.ID))
      .where(preferredEdgeTypeAlias.NODE_TYPE_ID.`in`(nodeTypeIds))
      .and(preferredEdgeTypeAlias.WORKSPACE_ID.eq(workspaceId))
      .and(edgeTypeAlias.WORKSPACE_ID.eq(workspaceId))
      .and(edgeTypeAlias.DELETED_AT.isNull)
      .orderBy(preferredEdgeTypeAlias.NODE_TYPE_ID, preferredEdgeTypeAlias.POSITION)
      .fetch()
      .groupBy(
        { it.get(preferredEdgeTypeAlias.NODE_TYPE_ID)!! },
        {
          PreferredEdgeTypeDbo(
            edgeTypeId = it.get(preferredEdgeTypeAlias.EDGE_TYPE_ID)!!,
            reverse = it.get(preferredEdgeTypeAlias.REVERSE)!!,
          )
        },
      )
  }

  fun replacePreferredEdgeTypes(
    nodeTypeId: ResourceId,
    preferredEdgeTypes: List<PreferredEdgeTypeDbo>,
    workspaceId: ResourceId,
  ) {
    dsl.transaction { configuration ->
      val tx = DSL.using(configuration)
      tx
        .deleteFrom(NODE_TYPE_PREFERRED_EDGE_TYPE)
        .where(NODE_TYPE_PREFERRED_EDGE_TYPE.NODE_TYPE_ID.eq(nodeTypeId))
        .execute()

      preferredEdgeTypes.forEachIndexed { position, preferredEdgeType ->
        tx
          .insertInto(NODE_TYPE_PREFERRED_EDGE_TYPE)
          .set(NODE_TYPE_PREFERRED_EDGE_TYPE.NODE_TYPE_ID, nodeTypeId)
          .set(NODE_TYPE_PREFERRED_EDGE_TYPE.EDGE_TYPE_ID, preferredEdgeType.edgeTypeId)
          .set(NODE_TYPE_PREFERRED_EDGE_TYPE.REVERSE, preferredEdgeType.reverse)
          .set(NODE_TYPE_PREFERRED_EDGE_TYPE.POSITION, position)
          .set(NODE_TYPE_PREFERRED_EDGE_TYPE.WORKSPACE_ID, workspaceId)
          .execute()
      }
    }
  }

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
  ): List<NodeEdgeDbo> =
    selectEdgesWithNodes(
      workspaceId,
      edgeAlias.FROM_NODE
        .eq(nodeId)
        .or(edgeAlias.TO_NODE.eq(nodeId)),
    ).fetch { it.toNodeEdgeDbo() }

  fun findEdgeById(
    edgeId: ResourceId,
    workspaceId: ResourceId,
  ): Edge? =
    dsl
      .selectFrom(EDGE)
      .where(EDGE.ID.eq(edgeId))
      .and(EDGE.WORKSPACE_ID.eq(workspaceId))
      .and(EDGE.DELETED_AT.isNull)
      .and(
        DSL.exists(
          dsl
            .selectOne()
            .from(fromNodeAlias)
            .where(fromNodeAlias.ID.eq(EDGE.FROM_NODE))
            .and(fromNodeAlias.WORKSPACE_ID.eq(workspaceId))
            .and(fromNodeAlias.DELETED_AT.isNull),
        ),
      ).and(
        DSL.exists(
          dsl
            .selectOne()
            .from(toNodeAlias)
            .where(toNodeAlias.ID.eq(EDGE.TO_NODE))
            .and(toNodeAlias.WORKSPACE_ID.eq(workspaceId))
            .and(toNodeAlias.DELETED_AT.isNull),
        ),
      ).fetchOneInto(Edge::class.java)

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
    dsl.transactionResult { configuration ->
      val tx = DSL.using(configuration)
      val updated =
        tx
          .update(NODE)
          .set(NODE.TITLE, title)
          .where(NODE.ID.eq(nodeId))
          .and(NODE.WORKSPACE_ID.eq(workspaceId))
          .and(NODE.DELETED_AT.isNull)
          .execute() > 0
      if (updated) NodeSearch.refreshSearchVector(tx, nodeId)
      updated
    }

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
   * Soft-deletes the node.
   */
  fun softDeleteNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
    deletedAt: OffsetDateTime,
  ): Boolean =
    dsl
      .update(NODE)
      .set(NODE.DELETED_AT, deletedAt)
      .where(NODE.ID.eq(nodeId))
      .and(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE.DELETED_AT.isNull)
      .execute() > 0

  fun restoreNode(
    nodeId: ResourceId,
    workspaceId: ResourceId,
  ): Boolean =
    dsl
      .update(NODE)
      .set(NODE.DELETED_AT, null as OffsetDateTime?)
      .where(NODE.ID.eq(nodeId))
      .and(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(NODE.DELETED_AT.isNotNull)
      .execute() > 0

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

  fun findEdgeWithNodesByEdgeId(
    edgeId: ResourceId,
    workspaceId: ResourceId,
  ): NodeEdgeDbo? =
    selectEdgesWithNodes(workspaceId, edgeAlias.ID.eq(edgeId))
      .fetchOne { it.toNodeEdgeDbo() }

  private fun selectEdgesWithNodes(
    workspaceId: ResourceId,
    condition: Condition,
  ) = dsl
    .select(*edgeAlias.fields(), *edgeTypeAlias.fields(), *fromNodeAlias.fields(), *toNodeAlias.fields())
    .from(edgeAlias)
    .innerJoin(fromNodeAlias)
    .on(edgeAlias.FROM_NODE.eq(fromNodeAlias.ID))
    .innerJoin(toNodeAlias)
    .on(edgeAlias.TO_NODE.eq(toNodeAlias.ID))
    .innerJoin(edgeTypeAlias)
    .on(edgeAlias.TYPE.eq(edgeTypeAlias.ID))
    .where(condition)
    .and(fromNodeAlias.WORKSPACE_ID.eq(workspaceId))
    .and(toNodeAlias.WORKSPACE_ID.eq(workspaceId))
    .and(edgeAlias.DELETED_AT.isNull)
    .and(fromNodeAlias.DELETED_AT.isNull)
    .and(toNodeAlias.DELETED_AT.isNull)
    .orderBy(edgeAlias.CREATED_AT, edgeAlias.ID)

  private fun Record.toNodeEdgeDbo() =
    NodeEdgeDbo(
      edge = into(edgeAlias).into(Edge::class.java),
      edgeType = into(edgeTypeAlias).into(EdgeType::class.java),
      fromNode = into(fromNodeAlias).into(Node::class.java),
      toNode = into(toNodeAlias).into(Node::class.java),
    )
}
