package app.graph

import app.graph.db.NodeEdgeDbo
import app.utils.ResourceId
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
  ): Node? = nodeDao.findById(nodeId)?.takeIf { it.workspaceId == workspaceId }

  fun findNodes(
    workspaceId: ResourceId,
    nodeTypeId: ResourceId? = null,
    searchQuery: String? = null,
  ): List<Node> =
    dsl
      .selectFrom(NODE)
      .where(NODE.WORKSPACE_ID.eq(workspaceId))
      .and(if (nodeTypeId != null) NODE.TYPE.eq(nodeTypeId) else DSL.noCondition())
      .and(if (searchQuery != null) lower(NODE.TITLE).likeIgnoreCase(lower("%$searchQuery%")) else DSL.noCondition())
      .orderBy(NODE.CREATED_AT.desc(), NODE.ID)
      .fetchInto(Node::class.java)

  fun findNodeTypeById(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): NodeType? = nodeTypeDao.findById(nodeTypeId)?.takeIf { it.workspaceId == workspaceId }

  fun findNodeTypes(workspaceId: ResourceId): List<NodeType> =
    dsl
      .selectFrom(NODE_TYPE)
      .where(NODE_TYPE.WORKSPACE_ID.eq(workspaceId))
      .orderBy(NODE_TYPE.CREATED_AT, NODE_TYPE.ID)
      .fetchInto(NodeType::class.java)

  fun findEdgeTypeById(
    edgeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): EdgeType? = edgeTypeDao.findById(edgeTypeId)?.takeIf { it.workspaceId == workspaceId }

  fun findEdgeTypes(workspaceId: ResourceId): List<EdgeType> =
    dsl
      .selectFrom(EDGE_TYPE)
      .where(EDGE_TYPE.WORKSPACE_ID.eq(workspaceId))
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
      .fetch { row ->
        NodeEdgeDbo(
          edge = row.into(edge).into(Edge::class.java),
          edgeType = row.into(edgeType).into(EdgeType::class.java),
          fromNode = row.into(fromNode).into(Node::class.java),
          toNode = row.into(toNode).into(Node::class.java),
        )
      }
  }
}
