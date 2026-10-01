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
import db.tables.references.NODE
import org.jooq.DSLContext

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

  fun findNodeById(nodeId: ResourceId): Node? = nodeDao.findById(nodeId)

  fun findEdgeNodesFromNode(nodeId: ResourceId): List<NodeEdgeDbo> {
    val edge = EDGE.`as`("edge")
    val fromNode = NODE.`as`("from_node")
    val toNode = NODE.`as`("to_node")
    return dsl
      .select(*edge.fields(), *fromNode.fields(), *toNode.fields())
      .from(edge)
      .innerJoin(fromNode)
      .on(edge.FROM_NODE.eq(fromNode.ID))
      .innerJoin(toNode)
      .on(edge.TO_NODE.eq(toNode.ID))
      .where(
        edge.FROM_NODE
          .eq(nodeId)
          .or(edge.TO_NODE.eq(nodeId)),
      ).fetch { row ->
        NodeEdgeDbo(
          edge = row.into(edge).into(Edge::class.java),
          fromNode = row.into(fromNode).into(Node::class.java),
          toNode = row.into(toNode).into(Node::class.java),
        )
      }
  }
}
