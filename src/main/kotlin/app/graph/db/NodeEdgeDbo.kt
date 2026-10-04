package app.graph.db

import db.tables.pojos.Edge
import db.tables.pojos.EdgeType
import db.tables.pojos.Node

data class NodeEdgeDbo(
  val edge: Edge,
  val edgeType: EdgeType,
  val fromNode: Node,
  val toNode: Node,
)
