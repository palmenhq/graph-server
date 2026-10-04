package app.graph

import app.graph.dtos.CreateEdgeRequest
import app.graph.dtos.CreateEdgeTypeRequest
import app.graph.dtos.CreateNodeRequest
import app.graph.dtos.CreateNodeTypeRequest
import app.graph.dtos.CreatePropertyRequest
import app.graph.dtos.DeletePropertyRequest
import app.graph.dtos.DeleteRequest
import app.graph.dtos.RenamePropertyRequest
import app.graph.dtos.SetPropertyValueRequest
import app.graph.dtos.UpdateEdgeRequest
import app.graph.dtos.UpdateEdgeTypeRequest
import app.graph.dtos.UpdateNodeRequest
import app.graph.dtos.UpdateNodeTypeRequest
import app.graph.dtos.toDto
import app.graph.dtos.toEdgeDto
import app.graph.dtos.toFullDto
import app.graph.dtos.toNeighborDto
import app.graph.dtos.toRecord
import app.utils.http.ClientError
import app.utils.http.ClientException
import app.utils.http.Cursor
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

fun Route.graphRoutes(
  graphService: GraphService,
  propertyService: PropertyService,
) {
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

      post("update") {
        val req = call.receive<UpdateNodeTypeRequest>()
        val result =
          try {
            graphService.updateNodeType(nodeTypeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      deleteRoute { id, workspaceId -> graphService.deleteNodeType(nodeTypeId = id, workspaceId = workspaceId) }

      get("getProperties") {
        val nodeTypeId =
          call.queryParameters["nodeTypeId"]
            ?: return@get call.respond(
              status = HttpStatusCode.UnprocessableEntity,
              message = ClientError(description = "Missing nodeTypeId", code = "missing_node_type_id"),
            )

        graphService.getNodeTypeById(nodeTypeId = nodeTypeId, workspaceId = call.workspaceId)
          ?: return@get call.respond(
            status = HttpStatusCode.UnprocessableEntity,
            message = ClientError(description = "Node type not found", code = "node_type_not_found"),
          )

        call.respond(
          ListResponse(
            propertyService
              .getPropertiesByNodeType(nodeTypeId = nodeTypeId, workspaceId = call.workspaceId)
              .map { it.toDto() },
          ),
        )
      }

      post("createProperty") {
        val req = call.receive<CreatePropertyRequest>()
        val result =
          try {
            propertyService.createProperty(
              nodeTypeId = req.nodeTypeId,
              name = req.name,
              valueType = req.valueType.toRecord(),
              workspaceId = call.workspaceId,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      post("renameProperty") {
        val req = call.receive<RenamePropertyRequest>()
        val result =
          try {
            propertyService.renameProperty(
              nodeTypeId = req.nodeTypeId,
              propertyId = req.propertyId,
              name = req.name,
              workspaceId = call.workspaceId,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toDto())
      }

      post("deleteProperty") {
        val req = call.receive<DeletePropertyRequest>()
        val result =
          try {
            propertyService.deleteProperty(
              nodeTypeId = req.nodeTypeId,
              propertyId = req.propertyId,
              workspaceId = call.workspaceId,
            )
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

      post("update") {
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
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
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

        call.respond(
          result.toFullDto(
            edges = emptyList(),
            neighbors = emptyList(),
            properties = propertyService.getPropertyValuesByNode(result, call.workspaceId),
          ),
        )
      }

      get("getAll") {
        val nodeTypeId = call.queryParameters["nodeTypeId"]
        val searchQuery = call.queryParameters["search"]
        val page =
          try {
            graphService.getNodes(
              workspaceId = call.workspaceId,
              nodeTypeId = nodeTypeId,
              searchQuery = searchQuery,
              cursor = call.queryParameters["cursor"]?.let { Cursor.decode(it) },
            )
          } catch (e: ClientException) {
            return@get call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }
        call.respond(page.map { it.toNeighborDto() })
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

        call.respond(
          node.toFullDto(
            edgeNodes,
            neighbors,
            propertyService.getPropertyValuesByNode(node, call.workspaceId),
          ),
        )
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

      post("update") {
        val req = call.receive<UpdateNodeRequest>()
        val result =
          try {
            graphService.updateNode(nodeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        val edgeNodes = graphService.getEdgeNodesFromNode(id = req.id, workspaceId = call.workspaceId)
        val neighbors =
          edgeNodes
            .map { if (it.fromNode.id == req.id) it.toNode else it.fromNode }
            .distinctBy { it.id }

        call.respond(
          result.toFullDto(
            edgeNodes,
            neighbors,
            propertyService.getPropertyValuesByNode(result, call.workspaceId),
          ),
        )
      }

      deleteRoute { id, workspaceId -> graphService.deleteNode(nodeId = id, workspaceId = workspaceId) }

      post("setProperty") {
        val req = call.receive<SetPropertyValueRequest>()
        val result =
          try {
            propertyService.setPropertyValue(
              nodeId = req.nodeId,
              propertyId = req.propertyId,
              value = req.value,
              workspaceId = call.workspaceId,
            )
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.second.toDto(name = result.first.name!!))
      }
    }

    route("edges") {
      post("create") {
        val req = call.receive<CreateEdgeRequest>()

        graphService
          .getEdgeTypeById(edgeTypeId = req.edgeTypeId, workspaceId = call.workspaceId) ?: return@post call.respond(
          status = HttpStatusCode.UnprocessableEntity,
          message = ClientError(description = "Edge type not found", code = "edge_type_not_found"),
        )

        val result =
          try {
            val edge =
              graphService.createEdge(
                edgeTypeId = req.edgeTypeId,
                fromNode = req.fromNode,
                toNode = req.toNode,
                workspaceId = call.workspaceId,
                title = req.title,
              )
            graphService.getEdgeWithNodesByEdgeId(edgeId = edge.id!!, workspaceId = call.workspaceId)!!
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
          }

        call.respond(result.toEdgeDto())
      }

      post("update") {
        val req = call.receive<UpdateEdgeRequest>()
        val result =
          try {
            graphService.updateEdge(edgeId = req.id, workspaceId = call.workspaceId, title = req.title)
          } catch (e: ClientException) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, e.toError())
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
