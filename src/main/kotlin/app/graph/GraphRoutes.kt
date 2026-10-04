package app.graph

import app.graph.dtos.CreateEdgeRequest
import app.graph.dtos.CreateEdgeTypeRequest
import app.graph.dtos.CreateNodeRequest
import app.graph.dtos.CreateNodeTypeRequest
import app.graph.dtos.DeleteRequest
import app.graph.dtos.UpdateEdgeRequest
import app.graph.dtos.UpdateEdgeTypeRequest
import app.graph.dtos.UpdateNodeRequest
import app.graph.dtos.UpdateNodeTypeRequest
import app.graph.dtos.toDto
import app.graph.dtos.toEdgeDto
import app.graph.dtos.toFullDto
import app.graph.dtos.toNeighborDto
import app.utils.http.ClientError
import app.utils.http.ClientException
import app.utils.http.ListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val ApplicationCall.workspaceId: String
  get() = parameters["workspaceId"]!!

private fun Route.deleteRoute(delete: (id: String, workspaceId: String) -> Unit) {
  post("delete") {
    val req = call.receive<DeleteRequest>()
    try {
      delete(req.id, call.workspaceId)
    } catch (e: ClientException) {
      return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
    }

    call.respond(HttpStatusCode.NoContent)
  }
}

fun Route.graphRoutes(graphService: GraphService) {
  route("workspaces/{workspaceId}/graph") {
    route("nodeTypes") {
      post("create") {
        val req = call.receive<CreateNodeTypeRequest>()
        val result =
          try {
            graphService.createNodeType(title = req.title, workspaceId = call.workspaceId)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      get("getAll") {
        call.respond(
          ListResponse(graphService.getNodeTypes(call.workspaceId).map { it.toDto() }),
        )
      }

      get("getById") {
        val id =
          call.queryParameters["id"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing id", code = "missing_id"),
            )

        val nodeType =
          graphService
            .getNodeTypeById(nodeTypeId = id, workspaceId = call.workspaceId) ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Node type not found", code = "node_type_not_found"),
          )

        call.respond(nodeType.toDto())
      }

      patch("update") {
        val req = call.receive<UpdateNodeTypeRequest>()
        val result =
          try {
            graphService.updateNodeType(nodeTypeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@patch call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      deleteRoute { id, workspaceId -> graphService.deleteNodeType(nodeTypeId = id, workspaceId = workspaceId) }
    }

    route("edgeTypes") {
      post("create") {
        val req = call.receive<CreateEdgeTypeRequest>()
        val result =
          try {
            graphService.createEdgeType(
              relationshipTitle = req.relationshipTitle,
              reverseRelationshipTitle = req.reverseRelationshipTitle,
              workspaceId = call.workspaceId,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      get("getAll") {
        call.respond(
          ListResponse(graphService.getEdgeTypes(call.workspaceId).map { it.toDto() }),
        )
      }

      get("getById") {
        val id =
          call.queryParameters["id"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing id", code = "missing_id"),
            )

        val edgeType =
          graphService
            .getEdgeTypeById(edgeTypeId = id, workspaceId = call.workspaceId) ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Edge type not found", code = "edge_type_not_found"),
          )

        call.respond(edgeType.toDto())
      }

      patch("update") {
        val req = call.receive<UpdateEdgeTypeRequest>()
        val result =
          try {
            graphService.updateEdgeType(
              edgeTypeId = req.id,
              workspaceId = call.workspaceId,
              relationshipTitle = req.relationshipTitle,
              reverseRelationshipTitle = req.reverseRelationshipTitle,
            )
          } catch (e: ClientException) {
            return@patch call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      deleteRoute { id, workspaceId -> graphService.deleteEdgeType(edgeTypeId = id, workspaceId = workspaceId) }
    }

    route("nodes") {
      post("create") {
        val req = call.receive<CreateNodeRequest>()

        val result =
          try {
            graphService.createNode(title = req.title, nodeTypeId = req.nodeTypeId, workspaceId = call.workspaceId)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toFullDto(emptyList(), emptyList()))
      }

      get("getAll") {
        val nodeTypeId = call.queryParameters["nodeTypeId"]
        val searchQuery = call.queryParameters["search"]
        val nodes =
          graphService
            .getNodes(workspaceId = call.workspaceId, nodeTypeId = nodeTypeId, searchQuery = searchQuery)
            .map { it.toNeighborDto() }
        call.respond(ListResponse(nodes))
      }

      get("getById") {
        val id =
          call.queryParameters["id"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing id", code = "missing_id"),
            )

        val node =
          graphService
            .getNodeById(nodeId = id, workspaceId = call.workspaceId) ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Node not found", code = "node_not_found"),
          )

        val edgeNodes =
          graphService
            .getEdgeNodesFromNode(id = id, workspaceId = call.workspaceId)
        val neighbors =
          edgeNodes
            .map { if (it.fromNode.id == id) it.toNode else it.fromNode }
            .distinctBy { it.id }

        call.respond(node.toFullDto(edgeNodes, neighbors))
      }

      get("getEdges") {
        val id =
          call.queryParameters["id"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing id", code = "missing_id"),
            )

        val edges =
          graphService
            .getEdgeNodesFromNode(id = id, workspaceId = call.workspaceId)
            .map { it.toEdgeDto() }
        call.respond(ListResponse(edges))
      }

      patch("update") {
        val req = call.receive<UpdateNodeRequest>()
        val result =
          try {
            graphService.updateNode(nodeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@patch call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        val edgeNodes = graphService.getEdgeNodesFromNode(id = req.id, workspaceId = call.workspaceId)
        val neighbors =
          edgeNodes
            .map { if (it.fromNode.id == req.id) it.toNode else it.fromNode }
            .distinctBy { it.id }

        call.respond(result.toFullDto(edgeNodes, neighbors))
      }

      deleteRoute { id, workspaceId -> graphService.deleteNode(nodeId = id, workspaceId = workspaceId) }
    }

    route("edges") {
      post("create") {
        val req = call.receive<CreateEdgeRequest>()

        val edgeType =
          graphService
            .getEdgeTypeById(edgeTypeId = req.edgeTypeId, workspaceId = call.workspaceId) ?: return@post call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Edge type not found", code = "edge_type_not_found"),
          )

        val result =
          try {
            graphService.createEdge(
              edgeTypeId = req.edgeTypeId,
              fromNode = req.fromNode,
              toNode = req.toNode,
              workspaceId = call.workspaceId,
              title = req.title,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto(edgeType))
      }

      patch("update") {
        val req = call.receive<UpdateEdgeRequest>()
        val result =
          try {
            graphService.updateEdge(edgeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@patch call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        // Resolved via the edge's nodes so that edges of soft-deleted edge types are still returned
        val edgeNode =
          graphService
            .getEdgeNodesFromNode(id = result.fromNode!!, workspaceId = call.workspaceId)
            .first { it.edge.id == result.id }

        call.respond(edgeNode.toEdgeDto())
      }

      deleteRoute { id, workspaceId -> graphService.deleteEdge(edgeId = id, workspaceId = workspaceId) }
    }
  }
}
