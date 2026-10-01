package app.graph

import app.graph.dtos.CreateEdgeRequest
import app.graph.dtos.CreateEdgeTypeRequest
import app.graph.dtos.CreateNodeRequest
import app.graph.dtos.CreateNodeTypeRequest
import app.graph.dtos.toDto
import app.graph.dtos.toFullDto
import app.utils.http.ClientError
import app.utils.http.ClientException
import app.utils.http.ListResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private val ApplicationCall.workspaceId: String
  get() = parameters["workspaceId"]!!

fun Route.graphRoutes(graphService: GraphService) {
  route("workspaces/{workspaceId}/graph") {
    route("nodeTypes") {
      post("create") {
        val req = call.receive<CreateNodeTypeRequest>()
        val result =
          try {
            graphService.createNodeType(req.name, call.workspaceId)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }
    }

    route("edgeTypes") {
      post("create") {
        val req = call.receive<CreateEdgeTypeRequest>()
        val result =
          try {
            graphService.createEdgeType(
              sourceNodeType = req.sourceNodeType,
              targetNodeType = req.targetNodeType,
              relationshipName = req.relationshipName,
              reverseRelationshipName = req.reverseRelationshipName,
              workspaceId = call.workspaceId,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }
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

      get("getById") {
        val id =
          call.queryParameters["id"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing id", code = "missing_id"),
            )

        val node =
          graphService.getNodeById(id)?.takeIf { it.workspaceId == call.workspaceId } ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Node not found", code = "node_not_found"),
          )

        val edgeNodes = graphService.getEdgeNodesFromNode(id).filter { it.edge.workspaceId == call.workspaceId }
        val edges = edgeNodes.map { it.edge }
        val neighbors =
          edgeNodes
            .map { if (it.fromNode.id == id) it.toNode else it.fromNode }
            .distinctBy { it.id }

        call.respond(node.toFullDto(edges, neighbors))
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
            .getEdgeNodesFromNode(id)
            .filter { it.edge.workspaceId == call.workspaceId }
            .map { it.edge.toDto() }
        call.respond(ListResponse(edges))
      }
    }

    route("edges") {
      post("create") {
        val req = call.receive<CreateEdgeRequest>()

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

        call.respond(result.toDto())
      }
    }
  }
}
